package com.adaptris.rest;

import java.util.HashMap;
import java.util.Map;

import com.adaptris.core.CoreException;
import com.adaptris.core.http.jetty.HttpConnection;
import com.adaptris.core.http.jetty.HttpConnection.HttpConfigurationProperty;
import com.adaptris.core.http.jetty.ServletWrapper;
import com.adaptris.core.util.LifecycleHelper;
import com.adaptris.util.KeyValuePair;

import lombok.extern.slf4j.Slf4j;

/**
 * Registry of the dedicated Jetty servers used by management components that are configured with a management port.
 * <p>
 * Each port has at most one server, backed by a standalone {@link HttpConnection}; it is deliberately <strong>not</strong> registered
 * with {@code JettyServerManager}, so that it has no effect on the main embedded Jetty instance or on anything using
 * {@code EmbeddedConnection}. Several {@link ManagementPortConnection} instances can share a server; its lifecycle is reference
 * counted so that the underlying server is only initialised/started by the first user and only stopped/closed by the last.
 * </p>
 */
@Slf4j
final class ManagementServers {

  private static final Object LOCK = new Object();
  private static final Map<Integer, SharedServer> SERVERS = new HashMap<>();

  private ManagementServers() {
  }

  /**
   * Register a user of the management server on the given port, creating (prepare + init) the server if this is the first user.
   * <p>
   * The server is not listening until {@link #start(int)} is called. If creating the server fails, nothing is registered.
   * </p>
   *
   * @param port the port.
   * @throws CoreException if the server could not be created.
   */
  static void init(int port) throws CoreException {
    synchronized (LOCK) {
      SharedServer server = SERVERS.get(port);
      if (server == null) {
        HttpConnection connection = createConnection(port);
        LifecycleHelper.prepare(connection);
        LifecycleHelper.init(connection);
        server = new SharedServer(connection);
        SERVERS.put(port, server);
        log.debug("Created management server on port {}", port);
      }
      server.initCount++;
    }
  }

  /**
   * Start the management server on the given port if this is the first user to start it; this is where the port is bound.
   * <p>
   * If starting fails (e.g. the port is in use), the start is not counted, so a later call will try again.
   * </p>
   *
   * @param port the port, which must have been initialised with {@link #init(int)}.
   * @throws CoreException if the server was not initialised, or could not be started.
   */
  static void start(int port) throws CoreException {
    synchronized (LOCK) {
      SharedServer server = lookup(port);
      if (server.startCount == 0) {
        LifecycleHelper.start(server.connection);
        log.debug("Started management server on port {}", port);
      }
      server.startCount++;
    }
  }

  /**
   * Release one start of the management server on the given port, stopping it (and releasing the port) when the last user stops.
   * <p>
   * Does nothing if there is no server on the port or it is not started.
   * </p>
   *
   * @param port the port.
   */
  static void stop(int port) {
    synchronized (LOCK) {
      SharedServer server = SERVERS.get(port);
      if (server != null && server.startCount > 0) {
        server.startCount--;
        if (server.startCount == 0) {
          LifecycleHelper.stop(server.connection);
          log.debug("Stopped management server on port {}", port);
        }
      }
    }
  }

  /**
   * Release one user of the management server on the given port; when the last user is released the server is stopped (if
   * required), closed and removed, so a later {@link #init(int)} creates a new one.
   * <p>
   * Does nothing if there is no server on the port.
   * </p>
   *
   * @param port the port.
   */
  static void close(int port) {
    synchronized (LOCK) {
      SharedServer server = SERVERS.get(port);
      if (server != null && server.initCount > 0) {
        server.initCount--;
        if (server.initCount == 0) {
          LifecycleHelper.stopAndClose(server.connection);
          SERVERS.remove(port);
          log.debug("Closed management server on port {}", port);
        }
      }
    }
  }

  /**
   * Add a servlet to the management server on the given port.
   * <p>
   * Serialised with every other registry operation, because the underlying Jetty servlet handler is not safe for concurrent
   * modification and several components may register at the same time.
   * </p>
   *
   * @param port the port, which must have been initialised with {@link #init(int)}.
   * @param wrapper the servlet and the path it is mapped to.
   * @throws CoreException if there is no server on the port.
   */
  static void addServlet(int port, ServletWrapper wrapper) throws CoreException {
    synchronized (LOCK) {
      lookup(port).connection.addServlet(wrapper);
    }
  }

  /**
   * Remove a servlet from the management server on the given port, leaving any other components' servlets in place.
   *
   * @param port the port, which must have been initialised with {@link #init(int)}.
   * @param wrapper the servlet and path previously passed to {@link #addServlet(int, ServletWrapper)}.
   * @throws CoreException if there is no server on the port.
   */
  static void removeServlet(int port, ServletWrapper wrapper) throws CoreException {
    synchronized (LOCK) {
      lookup(port).connection.removeServlet(wrapper);
    }
  }

  // for tests.
  static boolean isRegistered(int port) {
    synchronized (LOCK) {
      return SERVERS.containsKey(port);
    }
  }

  // for tests.
  static int serverCount() {
    synchronized (LOCK) {
      return SERVERS.size();
    }
  }

  private static SharedServer lookup(int port) throws CoreException {
    SharedServer server = SERVERS.get(port);
    if (server == null) {
      throw new CoreException("No management server initialised on port " + port);
    }
    return server;
  }

  /**
   * Build the standalone connection (and therefore Jetty server) for a port: plain HTTP, no security handler, and no
   * {@code Server}/{@code X-Powered-By} headers.
   */
  private static HttpConnection createConnection(int port) {
    HttpConnection connection = new HttpConnection();
    connection.setUniqueId("management-server-" + port);
    connection.setPort(port);
    // Match the failsafe jetty.xml used by the main embedded server: don't advertise the server.
    connection.getHttpConfiguration().addKeyValuePair(new KeyValuePair(HttpConfigurationProperty.SendServerVersion.name(), "false"));
    connection.getHttpConfiguration().addKeyValuePair(new KeyValuePair(HttpConfigurationProperty.SendXPoweredBy.name(), "false"));
    return connection;
  }

  private static final class SharedServer {
    private final HttpConnection connection;
    private int initCount;
    private int startCount;

    private SharedServer(HttpConnection connection) {
      this.connection = connection;
    }
  }

}
