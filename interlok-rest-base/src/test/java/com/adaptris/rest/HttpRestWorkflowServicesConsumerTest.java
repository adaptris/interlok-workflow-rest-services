package com.adaptris.rest;

import static com.adaptris.rest.WorkflowServicesConsumer.ERROR_DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.core.ServiceException;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.core.http.jetty.JettyMessageConsumer;
import com.adaptris.core.http.jetty.JettyResponseService;

public class HttpRestWorkflowServicesConsumerTest {

  private static final String PATH = "/workflow-services/*";

  private static final String ACCEPTED_FILTER = "POST,GET";

  private HttpRestWorkflowServicesConsumer servicesConsumer;

  private AdaptrisMessage originalMessage;

  private AdaptrisMessage processedMessage;

  @Mock
  private JettyResponseService mockResponseService;

  @BeforeEach
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);

    servicesConsumer = new HttpRestWorkflowServicesConsumer("HttpRestWorkflowServicesConsumerTest");
    originalMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    processedMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
  }

  @Test
  public void testCreateStandardConsumer() throws Exception {
    StandaloneConsumer standaloneConsumer = servicesConsumer.configureConsumer(new AdaptrisMessageListener() {
      @Override
      public void onAdaptrisMessage(AdaptrisMessage message, Consumer<AdaptrisMessage> onSuccess, Consumer<AdaptrisMessage> onFailure) {
      }

      @Override
      public String friendlyName() {
        return null;
      }
    }, PATH, ACCEPTED_FILTER);

    JettyMessageConsumer consumer = (JettyMessageConsumer) standaloneConsumer.getConsumer();
    assertEquals(PATH, consumer.getPath());
    assertEquals(ACCEPTED_FILTER, consumer.getMethods());
  }

  @Test
  public void testOkResponse() throws Exception {
    servicesConsumer.setResponseService(mockResponseService);
    servicesConsumer.doResponse(originalMessage, processedMessage);

    verify(mockResponseService).doService(processedMessage);
  }

  @Test
  public void testOkResponse_Throws() throws Exception {
    servicesConsumer.setResponseService(mockResponseService);
    doThrow(new ServiceException()).when(mockResponseService).doService(any());
    servicesConsumer.doResponse(originalMessage, processedMessage);
  }

  @Test
  public void testErrorResponse() throws Exception {
    servicesConsumer.setResponseService(mockResponseService);
    servicesConsumer.doErrorResponse(originalMessage, new Exception(), ERROR_DEFAULT);
    verify(mockResponseService).doService(originalMessage);
  }

  @Test
  public void testErrorResponse_Throws() throws Exception {
    servicesConsumer.setResponseService(mockResponseService);
    doThrow(new ServiceException()).when(mockResponseService).doService(any());
    servicesConsumer.doErrorResponse(originalMessage, new Exception(), ERROR_DEFAULT);
  }

}
