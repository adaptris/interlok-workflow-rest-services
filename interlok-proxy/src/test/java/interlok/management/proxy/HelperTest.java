package interlok.management.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpTrace;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Test;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.core.http.jetty.EmbeddedConnection;
import com.adaptris.core.http.jetty.JettyMessageConsumer;
import com.adaptris.core.http.jetty.JettyResponseService;
import com.adaptris.core.util.LifecycleHelper;
import com.adaptris.interlok.util.Closer;
import interlok.management.proxy.Helper.AdaptrisMessageWrapper;
import interlok.management.proxy.Helper.HttpResponseInfo;
import interlok.management.proxy.Helper.RequestHeadersConsumer;
import interlok.management.proxy.Helper.ResponseHeadersSupplier;
import interlok.management.proxy.Helper.ResponseToMessage;

@SuppressWarnings({"rawtypes", "unchecked"})
public class HelperTest {

  @Test
  public void testAdaptrisMessageEntity() throws Exception {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage("hello world");
    try (AdaptrisMessageWrapper wrapper = new AdaptrisMessageWrapper(msg);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      assertNotNull(wrapper.getContent());
      assertTrue(wrapper.isRepeatable());
      assertEquals(11, wrapper.getContentLength());
      assertTrue(wrapper.isStreaming());
      wrapper.writeTo(out);
    }
  }
  
  @Test
  public void testCreateResponseService() throws Exception {
    JettyResponseService service = Helper.createResponseService();
    assertNotNull(service);
    assertEquals(Config.EXPR_CONTENT_TYPE, service.getContentType());
    assertEquals(Config.EXPR_STATUS, service.getHttpStatus());
  }
  
  @Test
  public void testCreateRequest() throws Exception {
    assertThrows(NullPointerException.class, () -> Helper.createRequest("BLAH", "http://localhost:5555"));

    assertEquals(HttpDelete.class, Helper.createRequest(HttpDelete.METHOD_NAME, "http://localhost:5555").getClass());
    assertEquals(HttpGet.class, Helper.createRequest(HttpGet.METHOD_NAME, "http://localhost:5555").getClass());
    assertEquals(HttpHead.class, Helper.createRequest(HttpHead.METHOD_NAME, "http://localhost:5555").getClass());
    assertEquals(HttpOptions.class, Helper.createRequest(HttpOptions.METHOD_NAME, "http://localhost:5555").getClass());
    assertEquals(HttpPatch.class, Helper.createRequest(HttpPatch.METHOD_NAME, "http://localhost:5555").getClass());
    assertEquals(HttpPut.class, Helper.createRequest(HttpPut.METHOD_NAME, "http://localhost:5555").getClass());
    assertEquals(HttpPost.class, Helper.createRequest(HttpPost.METHOD_NAME, "http://localhost:5555").getClass());
    assertEquals(HttpTrace.class, Helper.createRequest(HttpTrace.METHOD_NAME, "http://localhost:5555").getClass());    
  }
  
  @Test
  public void testCreateWorkers() throws Exception {
    Properties cfg = new Properties();
    cfg.setProperty(Config.PROXY_PREFIX + "1", "/p::http://localhost:8080");
    cfg.setProperty(Config.PROXY_PREFIX + "2", "/p1/*::http://localhost:8080");
    cfg.setProperty(Config.PROXY_PREFIX + "3", "/p2:http://localhost:8080");
    
    List<StandaloneConsumer> workers = Helper.build(cfg);
    // proxy-3 won't pass.
    assertEquals(2, workers.size());
    assertEquals(EmbeddedConnection.class, workers.get(0).getConnection().getClass());
    assertEquals(workers.get(0).getConnection(), workers.get(1).getConnection());
  }
  
  @Test
  public void testConfigureRequest() throws Exception {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage("hello world");
    HttpUriRequestBase request = Helper.createRequest(HttpPost.METHOD_NAME, "http://localhost:5555");
    msg.addObjectHeader(Config.KEY_REQUEST_HEADERS, createKnownHeaders());
    HttpUriRequestBase r2 = Helper.configureRequest(msg,  request);
    assertEquals(request, r2);
    assertEquals(HelperTest.class.getSimpleName(), request.getHeader("X-Interlok-Junit").getValue());
  }
  
  @Test
  public void testCreateConsumer() throws Exception {
    JettyMessageConsumer consumer = Helper.createConsumer("/jolokia");
    assertEquals("/jolokia/*", consumer.getPath());
    assertEquals(RequestHeadersConsumer.class, consumer.getHeaderHandler().getClass());
  }
  
  @Test
  public void testJettyConsumer() throws Exception {
    JettyMessageConsumer consumer = Helper.createConsumer("/jolokia/*");
    assertEquals("/jolokia/*", consumer.getPath());
    assertEquals(RequestHeadersConsumer.class, consumer.getHeaderHandler().getClass());
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    when(mockRequest.getInputStream()).thenReturn(new MockInputStream());
    when(mockRequest.getCharacterEncoding()).thenReturn(null);
    when(mockRequest.getContentLength()).thenReturn(-1);
    Headers hdrs = new Headers(Collections.EMPTY_MAP);
    // Since we know we end up using RequestHeadersConsumer we know we'll be acessing the headers.
    when(mockRequest.getHeaderNames()).thenReturn(hdrs.enumeration());
    AdaptrisMessage msg = consumer.createMessage(mockRequest, null);
    assertEquals(0, msg.getSize());
    assertEquals(0, ((Map)msg.getObjectHeaders().get(Config.KEY_REQUEST_HEADERS)).size());
  }
  
  @Test
  public void testDoResponse() throws Exception {
    JettyResponseService service = Helper.createResponseService();
    try {
      LifecycleHelper.initAndStart(service);
      AdaptrisMessage broken = AdaptrisMessageFactory.getDefaultInstance().newMessage();
      // Missing HttpResponseInfo
      Helper.doResponse(service,  broken);
      AdaptrisMessage hasResponse = AdaptrisMessageFactory.getDefaultInstance().newMessage();
      hasResponse.addObjectHeader(Config.KEY_HTTP_RESPONSE, new HttpResponseInfo());
      Helper.doResponse(service,  hasResponse);
    } finally {
      LifecycleHelper.stopAndClose(service);
    }
  }

  @Test
  public void testRequestHeadersConsumer() throws Exception {
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    Map<String, String> hdrs = new HashMap<>();
    hdrs.put("X-Interlok-Junit", HelperTest.class.getSimpleName());
    hdrs.put("Transfer-Encoding", HelperTest.class.getSimpleName());
    Headers headers = new Headers(hdrs);
    // This is a bit of a crappy mock when, it's not precisely easy to "pass through"
    // the param into the getter, but since we only expect to end up with a single value, it
    // doesn't matter.
    when(mockRequest.getHeaderNames()).thenReturn(headers.enumeration());
    when(mockRequest.getHeader(anyString())).thenReturn(HelperTest.class.getSimpleName());
    
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    RequestHeadersConsumer handler = new RequestHeadersConsumer();
    handler.handleHeaders(msg, mockRequest);
    Map<String, String> objectHeaders = (Map<String, String>)msg.getObjectHeaders().get(Config.KEY_REQUEST_HEADERS);
    assertEquals(HelperTest.class.getSimpleName(), objectHeaders.get("X-Interlok-Junit"));
    // Transfer-Encoding should be filtered out
    assertEquals(1, objectHeaders.size());
  }

  @Test
  public void testResponseHeadersSupplier() throws Exception {
    ResponseHeadersSupplier handler = new ResponseHeadersSupplier();
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    HttpResponseInfo info = new HttpResponseInfo(createKnownHeaders());
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    msg.addObjectHeader(Config.KEY_HTTP_RESPONSE, info);
    handler.addHeaders(msg, mockResponse);
  }
  
  @Test
  public void testResponseToMessage_WithHeaders() throws Exception {
    ClassicHttpResponse response = mock(ClassicHttpResponse.class);
    AdaptrisMessage source = AdaptrisMessageFactory.getDefaultInstance().newMessage("hello world");  
    AdaptrisMessageWrapper entity = new AdaptrisMessageWrapper(source); 
    Header[] headers = toHeaderArray(createKnownHeaders());
    when(response.getEntity()).thenReturn(entity);
    when(response.getCode()).thenReturn(200);
    when(response.getHeaders()).thenReturn(headers);
    
    AdaptrisMessage target = AdaptrisMessageFactory.getDefaultInstance().newMessage();
    ResponseToMessage responseHandler = new ResponseToMessage(target);
    responseHandler.handleResponse(response);
    
    HttpResponseInfo info = (HttpResponseInfo) target.getObjectHeaders().get(Config.KEY_HTTP_RESPONSE);
    assertEquals(200, info.getStatus());
    assertEquals(Config.CONTENT_TYPE_DEFAULT, info.getContentType());
    assertEquals(3, info.getResponseHeaders().size());
  }
  
  
  public static Map<String, String> createKnownHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("X-Interlok-Junit", HelperTest.class.getSimpleName());
    headers.put("X-Interlok-Hello", "World");
    headers.put("X-Interlok-Text", "Lorem ipsum dolor sit amet");
    return headers;
  }
  
  private static Header[] toHeaderArray(Map<String, String> map) {
    List<Header> headers = new ArrayList<>();
    map.entrySet().forEach((e) -> headers.add(new BasicHeader(e.getKey(), e.getValue())));
    return headers.toArray(new Header[0]);
  }
  
  private class Headers {
    private transient Map<String, String> headers;
    
    public Headers(Map<String,String> toWrap) {
      headers =  Collections.unmodifiableMap(toWrap);
    }
    
    Enumeration<String> enumeration() {
      Iterator<String> keys = headers.keySet().iterator();
      return new Enumeration<String>() {
        @Override
        public boolean hasMoreElements() {
          return keys.hasNext();
        }

        @Override
        public String nextElement() {
          return keys.next();
        }      
      };
    }
  }
  
  private class MockInputStream extends ServletInputStream {

    private ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
    
    @Override
    public boolean isFinished() {
      return true;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
    }

    @Override
    public int read() throws IOException {
      return input.read();
    }
    
    @Override
    public void close() throws IOException {
      super.close();
      Closer.closeQuietly(input);
    }
  }
}
