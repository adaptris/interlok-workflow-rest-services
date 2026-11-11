package com.adaptris.rest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Minimal HTTP GET helper for tests that talk to a real Jetty server.
 */
public final class HttpHelper {

  private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private HttpHelper() {
  }

  public static Response get(int port, String path) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(10)).GET()
        .build();
    HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    return new Response(response.statusCode(), response.body(), response.headers().firstValue("Content-Type").orElse(null),
        response.headers().firstValue("Server").orElse(null));
  }

  /** Returns true if something answers HTTP on the port. */
  public static boolean listening(int port) {
    try {
      get(port, "/");
      return true;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  public static final class Response {
    private final int status;
    private final String body;
    private final String contentType;
    private final String server;

    public Response(int status, String body) {
      this(status, body, null, null);
    }

    Response(int status, String body, String contentType, String server) {
      this.status = status;
      this.body = body;
      this.contentType = contentType;
      this.server = server;
    }

    public int status() {
      return status;
    }

    public String body() {
      return body;
    }

    public String contentType() {
      return contentType;
    }

    public String server() {
      return server;
    }

    // equality is on status + body only, for readable assertions.
    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Response)) {
        return false;
      }
      Response other = (Response) o;
      return status == other.status && java.util.Objects.equals(body, other.body);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(status, body);
    }

    @Override
    public String toString() {
      return status + ":" + body;
    }
  }

}
