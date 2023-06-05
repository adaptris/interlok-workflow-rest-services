package interlok.management.proxy;

import static interlok.management.proxy.Config.KEY_HTTP_RESPONSE;
import static interlok.management.proxy.Config.KEY_METHOD;
import static interlok.management.proxy.Config.KEY_QUERY_STRING;
import static interlok.management.proxy.Config.KEY_URI;
import static interlok.management.proxy.Config.LOGGING_CONTEXT;
import static interlok.management.proxy.Config.LOGGING_CONTEXT_REF;
import static interlok.management.proxy.Helper.configureRequest;
import static interlok.management.proxy.Helper.doResponse;

import java.util.Optional;
import java.util.function.Consumer;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.MDC;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.CoreException;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.core.http.jetty.EmbeddedConnection;
import com.adaptris.core.http.jetty.JettyResponseService;
import com.adaptris.core.util.LifecycleHelper;

import interlok.management.proxy.Helper.HttpResponseInfo;
import interlok.management.proxy.Helper.ResponseToMessage;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ProxyWorker extends StandaloneConsumer implements AdaptrisMessageListener {

  // HttpClient is thread-safe and re-usable.
  private static CloseableHttpClient httpClient = HttpClients.createDefault();

  @Getter(AccessLevel.PRIVATE)
  @Setter(AccessLevel.PRIVATE)
  private transient JettyResponseService responseService;

  @Getter(AccessLevel.PRIVATE)
  @Setter(AccessLevel.PRIVATE)
  private transient String proxyUrl;

  public ProxyWorker(EmbeddedConnection conn, String uri, String proxyUrlBase) {
    super(conn, Helper.createConsumer(uri));
    setProxyUrl(proxyUrlBase);
    setResponseService(Helper.createResponseService());
    registerAdaptrisMessageListener(this);
  }

  @Override
  public void init() throws CoreException {
    LifecycleHelper.initAndStart(getResponseService());
    super.init();
  }

  @Override
  public void close() {
    LifecycleHelper.stopAndClose(getResponseService());
    super.close();
  }

  @Override
  public void onAdaptrisMessage(AdaptrisMessage msg, Consumer<AdaptrisMessage> onSuccess, Consumer<AdaptrisMessage> onFailure) {
    MDC.put(LOGGING_CONTEXT, friendlyName());
    try {
      // Figure out if there's ?param=value
      String url = url(msg.getMetadataValue(KEY_URI), msg.getMetadataValue(KEY_QUERY_STRING));
      // Get the method
      String method = msg.getMetadataValue(KEY_METHOD);
      HttpUriRequestBase operation = configureRequest(msg, Helper.createRequest(method, url));
      log.trace("Forwarding request : [{}][{}]", method, url);
      // Write the response into the original message
      httpClient.execute(operation, new ResponseToMessage(msg));
      // now write the reply back to the caller.
      doResponse(getResponseService(), msg);
      onSuccess.accept(msg);
    } catch (Exception e) {
      // respond with 500
      log.warn("Encountered exception attempting to proxy request, sending 500");
      msg.addObjectHeader(KEY_HTTP_RESPONSE, new HttpResponseInfo());
      doResponse(getResponseService(), msg);
      onFailure.accept(msg);
    } finally {
      MDC.remove(LOGGING_CONTEXT);
    }
  }

  @Override
  public String friendlyName() {
    return LOGGING_CONTEXT_REF;
  }

  private String url(String uri, String queryString) {
    final String url = getProxyUrl() + uri;
    return Optional.ofNullable(queryString).map((q) -> url + "?" + queryString).orElse(url);
  }

}
