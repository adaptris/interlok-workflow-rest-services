package com.adaptris.rest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.adaptris.rest.metrics.jvm.JvmMetricsGenerator;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class JvmMetricsGeneratorTest {

  private JvmMetricsGenerator generator;

  private SimpleMeterRegistry mockRegistry;

  @BeforeEach
  public void setUp() throws Exception {
    generator = new JvmMetricsGenerator();
    generator.init(null);
    generator.start();
  }

  @AfterEach
  public void tearDown() throws Exception {
    generator.stop();
    generator.destroy();
  }

  @Test
  public void testBindMetrics() throws Exception {
    mockRegistry = new SimpleMeterRegistry();
    generator.bindTo(mockRegistry);

    assertTrue(mockRegistry.getMeters().size() > 0);
    for (Meter m : mockRegistry.getMeters()) {
      assertNotNull(m.measure().iterator().next().getValue());
    }
  }

  @Test
  public void testBindMetricsMultipleTimes() throws Exception {
    mockRegistry = new SimpleMeterRegistry();
    generator.bindTo(mockRegistry);
    generator.bindTo(mockRegistry);
    generator.bindTo(mockRegistry);

    assertTrue(mockRegistry.getMeters().size() > 0);
    for (Meter m : mockRegistry.getMeters()) {
      assertNotNull(m.measure().iterator().next().getValue());
    }
  }

}
