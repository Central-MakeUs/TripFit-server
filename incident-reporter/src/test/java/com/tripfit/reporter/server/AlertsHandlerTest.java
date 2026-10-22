package com.tripfit.reporter.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.tripfit.reporter.alert.Alert;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlertsHandlerTest {

  private static final String BODY =
      """
      {"status": "firing", "alerts": [
        {"status": "firing", "labels": {"alertname": "앱 CPU"}, "fingerprint": "f1"},
        {"status": "resolved", "labels": {"alertname": "DB 메모리"}, "fingerprint": "f2"}]}
      """;

  private final List<Alert> received = new CopyOnWriteArrayList<>();
  private final HttpClient client = HttpClient.newHttpClient();
  private HttpServer server;
  private URI alertsUri;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/alerts", new AlertsHandler("secret-token", received::add));
    server.start();
    alertsUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/alerts");
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private HttpResponse<String> post(String authorization, String body) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(alertsUri).POST(HttpRequest.BodyPublishers.ofString(body));
    if (authorization != null) {
      request.header("Authorization", authorization);
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void post_withoutToken_returns401AndIgnoresAlerts() throws Exception {
    assertThat(post(null, BODY).statusCode()).isEqualTo(401);
    assertThat(post("Bearer wrong-token", BODY).statusCode()).isEqualTo(401);
    assertThat(received).isEmpty();
  }

  @Test
  void post_withToken_passesEveryAlertToProcessor() throws Exception {
    HttpResponse<String> response = post("Bearer secret-token", BODY);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(received).extracting(Alert::ruleName).containsExactly("앱 CPU", "DB 메모리");
  }

  @Test
  void post_invalidJson_returns400() throws Exception {
    assertThat(post("Bearer secret-token", "not json").statusCode()).isEqualTo(400);
  }

  @Test
  void get_returns405() throws Exception {
    HttpResponse<String> response =
        client.send(HttpRequest.newBuilder(alertsUri).GET().build(), HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(405);
  }
}
