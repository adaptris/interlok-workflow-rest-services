package com.adaptris.rest;

import com.adaptris.core.AdaptrisConnectionImp;
import com.adaptris.core.CoreException;
import com.adaptris.core.http.jetty.JettyServletRegistrar;
import com.adaptris.core.http.jetty.ServletWrapper;

import lombok.Getter;

/**
 * Connection used by RESTful management components when a management port is configured (see
 * {@link AbstractRestfulEndpoint#MANAGEMENT_PORT_KEY}).
 * <p>
 * Rather than registering servlets against the main embedded Jetty instance (as {@code EmbeddedConnection} does), this registers them
 * against a dedicated Jetty server listening on {@link #getPort()}. All components configured with the same port share a single
 * server (see {@link ManagementServers}). Note that the dedicated server does not inherit any {@code security-handler} configured on
 * the adapter's {@code EmbeddedConnection}, nor any settings from the main server's {@code jetty.xml}.
 * </p>
 * <p>
 * This is an internal connection; it is not intended to be configured in adapter XML.
 * </p>
 */
class ManagementPortConnection extends AdaptrisConnectionImp implements JettyServletRegistrar {

  @Getter
  private final int port;

  // Guard against unbalanced lifecycle calls so we never release a reference we don't hold.
  private transient boolean initialised;
  private transient boolean started;

  /**
   * @param port the dedicated management port; the server is shared with any other connection using the same port.
   */
  ManagementPortConnection(int port) {
    super();
    this.port = port;
  }

  @Override
  protected void prepareConnection() throws CoreException {
  }

  /**
   * Register with (and if necessary create) the shared server for {@link #getPort()}. Calling this more than once without an
   * intervening {@link #closeConnection()} has no further effect.
   */
  @Override
  protected void initConnection() throws CoreException {
    if (!initialised) {
      ManagementServers.init(port);
      initialised = true;
    }
  }

  /**
   * Start the shared server if it isn't already running; this is where a port conflict surfaces, as a {@link CoreException}.
   */
  @Override
  protected void startConnection() throws CoreException {
    if (!started) {
      ManagementServers.start(port);
      started = true;
    }
  }

  /**
   * Release this connection's start; the shared server only stops once every connection using it has stopped.
   */
  @Override
  protected void stopConnection() {
    if (started) {
      started = false;
      ManagementServers.stop(port);
    }
  }

  /**
   * Stop (if still started) and release this connection's registration; the shared server is closed and the port released
   * once the last connection using it is closed. Safe to call more than once, or without a prior init.
   */
  @Override
  protected void closeConnection() {
    stopConnection();
    if (initialised) {
      initialised = false;
      ManagementServers.close(port);
    }
  }

  @Override
  public void addServlet(ServletWrapper wrapper) throws CoreException {
    ManagementServers.addServlet(port, wrapper);
  }

  @Override
  public void removeServlet(ServletWrapper wrapper) throws CoreException {
    ManagementServers.removeServlet(port, wrapper);
  }

}
