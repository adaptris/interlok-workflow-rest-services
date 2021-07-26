package com.adaptris.rest;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.CoreException;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.core.SerializableAdaptrisMessage;
import com.adaptris.interlok.client.jmx.InterlokJmxClient;
import com.adaptris.interlok.types.SerializableMessage;

public class WorkflowServicesComponentTest {
  
  private static final String PATH_KEY = "jettyURI";
  
  private static final String HTTP_HEADER_HOST = "http.header.Host";
  
  private static final String WORKFLOW_MANAGER_CLASS = "com.adaptris.core.runtime.WorkflowManager";
  
  private static final String NOT_WORKFLOW_MANAGER_CLASS = "x.x.x";
  
  private static final String WORKFLOW_OBJECT_ONE = "com.adaptris:type=Workflow,adapter=MyInterlokInstance,channel=channel-1,id=standard-workflow-1";
  
  private static final String WORKFLOW_OBJECT_TWO = "com.adaptris:type=Workflow,adapter=MyInterlokInstance,channel=channel-2,id=standard-workflow-2";
  
  private AdaptrisMessage message;
  
  private AdaptrisMessage returnedMessage;
  
  private WorkflowServicesComponent workflowServicesComponent;
  
  @Mock private WorkflowServicesConsumer mockConsumer;
  
  @Mock private InterlokJmxClient mockJmxClient;
  
  @Mock private MBeanServer mockMbeanServer;
  
  @Mock private AdaptrisMessageFactory mockMessagefactory;

  private SerializableMessage mockSerMessage;
  
  private Set<ObjectInstance> mockReturnedWorkflows;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    
    message = DefaultMessageFactory.getDefaultInstance().newMessage();
    returnedMessage = DefaultMessageFactory.getDefaultInstance().newMessage();
    mockSerMessage = new SerializableAdaptrisMessage();
    
    workflowServicesComponent = new WorkflowServicesComponent();
    workflowServicesComponent.setConsumer(mockConsumer);
    workflowServicesComponent.setJmxClient(mockJmxClient);
    
    mockReturnedWorkflows = new HashSet<>();
    mockReturnedWorkflows.add(new ObjectInstance(new ObjectName(WORKFLOW_OBJECT_ONE), WORKFLOW_MANAGER_CLASS));
    mockReturnedWorkflows.add(new ObjectInstance(new ObjectName(WORKFLOW_OBJECT_TWO), WORKFLOW_MANAGER_CLASS));
    mockReturnedWorkflows.add(new ObjectInstance(new ObjectName(WORKFLOW_OBJECT_TWO), NOT_WORKFLOW_MANAGER_CLASS));
  }

  @After
  public void tearDown() throws Exception {
    stopComponent();
  }

  @Test
  public void testHappyPathMessageProcessed() throws Exception {
    startComponent();
    
    message.addMessageHeader(PATH_KEY, "/workflow-services/myAdapter/myChannel/myWorkflow");
    
    when(mockJmxClient.process(any(), any())).thenReturn(mockSerMessage);
    
    workflowServicesComponent.onAdaptrisMessage(message);
    
    verify(mockJmxClient).process(any(), any());
    verify(mockConsumer).doResponse(any(), any());
  }

  @Test
  public void testYamlDefRequest() throws Exception {
    startComponent();
    
    message.addMessageHeader(PATH_KEY, "/workflow-services/");
    workflowServicesComponent.onAdaptrisMessage(message);
    
    verify(mockJmxClient, times(0)).process(any(), any());
  }

  @Test
  public void testProcessingWorkflowsDefinition() throws Exception {
    startComponent();
    
    message.addMessageHeader(PATH_KEY, "/workflow-services/");
    message.addMessageHeader(HTTP_HEADER_HOST, "myHost:8080");
    workflowServicesComponent.setInterlokMBeanServer(mockMbeanServer);
    workflowServicesComponent.setMessageFactory(mockMessagefactory);
    
    when(mockMessagefactory.newMessage())
      .thenReturn(returnedMessage);
    
    when(mockMbeanServer.queryMBeans(any(), any()))
      .thenReturn(mockReturnedWorkflows);
    
    workflowServicesComponent.onAdaptrisMessage(message);
    
    assertTrue(returnedMessage.getContent().contains("standard-workflow-1"));
    assertTrue(returnedMessage.getContent().contains("standard-workflow-2"));
  }

  @Test
  public void testInitFails() throws Exception {
    doThrow(new CoreException("Expected"))
        .when(mockConsumer).init();
    
    startComponent();
    
    // If the init fails, then start shuld not run.
    verify(mockConsumer, times(0)).start();
  }

  @Test
  public void testErrorResponse() throws Exception {
    startComponent();

    message.addMessageHeader(PATH_KEY, "/workflow-services/1/2/3/4/5/6/7/8/9");
    workflowServicesComponent.onAdaptrisMessage(message);

    verify(mockJmxClient, times(0)).process(any(), any());
    verify(mockConsumer).doErrorResponse(any(), any(), anyInt());
  }
  
  private void startComponent() throws Exception {
    workflowServicesComponent.init(new Properties());
    workflowServicesComponent.start();
  }
  
  private void stopComponent() throws Exception {
    workflowServicesComponent.stop();
    workflowServicesComponent.destroy();
  }

}
