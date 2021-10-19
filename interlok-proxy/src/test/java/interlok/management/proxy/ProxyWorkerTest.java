package interlok.management.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.CoreException;
import com.adaptris.core.http.jetty.EmbeddedConnection;
import com.adaptris.core.http.jetty.ServletWrapper;
import com.adaptris.core.util.LifecycleHelper;
import com.adaptris.interlok.util.Closer;
import interlok.management.proxy.Helper.HttpResponseInfo;
import lombok.NoArgsConstructor;

public class ProxyWorkerTest {

  private static EmbeddedUndertow undertow;
  private static String proxyUrl;
  
  @BeforeClass
  public static void beforeClass() {
    undertow = new EmbeddedUndertow().start();
    proxyUrl = "http://localhost:" + undertow.getPort();
  }
  
  @AfterClass
  public static void afterClass() {
    Closer.closeQuietly(undertow);
  }
  
  @Test
  public void testLifecycle() throws Exception {
    ProxyWorker worker = new ProxyWorker(new MyEmbeddedConnection(), "/jolokia", proxyUrl);
    try {
      LifecycleHelper.initAndStart(worker);
    } finally {
      LifecycleHelper.stopAndClose(worker);
    }
    
  }
  
  @Test
  public void testOnMessage() throws Exception {
    ProxyWorker worker = new ProxyWorker(new MyEmbeddedConnection(), "/jolokia", proxyUrl);
    AtomicBoolean successFired = new AtomicBoolean(false);
    AtomicBoolean failFired = new AtomicBoolean(false);
    try  {      
      LifecycleHelper.initAndStart(worker);
      AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage();
      msg.addObjectHeader(Config.KEY_REQUEST_HEADERS, HelperTest.createKnownHeaders());
      msg.addMessageHeader(Config.KEY_METHOD,HttpGet.METHOD_NAME);
      msg.addMessageHeader(Config.KEY_URI, "/jolokia");      

      worker.onAdaptrisMessage(msg, (m) -> successFired.set(true), (m) -> failFired.set(true));
      HttpResponseInfo info = (HttpResponseInfo) msg.getObjectHeaders().get(Config.KEY_HTTP_RESPONSE);
      assertEquals(200, info.getStatus());
      assertEquals(Config.CONTENT_TYPE_DEFAULT, info.getContentType());
      assertEquals(EmbeddedUndertow.DEFAULT_HTTP_BODY, msg.getContent());
      assertTrue(successFired.get());
      assertFalse(failFired.get());
    } finally {
      LifecycleHelper.stopAndClose(worker);

    } 
  }
  
  @Test
  public void testOnMessage_WithParams() throws Exception {
    ProxyWorker worker = new ProxyWorker(new MyEmbeddedConnection(), "/jolokia", proxyUrl);
    AtomicBoolean successFired = new AtomicBoolean(false);
    AtomicBoolean failFired = new AtomicBoolean(false);
    try {
      LifecycleHelper.initAndStart(worker);
      AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage();
      msg.addObjectHeader(Config.KEY_REQUEST_HEADERS, HelperTest.createKnownHeaders());
      msg.addMessageHeader(Config.KEY_METHOD,HttpGet.METHOD_NAME);
      msg.addMessageHeader(Config.KEY_URI, "/jolokia");
      msg.addMessageHeader(Config.KEY_QUERY_STRING, "a=b&c=d");

      worker.onAdaptrisMessage(msg, (m) -> successFired.set(true), (m) -> failFired.set(true));
      HttpResponseInfo info = (HttpResponseInfo) msg.getObjectHeaders().get(Config.KEY_HTTP_RESPONSE);
      assertEquals(200, info.getStatus());
      assertEquals(Config.CONTENT_TYPE_DEFAULT, info.getContentType());
      assertEquals(EmbeddedUndertow.DEFAULT_HTTP_BODY, msg.getContent());
      assertTrue(successFired.get());
      assertFalse(failFired.get());
      
    } finally {
      LifecycleHelper.stopAndClose(worker);

    } 
  }
  
  @Test
  public void testOnMessage_WithException() throws Exception {
    ProxyWorker worker = new ProxyWorker(new MyEmbeddedConnection(), "/jolokia", proxyUrl);
    AtomicBoolean successFired = new AtomicBoolean(false);
    AtomicBoolean failFired = new AtomicBoolean(false);
    try {
      LifecycleHelper.initAndStart(worker);
      AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage();
      msg.addObjectHeader(Config.KEY_REQUEST_HEADERS, HelperTest.createKnownHeaders());
      // Miss off the method, which should cause a NPE which should mean we get  
      // a 500 w/o every going to undertow..
      // msg.addMessageHeader(Config.KEY_METHOD,HttpGet.METHOD_NAME);
      msg.addMessageHeader(Config.KEY_URI, "/jolokia");

      worker.onAdaptrisMessage(msg, (m) -> successFired.set(true), (m) -> failFired.set(true));
      HttpResponseInfo info = (HttpResponseInfo) msg.getObjectHeaders().get(Config.KEY_HTTP_RESPONSE);
      assertEquals(500, info.getStatus());

      assertFalse(successFired.get());
      assertTrue(failFired.get());      
    } finally {
      LifecycleHelper.stopAndClose(worker);
    } 
  }
  
  
  // Make JettyServletRegistrar no-op because we don't care about actually starting up 
  // jetty.
  // Override initConnection so we don't wait for the embedded Jetty to startup.
  @NoArgsConstructor
  private class MyEmbeddedConnection extends EmbeddedConnection {
    @Override
    public void addServlet(ServletWrapper wrapper) throws CoreException {
    }
    
    @Override
    public void removeServlet(ServletWrapper wrapper) throws CoreException {

    }
    
    @Override
    protected void initConnection() throws CoreException {
    }
  }
}
