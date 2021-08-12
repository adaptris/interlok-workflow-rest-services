package com.adaptris.rest;

import static com.adaptris.rest.AbstractRestfulEndpoint.MDC_KEY;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.MDC;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.core.http.jetty.EmbeddedConnection;
import com.adaptris.core.http.jetty.JettyConstants;
import com.adaptris.core.http.jetty.JettyMessageConsumer;
import com.adaptris.core.http.jetty.JettyResponseService;
import com.adaptris.core.http.jetty.MetadataHeaderHandler;
import com.adaptris.core.http.jetty.MetadataParameterHandler;
import com.adaptris.core.http.jetty.NoOpResponseHeaderProvider;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpRestWorkflowServicesConsumer extends WorkflowServicesConsumer {

  private static final String METADATA_STATUS = "httpReplyStatus";
  private static final String METADATA_CONTENT_TYPE = "httpReplyContentType";

  private static final String HEADER_PREFIX = "http.header.";

  private static final String PARAMETER_PREFIX = "http.param.";

  @Getter(AccessLevel.PACKAGE)
  @Setter(AccessLevel.PACKAGE)
  private transient JettyResponseService responseService;

  @Getter(AccessLevel.PRIVATE)
  @Setter(AccessLevel.PRIVATE)
  private transient String owner;

  public HttpRestWorkflowServicesConsumer(String ownerRef) {
    setOwner(ownerRef);
  }

  @Override
  protected StandaloneConsumer configureConsumer(AdaptrisMessageListener messageListener, String consumedUrlPath, String acceptedHttpMethods) {
    setResponseService(new JettyResponseService().withResponseHeaderProvider(new NoOpResponseHeaderProvider())
        .withHttpStatus("%message{httpReplyStatus}").withContentType("%message{httpReplyContentType}"));

    EmbeddedConnection jettyConnection = new EmbeddedConnection();
    JettyMessageConsumer messageConsumer = new JettyMessageConsumer() {
      @Override
      public AdaptrisMessage createMessage(HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException {
        MDC.put(MDC_KEY, getOwner());
        return super.createMessage(request, response);
      }
    };

    messageConsumer.setPath(consumedUrlPath);
    messageConsumer.setMethods(acceptedHttpMethods);
    messageConsumer.setHeaderHandler(new MetadataHeaderHandler(HEADER_PREFIX));
    messageConsumer.setParameterHandler(new MetadataParameterHandler(PARAMETER_PREFIX));
    messageConsumer.registerAdaptrisMessageListener(messageListener);

    return new StandaloneConsumer(jettyConnection, messageConsumer);
  }

  @Override
  public void doResponse(AdaptrisMessage originalMessage, AdaptrisMessage processedMessage,
      String contentType, int httpStatus) {
    try {
      processedMessage.addObjectHeader(JettyConstants.JETTY_WRAPPER,
          originalMessage.getObjectHeaders().get(JettyConstants.JETTY_WRAPPER));
      processedMessage.addMetadata(METADATA_STATUS, String.valueOf(httpStatus));
      processedMessage.addMetadata(METADATA_CONTENT_TYPE,
          StringUtils.defaultIfBlank(contentType, CONTENT_TYPE_DEFAULT));
      getResponseService().doService(processedMessage);
    } catch (Exception exc) {
      log.trace("Ignored exception sending HTTP response {}", exc.getMessage());
    }
  }

  @Override
  public void doErrorResponse(AdaptrisMessage message, Exception e, int httpStatus) {
    message.setContent(ExceptionUtils.getStackTrace(e), message.getContentEncoding());
    doResponse(message, message, CONTENT_TYPE_DEFAULT, httpStatus);
  }
}
