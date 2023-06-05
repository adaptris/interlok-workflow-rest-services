package com.adaptris.rest.metrics.jvm;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.rest.metrics.MetricBinder;
import com.adaptris.rest.metrics.MetricProviders;

import io.github.mweirauch.micrometer.jvm.extras.ProcessMemoryMetrics;
import io.github.mweirauch.micrometer.jvm.extras.ProcessThreadMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmCompilationMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import lombok.Getter;

public class JvmMetricsGenerator extends MgmtComponentImpl implements MetricBinder {

  @Getter
  private final Collection<Tag> tags;
  
  private final List<Class<? extends MeterBinder>> meterBinders = Arrays.asList(
      ClassLoaderMetrics.class,
      JvmCompilationMetrics.class,
      JvmGcMetrics.class,
      JvmHeapPressureMetrics.class,
      JvmMemoryMetrics.class,
      JvmThreadMetrics.class,
      ProcessorMetrics.class,
      ProcessMemoryMetrics.class,
      ProcessThreadMetrics.class,
      FileDescriptorMetrics.class,
      UptimeMetrics.class);

  private volatile boolean metricsRegistered;

  public JvmMetricsGenerator() {
    this(Collections.emptyList());
  }

  public JvmMetricsGenerator(Collection<Tag> tags) {
    this.tags = tags;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    if (!metricsRegistered) {
      meterBinders.forEach(binder -> {
        try {
          log.trace("Generating metrics from meter: {}", binder.getSimpleName());
          binder.getDeclaredConstructor().newInstance().bindTo(registry);
        } catch (Exception e) {
          log.warn("Could not collect metrics from binder: {}", binder.getSimpleName(), e);
        }
      });
      metricsRegistered = true;
    }
  }

  @Override
  public void init(Properties config) throws Exception {
    MetricProviders.addProvider(this);

    metricsRegistered = false;
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
