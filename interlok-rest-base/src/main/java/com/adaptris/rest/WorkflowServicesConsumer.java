package com.adaptris.rest;

import java.net.HttpURLConnection;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.ComponentLifecycle;
import com.adaptris.core.ComponentLifecycleExtension;
import com.adaptris.core.CoreException;
import com.adaptris.core.ServiceException;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.core.util.LifecycleHelper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
public abstract class WorkflowServicesConsumer implements ComponentLifecycle, ComponentLifecycleExtension {

  public static final int OK_200 = HttpURLConnection.HTTP_OK;
  public static final int ERROR_BAD_REQUEST = HttpURLConnection.HTTP_BAD_REQUEST;
  public static final int ERROR_DEFAULT = HttpURLConnection.HTTP_INTERNAL_ERROR;
  public static final int ERROR_NOT_READY = HttpURLConnection.HTTP_UNAVAILABLE;

  public static final String CONTENT_TYPE_DEFAULT = "text/plain";
  public static final String CONTENT_TYPE_JSON = "application/json";

  @Getter
  @Setter
  private StandaloneConsumer standaloneConsumer;

  @Getter
  @Setter
  private AdaptrisMessageListener messageListener;

  /**
   * This is the url that this consumer will listen for requests.
   * For example "/workflow-services/*"; will trigger this consumer for any requests on "http://host:port/workflow-services/...".
   */
  @Getter
  @Setter
  private String consumedUrlPath;

  /**
   * A comma separated list of accepted http request methods; GET, POST, PATCH etc etc
   * And example might be "GET,POST".
   */
  @Getter
  @Setter
  private String acceptedHttpMethods;

  protected abstract StandaloneConsumer configureConsumer(AdaptrisMessageListener messageListener, String consumedUrlPath, String acceptedHttpMethods);

  public void doResponse(AdaptrisMessage original, AdaptrisMessage processed) throws ServiceException {
    doResponse(original, processed, CONTENT_TYPE_DEFAULT);
  }

  public void doResponse(AdaptrisMessage orig, AdaptrisMessage proc, String contentType)
      throws ServiceException {
    doResponse(orig, proc, contentType, OK_200);
  }

  public abstract void doResponse(AdaptrisMessage orig, AdaptrisMessage proc, String contentType,
      int httpResponse);

  public abstract void doErrorResponse(AdaptrisMessage message, Exception e, int httpStatus);

  @Override
  public void prepare() throws CoreException {
    setStandaloneConsumer(configureConsumer(getMessageListener(), getConsumedUrlPath(), getAcceptedHttpMethods()));
    LifecycleHelper.prepare(getStandaloneConsumer());
  }

  @Override
  public void init() throws CoreException {
    LifecycleHelper.init(getStandaloneConsumer());
  }

  @Override
  public void start() throws CoreException {
    LifecycleHelper.start(getStandaloneConsumer());
  }

  @Override
  public void stop() {
    LifecycleHelper.stop(getStandaloneConsumer());
  }

  @Override
  public void close() {
    LifecycleHelper.close(getStandaloneConsumer());
  }
}
