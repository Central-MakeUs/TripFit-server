package com.tripfit.reporter.config;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 리포터의 모든 설정은 환경 변수로만 받는다. 로컬과 운영의 차이가 env 값뿐이도록 하기 위해서다.
// 필수 값이 하나라도 비어 있으면 기동 시점에 한꺼번에 알려 주고 멈춘다.
public record ReporterConfig(
    String environmentName,
    String reporterToken,
    int port,
    URI prometheusUrl,
    URI lokiUrl,
    String lokiQuery,
    URI grafanaUrl,
    Path sourceRoot,
    String sourceRepoUrl,
    boolean gitPullEnabled,
    String llmProvider,
    String geminiApiKey,
    String geminiModel,
    String geminiFallbackModel,
    URI geminiBaseUrl,
    URI discordWebhookUrl,
    boolean debugLogPrompt) {

  // 키·토큰·웹훅 주소가 로그에 실리지 않도록 자동 toString을 덮는다.
  @Override
  public String toString() {
    return "ReporterConfig[env=" + environmentName + ", port=" + port + ", model=" + geminiModel
        + ", fallbackModel=" + geminiFallbackModel + "]";
  }

  static final String DEFAULT_LOKI_QUERY = "{container_name=\"tripfit-app\"} |~ \"ERROR|WARN\"";
  static final String DEFAULT_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com";

  public static ReporterConfig fromEnv(Map<String, String> env) {
    List<String> missing = new ArrayList<>();
    String environmentName = required(env, "ENVIRONMENT_NAME", missing);
    String token = required(env, "REPORTER_TOKEN", missing);
    String prometheus = required(env, "PROMETHEUS_URL", missing);
    String loki = required(env, "LOKI_URL", missing);
    String grafana = required(env, "GRAFANA_URL", missing);
    String sourceRoot = required(env, "SOURCE_ROOT", missing);
    String apiKey = required(env, "GEMINI_API_KEY", missing);
    String model = required(env, "GEMINI_MODEL", missing);
    String discord = required(env, "DISCORD_WEBHOOK_URL", missing);
    if (!missing.isEmpty()) {
      throw new IllegalStateException("필수 환경 변수가 비어 있습니다: " + String.join(", ", missing));
    }

    String provider = env.getOrDefault("LLM_PROVIDER", "gemini");
    if (!"gemini".equals(provider)) {
      throw new IllegalStateException("지원하지 않는 LLM_PROVIDER입니다: " + provider);
    }

    return new ReporterConfig(
        environmentName,
        token,
        Integer.parseInt(env.getOrDefault("REPORTER_PORT", "9095")),
        URI.create(stripTrailingSlash(prometheus)),
        URI.create(stripTrailingSlash(loki)),
        env.getOrDefault("LOKI_QUERY", DEFAULT_LOKI_QUERY),
        URI.create(stripTrailingSlash(grafana)),
        Path.of(sourceRoot),
        env.getOrDefault("SOURCE_REPO_URL", "").trim(),
        Boolean.parseBoolean(env.getOrDefault("GIT_PULL_ENABLED", "true")),
        provider,
        apiKey,
        model,
        env.getOrDefault("GEMINI_FALLBACK_MODEL", "").trim(),
        URI.create(stripTrailingSlash(env.getOrDefault("GEMINI_BASE_URL", DEFAULT_GEMINI_BASE_URL))),
        URI.create(discord),
        Boolean.parseBoolean(env.getOrDefault("DEBUG_LOG_PROMPT", "false")));
  }

  private static String required(Map<String, String> env, String key, List<String> missing) {
    String value = env.get(key);
    if (value == null || value.isBlank()) {
      missing.add(key);
      return null;
    }
    return value.trim();
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
