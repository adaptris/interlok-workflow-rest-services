package com.adaptris.rest.datadog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.adaptris.rest.datadog.DatadogPushComponent;
import com.adaptris.rest.metrics.MetricBinder;
import com.adaptris.rest.metrics.MetricProviders;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

public class DatadogPushComponentTest {

  private DatadogPushComponent component;
  
  private Properties properties;

  @Before
  public void setUp() throws Exception {
    properties = new Properties();
    properties.put("datadogPushTimerSeconds", "1");
    properties.put("datadogUrlKey", "http://localhost:5000");
    properties.put("datadogApiKey", "my-api-key");
    
    component = new DatadogPushComponent();
    component.init(properties);
  }

  @After
  public void tearDown() throws Exception {
    component.destroy();
  }

  @Test
  public void testDefaultsNoApiKey() throws Exception {
    try {
      component.init(new Properties());
      fail("No API key, exception expected.");
    } catch (Exception ex) {
      //expected
    }
  }
  
  @Test
  public void testDefaultsWithApiKey() throws Exception {
    properties = new Properties();
    properties.put("datadogApiKey", "my-api-key");
    
    component.init(properties);
  }
  
  @Test
  public void testNoMetrics() throws Exception {
    component.start();
    Thread.sleep(1500);
    component.stop();
    
    assertEquals(0, component.getDatadogRegistry().getMeters().size());
  }

  @Test
  public void testMetrics() throws Exception {
    MetricProviders.getProviders().clear();
    MetricProviders.getProviders().add(new MockMetricProvider());

    component.start();
    Thread.sleep(1500);
    component.stop();
    
    assertEquals(1, component.getDatadogRegistry().getMeters().size());
  }

  @Test
  public void testErrorOnMetricBind() throws Exception {
    MetricProviders.getProviders().clear();
    MetricProviders.getProviders().add(new MockFailingMetricProvider());
    component.start();
    Thread.sleep(1500);
    component.stop();
    
    assertEquals(0, component.getDatadogRegistry().getMeters().size());
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
