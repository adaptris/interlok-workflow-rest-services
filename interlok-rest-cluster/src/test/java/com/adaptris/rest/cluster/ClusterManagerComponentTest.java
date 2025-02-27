package com.adaptris.rest.cluster;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import javax.management.MalformedObjectNameException;

import org.awaitility.Durations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.core.XStreamJsonMarshaller;
import com.adaptris.core.cache.ExpiringMapCache;
import com.adaptris.mgmt.cluster.ClusterInstance;
import com.adaptris.mgmt.cluster.mbean.ClusterManagerMBean;
import com.adaptris.rest.MockWorkflowConsumer;
import com.adaptris.rest.util.JmxMBeanHelper;

public class ClusterManagerComponentTest {

  private static final String CLUSTER_MANAGER_OBJECT_NAME = "com.adaptris:type=ClusterManager,id=ClusterManager";

  private ClusterManagerComponent clusterManagerComponent;

  private MockWorkflowConsumer testConsumer = new MockWorkflowConsumer();

  private ExpiringMapCache expiringMapCache;

  private ClusterInstance clusterInstanceOne;

  private ClusterInstance clusterInstanceTwo;

  private AdaptrisMessage message;

  @Mock private JmxMBeanHelper mockJmxHelper;

  @Mock private ClusterManagerMBean mockClusterManagerMBean;

  @BeforeEach
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);

    message = DefaultMessageFactory.getDefaultInstance().newMessage();

    clusterManagerComponent = new ClusterManagerComponent();
    clusterManagerComponent.setJmxMBeanHelper(mockJmxHelper);
    clusterManagerComponent.setConsumer(testConsumer);

    clusterManagerComponent.init(new Properties());
    clusterManagerComponent.start();

    expiringMapCache = new ExpiringMapCache();
    expiringMapCache.init();

    clusterInstanceOne = new ClusterInstance(UUID.randomUUID(), "id-1", "jms-address-1");
    clusterInstanceTwo = new ClusterInstance(UUID.randomUUID(), "id-2", "jms-address-2");

    when(mockJmxHelper.proxyMBean(CLUSTER_MANAGER_OBJECT_NAME, ClusterManagerMBean.class))
        .thenReturn(mockClusterManagerMBean);
    when(mockClusterManagerMBean.getClusterInstances())
        .thenReturn(expiringMapCache);
  }

  @AfterEach
  public void tearDown() throws Exception {
    clusterManagerComponent.stop();
    clusterManagerComponent.destroy();
  }

  @Test
  public void testNoClusters() throws Exception {
    clusterManagerComponent.onAdaptrisMessage(message);

    await()
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .until(testConsumer::complete);

    // assert no cluster instances;
    assertFalse(testConsumer.getPayload().contains("instance"));
  }

  @Test
  public void testMyOwnClusterInstance() throws Exception {
    expiringMapCache.put(clusterInstanceOne.getUniqueId(), clusterInstanceOne);

    clusterManagerComponent.onAdaptrisMessage(message);

    await()
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .until(testConsumer::complete);

    List<?> instances = (List<?>) new XStreamJsonMarshaller().unmarshal(testConsumer.getPayload());
    assertTrue(instances.size() == 1);
    
    @SuppressWarnings("rawtypes")
    ClusterInstance clusterInstance = (ClusterInstance) instances.get(0);
    
    assertTrue(clusterInstance.getUniqueId().equals(clusterInstanceOne.getUniqueId()));
    assertTrue(clusterInstance.getClusterUuid().equals(clusterInstanceOne.getClusterUuid()));
    assertTrue(clusterInstance.getJmxAddress().equals(clusterInstanceOne.getJmxAddress()));
  }

  @Test
  public void testMultipleClusterInstances() throws Exception {
    expiringMapCache.put(clusterInstanceOne.getUniqueId(), clusterInstanceOne);
    expiringMapCache.put(clusterInstanceTwo.getUniqueId(), clusterInstanceTwo);

    clusterManagerComponent.onAdaptrisMessage(message);

    await()
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .until(testConsumer::complete);

    List<?> instances = (List<?>) new XStreamJsonMarshaller().unmarshal(testConsumer.getPayload());
    
    @SuppressWarnings("rawtypes")
	ArrayList instancesList = (ArrayList) instances;
    assertTrue(instancesList.size() == 2);
    
    ClusterInstance clusterInstance = (ClusterInstance) instancesList.get(0);
    assertTrue(clusterInstance.getUniqueId().equals(clusterInstanceOne.getUniqueId()));
    assertTrue(clusterInstance.getClusterUuid().equals(clusterInstanceOne.getClusterUuid()));
    assertTrue(clusterInstance.getJmxAddress().equals(clusterInstanceOne.getJmxAddress()));

    ClusterInstance clusterInstance1 = (ClusterInstance) instancesList.get(1);    
    assertTrue(clusterInstance1.getUniqueId().equals(clusterInstanceTwo.getUniqueId()));
    assertTrue(clusterInstance1.getClusterUuid().equals(clusterInstanceTwo.getClusterUuid()));
    assertTrue(clusterInstance1.getJmxAddress().equals(clusterInstanceTwo.getJmxAddress()));
  }

  @Test
  public void testMBeansNotAvailable() throws Exception {
    doThrow(new MalformedObjectNameException("expected"))
        .when(mockJmxHelper).proxyMBean(CLUSTER_MANAGER_OBJECT_NAME, ClusterManagerMBean.class);

    clusterManagerComponent.onAdaptrisMessage(message);

    await()
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .until(testConsumer::complete);

    assertTrue(testConsumer.isError());
  }
  
}
