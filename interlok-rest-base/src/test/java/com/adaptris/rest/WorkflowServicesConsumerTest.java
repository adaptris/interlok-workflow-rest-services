package com.adaptris.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.verify;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;

import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.core.http.jetty.JettyMessageConsumer;
import com.adaptris.core.stubs.MockMessageListener;

public class WorkflowServicesConsumerTest {

  private WorkflowServicesConsumer servicesConsumer;

  @Mock
  private StandaloneConsumer mockStandaloneConsumer;

  @Mock
  private AdaptrisMessageListener mockMessageListener;

  @BeforeEach
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);

    servicesConsumer = new HttpRestWorkflowServicesConsumer("WorkflowServicesConsumerTest");
    servicesConsumer.setMessageListener(mockMessageListener);
    servicesConsumer.setAcceptedHttpMethods("POST,GET");
    servicesConsumer.setConsumedUrlPath("/myPath/");
  }

  @Test
  public void testLifecycle() throws Exception {
    servicesConsumer.prepare();

    servicesConsumer.setStandaloneConsumer(mockStandaloneConsumer);
    servicesConsumer.init();
    servicesConsumer.start();
    servicesConsumer.stop();
    servicesConsumer.close();

    verify(mockStandaloneConsumer).requestInit();
    verify(mockStandaloneConsumer).requestStart();
    verify(mockStandaloneConsumer).requestStop();
    verify(mockStandaloneConsumer).requestClose();
  }

  @Test
  public void testJettyConsumer() throws Exception {
    WorkflowServicesConsumer consumer = new HttpRestWorkflowServicesConsumer("WorkflowServicesConsumerTest");
    StandaloneConsumer sc = consumer.configureConsumer(new MockMessageListener(), "/path/to/url", "GET");
    assertTrue(sc.getConsumer() instanceof JettyMessageConsumer);
    // Bit of a dodgy test.
    try {
      ((JettyMessageConsumer) sc.getConsumer()).createMessage(Mockito.mock(HttpServletRequest.class),
          Mockito.mock(HttpServletResponse.class));
      fail();
    } catch (Exception expected) {

    }
    assertEquals("WorkflowServicesConsumerTest", MDC.get(AbstractRestfulEndpoint.MDC_KEY));
  }

}
