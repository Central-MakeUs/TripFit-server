package com.tripfit.reporter;

import com.sun.net.httpserver.HttpServer;
import com.tripfit.reporter.alert.AlertDeduplicator;
import com.tripfit.reporter.collect.ContextCollector;
import com.tripfit.reporter.collect.LokiClient;
import com.tripfit.reporter.collect.PrometheusClient;
import com.tripfit.reporter.collect.SourceCodeLocator;
import com.tripfit.reporter.config.ReporterConfig;
import com.tripfit.reporter.llm.GeminiClient;
import com.tripfit.reporter.notify.DiscordNotifier;
import com.tripfit.reporter.report.SuspectValidator;
import com.tripfit.reporter.server.AlertsHandler;
import com.tripfit.reporter.server.MetricsHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

// AI 원인 리포터 실행 진입점이다. 설정을 환경 변수에서 읽고 /alerts·/metrics를 여는 HTTP 서버를 띄운다.
// 스펙: docs/specs/cross-cutting/incident-ai-report.md
public final class ReporterApplication {

  private static final Logger LOG = Logger.getLogger(ReporterApplication.class.getName());
  // 무료 등급 호출 한도와 서버 E 메모리를 고려해 분석은 동시에 두 건, 대기는 20건까지만 둔다.
  private static final int ANALYSIS_THREADS = 2;
  private static final int ANALYSIS_QUEUE = 20;

  private ReporterApplication() {}

  public static void main(String[] args) throws IOException {
    ReporterConfig config = ReporterConfig.fromEnv(System.getenv());
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    Clock clock = Clock.systemUTC();

    SourceCodeLocator code = new SourceCodeLocator(config.sourceRoot(), config.sourceRepoUrl(), config.gitPullEnabled());
    ContextCollector collector =
        new ContextCollector(
            new PrometheusClient(config.prometheusUrl(), http),
            new LokiClient(config.lokiUrl(), config.lokiQuery(), http),
            code,
            clock);
    ReporterMetrics metrics = new ReporterMetrics();
    ExecutorService analysisPool =
        new ThreadPoolExecutor(ANALYSIS_THREADS, ANALYSIS_THREADS, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(ANALYSIS_QUEUE));
    ExecutorService deliveryExecutor = Executors.newSingleThreadExecutor();
    ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    IncidentProcessor processor =
        new IncidentProcessor(
            config.environmentName(),
            config.grafanaUrl(),
            new AlertDeduplicator(),
            collector,
            new GeminiClient(config.geminiBaseUrl(), config.geminiModel(), config.geminiFallbackModel(), config.geminiApiKey(), http),
            new SuspectValidator(config.sourceRoot()),
            new DiscordNotifier(config.discordWebhookUrl(), http),
            metrics,
            analysisPool,
            deliveryExecutor,
            watchdog,
            clock,
            IncidentProcessor.DEFAULT_TIMEOUT,
            config.debugLogPrompt());

    HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
    server.createContext("/alerts", new AlertsHandler(config.reporterToken(), processor::submit));
    server.createContext("/metrics", new MetricsHandler(metrics));
    server.setExecutor(Executors.newFixedThreadPool(4));
    server.start();

    // docker stop·재배포 때 진행 중인 분석이 메시지 없이 사라지지 않도록, 새 요청을 막고 남은 일을 잠시 기다린다.
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      server.stop(1);
      analysisPool.shutdown();
      awaitQuietly(analysisPool, IncidentProcessor.DEFAULT_TIMEOUT.toSeconds());
      watchdog.shutdown();
      deliveryExecutor.shutdown();
      awaitQuietly(deliveryExecutor, 25);
    }));
    LOG.info("리포터 시작 — 포트 " + config.port() + ", 환경 " + config.environmentName());
  }

  private static void awaitQuietly(ExecutorService executor, long seconds) {
    try {
      executor.awaitTermination(seconds, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
