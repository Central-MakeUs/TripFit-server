package com.tripfit.reporter.collect;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// 수치에서 뽑은 API 경로(예: GET /api/v1/trips/{tripId})를 TripFit 저장소의 Controller 메서드로 찾아가고,
// 그 메서드가 부르는 Service 메서드까지 줄 번호와 함께 잘라 온다. TripController → TripService처럼
// 한 줄짜리 위임만 하는 facade가 있어서, 위임 메서드를 만나면 한 단계 더 따라간다.
// 자바 문법을 완전히 해석하지 않고 이 저장소의 코드 모양(어노테이션 한 줄 매핑, private final 생성자 주입)에
// 기대는 단순한 방식이라, 찾지 못하면 빈 결과를 돌려주고 리포트는 코드 없이 계속 만든다.
public class SourceCodeLocator {

  public record Snippet(String file, int startLine, int endLine, String text) {}

  record Endpoint(String httpMethod, String path, Path file, int startLine, int endLine) {}

  static final int MAX_SNIPPET_LINES = 80;
  static final int MAX_DELEGATION_BODY_LINES = 5;

  private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\((.*)\\)");
  private static final Pattern METHOD_MAPPING =
      Pattern.compile("@(Get|Post|Put|Patch|Delete)Mapping\\b(.*)");
  private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");
  private static final Pattern SIGNATURE =
      Pattern.compile("^\\s*(?:public|protected|private)?\\s*(?:static\\s+)?[\\w<>\\[\\], ?]+\\s+(\\w+)\\s*\\(");
  private static final Pattern FIELD = Pattern.compile("private\\s+final\\s+([\\w<>, ]+?)\\s+(\\w+)\\s*;");
  private static final Pattern CALL = Pattern.compile("\\b(\\w+)\\.(\\w+)\\s*\\(");

  private final Path sourceRoot;
  private final String repoUrl;
  private final boolean gitPullEnabled;

  public SourceCodeLocator(Path sourceRoot, boolean gitPullEnabled) {
    this(sourceRoot, "", gitPullEnabled);
  }

  // repoUrl이 있으면 처음 분석할 때 sourceRoot가 비어 있으면 저장소를 받아 온다(서버 E의 리포터 전용 사본).
  public SourceCodeLocator(Path sourceRoot, String repoUrl, boolean gitPullEnabled) {
    this.sourceRoot = sourceRoot;
    this.repoUrl = repoUrl == null ? "" : repoUrl;
    this.gitPullEnabled = gitPullEnabled;
  }

  public Path sourceRoot() {
    return sourceRoot;
  }

  // 분석 직전에 리포터 전용 저장소 사본을 main에 맞춘다. 실패해도 이전 코드로 계속 진행하고 사유만 돌려준다.
  // 분석 두 건이 동시에 git을 돌리면 index.lock에서 충돌하므로 한 번에 하나만 실행한다.
  public synchronized Optional<String> refresh() {
    if (!gitPullEnabled) {
      return Optional.empty();
    }
    boolean cloned = Files.isDirectory(sourceRoot.resolve(".git"));
    if (!cloned && repoUrl.isBlank()) {
      return Optional.of("코드 사본이 없고 SOURCE_REPO_URL도 없습니다");
    }
    List<String> command =
        cloned
            ? List.of("git", "-C", sourceRoot.toString(), "pull", "--ff-only", "--quiet")
            : List.of("git", "clone", "--quiet", "--depth", "50", repoUrl, sourceRoot.toString());
    Process process = null;
    Optional<String> failure;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(true).start();
      if (!process.waitFor(15, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        failure = Optional.of("코드 최신화 시간 초과");
      } else {
        failure = process.exitValue() == 0
            ? Optional.empty()
            : Optional.of("코드 최신화 실패(git 종료 코드 " + process.exitValue() + ")");
      }
    } catch (IOException e) {
      failure = Optional.of("코드 최신화 실패(" + e.getMessage() + ")");
    } catch (InterruptedException e) {
      // 시간 초과로 분석이 중단되면 git이 index.lock을 쥔 채 남지 않도록 같이 끝낸다.
      if (process != null) {
        process.destroyForcibly();
      }
      Thread.currentThread().interrupt();
      failure = Optional.of("코드 최신화 중단");
    }
    // 처음 받기(clone)가 끝나지 못하면 반쯤 만들어진 .git이 남아 다음부터 pull만 시도하게 된다. 지우고 다음 분석에서 다시 받는다.
    if (failure.isPresent() && !cloned) {
      deleteQuietly(sourceRoot);
    }
    return failure;
  }

  private static void deleteQuietly(Path root) {
    if (!Files.exists(root)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(root)) {
      paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException ignored) {
          // 다음 분석에서 다시 시도한다.
        }
      });
    } catch (IOException | UncheckedIOException ignored) {
      // 다음 분석에서 다시 시도한다.
    }
  }

  // 경로 하나에 대해 Controller 메서드와, 그 메서드가 부르는 Service 메서드(위임이면 한 단계 더)를 돌려준다.
  public List<Snippet> locate(String httpMethod, String uri) throws IOException {
    Map<String, Path> classIndex = indexClasses();
    String wanted = normalize(uri);
    for (Endpoint endpoint : endpoints(classIndex)) {
      boolean methodMatches =
          httpMethod == null || httpMethod.isBlank() || endpoint.httpMethod().equalsIgnoreCase(httpMethod);
      if (methodMatches && endpoint.path().equals(wanted)) {
        return follow(endpoint, classIndex);
      }
    }
    return List.of();
  }

  List<Endpoint> endpoints(Map<String, Path> classIndex) throws IOException {
    List<Endpoint> endpoints = new ArrayList<>();
    for (Path file : classIndex.values()) {
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      if (lines.stream().noneMatch(l -> l.contains("@RestController"))) {
        continue;
      }
      int classLine = indexOf(lines, l -> l.matches(".*\\bclass\\s+\\w+.*"));
      String basePath = "";
      for (int i = 0; i < classLine; i++) {
        Matcher m = CLASS_MAPPING.matcher(lines.get(i));
        if (m.find()) {
          basePath = firstLiteral(m.group(1));
        }
      }
      for (int i = classLine + 1; i < lines.size(); i++) {
        Matcher m = METHOD_MAPPING.matcher(lines.get(i));
        if (!m.find()) {
          continue;
        }
        int signature = findSignature(lines, i + 1);
        if (signature < 0) {
          continue;
        }
        int end = findBlockEnd(lines, signature);
        endpoints.add(
            new Endpoint(
                m.group(1).toUpperCase(Locale.ROOT),
                normalize(basePath + firstLiteral(m.group(2))),
                file,
                signature,
                end));
      }
    }
    return endpoints;
  }

  private List<Snippet> follow(Endpoint endpoint, Map<String, Path> classIndex) throws IOException {
    List<Snippet> snippets = new ArrayList<>();
    List<String> controllerLines = Files.readAllLines(endpoint.file(), StandardCharsets.UTF_8);
    snippets.add(snippet(endpoint.file(), controllerLines, endpoint.startLine(), endpoint.endLine()));

    List<String> currentLines = controllerLines;
    int bodyStart = endpoint.startLine();
    int bodyEnd = endpoint.endLine();
    // Controller에서 한 번, 위임 메서드였다면 한 번 더 — 최대 두 번 따라간다.
    for (int hop = 0; hop < 2; hop++) {
      Optional<Path> calleeFile = Optional.empty();
      String calleeMethod = null;
      Map<String, String> fields = fields(currentLines);
      for (int i = bodyStart; i <= bodyEnd && calleeFile.isEmpty(); i++) {
        Matcher call = CALL.matcher(currentLines.get(i));
        while (call.find()) {
          String type = fields.get(call.group(1));
          if (type != null && classIndex.containsKey(type)) {
            calleeFile = Optional.of(classIndex.get(type));
            calleeMethod = call.group(2);
            break;
          }
        }
      }
      if (calleeFile.isEmpty()) {
        break;
      }
      List<String> calleeLines = Files.readAllLines(calleeFile.get(), StandardCharsets.UTF_8);
      int start = findMethod(calleeLines, calleeMethod);
      if (start < 0) {
        break;
      }
      int end = findBlockEnd(calleeLines, start);
      snippets.add(snippet(calleeFile.get(), calleeLines, start, end));
      boolean isDelegation = end - start - 1 <= MAX_DELEGATION_BODY_LINES;
      if (!isDelegation) {
        break;
      }
      currentLines = calleeLines;
      bodyStart = start;
      bodyEnd = end;
    }
    return snippets;
  }

  private Map<String, Path> indexClasses() throws IOException {
    Path javaRoot = sourceRoot.resolve("src/main/java");
    if (!Files.isDirectory(javaRoot)) {
      return Map.of();
    }
    Map<String, Path> index = new HashMap<>();
    try (Stream<Path> files = Files.walk(javaRoot)) {
      files
          .filter(p -> p.toString().endsWith(".java"))
          .forEach(p -> index.putIfAbsent(p.getFileName().toString().replace(".java", ""), p));
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
    return index;
  }

  private static Map<String, String> fields(List<String> lines) {
    Map<String, String> fields = new HashMap<>();
    for (String line : lines) {
      Matcher m = FIELD.matcher(line);
      if (m.find()) {
        String type = m.group(1).replaceAll("<.*", "").trim();
        fields.put(m.group(2), type);
      }
    }
    return fields;
  }

  // 매핑 어노테이션 아래의 다른 어노테이션(여러 줄짜리 @ApiResponses 등)을 건너뛰고 메서드 선언 줄을 찾는다.
  private static int findSignature(List<String> lines, int from) {
    int parenDepth = 0;
    for (int i = from; i < lines.size(); i++) {
      String trimmed = lines.get(i).trim();
      boolean insideAnnotation = parenDepth > 0;
      parenDepth += count(trimmed, '(') - count(trimmed, ')');
      if (insideAnnotation || trimmed.startsWith("@") || trimmed.isEmpty() || trimmed.startsWith("//")) {
        continue;
      }
      if (SIGNATURE.matcher(lines.get(i)).find()) {
        return i;
      }
      return -1;
    }
    return -1;
  }

  private static int findMethod(List<String> lines, String name) {
    Pattern declaration =
        Pattern.compile("^\\s*(?:public|protected|private)\\b[^=;]*\\s" + Pattern.quote(name) + "\\s*\\(");
    for (int i = 0; i < lines.size(); i++) {
      if (declaration.matcher(lines.get(i)).find()) {
        return i;
      }
    }
    return -1;
  }

  // 선언 줄부터 중괄호 짝이 맞는 줄까지를 메서드 범위로 본다. 문자열 안의 중괄호는 무시하지 않는 단순 계산이다.
  private static int findBlockEnd(List<String> lines, int start) {
    int depth = 0;
    boolean opened = false;
    for (int i = start; i < lines.size(); i++) {
      String line = lines.get(i);
      depth += count(line, '{') - count(line, '}');
      if (line.indexOf('{') >= 0) {
        opened = true;
      }
      if (opened && depth <= 0) {
        return i;
      }
    }
    return lines.size() - 1;
  }

  private Snippet snippet(Path file, List<String> lines, int start, int end) {
    int last = Math.min(end, start + MAX_SNIPPET_LINES - 1);
    StringBuilder text = new StringBuilder();
    for (int i = start; i <= last; i++) {
      text.append(String.format("%4d | %s%n", i + 1, lines.get(i)));
    }
    if (last < end) {
      text.append("     | … (").append(end - last).append("줄 생략)").append(System.lineSeparator());
    }
    String relative = sourceRoot.relativize(file).toString().replace('\\', '/');
    return new Snippet(relative, start + 1, last + 1, text.toString());
  }

  private static String firstLiteral(String text) {
    Matcher m = STRING_LITERAL.matcher(text == null ? "" : text);
    return m.find() ? m.group(1) : "";
  }

  static String normalize(String path) {
    String normalized = ("/" + path).replaceAll("/+", "/");
    return normalized.length() > 1 && normalized.endsWith("/")
        ? normalized.substring(0, normalized.length() - 1)
        : normalized;
  }

  private static int indexOf(List<String> lines, java.util.function.Predicate<String> test) {
    for (int i = 0; i < lines.size(); i++) {
      if (test.test(lines.get(i))) {
        return i;
      }
    }
    return lines.size();
  }

  private static int count(String text, char c) {
    int n = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == c) {
        n++;
      }
    }
    return n;
  }
}
