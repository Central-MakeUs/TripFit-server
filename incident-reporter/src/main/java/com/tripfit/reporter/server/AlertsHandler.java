package com.tripfit.reporter.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tripfit.reporter.alert.Alert;
import com.tripfit.reporter.alert.AlertWebhook;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Consumer;
import java.util.logging.Logger;

// POST /alerts — Grafana 감시 규칙의 웹훅을 받는다. 공유 토큰이 맞지 않으면 401로 거절하고,
// 맞으면 알림을 처리기에 넘긴 뒤 바로 200을 돌려준다(분석은 처리기가 따로 한다).
public class AlertsHandler implements HttpHandler {

  static final int MAX_BODY_BYTES = 1024 * 1024;
  private static final Logger LOG = Logger.getLogger(AlertsHandler.class.getName());

  private final byte[] expectedAuthorization;
  private final Consumer<Alert> processor;

  public AlertsHandler(String token, Consumer<Alert> processor) {
    this.expectedAuthorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
    this.processor = processor;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      if (!"POST".equals(exchange.getRequestMethod())) {
        respond(exchange, 405, "method not allowed");
        return;
      }
      String authorization = exchange.getRequestHeaders().getFirst("Authorization");
      // 비교 시간이 토큰 내용에 따라 달라지지 않도록 MessageDigest.isEqual로 비교한다.
      if (authorization == null
          || !MessageDigest.isEqual(expectedAuthorization, authorization.getBytes(StandardCharsets.UTF_8))) {
        respond(exchange, 401, "unauthorized");
        return;
      }
      byte[] body = readLimited(exchange.getRequestBody());
      if (body == null) {
        respond(exchange, 413, "payload too large");
        return;
      }
      AlertWebhook webhook;
      try {
        webhook = AlertWebhook.parse(body);
      } catch (IOException e) {
        respond(exchange, 400, "invalid alertmanager payload");
        return;
      }
      for (Alert alert : webhook.alerts()) {
        try {
          processor.accept(alert);
        } catch (RuntimeException e) {
          LOG.warning("알림 처리 시작 실패: " + alert.ruleName() + " (" + e.getMessage() + ")");
        }
      }
      respond(exchange, 200, "ok");
    }
  }

  private static byte[] readLimited(InputStream in) throws IOException {
    byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
    return body.length > MAX_BODY_BYTES ? null : body;
  }

  static void respond(HttpExchange exchange, int status, String text) throws IOException {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
  }
}
