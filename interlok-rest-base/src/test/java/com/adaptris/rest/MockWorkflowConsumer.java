package com.adaptris.rest;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.StandaloneConsumer;

import lombok.Getter;
import lombok.Setter;

public class MockWorkflowConsumer extends WorkflowServicesConsumer {

  @Getter
  @Setter
  private String payload;
  
  @Getter
  @Setter
  private boolean isError;
  
  @Getter
  @Setter
  private int httpStatus = -1;

  @Override
  protected StandaloneConsumer configureConsumer(AdaptrisMessageListener messageListener, String consumedUrlPath, String acceptedHttpMethods) {
    return null;
  }

  @Override
  public void doResponse(AdaptrisMessage originalMessage, AdaptrisMessage processedMessage,
      String contentType, int status) {
    payload = processedMessage.getContent();
    httpStatus = status;
  }


  @Override
  public void doErrorResponse(AdaptrisMessage message, Exception e, int status) {
    isError = true;
    httpStatus = status;
  }

  public boolean complete() {
    return isError == true || payload != null;
  }

  @Override
  public void prepare() {

  }
}