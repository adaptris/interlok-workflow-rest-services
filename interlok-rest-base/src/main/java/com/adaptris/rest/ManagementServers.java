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

  static void addServlet(int port, ServletWrapper wrapper) throws CoreException {
    synchronized (LOCK) {
      lookup(port).connection.addServlet(wrapper);
    }
  }

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
