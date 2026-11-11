package com.adaptris.rest.healthcheck;

import static com.adaptris.rest.AbstractRestfulEndpoint.MANAGEMENT_PORT_KEY;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.interlok.junit.scaffolding.util.PortManager;
import com.adaptris.rest.WorkflowServicesConsumer;

/**
 * Tests for serving the health-check on the dedicated management port, and for staying on the main Jetty port when not configured
 * or opted out.
 */
public class HealthCheckManagementPortTest {

  private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private Integer port;
  private WorkflowHealthCheckComponent component;

  @BeforeEach
  public void setUp() {
    port = PortManager.nextUnusedPort(18500);
    component = new WorkflowHealthCheckComponent();
  }

  @AfterEach
  public void tearDown() throws Exception {
    component.stop();
    component.destroy();
    PortManager.release(port);
  }

  @Test
  public void testNotConfigured_NoPortOnConsumer() throws Exception {
    RecordingConsumer consumer = recordingConsumer();
    component.init(new Properties());
    component.start();
    await().atMost(Duration.ofSeconds(10)).until(() -> consumer.getMessageListener() != null);
    assertNull(consumer.getPort());
  }

  @Test
  public void testConfigured_PortOnConsumer() throws Exception {
    RecordingConsumer consumer = recordingConsumer();
    component.init(props(String.valueOf(port), null));
    component.start();
    await().atMost(Duration.ofSeconds(10)).until(() -> consumer.getMessageListener() != null);
    assertEquals(port, consumer.getPort());
  }

  @Test
  public void testConfigured_OptedOut_NoPortOnConsumer() throws Exception {
    RecordingConsumer consumer = recordingConsumer();
    component.init(props(String.valueOf(port), "false"));
    component.start();
    await().atMost(Duration.ofSeconds(10)).until(() -> consumer.getMessageListener() != null);
    assertNull(consumer.getPort());
  }

  @Test
  public void testInvalidPort_FailsInit() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> component.init(props("99999", null)));
    assertTrue(e.getMessage().contains(MANAGEMENT_PORT_KEY));
  }

  @Test
  public void testServesHealthOnManagementPort() throws Exception {
    Properties p = props(String.valueOf(port), null);
    p.setProperty("rest.health-check.path", "/healthcheck/*");
    component.init(p);
    component.start();

    await().atMost(Duration.ofSeconds(30)).until(() -> listening(port));
    assertEquals(200, status(port, "/healthcheck/alive"));
    assertEquals(404, status(port, "/prometheus/metrics"));

    component.stop();
    component.destroy();
    await().atMost(Duration.ofSeconds(10)).until(() -> !listening(port));
  }

  private RecordingConsumer recordingConsumer() {
    RecordingConsumer consumer = new RecordingConsumer();
    component.setConsumer(consumer);
    return consumer;
  }

  private static Properties props(String port, String enabled) {
    Properties p = new Properties();
    if (port != null) {
      p.setProperty(MANAGEMENT_PORT_KEY, port);
    }
    if (enabled != null) {
      p.setProperty(WorkflowHealthCheckComponent.BOOTSTRAP_MANAGEMENT_PORT_ENABLED_KEY, enabled);
    }
    return p;
  }

  private static int status(int port, String path) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(10)).GET()
        .build();
    return CLIENT.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
  }

  private static boolean listening(int port) {
    try {
      status(port, "/");
      return true;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  // Records what the component configures, without starting anything.
  private static class RecordingConsumer extends WorkflowServicesConsumer {
    @Override
    protected StandaloneConsumer configureConsumer(AdaptrisMessageListener messageListener, String consumedUrlPath,
        String acceptedHttpMethods) {
      return null;
    }

    @Override
    protected void doResponse(AdaptrisMessage orig, AdaptrisMessage proc, String contentType, int httpResponse) {
    }

    @Override
    protected void doErrorResponse(AdaptrisMessage message, Exception e, int httpStatus) {
    }

    @Override
    public void prepare() {
    }
  }

}
