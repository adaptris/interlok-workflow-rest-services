package com.adaptris.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.adaptris.core.CoreException;
import com.adaptris.core.http.jetty.ServletWrapper;
import com.adaptris.core.util.LifecycleHelper;
import com.adaptris.interlok.junit.scaffolding.util.PortManager;

/**
 * Tests for the shared, reference-counted management servers, using {@link ManagementPortConnection} against real ports.
 */
public class ManagementServersTest {

  private Integer port;
  private Integer otherPort;
  private final List<ManagementPortConnection> connections = new ArrayList<>();

  @BeforeEach
  public void setUp() {
    port = PortManager.nextUnusedPort(18800);
    otherPort = PortManager.nextUnusedPort(18900);
  }

  @AfterEach
  public void tearDown() {
    connections.forEach(LifecycleHelper::stopAndClose);
    PortManager.release(port);
    PortManager.release(otherPort);
  }

  @Test
  public void testSharedServer() throws Exception {
    int before = ManagementServers.serverCount();
    ManagementPortConnection one = started(port);
    ManagementPortConnection two = started(port);
    ServletWrapper w1 = new ServletWrapper(new TextServlet("one"), "/one/*");
    ServletWrapper w2 = new ServletWrapper(new TextServlet("two"), "/two/*");
    one.addServlet(w1);
    two.addServlet(w2);

    assertEquals(before + 1, ManagementServers.serverCount());
    assertEquals(new HttpHelper.Response(200, "one"), HttpHelper.get(port, "/one/a"));
    assertEquals(new HttpHelper.Response(200, "two"), HttpHelper.get(port, "/two/b"));
    assertEquals(404, HttpHelper.get(port, "/three").status());

    // Stopping one user leaves the other serving.
    one.removeServlet(w1);
    LifecycleHelper.stopAndClose(one);
    assertTrue(ManagementServers.isRegistered(port));
    assertEquals(new HttpHelper.Response(200, "two"), HttpHelper.get(port, "/two/b"));
    assertEquals(404, HttpHelper.get(port, "/one/a").status());

    // Stopping the last user releases the port.
    two.removeServlet(w2);
    LifecycleHelper.stopAndClose(two);
    assertFalse(ManagementServers.isRegistered(port));
    assertEquals(before, ManagementServers.serverCount());
    assertPortFree(port);
  }

  @Test
  public void testDifferentPorts_AreIndependent() throws Exception {
    ManagementPortConnection one = started(port);
    ManagementPortConnection two = started(otherPort);
    one.addServlet(new ServletWrapper(new TextServlet("one"), "/x/*"));
    two.addServlet(new ServletWrapper(new TextServlet("two"), "/x/*"));

    assertEquals(new HttpHelper.Response(200, "one"), HttpHelper.get(port, "/x/"));
    assertEquals(new HttpHelper.Response(200, "two"), HttpHelper.get(otherPort, "/x/"));

    LifecycleHelper.stopAndClose(one);
    assertPortFree(port);
    assertEquals(new HttpHelper.Response(200, "two"), HttpHelper.get(otherPort, "/x/"));
  }

  @Test
  public void testRestart() throws Exception {
    ManagementPortConnection one = started(port);
    ServletWrapper w = new ServletWrapper(new TextServlet("one"), "/one/*");
    one.addServlet(w);
    one.removeServlet(w);
    LifecycleHelper.stopAndClose(one);
    assertPortFree(port);

    ManagementPortConnection again = started(port);
    again.addServlet(new ServletWrapper(new TextServlet("again"), "/one/*"));
    assertEquals(new HttpHelper.Response(200, "again"), HttpHelper.get(port, "/one/"));
  }

  @Test
  public void testUnbalancedLifecycle_DoesNotReleaseOtherUsers() throws Exception {
    ManagementPortConnection one = started(port);
    one.addServlet(new ServletWrapper(new TextServlet("one"), "/one/*"));

    ManagementPortConnection neverStarted = new ManagementPortConnection(port);
    neverStarted.stopConnection();
    neverStarted.closeConnection();
    LifecycleHelper.stopAndClose(neverStarted);

    // closing twice is also harmless.
    ManagementPortConnection two = started(port);
    two.closeConnection();
    two.closeConnection();

    assertTrue(ManagementServers.isRegistered(port));
    assertEquals(new HttpHelper.Response(200, "one"), HttpHelper.get(port, "/one/"));
  }

  @Test
  public void testConcurrentInit_CreatesOneServer() throws Exception {
    int before = ManagementServers.serverCount();
    int threads = 4;
    CyclicBarrier barrier = new CyclicBarrier(threads);
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      List<Future<ManagementPortConnection>> results = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        results.add(executor.submit(() -> {
          ManagementPortConnection c = new ManagementPortConnection(port);
          barrier.await(10, TimeUnit.SECONDS);
          return LifecycleHelper.initAndStart(c);
        }));
      }
      for (Future<ManagementPortConnection> f : results) {
        connections.add(f.get(30, TimeUnit.SECONDS));
      }
    } finally {
      executor.shutdownNow();
    }
    assertEquals(before + 1, ManagementServers.serverCount());
    connections.get(0).addServlet(new ServletWrapper(new TextServlet("ok"), "/ok/*"));
    assertEquals(new HttpHelper.Response(200, "ok"), HttpHelper.get(port, "/ok/"));
  }

  @Test
  public void testPortInUse_FailsAndCleansUp() throws Exception {
    try (ServerSocket blocker = new ServerSocket(port)) {
      ManagementPortConnection c = new ManagementPortConnection(port);
      assertThrows(CoreException.class, () -> LifecycleHelper.initAndStart(c));
      assertFalse(ManagementServers.isRegistered(port));
    }
  }

  @Test
  public void testServletOperations_WithoutServer() {
    ManagementPortConnection c = new ManagementPortConnection(port);
    ServletWrapper w = new ServletWrapper(new TextServlet("x"), "/x/*");
    assertThrows(CoreException.class, () -> c.addServlet(w));
    assertThrows(CoreException.class, () -> c.removeServlet(w));
  }

  private ManagementPortConnection started(int p) throws CoreException {
    ManagementPortConnection c = LifecycleHelper.initAndStart(new ManagementPortConnection(p));
    connections.add(c);
    return c;
  }

  private static void assertPortFree(int p) throws IOException {
    try (ServerSocket s = new ServerSocket(p)) {
      assertTrue(s.isBound());
    }
  }

  private static class TextServlet extends HttpServlet {
    private static final long serialVersionUID = 2026111101L;
    private final String text;

    TextServlet(String text) {
      this.text = text;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      resp.setStatus(200);
      resp.setContentType("text/plain");
      resp.getWriter().write(text);
    }
  }

}
