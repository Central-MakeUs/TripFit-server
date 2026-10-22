package com.tripfit.reporter.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripfit.reporter.llm.IncidentReport;
import com.tripfit.reporter.llm.IncidentReport.Suspect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SuspectValidatorTest {

  @TempDir Path temp;

  @Test
  void validate_keepsOnlyRealAppCodeFileAndLine() throws Exception {
    Path repo = temp.resolve("repo");
    Files.createDirectories(repo.resolve("src/main/java/com/example"));
    Files.writeString(repo.resolve("src/main/java/com/example/A.java"), "line1\nline2\nline3\n");
    Files.createDirectories(repo.resolve("src/main/resources"));
    Files.writeString(repo.resolve("src/main/resources/application.yml"), "a: b\n");
    Files.writeString(repo.resolve(".env.example"), "X=1\n");
    Files.writeString(temp.resolve("outside.java"), "x\n");
    String a = "src/main/java/com/example/A.java";
    IncidentReport report =
        new IncidentReport("s", List.of(),
            List.of(
                new Suspect(a, 3, "있음"),
                new Suspect(a, 4, "줄 범위 밖"),
                new Suspect("src/main/java/com/example/Missing.java", 1, "없는 파일"),
                new Suspect("../outside.java", 1, "저장소 밖"),
                new Suspect(a, 0, "줄 번호 0"),
                new Suspect("src/main/resources/application.yml", 1, "앱 코드가 아님"),
                new Suspect(".env.example", 1, "앱 코드가 아님")),
            List.of(), IncidentReport.Confidence.LOW);

    SuspectValidator.Result result = new SuspectValidator(repo).validate(report);

    assertThat(result.report().suspects()).containsExactly(new Suspect(a, 3, "있음"));
    assertThat(result.dropped()).isEqualTo(6);
  }
}
