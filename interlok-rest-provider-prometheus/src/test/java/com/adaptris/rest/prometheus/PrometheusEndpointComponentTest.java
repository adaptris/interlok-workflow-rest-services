package com.adaptris.rest.prometheus;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.rest.MockWorkflowConsumer;
import com.adaptris.rest.metrics.MetricBinder;
import com.adaptris.rest.metrics.MetricProviders;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

public class PrometheusEndpointComponentTest {

  private PrometheusEndpointComponent component;

  private AdaptrisMessage message;

  private MockWorkflowConsumer mockConsumer;

  @Before
  public void setUp() throws Exception {
    mockConsumer = new MockWorkflowConsumer();

    component = new PrometheusEndpointComponent();
    component.setConsumer(mockConsumer);
    component.init(new Properties());
    component.start();
    message = DefaultMessageFactory.getDefaultInstance().newMessage();
  }

  @After
  public void tearDown() throws Exception {
    component.stop();
    component.destroy();

  }

  @Test
  public void testNoMetrics() throws Exception {
    component.onAdaptrisMessage(message);

    assertFalse(mockConsumer.isError());
    assertTrue(mockConsumer.getPayload().equals(""));
  }

  @Test
  public void testMetrics() throws Exception {
    MetricProviders.getProviders().clear();
    MetricProviders.getProviders().add(new MockMetricProvider());

    component.onAdaptrisMessage(message);

    assertFalse(mockConsumer.isError());
    assertTrue(mockConsumer.getPayload().contains("test_metric"));
  }

  @Test
  public void testErrorResponseOnMetricGenerationException() throws Exception {
    MetricProviders.getProviders().clear();
    MetricProviders.getProviders().add(new MockFailingMetricProvider());
    component.onAdaptrisMessage(message);

    assertFalse(mockConsumer.isError());
    assertTrue(mockConsumer.getPayload().isEmpty());
  }

  @Test
  public void testErrorResponse() throws Exception {
    component.onAdaptrisMessage(null);

    assertTrue(mockConsumer.isError());
    assertTrue(mockConsumer.getHttpStatus() == 500);
  }

  @Test
  public void testDebugLogging() throws Exception {
    component.exceptionLogging(true, "hello", new Exception());
    component.exceptionLogging(false, "hello", new Exception());
  }


  class MockMetricProvider implements MetricBinder {
    int value = 100;

    @Override
    public void bindTo(MeterRegistry registry) throws Exception {
      Gauge.builder("test-metric", this, MockMetricProvider::getValue)
          .description("A test metric.")
          .register(registry);
    }

    public int getValue() {
      return value;
    }
  }

  class MockFailingMetricProvider implements MetricBinder {

    @Override
    public void bindTo(MeterRegistry registry) throws Exception {
      throw new Exception("Expected");
    }
  }
}