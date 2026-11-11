package com.adaptris.rest.metrics.prometheus;

import static com.adaptris.rest.AbstractRestfulEndpoint.MANAGEMENT_PORT_KEY;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.adaptris.interlok.junit.scaffolding.util.PortManager;
import com.adaptris.rest.HttpHelper;
import com.adaptris.rest.MockWorkflowConsumer;

public class PrometheusManagementPortTest {

  private Integer port;
  private PrometheusEndpointComponent component;

  @BeforeEach
  public void setUp() {
    port = PortManager.nextUnusedPort(18600);
    component = new PrometheusEndpointComponent();
  }

  @AfterEach
  public void tearDown() throws Exception {
    component.stop();
    component.destroy();
    PortManager.release(port);
  }

  @Test
  public void testNotConfigured_NoPortOnConsumer() throws Exception {
    MockWorkflowConsumer consumer = mockConsumer();
    component.init(new Properties());
    component.start();
    await().atMost(Duration.ofSeconds(10)).until(() -> consumer.getMessageListener() != null);
    assertNull(consumer.getPort());
  }

  @Test
  public void testConfigured_PortOnConsumer() throws Exception {
    MockWorkflowConsumer consumer = mockConsumer();
    component.init(props(String.valueOf(port), null));
    component.start();
    await().atMost(Duration.ofSeconds(10)).until(() -> consumer.getMessageListener() != null);
    assertEquals(port, consumer.getPort());
  }

  @Test
  public void testConfigured_OptedOut_NoPortOnConsumer() throws Exception {
    MockWorkflowConsumer consumer = mockConsumer();
    component.init(props(String.valueOf(port), "false"));
    component.start();
    await().atMost(Duration.ofSeconds(10)).until(() -> consumer.getMessageListener() != null);
    assertNull(consumer.getPort());
  }

  @Test
  public void testInvalidPort_FailsInit() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> component.init(props("not-a-port", null)));
    assertTrue(e.getMessage().contains(MANAGEMENT_PORT_KEY));
  }

  @Test
  public void testServesMetricsOnManagementPort() throws Exception {
    component.init(props(String.valueOf(port), null));
    component.start();

    await().atMost(Duration.ofSeconds(30)).until(() -> HttpHelper.listening(port));
    HttpHelper.Response response = HttpHelper.get(port, "/prometheus/metrics");
    assertEquals(200, response.status());
    assertTrue(response.contentType().startsWith("text/plain"), response.contentType());
    assertEquals(404, HttpHelper.get(port, "/workflow-health-check/alive").status());

    component.stop();
    component.destroy();
    await().atMost(Duration.ofSeconds(10)).until(() -> !HttpHelper.listening(port));
  }

  private MockWorkflowConsumer mockConsumer() {
    MockWorkflowConsumer consumer = new MockWorkflowConsumer();
    component.setConsumer(consumer);
    return consumer;
  }

  private static Properties props(String port, String enabled) {
    Properties p = new Properties();
    if (port != null) {
      p.setProperty(MANAGEMENT_PORT_KEY, port);
    }
    if (enabled != null) {
      p.setProperty(PrometheusEndpointComponent.BOOTSTRAP_MANAGEMENT_PORT_ENABLED_KEY, enabled);
    }
    return p;
  }

}
