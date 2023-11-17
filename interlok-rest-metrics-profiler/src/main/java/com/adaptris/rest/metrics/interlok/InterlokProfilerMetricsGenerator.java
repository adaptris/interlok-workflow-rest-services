package com.adaptris.rest.metrics.interlok;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.management.ObjectName;

import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.profiler.jmx.TimedThroughputMetricMBean;
import com.adaptris.rest.metrics.MetricBinder;
import com.adaptris.rest.metrics.MetricProviders;
import com.adaptris.rest.util.JmxMBeanHelper;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.Getter;
import lombok.Setter;

public class InterlokProfilerMetricsGenerator extends MgmtComponentImpl implements MetricBinder {

  private static final String COUNT_HELP = "The number of messages this component has processed.";
  private static final String FAIL_COUNT_HELP = "The number of failed messages this component has processed.";
  private static final String AVG_NANOS_HELP = "The average time in nanoseconds this component has takes to process a message.";

  private static final String PROFILER_OBJECT_NAME = "com.adaptris:type=Profiler,*";

  private static final String METRIC_COUNT = ".count";
  private static final String METRIC_FAIL_COUNT = ".fail.count";
  private static final String METRIC_AVG_NANOS = ".avgnanos";
  private static final String COMPONENT_TYPE_PROPERTY = "componentType";
  private static final String COMPONENT_TAG = "component";
  private static final String WORKFLOW_TAG = "workflow";

  private Map<String, Meter> meterMap;

  @Getter
  @Setter
  private JmxMBeanHelper jmxMBeanHelper;

  public InterlokProfilerMetricsGenerator() {
    meterMap = new HashMap<>();
    setJmxMBeanHelper(new JmxMBeanHelper());
  }

  @Override
  public void bindTo(MeterRegistry registry) throws Exception {
    Set<ObjectName> queryMBeans = getJmxMBeanHelper().getMBeanNames(PROFILER_OBJECT_NAME);
    queryMBeans.forEach(object -> {
      TimedThroughputMetricMBean mBean = getJmxMBeanHelper().proxyMBean(object, TimedThroughputMetricMBean.class);
      // If no workflow ID, then this may be an event or the base workflow rest HTTP acceptor service so ignore.
      if (mBean.getWorkflowId() != null) {
        Counter countCounter = getOrCreateCountMeter(METRIC_COUNT + mBean.getUniqueId(), object, mBean, registry);
        countCounter.increment(mBean.getMessageCount() - countCounter.count());

        Counter failCountCounter = getOrCreateFailedCountMeter(METRIC_FAIL_COUNT + mBean.getUniqueId(), object, mBean, registry);
        failCountCounter.increment(mBean.getFailedMessageCount() - failCountCounter.count());

        getOrCreateNanosMeter(METRIC_AVG_NANOS + mBean.getUniqueId(), object, mBean, registry);
      }
    });
  }

  private Gauge getOrCreateNanosMeter(String key, ObjectName object, TimedThroughputMetricMBean mBean, MeterRegistry registry) {
    Gauge meter = (Gauge) meterMap.get(key);
    if (meter == null) {
      meter = Gauge.builder(object.getKeyProperty(COMPONENT_TYPE_PROPERTY) + METRIC_AVG_NANOS, () -> {
        return mBean.getAverageNanoseconds();
      }).tags(Tags.of(COMPONENT_TAG, mBean.getUniqueId()).and(WORKFLOW_TAG, mBean.getWorkflowId())).description(AVG_NANOS_HELP)
          .register(registry);
      meterMap.put(key, meter);
    }

    return meter;
  }

  private Counter getOrCreateCountMeter(String key, ObjectName object, TimedThroughputMetricMBean mBean, MeterRegistry registry) {
    Counter meter = (Counter) meterMap.get(key);
    if (meter == null) {
      meter = Counter.builder(object.getKeyProperty(COMPONENT_TYPE_PROPERTY) + METRIC_COUNT).description(COUNT_HELP)
          .tags(Tags.of(COMPONENT_TAG, mBean.getUniqueId()).and(WORKFLOW_TAG, mBean.getWorkflowId())).register(registry);

      meterMap.put(key, meter);
    }

    return meter;
  }

  private Counter getOrCreateFailedCountMeter(String key, ObjectName object, TimedThroughputMetricMBean mBean, MeterRegistry registry) {
    Counter meter = (Counter) meterMap.get(key);
    if (meter == null) {
      meter = Counter.builder(object.getKeyProperty(COMPONENT_TYPE_PROPERTY) + METRIC_FAIL_COUNT).description(FAIL_COUNT_HELP)
          .tags(Tags.of(COMPONENT_TAG, mBean.getUniqueId()).and(WORKFLOW_TAG, mBean.getWorkflowId())).register(registry);

      meterMap.put(key, meter);
    }

    return meter;
  }

  @Override
  public void init(Properties config) throws Exception {
    MetricProviders.addProvider(this);
  }

  @Override
  public void start() throws Exception {
  }

  @Override
  public void stop() throws Exception {
  }

  @Override
  public void destroy() throws Exception {
  }

}
