package com.tripfit.reporter.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Discord 429의 Retry-After를 따르되 10초를 넘기지 않는지 확인한다.
class DiscordNotifierTest {

  private HttpServer server;
  private URI base;
  private String retryAfter;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      if (retryAfter != null) {
        exchange.getResponseHeaders().set("Retry-After", retryAfter);
      }
      exchange.sendResponseHeaders(429, -1);
      exchange.close();
    });
    server.start();
    base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private Duration delayFor(String header) throws Exception {
    retryAfter = header;
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(base).GET().build(), HttpResponse.BodyHandlers.ofString());
    return DiscordNotifier.retryDelay(response);
  }

  @Test
  void retryDelay_followsHeaderInSeconds() throws Exception {
    assertThat(delayFor("1.5")).isEqualTo(Duration.ofMillis(1500));
  }

  @Test
  void retryDelay_cappedAtTenSeconds() throws Exception {
    assertThat(delayFor("60")).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  void retryDelay_missingOrInvalidHeader_usesTwoSeconds() throws Exception {
    assertThat(delayFor(null)).isEqualTo(Duration.ofSeconds(2));
    assertThat(delayFor("soon")).isEqualTo(Duration.ofSeconds(2));
  }
}
