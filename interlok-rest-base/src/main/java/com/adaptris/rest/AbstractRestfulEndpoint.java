package com.adaptris.rest;

import java.util.Properties;
import org.apache.commons.lang3.ObjectUtils;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.CoreException;
import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.core.util.LifecycleHelper;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public abstract class AbstractRestfulEndpoint extends MgmtComponentImpl implements AdaptrisMessageListener {
  public static final String MDC_KEY = "ManagementComponent";

  @Getter
  @Setter
  private transient WorkflowServicesConsumer consumer;

  @Setter(AccessLevel.PROTECTED)
  @Getter(AccessLevel.PROTECTED)
  private String configuredUrlPath;

  public AbstractRestfulEndpoint() {
    setConsumer(new HttpRestWorkflowServicesConsumer(friendlyName()));
  }

  @Override
  public void init(Properties config) throws Exception {}

  @Override
  public void start() throws Exception {
    AbstractRestfulEndpoint instance = this;
    new Thread(() -> {
      try {
        getConsumer().setAcceptedHttpMethods(getAcceptedFilter());
        getConsumer().setConsumedUrlPath(configuredUrlPath());
        getConsumer().setMessageListener(instance);
        LifecycleHelper.initAndStart(getConsumer());

        log.debug("{} component started.", friendlyName());
      } catch (CoreException e) {
        log.error("Could not start [{}]", friendlyName(), e);
      }
    }).start();
  }

  protected abstract String getAcceptedFilter();

  protected abstract String getDefaultUrlPath();

  protected String configuredUrlPath() {
    return ObjectUtils.defaultIfNull(getConfiguredUrlPath(), getDefaultUrlPath());
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
