package com.adaptris.rest;

import java.net.HttpURLConnection;
import java.util.Properties;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.CoreException;
import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.core.util.LifecycleHelper;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public abstract class AbstractRestfulEndpoint extends MgmtComponentImpl implements AdaptrisMessageListener {
  public static final String MDC_KEY = "ManagementComponent";

  /**
   * Bootstrap property that moves supporting management components onto a dedicated Jetty server listening on this port, similar to
   * Spring Boot's {@code management.server.port}.
   * <p>
   * If not set (or blank), every component behaves exactly as before and is served by the main embedded Jetty instance. If set, it
   * must be a port number between 1 and 65535, otherwise the component fails to initialise. All components that use it share a single
   * server. Components that support it may also offer a {@code ...management-port.enabled=false} bootstrap property to stay on the main
   * Jetty instance.
   * </p>
   */
  public static final String MANAGEMENT_PORT_KEY = "rest.management.port";

  private static final int MIN_PORT = 1;
  private static final int MAX_PORT = 65535;

  @Getter
  @Setter
  private transient WorkflowServicesConsumer consumer;

  @Setter(AccessLevel.PROTECTED)
  @Getter(AccessLevel.PROTECTED)
  private String configuredUrlPath;

  /**
   * The dedicated management port, or {@code null} to use the main embedded Jetty instance.
   */
  @Setter(AccessLevel.PROTECTED)
  @Getter(AccessLevel.PROTECTED)
  private Integer configuredPort;

  public AbstractRestfulEndpoint() {
    setConsumer(new HttpRestWorkflowServicesConsumer(friendlyName()));
  }

  @Override
  public void init(Properties config) throws Exception {
  }

  /**
   * Configure the consumer (accepted methods, URL path, management port and listener) and start it on a background thread.
   * <p>
   * Failures to start, such as the management port already being in use, are logged rather than thrown, so they don't prevent the
   * adapter from starting.
   * </p>
   */
  @Override
  public void start() throws Exception {
    AbstractRestfulEndpoint instance = this;
    new Thread(() -> {
      try {
        getConsumer().setAcceptedHttpMethods(getAcceptedFilter());
        getConsumer().setConsumedUrlPath(configuredUrlPath());
        getConsumer().setPort(getConfiguredPort());
        getConsumer().setMessageListener(instance);
        LifecycleHelper.initAndStart(getConsumer());

        log.debug("{} component started.", friendlyName());
      } catch (CoreException e) {
        log.error("Could not start [{}]", friendlyName(), e);
      }
    }).start();
  }

  public void doResponse(AdaptrisMessage orig, AdaptrisMessage proc, String contentType, int httpResponse) {
    getConsumer().doResponse(orig, proc, contentType, httpResponse);
  }

  public void doResponse(AdaptrisMessage orig, AdaptrisMessage proc, String contentType) {
    getConsumer().doResponse(orig, proc, contentType, HttpURLConnection.HTTP_OK);
  }

  public void doErrorResponse(AdaptrisMessage message, Exception e, int httpStatus) {
    getConsumer().doErrorResponse(message, e, httpStatus);
  }

  protected abstract String getAcceptedFilter();

  protected abstract String getDefaultUrlPath();

  protected String configuredUrlPath() {
    return ObjectUtils.defaultIfNull(getConfiguredUrlPath(), getDefaultUrlPath());
  }

  /**
   * Resolve the dedicated management port for a component from the bootstrap properties.
   *
   * @param config the bootstrap properties.
   * @param componentEnabledKey the component's opt-out property; if this is set to anything other than a "true" value (as per
   *          {@link BooleanUtils#toBoolean(String)}), the component stays on the main Jetty instance. Defaults to true if not set.
   * @return the port, or {@code null} if {@link #MANAGEMENT_PORT_KEY} is not set or the component has opted out.
   * @throws IllegalArgumentException if {@link #MANAGEMENT_PORT_KEY} is set but is not a valid port number.
   */
  protected static Integer managementPort(Properties config, String componentEnabledKey) {
    Integer port = parsePort(MANAGEMENT_PORT_KEY, config.getProperty(MANAGEMENT_PORT_KEY));
    if (port == null) {
      return null;
    }
    return BooleanUtils.toBoolean(config.getProperty(componentEnabledKey, "true")) ? port : null;
  }

  /**
   * Parse a port number from a property value.
   *
   * @param key the property name, used in the error message.
   * @param value the raw value; surrounding whitespace is ignored.
   * @return the port, or {@code null} if the value is null or blank.
   * @throws IllegalArgumentException if the value is not an integer between 1 and 65535.
   */
  static Integer parsePort(String key, String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    try {
      int port = Integer.parseInt(value.trim());
      if (port >= MIN_PORT && port <= MAX_PORT) {
        return port;
      }
    } catch (NumberFormatException e) {
      // fall through.
    }
    throw new IllegalArgumentException(
        String.format("%s must be a port number between %d and %d, but was [%s]", key, MIN_PORT, MAX_PORT, value));
  }

  @Override
  public String friendlyName() {
    return this.getClass().getSimpleName();
  }

  @Override
  public void stop() throws Exception {
    LifecycleHelper.stop(getConsumer());
    log.debug("{} component stopped.", friendlyName());
  }

  @Override
  public void destroy() throws Exception {
    LifecycleHelper.close(getConsumer());
  }

}
