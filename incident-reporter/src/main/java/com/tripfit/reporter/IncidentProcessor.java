package com.tripfit.reporter;

import com.tripfit.reporter.alert.Alert;
import com.tripfit.reporter.alert.AlertDeduplicator;
import com.tripfit.reporter.collect.ContextCollector;
import com.tripfit.reporter.collect.IncidentContext;
import com.tripfit.reporter.llm.IncidentReport;
import com.tripfit.reporter.llm.LlmClient;
import com.tripfit.reporter.llm.LlmException;
import com.tripfit.reporter.llm.PromptBuilder;
import com.tripfit.reporter.notify.DiscordMessages;
import com.tripfit.reporter.notify.DiscordNotifier;
import com.tripfit.reporter.report.SuspectValidator;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

// 알림 한 건을 받아 Discord 메시지 하나로 끝낸다. 발생이면 자료를 모아 AI 리포트를 보내고, 해제면 한 줄만 보낸다.
// 스펙의 약속은 "Discord 메시지는 절대 빠뜨리지 않는다"이다. 그래서
// - 분석이 시간 상한을 넘기면 분석 스레드를 중단시키고, 그때까지 모은 수치로 대체 메시지를 보낸다.
// - 분석 대기열이 가득 차도 대체 메시지를 보낸다.
// - 메시지를 만들거나 보내다 예외가 나도 최소 문구라도 보낸다.
// 전송은 전송 전용 스레드 하나에서 차례로 하고, 같은 알림의 해제 한 줄은 발생 리포트가 나간 뒤에 보낸다.
public class IncidentProcessor {

  static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
  private static final Logger LOG = Logger.getLogger(IncidentProcessor.class.getName());

  private final String environmentName;
  private final URI grafanaUrl;
  private final AlertDeduplicator deduplicator;
  private final ContextCollector collector;
  private final LlmClient llm;
  private final SuspectValidator validator;
  private final DiscordNotifier notifier;
  private final ReporterMetrics metrics;
  private final ExecutorService analysisPool;
  private final ExecutorService deliveryExecutor;
  private final ScheduledExecutorService watchdog;
  private final Clock clock;
  private final Duration timeout;
  private final boolean debugLogPrompt;
  // fingerprint별로 아직 끝나지 않은 발생 리포트 전송이다. 해제 한 줄은 이것이 끝난 뒤에 보낸다.
  private final Map<String, CompletableFuture<Void>> pendingReports = new ConcurrentHashMap<>();

  public IncidentProcessor(
      String environmentName,
      URI grafanaUrl,
      AlertDeduplicator deduplicator,
      ContextCollector collector,
      LlmClient llm,
      SuspectValidator validator,
      DiscordNotifier notifier,
      ReporterMetrics metrics,
      ExecutorService analysisPool,
      ExecutorService deliveryExecutor,
      ScheduledExecutorService watchdog,
      Clock clock,
      Duration timeout,
      boolean debugLogPrompt) {
    this.environmentName = environmentName;
    this.grafanaUrl = grafanaUrl;
    this.deduplicator = deduplicator;
    this.collector = collector;
    this.llm = llm;
    this.validator = validator;
    this.notifier = notifier;
    this.metrics = metrics;
    this.analysisPool = analysisPool;
    this.deliveryExecutor = deliveryExecutor;
    this.watchdog = watchdog;
    this.clock = clock;
    this.timeout = timeout;
    this.debugLogPrompt = debugLogPrompt;
  }

  // 웹훅 요청 스레드를 붙잡지 않도록 바로 돌아온다. 분석은 분석 스레드에서, 전송은 전송 스레드에서 한다.
  public void submit(Alert alert) {
    String key = keyOf(alert);
    if (!alert.isFiring()) {
      deduplicator.forget(alert);
      Duration duration = alert.duration(clock.instant());
      CompletableFuture<Void> previous = pendingReports.getOrDefault(key, CompletableFuture.completedFuture(null));
      try {
        previous.handleAsync(
            (ignored, error) -> {
              try {
                if (send(DiscordMessages.resolved(environmentName, alert, duration)).sent()) {
                  metrics.resolvedSent();
                }
              } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "해제 메시지 전송 실패: " + alert.ruleName(), e);
              }
              return null;
            },
            deliveryExecutor);
      } catch (RejectedExecutionException e) {
        LOG.warning("리포터가 종료 중이라 해제 메시지를 보내지 못했습니다: " + alert.ruleName());
      }
      return;
    }
    if (!deduplicator.shouldAnalyze(alert)) {
      LOG.info("이미 분석한 알림이라 건너뜁니다: " + alert.ruleName());
      return;
    }

    CompletableFuture<Void> delivered = new CompletableFuture<>();
    pendingReports.put(key, delivered);
    delivered.whenComplete((ignored, error) -> pendingReports.remove(key, delivered));

    AtomicBoolean finished = new AtomicBoolean();
    AtomicReference<IncidentContext> collected = new AtomicReference<>();
    Future<?> analysis;
    try {
      analysis =
          analysisPool.submit(
              () -> {
                Outcome outcome = analyze(alert, collected);
                if (finished.compareAndSet(false, true)) {
                  enqueueDelivery(alert, outcome, delivered);
                }
              });
    } catch (RejectedExecutionException e) {
      finished.set(true);
      enqueueDelivery(alert, Outcome.failed("분석 대기열이 가득 찼습니다", null), delivered);
      return;
    }
    watchdog.schedule(
        () -> {
          if (finished.compareAndSet(false, true)) {
            analysis.cancel(true);
            enqueueDelivery(alert, Outcome.failed("분석 시간 초과(" + timeout.toSeconds() + "초)", collected.get()), delivered);
          }
        },
        timeout.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  // 분석 결과다. report가 null이면 failureReason으로 대체 메시지를 만든다.
  record Outcome(IncidentReport report, int droppedSuspects, IncidentContext context, String failureReason) {

    static Outcome failed(String reason, IncidentContext context) {
      return new Outcome(null, 0, context, reason);
    }
  }

  Outcome analyze(Alert alert, AtomicReference<IncidentContext> collected) {
    try {
      IncidentContext context = collector.collect(alert);
      collected.set(context);
      String prompt = PromptBuilder.userPrompt(alert, context, clock.instant());
      if (debugLogPrompt) {
        LOG.info("Gemini로 보내는 프롬프트:\n" + prompt);
      }
      try {
        IncidentReport report = llm.analyze(PromptBuilder.SYSTEM_PROMPT, prompt);
        SuspectValidator.Result validated = validator.validate(report);
        return new Outcome(validated.report(), validated.dropped(), context, null);
      } catch (LlmException e) {
        return Outcome.failed(e.getMessage(), context);
      }
    } catch (RuntimeException e) {
      LOG.log(Level.WARNING, "분석 중 오류: " + alert.ruleName(), e);
      return Outcome.failed("리포터 내부 오류(" + e.getClass().getSimpleName() + ")", collected.get());
    }
  }

  private void enqueueDelivery(Alert alert, Outcome outcome, CompletableFuture<Void> delivered) {
    try {
      deliveryExecutor.execute(
          () -> {
            try {
              deliver(alert, outcome);
            } finally {
              delivered.complete(null);
            }
          });
    } catch (RejectedExecutionException e) {
      LOG.warning("리포터가 종료 중이라 메시지를 보내지 못했습니다: " + alert.ruleName());
      delivered.complete(null);
    }
  }

  // 메시지를 만들다 예외가 나면 최소 문구라도 보낸다. Grafana 링크 때문에 Discord가 거절하면 링크를 빼고 다시 보낸다.
  void deliver(Alert alert, Outcome outcome) {
    if (outcome.report() == null) {
      LOG.warning("AI 분석 실패로 대체 메시지를 보냅니다: " + alert.ruleName() + " — " + outcome.failureReason());
    }
    URI link = safeLink(alert.generatorURL());
    try {
      DiscordNotifier.Result result = send(message(alert, outcome, link));
      // 본문이 거절된 경우(링크 등)에만 링크를 빼고 다시 보낸다. 429·5xx는 DiscordNotifier가 이미 다시 보냈다.
      if (result == DiscordNotifier.Result.REJECTED && link != null) {
        result = send(message(alert, outcome, null));
      }
      if (result.sent()) {
        if (outcome.report() != null) {
          metrics.reportSucceeded();
        } else {
          metrics.reportFallback();
        }
      }
    } catch (RuntimeException e) {
      LOG.log(Level.WARNING, "메시지 작성 실패: " + alert.ruleName(), e);
      if (send(DiscordMessages.minimal(environmentName, alert, "리포트 작성 중 오류(" + e.getClass().getSimpleName() + ")")).sent()) {
        metrics.reportFallback();
      }
    }
  }

  private String message(Alert alert, Outcome outcome, URI link) {
    if (outcome.report() != null) {
      return DiscordMessages.firing(environmentName, alert, outcome.report(), outcome.droppedSuspects(),
          outcome.context().missing(), link);
    }
    return DiscordMessages.fallback(environmentName, alert, outcome.failureReason(), outcome.context(), link);
  }

  // Grafana가 준 링크가 http(s) 주소가 아니면 설정의 Grafana 주소를 쓴다.
  private URI safeLink(String generatorUrl) {
    if (generatorUrl != null && !generatorUrl.isBlank()) {
      try {
        URI uri = URI.create(generatorUrl.trim());
        if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
          return uri;
        }
      } catch (IllegalArgumentException e) {
        LOG.fine("잘못된 generatorURL: " + generatorUrl);
      }
    }
    return grafanaUrl;
  }

  private DiscordNotifier.Result send(String body) {
    DiscordNotifier.Result result = notifier.send(body);
    if (!result.sent()) {
      metrics.discordFailed();
    }
    return result;
  }

  private static String keyOf(Alert alert) {
    String fingerprint = alert.fingerprint();
    return fingerprint == null || fingerprint.isBlank() ? "rule:" + alert.ruleName() : fingerprint;
  }
}
