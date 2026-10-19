package com.tripfit.reporter.notify;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

// 만든 메시지를 Discord 웹훅으로 보낸다. 웹훅이 일시적으로 거절(429·5xx)하면 한 번만 다시 보낸다.
// 그래도 실패하면 로그만 남긴다 — 리포터가 여기서 멈추면 다음 알림까지 막히기 때문이다.
public class DiscordNotifier {

  private static final Logger LOG = Logger.getLogger(DiscordNotifier.class.getName());
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(10);

  private final URI webhookUrl;
  private final HttpClient client;

  public DiscordNotifier(URI webhookUrl, HttpClient client) {
    this.webhookUrl = webhookUrl;
    this.client = client;
  }

  // Discord가 알려준 대기 시간(Retry-After 헤더, 초)만큼 기다린다. 없거나 읽을 수 없으면 2초, 길어도 10초까지만
  // 기다린다(2026-10-19 사용자 결정 — 전송 스레드가 오래 막히면 다음 알림이 밀린다).
  static Duration retryDelay(HttpResponse<?> response) {
    Duration fallback = Duration.ofSeconds(2);
    return response.headers().firstValue("Retry-After").map(value -> {
      try {
        long millis = (long) (Double.parseDouble(value.trim()) * 1000);
        return Duration.ofMillis(Math.max(0, Math.min(millis, MAX_RETRY_DELAY.toMillis())));
      } catch (NumberFormatException e) {
        return fallback;
      }
    }).orElse(fallback);
  }

  // 전송 결과다. REJECTED는 Discord가 본문을 받아들이지 않은 경우(429가 아닌 4xx)라 내용을 바꿔 보내면 될 수 있다.
  public enum Result {
    SENT,
    REJECTED,
    FAILED;

    public boolean sent() {
      return this == SENT;
    }
  }

  public Result send(String jsonBody) {
    for (int attempt = 1; attempt <= 2; attempt++) {
      try {
        HttpResponse<String> response =
            client.send(
                HttpRequest.newBuilder(webhookUrl)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status / 100 == 2) {
          return Result.SENT;
        }
        boolean retryable = status == 429 || status / 100 == 5;
        LOG.warning("Discord 전송 실패 " + status + (retryable && attempt == 1 ? " — 다시 보냅니다" : ""));
        if (!retryable) {
          return Result.REJECTED;
        }
        if (attempt == 1) {
          Thread.sleep(retryDelay(response).toMillis());
        }
      } catch (IOException e) {
        LOG.warning("Discord 전송 실패: " + e.getMessage());
      } catch (RuntimeException e) {
        // 웹훅 주소가 잘못된 경우 등 — 다시 보내도 같으므로 바로 포기한다.
        LOG.warning("Discord 전송 실패: " + e.getClass().getSimpleName());
        return Result.FAILED;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return Result.FAILED;
      }
    }
    return Result.FAILED;
  }
}
