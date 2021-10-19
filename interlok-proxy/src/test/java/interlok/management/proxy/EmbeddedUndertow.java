package interlok.management.proxy;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import com.adaptris.interlok.junit.scaffolding.util.PortManager;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.HttpString;

public class EmbeddedUndertow implements AutoCloseable {

  private Integer port = null;
  private Undertow server = null;
  private ArrayDeque<ResponseMessage> httpResponses;

  public static final String DEFAULT_HTTP_BODY = "Hello from Undertow";
  
  public EmbeddedUndertow() {
    port = PortManager.nextUnusedPort(8080);
    httpResponses = new ArrayDeque<>();
  }

  public EmbeddedUndertow withResponses(ResponseMessage... s) {
    httpResponses = new ArrayDeque<>(Arrays.asList(s));
    return this;
  }

  public EmbeddedUndertow start() {
    if (httpResponses.size() < 1) {
      httpResponses.add(new ResponseMessage().withBody("Hello from Undertow"));
    }
    server = Undertow.builder().addHttpListener(port, "localhost")
        .setHandler(new BlockingHandler(new MyRequestHandler()))
        .build();
    server.start();
    return this;
  }

  @Override
  public void close() throws IOException {
    server.stop();
    server = null;
    PortManager.release(port);
  }

  public Integer getPort() {
    return port;
  }

  private class MyRequestHandler implements HttpHandler {


    MyRequestHandler() {

    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
      ResponseMessage wrapper = nextResponse();
      exchange.setStatusCode(wrapper.getStatus());
      for (Map.Entry<HttpString, String> e : wrapper.getHeaders().entrySet()) {
        exchange.getResponseHeaders().put(e.getKey(), e.getValue());
      }
      exchange.getResponseSender().send(wrapper.getBody());
    }

    private synchronized ResponseMessage nextResponse() {
      // We can dequeue responses from our list of responses.
      // If there's only 1 left, just re-use that.
      if (httpResponses.size() > 1) {
        return httpResponses.removeFirst();
      }
      return httpResponses.getFirst();
    }
  }

  public class ResponseMessage {
    private String body;
    private Map<HttpString, String> headers = new HashMap<>();
    private int status = 200;

    public ResponseMessage() {

    }

    public String getBody() {
      return body;
    }

    public ResponseMessage withBody(String msg) {
      body = msg;
      return this;
    }

    public Map<HttpString, String> getHeaders() {
      return headers;
    }

    public ResponseMessage withHeaders(Map<HttpString, String> hdrs) {
      headers = hdrs;
      return this;
    }

    public int getStatus() {
      return status;
    }

    public ResponseMessage withStatus(int httpStatus) {
      status = httpStatus;
      return this;
    }
  }
}
