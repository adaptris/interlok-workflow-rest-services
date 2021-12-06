package interlok.management.proxy;

import java.util.Arrays;
import java.util.List;
import com.adaptris.core.CoreConstants;
import com.adaptris.core.http.jetty.JettyConstants;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Config {
  public static final String PROXY_PREFIX="interlok.proxy.";
  
  public static final String SEPARATOR = "::";
  
  static final String KEY_QUERY_STRING = JettyConstants.JETTY_QUERY_STRING;
  static final String KEY_URI = JettyConstants.JETTY_URI;
  static final String KEY_METHOD = CoreConstants.HTTP_METHOD;
  
  // Declaring it this way removes the only dependency on interlok-rest-base...
  // static final String LOGGING_CONTEXT =  AbstractRestfulEndpoint.MDC_KEY;
  static final String LOGGING_CONTEXT = "ManagementComponent";

  // Configuration for JettyResponseService
  static final String METADATA_STATUS = "httpReplyStatus";
  static final String METADATA_CONTENT_TYPE = "httpReplyContentType";  
  static final String EXPR_STATUS = "%message{" + METADATA_STATUS + "}";
  static final String EXPR_CONTENT_TYPE = "%message{" + METADATA_CONTENT_TYPE + "}";
  
  // Object metadata keys
  static final String KEY_REQUEST_HEADERS = "__httpRequestHeaders";
  static final String KEY_HTTP_RESPONSE = "__httpResponse";
  
  // Headers we want to ignore when interacting with the target system.
  // We don't want to "pass through" things like Accept-Encoding because we want to use what
  // http5 can handle.
  // Similarly we don't want to send back Transfer-Encoding because we need to let jetty decide.
  static final List<String> IGNORED_REQUEST_HEADERS = Arrays.asList("Host", "Accept-Encoding", "Content-Length", "Transfer-Encoding");
  static final List<String> IGNORED_RESPONSE_HEADERS = Arrays.asList("Content-Length", "Transfer-Encoding");

  static final String LOGGING_CONTEXT_REF = ProxyMgmtComponent.class.getSimpleName();

  static final String CONTENT_TYPE_DEFAULT = "text/plain";

}
