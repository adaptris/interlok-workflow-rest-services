package interlok.management.proxy;

import static com.adaptris.core.util.PropertyHelper.asMap;
import static com.adaptris.core.util.PropertyHelper.getPropertySubset;
import static interlok.management.proxy.Config.CONTENT_TYPE_DEFAULT;
import static interlok.management.proxy.Config.EXPR_CONTENT_TYPE;
import static interlok.management.proxy.Config.EXPR_STATUS;
import static interlok.management.proxy.Config.IGNORED_RESPONSE_HEADERS;
import static interlok.management.proxy.Config.KEY_HTTP_RESPONSE;
import static interlok.management.proxy.Config.KEY_REQUEST_HEADERS;
import static interlok.management.proxy.Config.LOGGING_CONTEXT;
import static interlok.management.proxy.Config.LOGGING_CONTEXT_REF;
import static interlok.management.proxy.Config.METADATA_CONTENT_TYPE;
import static interlok.management.proxy.Config.METADATA_STATUS;
import static interlok.management.proxy.Config.PROXY_PREFIX;
import static interlok.management.proxy.Config.SEPARATOR;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.util.Args;
import org.slf4j.MDC;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.core.http.jetty.EmbeddedConnection;
import com.adaptris.core.http.jetty.HeaderHandlerImpl;
import com.adaptris.core.http.jetty.JettyMessageConsumer;
import com.adaptris.core.http.jetty.JettyResponseService;
import com.adaptris.core.http.server.ResponseHeaderProvider;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
// Everything really is static, since there's no state that can't be passed in as params
// This doesn't feel necessarily clean, but does allow us to mock and unit test to our hearts
// content.
public class Helper {

  private static final Map<String, RequestBuilder> REQUEST_BUILDERS;

  static {
    Map<String, RequestBuilder> map = new HashMap<>();
    map.put(HttpDelete.METHOD_NAME.toUpperCase(), (url) -> new HttpDelete(url));
    map.put(HttpGet.METHOD_NAME.toUpperCase(), (url) -> new HttpGet(url));
    map.put(HttpHead.METHOD_NAME.toUpperCase(), (url) -> new HttpHead(url));
    map.put(HttpOptions.METHOD_NAME.toUpperCase(), (url) -> new HttpOptions(url));
    map.put(HttpPatch.METHOD_NAME.toUpperCase(), (url) -> new HttpPatch(url));
    map.put(HttpPut.METHOD_NAME.toUpperCase(), (url) -> new HttpPut(url));
    map.put(HttpPost.METHOD_NAME.toUpperCase(), (url) -> new HttpPost(url));
    map.put(HttpTrace.METHOD_NAME.toUpperCase(), (url) -> new HttpTrace(url));
    REQUEST_BUILDERS = Collections.unmodifiableMap(map);
  }

  /** Create a jetty response service.
   * 
   * @return a jetty response service
   */
  static JettyResponseService createResponseService() {
    return new JettyResponseService().withResponseHeaderProvider(new ResponseHeadersSupplier())
        .withHttpStatus(EXPR_STATUS).withContentType(EXPR_CONTENT_TYPE);
  }

  /** Create a HTTP request.
   * 
   */
  static HttpUriRequestBase createRequest(String method, String url) {
    return REQUEST_BUILDERS.get(method.toUpperCase()).create(url);
  }

  /** Create a list of standalone consumers from config.
   * 
   */
  static List<StandaloneConsumer> build(Properties config) {
    Map<String, String> urlsToProxy = asMap(getPropertySubset(config, PROXY_PREFIX, true));
    List<StandaloneConsumer> consumers = new ArrayList<>();
    EmbeddedConnection conn = new EmbeddedConnection();
    for (Map.Entry<String, String> entry : urlsToProxy.entrySet()) {
      String[] url = entry.getValue().split(SEPARATOR);
      if (url.length != 2) {
        log.trace("Ignoring [{}]", entry.getValue());
        continue;
      }
      log.trace("Creating consumer on [{}] mapped to [{}]", url[0], url[1]);
      consumers.add(new ProxyWorker(conn, url[0], url[1]));
    }
    return consumers;
  }


  /** Configure the apache http request with information freom the adaptris message.
   * 
   */
  @SuppressWarnings("unchecked")
  static HttpUriRequestBase configureRequest(AdaptrisMessage msg, HttpUriRequestBase request) {
    // Set the payload
    request.setEntity(new AdaptrisMessageWrapper(msg));
    // Copy inbound request headers.
    Map<String, String> headers =
        (Map<String, String>) msg.getObjectHeaders().get(KEY_REQUEST_HEADERS);
    headers.entrySet().stream().forEach((h) -> request.addHeader(h.getKey(), h.getValue()));
    return request;
  }

  // Send a response via the passed in service.
  static void doResponse(JettyResponseService service, AdaptrisMessage msg) {
    try {
      HttpResponseInfo info = (HttpResponseInfo) msg.getObjectHeaders().get(KEY_HTTP_RESPONSE);
      msg.addMetadata(METADATA_STATUS, String.valueOf(info.getStatus()));
      msg.addMetadata(METADATA_CONTENT_TYPE, info.getContentType());
      service.doService(msg);
    } catch (Exception exc) {
      log.trace("Ignored exception sending HTTP response {}", exc.getMessage());
    }
  }
  

  // Create a jetty message consumer for the path specified.
  static JettyMessageConsumer createConsumer(String path) {
    JettyMessageConsumer consumer = new JettyMessageConsumer() {
      @Override
      public AdaptrisMessage createMessage(HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException {
        MDC.put(LOGGING_CONTEXT, LOGGING_CONTEXT_REF);
        return super.createMessage(request, response);
      }
    };
    consumer.setPath(rewild(path));
    consumer.setHeaderHandler(new RequestHeadersConsumer());
    // jettyQueryString is auto-populated so no specific requirement here.
    // consumer.setParameterHandler(null);
    return consumer;
  }


  private static String rewild(String s) {
    if (s.endsWith("/*"))
      return s;
    return s + "/*";
  }

  // Could be a Function, but Map<String, Function<String, HttpUriRequestBase>> as a declaration is
  // awesome but also useless in conveying intent.
  @FunctionalInterface
  private interface RequestBuilder {
    HttpUriRequestBase create(String url);
  }

  // Write servlet request headers as object metadata in the adaptris message.
  @NoArgsConstructor
  static class RequestHeadersConsumer extends HeaderHandlerImpl {

    @Override
    public void handleHeaders(AdaptrisMessage message, HttpServletRequest request) {
      Map<String, String> objectHeaders = new HashMap<>();
      for (Enumeration<String> e = request.getHeaderNames(); e.hasMoreElements();) {
        String key = e.nextElement();
        if (!Config.IGNORED_REQUEST_HEADERS.contains(key)) {
          String value = request.getHeader(key);
          objectHeaders.put(key, value);
        }
      }
      message.addObjectHeader(KEY_REQUEST_HEADERS, objectHeaders);
    }

  }

  // Write headers onto the http servlet response when responding to the caller.
  @NoArgsConstructor
  static class ResponseHeadersSupplier implements ResponseHeaderProvider<HttpServletResponse> {

    @Override
    public HttpServletResponse addHeaders(AdaptrisMessage msg, HttpServletResponse target) {
      HttpResponseInfo info = (HttpResponseInfo) msg.getObjectHeaders().get(KEY_HTTP_RESPONSE);
      Map<String, String> headers = info.getResponseHeaders();
      headers.entrySet().stream().forEach((h) -> target.addHeader(h.getKey(), h.getValue()));
      return target;
    }

  }

  // Handle the output from making the request to the targeted service.
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  static class ResponseToMessage implements HttpClientResponseHandler<AdaptrisMessage> {

    private transient AdaptrisMessage msg;
    
    public ResponseToMessage(AdaptrisMessage target) {
      msg = target;
    }
    
    @Override
    public AdaptrisMessage handleResponse(ClassicHttpResponse response)
        throws HttpException, IOException {
      HttpEntity entity = response.getEntity();
      try (OutputStream out = msg.getOutputStream(); InputStream in = entity.getContent()) {
        IOUtils.copy(in, out);
      }
      msg.setContentEncoding(entity.getContentEncoding());
      // Do everything via object metadata.
      msg.addObjectHeader(KEY_HTTP_RESPONSE, new HttpResponseInfo(response));
      return msg;
    }

  }

  // Wraps headers/status/content-type for insertion as object metadata.
  @SuppressWarnings("unchecked")
  @AllArgsConstructor
  static class HttpResponseInfo {    

    @Getter
    @Setter(AccessLevel.PRIVATE)
    private transient int status;
    @Getter
    @Setter(AccessLevel.PRIVATE)
    private transient String contentType;
    @Getter
    @Setter(AccessLevel.PRIVATE)
    private transient Map<String, String> responseHeaders;

    public HttpResponseInfo() {
      this(Collections.EMPTY_MAP);
    }
    
    public HttpResponseInfo(Map<String, String> headers) {
      this(HttpURLConnection.HTTP_INTERNAL_ERROR, CONTENT_TYPE_DEFAULT, headers);
    }
   
    public HttpResponseInfo(ClassicHttpResponse response) {
      this(response.getCode(),
          StringUtils.defaultIfBlank(response.getEntity().getContentType(), CONTENT_TYPE_DEFAULT),
          grabHeaders(response));
    }

    private static Map<String, String> grabHeaders(HttpResponse response) {
      Header[] headers = response.getHeaders();
      Map<String, String> responseHdrs = new HashMap<>();
      Optional.ofNullable(headers).ifPresent((hdrs) -> {
        for (Header h : hdrs) {
          if (!IGNORED_RESPONSE_HEADERS.contains(h.getName())) {
            responseHdrs.put(h.getName(), h.getValue());
          }
        }
      });
      return responseHdrs;
    }

  }


  // Wraps an adaptris message for sending via apache http.
  static class AdaptrisMessageWrapper extends AbstractHttpEntity {

    private transient final AdaptrisMessage msg;

    public AdaptrisMessageWrapper(AdaptrisMessage msg) {
      // Only if Content-Type does not exist already in headers, is Content-Type queried
      // from the Entity, which means if it exists in metadata, we're good.
      // otherwise it doesn't matter
      super((String) null, null, false);
      this.msg = msg;
    }


    @Override
    public boolean isRepeatable() {
      return true;
    }

    @Override
    public long getContentLength() {
      return msg.getSize();
    }

    @Override
    public InputStream getContent() throws IOException {
      return msg.getInputStream();
    }

    @Override
    public void writeTo(final OutputStream output) throws IOException {
      Args.notNull(output, "OutputStream");
      try (InputStream in = msg.getInputStream()) {
        IOUtils.copy(in, output);
      }
    }

    @Override
    public boolean isStreaming() {
      return true;
    }

    @Override
    public void close() throws IOException {

    }
  }
}
