package com.tripfit.reporter;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripfit.reporter.alert.Alert;
import com.tripfit.reporter.alert.AlertDeduplicator;
import com.tripfit.reporter.collect.ContextCollector;
import com.tripfit.reporter.collect.IncidentContext;
import com.tripfit.reporter.llm.IncidentReport;
import com.tripfit.reporter.llm.LlmClient;
import com.tripfit.reporter.llm.LlmException;
import com.tripfit.reporter.notify.DiscordNotifier;
import com.tripfit.reporter.report.SuspectValidator;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// 리포터의 약속 "Discord 메시지는 절대 빠뜨리지 않는다"를 실패 경로마다 고정한다.
class IncidentProcessorTest {

  @TempDir Path repo;

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T09:05:00Z"), ZoneOffset.UTC);
  private static final IncidentContext EMPTY = new IncidentContext(List.of(), List.of(), List.of(), List.of(), List.of());

  private final List<String> sent = new CopyOnWriteArrayList<>();
  private final ExecutorService analysisPool = Executors.newFixedThreadPool(2);
  private final ExecutorService delivery = Executors.newSingleThreadExecutor();
  private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();

  @AfterEach
  void shutdown() {
    analysisPool.shutdownNow();
    delivery.shutdownNow();
    watchdog.shutdownNow();
  }

  private static Alert firing(String generatorUrl) {
    return new Alert("firing", Map.of("alertname", "DB 커넥션 풀 대기", "resource", "db_pool", "target", "db"),
        null, "2026-10-19T09:00:00Z", null, "f1", generatorUrl, Map.of("A", 3.0));
  }

  private static Alert resolved() {
    return new Alert("resolved", Map.of("alertname", "DB 커넥션 풀 대기"), null,
        "2026-10-19T09:00:00Z", "2026-10-19T09:04:00Z", "f1", null, null);
  }

  private static final IncidentReport REPORT =
      new IncidentReport("커넥션이 바닥났습니다.", List.of("대기 3"), List.of(), List.of("확인"), IncidentReport.Confidence.MEDIUM);

  // Discord가 처음 한 번은 본문을 거절(400)하도록 만들 때 쓴다.
  private boolean rejectFirst;

  private IncidentProcessor processor(Function<Alert, IncidentContext> collect, LlmClient llm, Duration timeout) {
    return processor(collect, llm, timeout, analysisPool);
  }

  private IncidentProcessor processor(
      Function<Alert, IncidentContext> collect, LlmClient llm, Duration timeout, ExecutorService pool) {
    ContextCollector collector =
        new ContextCollector(null, null, null, CLOCK) {
          @Override
          public IncidentContext collect(Alert alert) {
            return collect.apply(alert);
          }
        };
    DiscordNotifier notifier =
        new DiscordNotifier(URI.create("http://discord.invalid"), null) {
          @Override
          public Result send(String jsonBody) {
            sent.add(jsonBody);
            if (rejectFirst && sent.size() == 1) {
              return Result.REJECTED;
            }
            return Result.SENT;
          }
        };
    return new IncidentProcessor("local", URI.create("http://grafana.local"), new AlertDeduplicator(), collector, llm,
        new SuspectValidator(repo), notifier, new ReporterMetrics(), pool, delivery, watchdog, CLOCK, timeout,
        false);
  }

  private void awaitMessages(int count) throws InterruptedException {
    for (int i = 0; i < 100 && sent.size() < count; i++) {
      Thread.sleep(50);
    }
    assertThat(sent).hasSizeGreaterThanOrEqualTo(count);
  }

  @Test
  void submit_llmFails_sendsFallbackWithReason() throws Exception {
    processor(a -> EMPTY, (s, u) -> { throw new LlmException("Gemini 호출 한도 초과(429)"); }, Duration.ofSeconds(5))
        .submit(firing(null));

    awaitMessages(1);
    assertThat(sent.get(0)).contains("AI 분석 실패", "429");
  }

  @Test
  void submit_collectorThrows_stillSendsFallback() throws Exception {
    processor(a -> { throw new IllegalStateException("boom"); }, (s, u) -> REPORT, Duration.ofSeconds(5))
        .submit(firing(null));

    awaitMessages(1);
    assertThat(sent.get(0)).contains("AI 분석 실패", "리포터 내부 오류");
  }

  @Test
  void submit_analysisTooSlow_sendsTimeoutFallbackAndInterrupts() throws Exception {
    CountDownLatch interrupted = new CountDownLatch(1);
    LlmClient hanging = (s, u) -> {
      try {
        Thread.sleep(10_000);
      } catch (InterruptedException e) {
        interrupted.countDown();
        Thread.currentThread().interrupt();
      }
      throw new LlmException("중단됨");
    };
    processor(a -> EMPTY, hanging, Duration.ofMillis(300)).submit(firing(null));

    awaitMessages(1);
    assertThat(sent.get(0)).contains("분석 시간 초과");
    assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
    Thread.sleep(200);
    assertThat(sent).hasSize(1);
  }

  @Test
  void submit_invalidGeneratorUrl_reportStillSent() throws Exception {
    processor(a -> EMPTY, (s, u) -> REPORT, Duration.ofSeconds(5)).submit(firing("::not a url::"));

    awaitMessages(1);
    assertThat(sent.get(0)).contains("커넥션이 바닥났습니다.", "http://grafana.local");
  }

  @Test
  void submit_resolvedWhileAnalyzing_resolvedSentAfterReport() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    LlmClient slow = (s, u) -> {
      try {
        release.await(2, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return REPORT;
    };
    IncidentProcessor processor = processor(a -> EMPTY, slow, Duration.ofSeconds(5));
    processor.submit(firing(null));
    processor.submit(resolved());
    Thread.sleep(100);
    assertThat(sent).isEmpty();

    release.countDown();
    awaitMessages(2);
    assertThat(sent.get(0)).contains("🚨");
    assertThat(sent.get(1)).contains("✅", "해제");
  }

  @Test
  void submit_duplicateFiring_analyzedOnce() throws Exception {
    IncidentProcessor processor = processor(a -> EMPTY, (s, u) -> REPORT, Duration.ofSeconds(5));
    processor.submit(firing(null));
    processor.submit(firing(null));

    awaitMessages(1);
    Thread.sleep(200);
    assertThat(sent).hasSize(1);
  }
  @Test
  void submit_analysisQueueFull_sendsFallbackInsteadOfDropping() throws Exception {
    // 분석 자리 1개·대기열 0개로, 첫 알림이 분석 중일 때 둘째 알림은 거절된다.
    ThreadPoolExecutor tiny = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new SynchronousQueue<>());
    CountDownLatch release = new CountDownLatch(1);
    LlmClient slow = (s, u) -> {
      try {
        release.await(2, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return REPORT;
    };
    IncidentProcessor processor = processor(a -> EMPTY, slow, Duration.ofSeconds(5), tiny);
    processor.submit(firing(null));
    processor.submit(new Alert("firing", Map.of("alertname", "앱 CPU"), null, "2026-10-19T09:01:00Z", null, "f2", null, null));

    awaitMessages(1);
    assertThat(sent.get(0)).contains("분석 대기열이 가득 찼습니다");
    release.countDown();
    awaitMessages(2);
    tiny.shutdownNow();
  }

  @Test
  void deliver_bodyRejected_resendsWithoutLink() throws Exception {
    rejectFirst = true;
    processor(a -> EMPTY, (s, u) -> REPORT, Duration.ofSeconds(5)).submit(firing("https://grafana.example/alert"));

    awaitMessages(2);
    assertThat(sent.get(0)).contains("https://grafana.example/alert");
    assertThat(sent.get(1)).doesNotContain("grafana.example");
  }
}
