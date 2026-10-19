package com.tripfit.reporter.report;

import com.tripfit.reporter.llm.IncidentReport;
import com.tripfit.reporter.llm.IncidentReport.Suspect;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// 모델이 짚은 "의심 코드 위치"가 저장소 사본에 실제로 있는지 확인한다. 없는 파일·범위 밖 줄은 빼고,
// 몇 개를 뺐는지 리포트에 표시한다. 모델이 그럴듯한 경로를 지어내는 경우를 막기 위해서다.
public class SuspectValidator {

  public record Result(IncidentReport report, int dropped) {}

  private final Path sourceRoot;

  public SuspectValidator(Path sourceRoot) {
    this.sourceRoot = sourceRoot.toAbsolutePath().normalize();
  }

  public Result validate(IncidentReport report) {
    List<Suspect> kept = new ArrayList<>();
    for (Suspect suspect : report.suspects()) {
      if (exists(suspect)) {
        kept.add(suspect);
      }
    }
    return new Result(report.withSuspects(kept), report.suspects().size() - kept.size());
  }

  private boolean exists(Suspect suspect) {
    Path file = sourceRoot.resolve(suspect.file()).normalize();
    // 앱 코드(src/main/java) 안의 파일만 인정한다. "../"로 밖을 가리키거나 문서·설정 파일을 짚은 경우는 뺀다
    // (2026-10-19 사용자 결정).
    if (!file.startsWith(sourceRoot.resolve("src/main/java")) || !Files.isRegularFile(file) || suspect.line() < 1) {
      return false;
    }
    try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
      return lines.count() >= suspect.line();
    } catch (IOException | java.io.UncheckedIOException e) {
      return false;
    }
  }
}
