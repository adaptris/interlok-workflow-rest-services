package com.adaptris.rest.metrics.interlok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.management.ObjectName;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adaptris.profiler.jmx.TimedThroughputMetric;
import com.adaptris.profiler.jmx.TimedThroughputMetricMBean;
import com.adaptris.rest.util.JmxMBeanHelper;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

public class InterlokProfilerMetricsGeneratorTest {

  private InterlokProfilerMetricsGenerator component;

  @Mock
  private JmxMBeanHelper mockJmxHelper;

  private TimedThroughputMetric metricMbeanOne, metricMbeanTwo, metricMbeanThree;

  private PrometheusMeterRegistry meterRegistry;

  @BeforeEach
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);

    metricMbeanOne = new TimedThroughputMetric();
    metricMbeanOne.setUniqueId("workflow");
    metricMbeanOne.setWorkflowId("workflowid");

    metricMbeanTwo = new TimedThroughputMetric();
    metricMbeanTwo.setUniqueId("producer");
    metricMbeanTwo.setWorkflowId("workflowid");

    metricMbeanThree = new TimedThroughputMetric();
    metricMbeanThree.setUniqueId("workflow");

    Set<ObjectName> metricSet = new HashSet<>();
    ObjectName o1 = new ObjectName("com.adaptris:type=profiler,componentType=workflow,id=1");
    ObjectName o2 = new ObjectName("com.adaptris:type=profiler,componentType=producer,id=2");

    metricSet.add(o1);
    metricSet.add(o2);

    when(mockJmxHelper.getMBeanNames(anyString())).thenReturn(metricSet);

    when(mockJmxHelper.proxyMBean(o1, TimedThroughputMetricMBean.class)).thenReturn(metricMbeanOne);
    when(mockJmxHelper.proxyMBean(o2, TimedThroughputMetricMBean.class)).thenReturn(metricMbeanTwo);

    meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    component = new InterlokProfilerMetricsGenerator();
    component.setJmxMBeanHelper(mockJmxHelper);

    component.init(null);
    component.start();
  }

  @AfterEach
  public void tearDown() throws Exception {
    component.stop();
    component.destroy();
  }

  @Test
  public void testNoMetrics() throws Exception {
    when(mockJmxHelper.getMBeanNames(anyString()))
        .thenReturn(Collections.emptySet());

    component.bindTo(meterRegistry);
    assertEquals(0, meterRegistry.getMeters().size());
  }

  @Test
  public void testMetricsWithoutWorkflows() throws Exception {
    Set<ObjectName> metricSet = new HashSet<>();
    ObjectName o3 = new ObjectName("com.adaptris:type=profiler,componentType=producer,id=2");
    metricSet.add(o3);

    when(mockJmxHelper.proxyMBean(o3, TimedThroughputMetricMBean.class)).thenReturn(metricMbeanThree);

    when(mockJmxHelper.getMBeanNames(anyString())).thenReturn(metricSet);

    component.bindTo(meterRegistry);
    assertEquals(0, meterRegistry.getMeters().size());
  }

  @Test
  public void testMetrics() throws Exception {
    component.bindTo(meterRegistry);
    assertEquals(6, meterRegistry.getMeters().size());
  }

  @Test
  public void testBindMetricsMultipleTimes() throws Exception {
    component.bindTo(meterRegistry);
    component.bindTo(meterRegistry);
    component.bindTo(meterRegistry);
    component.bindTo(meterRegistry);
    component.bindTo(meterRegistry);

    // AvgNanos, count and failed count metrics for both MBeans == 6 in total.
    assertEquals(6, meterRegistry.getMeters().size());

    String scrape = meterRegistry.scrape();

    assertTrue(scrape.contains("workflow_avgnanos"));
    assertTrue(scrape.contains("producer_avgnanos"));

    assertTrue(scrape.contains("workflow_count_total"));
    assertTrue(scrape.contains("producer_count_total"));

    assertTrue(scrape.contains("workflow_fail_count_total"));
    assertTrue(scrape.contains("producer_fail_count_total"));
  }

}