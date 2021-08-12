package com.adaptris.rest.healthcheck;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.apache.commons.lang3.StringUtils;
import org.awaitility.Durations;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.CoreException;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.core.StartedState;
import com.adaptris.core.StoppedState;
import com.adaptris.core.XStreamJsonMarshaller;
import com.adaptris.core.http.jetty.JettyConstants;
import com.adaptris.core.runtime.AdapterManager;
import com.adaptris.rest.WorkflowServicesConsumer;
import com.adaptris.rest.util.JmxMBeanHelper;

public class WorkflowHealthCheckComponentTest {

  private static final String ADAPTER_ID = "MyAdapterId";
  private static final String CHANNEL_ID = "MyChannelId";
  private static final String WORKFLOW_ID1 = "MyWorkflowId1";
  private static final String WORKFLOW_ID2 = "MyWorkflowId2";
  private static final String UNIQUE_ID = "UniqueId";
  private static final String CHILDREN_ATTRIBUTE = "Children";
  private static final String COMPONENT_STATE = "ComponentState";
  private static final String CHANNEL_OBJECT_NAME = "com.adaptris:type=Channel,adapter=" + ADAPTER_ID + ",id=" + CHANNEL_ID;
  private static final String WORKFLOW_OBJECT_NAME_1 = "com.adaptris:type=Workflow,adapter=" + ADAPTER_ID + ",channel=" + CHANNEL_ID + ",id=" + WORKFLOW_ID1;
  private static final String WORKFLOW_OBJECT_NAME_2 = "com.adaptris:type=Channel,adapter=" + ADAPTER_ID + ",channel=" + CHANNEL_ID + ",id=" + WORKFLOW_ID2;
  private static final String PATH_KEY = JettyConstants.JETTY_URI;

  @Test(expected = CoreException.class)
  public void testMarshalling() throws Exception {
    List<AdapterState> states = new ArrayList<>();
    WorkflowHealthCheckComponent healthCheck = new WorkflowHealthCheckComponent();
    assertFalse(StringUtils.isBlank(healthCheck.toString(states)));

    XStreamJsonMarshaller mockMarshaller = Mockito.mock(XStreamJsonMarshaller.class);
    doThrow(new CoreException("Expected")).when(mockMarshaller).marshal(any());
    healthCheck.setMarshaller(mockMarshaller);

    healthCheck.toString(states);
  }

  @Test
  public void testNoMBeans() throws Exception {
    AdaptrisMessage message = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    message.addMessageHeader(PATH_KEY, "/workflow-health-check");
    MockedHealthCheckWrapper wrapper = new MockedHealthCheckWrapper().build(true);
    JmxMBeanHelper mockJmxHelper = wrapper.jmxHelper();
    TestConsumer testConsumer = wrapper.testConsumer();

    when(mockJmxHelper.getMBeans(anyString())).thenReturn(Collections.emptySet());
    try {
      wrapper.start();
      wrapper.healthCheck().onAdaptrisMessage(message);

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
          .until(testConsumer::complete);

      assertFalse(testConsumer.isError);
    } finally {
      wrapper.destroy();
    }
  }

  @Test
  public void testErrorFromMBean() throws Exception {
    AdaptrisMessage message = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    message.addMessageHeader(PATH_KEY, "/workflow-health-check");
    MockedHealthCheckWrapper wrapper = new MockedHealthCheckWrapper().build(true);
    JmxMBeanHelper mockJmxHelper = wrapper.jmxHelper();
    TestConsumer testConsumer = wrapper.testConsumer();

    doThrow(new Exception("Expected")).when(mockJmxHelper).getMBeans(anyString());
    try {
      wrapper.start();
      wrapper.healthCheck().onAdaptrisMessage(message);

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
          .until(testConsumer::complete);

      assertTrue(testConsumer.isError);
      assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, testConsumer.httpStatus);
    } finally {
      wrapper.destroy();
    }
  }

  @Test
  public void testErrorFromMBeanAttribute() throws Exception {
    AdaptrisMessage message = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    message.addMessageHeader(PATH_KEY, "/workflow-health-check");
    MockedHealthCheckWrapper wrapper = new MockedHealthCheckWrapper().build(true);
    JmxMBeanHelper mockJmxHelper = wrapper.jmxHelper();
    TestConsumer testConsumer = wrapper.testConsumer();

    doThrow(new Exception("Expected"))
        .when(mockJmxHelper).getStringAttribute(anyString(), any());

    try {
      wrapper.start();
      wrapper.healthCheck().onAdaptrisMessage(message);

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
          .until(testConsumer::complete);

      assertTrue(testConsumer.isError);
      assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, testConsumer.httpStatus);

    } finally {
      wrapper.destroy();
    }
  }


  @Test
  public void testHealthCheck_AllStarted() throws Exception {
    AdaptrisMessage message = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    message.addMessageHeader(PATH_KEY, "/workflow-health-check");
    MockedHealthCheckWrapper wrapper = new MockedHealthCheckWrapper().build(true);
    TestConsumer testConsumer = wrapper.testConsumer();
    try {
      wrapper.start();
      wrapper.healthCheck().onAdaptrisMessage(message);

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
          .until(testConsumer::complete);
      assertFalse(testConsumer.isError);
      assertTrue(testConsumer.payload.contains(ADAPTER_ID));
      assertTrue(testConsumer.payload.contains(CHANNEL_ID));
      assertTrue(testConsumer.payload.contains(WORKFLOW_ID1));
      assertTrue(testConsumer.payload.contains(WORKFLOW_ID2));
    } finally {
      wrapper.destroy();
    }
  }

  @Test
  public void testHealthCheck_NotStarted() throws Exception {
    AdaptrisMessage message = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    message.addMessageHeader(PATH_KEY, "/workflow-health-check");
    MockedHealthCheckWrapper wrapper = new MockedHealthCheckWrapper().build(false);
    TestConsumer testConsumer = wrapper.testConsumer();
    try {
      wrapper.start();
      wrapper.healthCheck().onAdaptrisMessage(message);

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
          .until(testConsumer::complete);
      assertFalse(testConsumer.isError);
      assertTrue(testConsumer.payload.contains(ADAPTER_ID));
      assertTrue(testConsumer.payload.contains(CHANNEL_ID));
      assertTrue(testConsumer.payload.contains(WORKFLOW_ID1));
      assertTrue(testConsumer.payload.contains(WORKFLOW_ID2));
    } finally {
      wrapper.destroy();
    }
  }

  @Test
  public void testLiveness() throws Exception {
    AdaptrisMessage message = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    message.addMessageHeader(PATH_KEY, "/workflow-health-check/alive");
    MockedHealthCheckWrapper wrapper = new MockedHealthCheckWrapper().build(false);
    TestConsumer testConsumer = wrapper.testConsumer();
    try {
      wrapper.start();
      wrapper.healthCheck().onAdaptrisMessage(message);

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
          .until(testConsumer::complete);
      assertFalse(testConsumer.isError);
      assertEquals("", testConsumer.payload);
    } finally {
      wrapper.destroy();
    }
  }

  @Test
  public void testReadiness_NotReady() throws Exception {
    AdaptrisMessage message = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    message.addMessageHeader(PATH_KEY, "/workflow-health-check/ready");
    MockedHealthCheckWrapper wrapper = new MockedHealthCheckWrapper().build(false);
    TestConsumer testConsumer = wrapper.testConsumer();
    try {
      wrapper.start();
      wrapper.healthCheck().onAdaptrisMessage(message);

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
          .until(testConsumer::complete);
      assertFalse(testConsumer.isError);
      assertTrue(testConsumer.payload.contains("is not started"));
      assertEquals(HttpURLConnection.HTTP_UNAVAILABLE, testConsumer.httpStatus);
    } finally {
      wrapper.destroy();
    }
  }

  @Test
  public void testReadiness_Ready() throws Exception {
    AdaptrisMessage message = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    message.addMessageHeader(PATH_KEY, "/workflow-health-check/ready");
    MockedHealthCheckWrapper wrapper = new MockedHealthCheckWrapper().build(true);
    TestConsumer testConsumer = wrapper.testConsumer();
    try {
      wrapper.start();
      wrapper.healthCheck().onAdaptrisMessage(message);

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
          .until(testConsumer::complete);
      assertEquals(HttpURLConnection.HTTP_OK, testConsumer.httpStatus);
      assertFalse(testConsumer.isError);
      assertEquals("", testConsumer.payload);
    } finally {
      wrapper.destroy();
    }
  }

  // Can't do this in @Before / @After since I want to control the mocking behaviour.
  private class MockedHealthCheckWrapper {

    private WorkflowHealthCheckComponent healthCheck;
    private TestConsumer testConsumer;

    @Mock
    private JmxMBeanHelper mockJmxHelper;

    public MockedHealthCheckWrapper() throws Exception {
      MockitoAnnotations.openMocks(this);
    }

    public MockedHealthCheckWrapper build(boolean workflowsAreStarted) throws Exception {
      ObjectName adapterObjectName = new ObjectName("com.adaptris:type=Adapter,id=" + ADAPTER_ID);
      ObjectInstance adapter =
          new ObjectInstance(adapterObjectName, AdapterManager.class.getName());
      Set<ObjectInstance> adapterInstances = new HashSet<>();
      adapterInstances.add(adapter);

      Set<ObjectName> channelObjectNames = new HashSet<>();
      ObjectName channelObjectName = new ObjectName(CHANNEL_OBJECT_NAME);
      channelObjectNames.add(channelObjectName);

      Set<ObjectName> workflowObjectNames = new HashSet<>();
      ObjectName workflowObjectName1 = new ObjectName(WORKFLOW_OBJECT_NAME_1);
      ObjectName workflowObjectName2 = new ObjectName(WORKFLOW_OBJECT_NAME_2);
      workflowObjectNames.add(workflowObjectName1);
      workflowObjectNames.add(workflowObjectName2);


      when(mockJmxHelper.getMBeans(anyString())).thenReturn(adapterInstances);
      when(mockJmxHelper.getStringAttribute(adapterObjectName.toString(), UNIQUE_ID))
          .thenReturn(ADAPTER_ID);
      when(mockJmxHelper.getStringAttributeClassName(adapterObjectName.toString(), COMPONENT_STATE))
          .thenReturn(StartedState.class.getSimpleName());

      when(mockJmxHelper.getObjectSetAttribute(adapterObjectName.toString(), CHILDREN_ATTRIBUTE))
          .thenReturn(channelObjectNames);
      when(mockJmxHelper.getStringAttribute(channelObjectName.toString(), UNIQUE_ID))
          .thenReturn(CHANNEL_ID);
      when(mockJmxHelper.getStringAttributeClassName(channelObjectName.toString(), COMPONENT_STATE))
          .thenReturn(StartedState.class.getSimpleName());

      when(mockJmxHelper.getObjectSetAttribute(channelObjectName.toString(), CHILDREN_ATTRIBUTE))
          .thenReturn(workflowObjectNames);

      String workflowState = StartedState.class.getSimpleName();
      if (!workflowsAreStarted) {
        workflowState = StoppedState.class.getSimpleName();
      }

      when(mockJmxHelper.getStringAttribute(workflowObjectName1.toString(), UNIQUE_ID))
          .thenReturn(WORKFLOW_ID1);
      when(mockJmxHelper.getStringAttributeClassName(workflowObjectName1.toString(),
          COMPONENT_STATE)).thenReturn(workflowState);

      when(mockJmxHelper.getStringAttribute(workflowObjectName2.toString(), UNIQUE_ID))
          .thenReturn(WORKFLOW_ID2);
      when(mockJmxHelper.getStringAttributeClassName(workflowObjectName2.toString(),
            COMPONENT_STATE)).thenReturn(workflowState);

      healthCheck = new WorkflowHealthCheckComponent();
      testConsumer = new TestConsumer();
      healthCheck.setConsumer(testConsumer);
      healthCheck.setJmxMBeanHelper(mockJmxHelper);
      return this;
    }


    public void start() throws Exception {
      healthCheck.init(new Properties());
      healthCheck.start();

    }

    public void destroy() throws Exception {
      healthCheck.stop();
      healthCheck.destroy();
    }

    public WorkflowHealthCheckComponent healthCheck() {
      return healthCheck;
    }

    public TestConsumer testConsumer() {
      return testConsumer;
    }

    public JmxMBeanHelper jmxHelper() {
      return mockJmxHelper;
    }

  }

  class TestConsumer extends WorkflowServicesConsumer {

    String payload;
    boolean isError;
    Exception storedException;
    int httpStatus = -1;

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
      storedException = e;
      httpStatus = status;
    }

    public boolean complete() {
      return isError == true || payload != null;
    }

    @Override
    public void prepare() {

    }
  }
}
