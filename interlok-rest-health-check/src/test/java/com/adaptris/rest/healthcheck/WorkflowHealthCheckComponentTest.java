package com.adaptris.rest.healthcheck;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.junit.jupiter.api.Test;
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
  private static final String CHILD_RUNTIME_INFO_COMPONENTS_ATTRIBUTE = "ChildRuntimeInfoComponents";
  private static final String COMPONENT_STATE = "ComponentState";
  private static final String AUTO_START = "AutoStart";
  private static final String CONNECTION_ID_ADAPTER = "AdapterConnection";
  private static final String CONNECTION_ID_CHANNEL = "ChannelConnection";
  private static final String CONNECTION_ID_WORKFLOW = "WorkflowConnection";
  
  private static final String CHANNEL_OBJECT_NAME = "com.adaptris:type=Channel,adapter=" + ADAPTER_ID + ",id=" + CHANNEL_ID;
  private static final String WORKFLOW_OBJECT_NAME_1 = "com.adaptris:type=Workflow,adapter=" + ADAPTER_ID + ",channel=" + CHANNEL_ID + ",id=" + WORKFLOW_ID1;
  private static final String WORKFLOW_OBJECT_NAME_2 = "com.adaptris:type=Channel,adapter=" + ADAPTER_ID + ",channel=" + CHANNEL_ID + ",id=" + WORKFLOW_ID2;
  private static final String CONNECTION_OBJECT_NAME_ADAPTER = "com.adaptris:type=Connection,adapter=" + ADAPTER_ID + ",id=" + CONNECTION_ID_ADAPTER;
  private static final String CONNECTION_OBJECT_NAME_CHANNEL = "com.adaptris:type=Connection,adapter=" + ADAPTER_ID + ",channel=" + CHANNEL_ID + ",id=" + CONNECTION_ID_CHANNEL;
  private static final String CONNECTION_OBJECT_NAME_WORKFLOW = "com.adaptris:type=Connection,adapter=" + ADAPTER_ID + ",channel=" + CHANNEL_ID + ",workflow=" + WORKFLOW_ID1 + ",id=" + CONNECTION_ID_WORKFLOW;
  private static final String PATH_KEY = JettyConstants.JETTY_URI;
  private static final String CONNECTION_CHECK_KEY = "rest.health-check.connection-check";

  @Test
  public void testMarshalling() throws Exception {
    List<AdapterState> states = new ArrayList<>();
    WorkflowHealthCheckComponent healthCheck = new WorkflowHealthCheckComponent();
    assertFalse(StringUtils.isBlank(healthCheck.toString(states)));

    XStreamJsonMarshaller mockMarshaller = Mockito.mock(XStreamJsonMarshaller.class);
    doThrow(new CoreException("Expected")).when(mockMarshaller).marshal(any());
    healthCheck.setMarshaller(mockMarshaller);

    assertThrows(CoreException.class, () -> healthCheck.toString(states));
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

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS).until(testConsumer::complete);

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

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS).until(testConsumer::complete);

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

    doThrow(new Exception("Expected")).when(mockJmxHelper).getStringAttribute(anyString(), any());

    try {
      wrapper.start();
      wrapper.healthCheck().onAdaptrisMessage(message);

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS).until(testConsumer::complete);

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

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS).until(testConsumer::complete);
      assertFalse(testConsumer.isError);
      assertTrue(testConsumer.payload.contains(ADAPTER_ID));
      assertTrue(testConsumer.payload.contains(CHANNEL_ID));
      assertTrue(testConsumer.payload.contains(WORKFLOW_ID1));
      assertFalse(testConsumer.payload.contains(WORKFLOW_ID2));
    } finally {
      wrapper.destroy();
    }
  }
  
  @Test
  public void testHealthCheck_AutoStartFalse() throws Exception {
    AdaptrisMessage message = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    message.addMessageHeader(PATH_KEY, "/workflow-health-check");
    MockedHealthCheckWrapper wrapper = new MockedHealthCheckWrapper().build(true);
    JmxMBeanHelper mockJmxHelper = wrapper.jmxHelper();
    TestConsumer testConsumer = wrapper.testConsumer();
    
    when(mockJmxHelper.getStringAttribute(new ObjectName(CHANNEL_OBJECT_NAME).toString(), AUTO_START)).thenReturn("false");
    
    try {
      wrapper.start();
      wrapper.healthCheck().onAdaptrisMessage(message);

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS).until(testConsumer::complete);
      assertFalse(testConsumer.isError);
      assertTrue(testConsumer.payload.contains(ADAPTER_ID));
      assertFalse(testConsumer.payload.contains(CHANNEL_ID));
      assertFalse(testConsumer.payload.contains(WORKFLOW_ID1));
      assertFalse(testConsumer.payload.contains(WORKFLOW_ID2));
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

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS).until(testConsumer::complete);
      assertFalse(testConsumer.isError);
      assertTrue(testConsumer.payload.contains(ADAPTER_ID));
      assertTrue(testConsumer.payload.contains(CHANNEL_ID));
      assertTrue(testConsumer.payload.contains(WORKFLOW_ID1));
      assertFalse(testConsumer.payload.contains(WORKFLOW_ID2));
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

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS).until(testConsumer::complete);
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

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS).until(testConsumer::complete);
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

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS).until(testConsumer::complete);
      assertEquals(HttpURLConnection.HTTP_OK, testConsumer.httpStatus);
      assertFalse(testConsumer.isError);
      assertEquals("", testConsumer.payload);
    } finally {
      wrapper.destroy();
    }
  }

  @Test
  public void testReadiness_NotReadyWhenConnectionStopped() throws Exception {
    AdaptrisMessage message = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    message.addMessageHeader(PATH_KEY, "/workflow-health-check/ready");
    MockedHealthCheckWrapper wrapper = new MockedHealthCheckWrapper().build(true);
    TestConsumer testConsumer = wrapper.testConsumer();
    JmxMBeanHelper mockJmxHelper = wrapper.jmxHelper();
    try {
      when(mockJmxHelper.getStringAttributeClassName(CONNECTION_OBJECT_NAME_ADAPTER, COMPONENT_STATE))
          .thenReturn(StoppedState.class.getSimpleName());

      Properties p = new Properties();
      p.setProperty(CONNECTION_CHECK_KEY, "true");
      wrapper.start(p);
      wrapper.healthCheck().onAdaptrisMessage(message);

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS).until(testConsumer::complete);
      assertEquals(HttpURLConnection.HTTP_UNAVAILABLE, testConsumer.httpStatus);
      assertTrue(testConsumer.payload.contains("is not started"));
    } finally {
      wrapper.destroy();
    }
  }

  @Test
  public void testHealthCheck_ConnectionParentsIncluded() throws Exception {
    AdaptrisMessage message = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    message.addMessageHeader(PATH_KEY, "/workflow-health-check");
    MockedHealthCheckWrapper wrapper = new MockedHealthCheckWrapper().build(true);
    TestConsumer testConsumer = wrapper.testConsumer();
    try {
      Properties p = new Properties();
      p.setProperty(CONNECTION_CHECK_KEY, "true");
      wrapper.start(p);
      wrapper.healthCheck().onAdaptrisMessage(message);

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS).until(testConsumer::complete);
      assertFalse(testConsumer.isError);
      assertTrue(testConsumer.payload.contains("\"connection-states\""));
      assertTrue(testConsumer.payload.contains("\"parent-type\":\"adapter\""));
      assertTrue(testConsumer.payload.contains("\"parent-id\":\"" + ADAPTER_ID + "\""));
      assertTrue(testConsumer.payload.contains("\"parent-type\":\"channel\""));
      assertTrue(testConsumer.payload.contains("\"parent-id\":\"" + CHANNEL_ID + "\""));
      assertTrue(testConsumer.payload.contains("\"parent-type\":\"workflow\""));
      assertTrue(testConsumer.payload.contains("\"parent-id\":\"" + WORKFLOW_ID1 + "\""));
    } finally {
      wrapper.destroy();
    }
  }

  @Test
  public void testHealthCheck_IgnoresNonWorkflowChildren() throws Exception {
    AdaptrisMessage message = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    message.addMessageHeader(PATH_KEY, "/workflow-health-check");
    MockedHealthCheckWrapper wrapper = new MockedHealthCheckWrapper().build(true);
    JmxMBeanHelper mockJmxHelper = wrapper.jmxHelper();
    TestConsumer testConsumer = wrapper.testConsumer();

    Set<ObjectName> mixedChildren = new HashSet<>();
    ObjectName workflowObjectName1 = new ObjectName(WORKFLOW_OBJECT_NAME_1);
    ObjectName workflowConnection = new ObjectName(CONNECTION_OBJECT_NAME_WORKFLOW);
    mixedChildren.add(workflowObjectName1);
    mixedChildren.add(workflowConnection);

    when(mockJmxHelper.getObjectSetAttribute(new ObjectName(CHANNEL_OBJECT_NAME).toString(), CHILDREN_ATTRIBUTE))
        .thenReturn(mixedChildren);

    try {
      Properties p = new Properties();
      p.setProperty(CONNECTION_CHECK_KEY, "true");
      wrapper.start(p);
      wrapper.healthCheck().onAdaptrisMessage(message);

      await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS).until(testConsumer::complete);
      assertFalse(testConsumer.isError);
      assertEquals(HttpURLConnection.HTTP_OK, testConsumer.httpStatus);
      assertTrue(testConsumer.payload.contains(WORKFLOW_ID1));
      assertFalse(testConsumer.payload.contains(WORKFLOW_ID2));
    } finally {
      wrapper.destroy();
    }
  }

  // Can't do this in @BeforeEach / @AfterEach since I want to control the mocking behaviour.
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
      ObjectInstance adapter = new ObjectInstance(adapterObjectName, AdapterManager.class.getName());
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

      Set<ObjectName> adapterConnectionObjectNames = new HashSet<>();
      ObjectName adapterConnection = new ObjectName(CONNECTION_OBJECT_NAME_ADAPTER);
      adapterConnectionObjectNames.add(adapterConnection);

      Set<ObjectName> channelConnectionObjectNames = new HashSet<>();
      ObjectName channelConnection = new ObjectName(CONNECTION_OBJECT_NAME_CHANNEL);
      channelConnectionObjectNames.add(channelConnection);

      Set<ObjectName> workflowConnectionObjectNames = new HashSet<>();
      ObjectName workflowConnection = new ObjectName(CONNECTION_OBJECT_NAME_WORKFLOW);
      workflowConnectionObjectNames.add(workflowConnection);

      when(mockJmxHelper.getMBeans(anyString())).thenReturn(adapterInstances);
      when(mockJmxHelper.getStringAttribute(adapterObjectName.toString(), UNIQUE_ID)).thenReturn(ADAPTER_ID);
      when(mockJmxHelper.getStringAttributeClassName(adapterObjectName.toString(), COMPONENT_STATE))
          .thenReturn(StartedState.class.getSimpleName());

      when(mockJmxHelper.getObjectSetAttribute(adapterObjectName.toString(), CHILDREN_ATTRIBUTE)).thenReturn(channelObjectNames);
        when(mockJmxHelper.getObjectSetAttribute(adapterObjectName.toString(), CHILD_RUNTIME_INFO_COMPONENTS_ATTRIBUTE))
          .thenReturn(adapterConnectionObjectNames);
      when(mockJmxHelper.getStringAttribute(channelObjectName.toString(), UNIQUE_ID)).thenReturn(CHANNEL_ID);
      when(mockJmxHelper.getStringAttribute(channelObjectName.toString(), AUTO_START)).thenReturn("true");
      when(mockJmxHelper.getStringAttributeClassName(channelObjectName.toString(), COMPONENT_STATE))
          .thenReturn(StartedState.class.getSimpleName());
        when(mockJmxHelper.getObjectSetAttribute(channelObjectName.toString(), CHILD_RUNTIME_INFO_COMPONENTS_ATTRIBUTE))
          .thenReturn(channelConnectionObjectNames);

      when(mockJmxHelper.getObjectSetAttribute(channelObjectName.toString(), CHILDREN_ATTRIBUTE)).thenReturn(workflowObjectNames);

      String workflowState = StartedState.class.getSimpleName();
      if (!workflowsAreStarted) {
        workflowState = StoppedState.class.getSimpleName();
      }

      when(mockJmxHelper.getStringAttribute(workflowObjectName1.toString(), UNIQUE_ID)).thenReturn(WORKFLOW_ID1);
      when(mockJmxHelper.getStringAttributeClassName(workflowObjectName1.toString(), COMPONENT_STATE)).thenReturn(workflowState);
        when(mockJmxHelper.getObjectSetAttribute(workflowObjectName1.toString(), CHILD_RUNTIME_INFO_COMPONENTS_ATTRIBUTE))
          .thenReturn(workflowConnectionObjectNames);

      when(mockJmxHelper.getStringAttribute(workflowObjectName2.toString(), UNIQUE_ID)).thenReturn(WORKFLOW_ID2);
      when(mockJmxHelper.getStringAttributeClassName(workflowObjectName2.toString(), COMPONENT_STATE)).thenReturn(workflowState);
        when(mockJmxHelper.getObjectSetAttribute(workflowObjectName2.toString(), CHILD_RUNTIME_INFO_COMPONENTS_ATTRIBUTE))
          .thenReturn(Collections.emptySet());

        when(mockJmxHelper.getStringAttribute(adapterConnection.toString(), UNIQUE_ID)).thenReturn(CONNECTION_ID_ADAPTER);
        when(mockJmxHelper.getStringAttributeClassName(adapterConnection.toString(), COMPONENT_STATE))
          .thenReturn(StartedState.class.getSimpleName());

        when(mockJmxHelper.getStringAttribute(channelConnection.toString(), UNIQUE_ID)).thenReturn(CONNECTION_ID_CHANNEL);
        when(mockJmxHelper.getStringAttributeClassName(channelConnection.toString(), COMPONENT_STATE))
          .thenReturn(StartedState.class.getSimpleName());

        when(mockJmxHelper.getStringAttribute(workflowConnection.toString(), UNIQUE_ID)).thenReturn(CONNECTION_ID_WORKFLOW);
        when(mockJmxHelper.getStringAttributeClassName(workflowConnection.toString(), COMPONENT_STATE))
          .thenReturn(StartedState.class.getSimpleName());

      healthCheck = new WorkflowHealthCheckComponent();
      testConsumer = new TestConsumer();
      healthCheck.setConsumer(testConsumer);
      healthCheck.setJmxMBeanHelper(mockJmxHelper);
      return this;
    }

    public void start() throws Exception {
      start(new Properties());
    }

    public void start(Properties properties) throws Exception {
      healthCheck.init(properties);
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
    protected StandaloneConsumer configureConsumer(AdaptrisMessageListener messageListener, String consumedUrlPath,
        String acceptedHttpMethods) {
      return null;
    }

    @Override
    protected void doResponse(AdaptrisMessage originalMessage, AdaptrisMessage processedMessage, String contentType, int status) {
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
      return isError || payload != null;
    }

    @Override
    public void prepare() {

    }
  }
}
