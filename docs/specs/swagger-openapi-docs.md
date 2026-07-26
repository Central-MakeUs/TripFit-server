# Swagger/OpenAPI 문서 가독성 개선

- 상태: Implemented (이슈 미생성)
- wave: 무관 (도구/문서 품질)
- 선행: 없음
- 목적: 프론트가 Swagger UI로 API를 파악할 때 불편한 지점을 정리하고 개선.

## 배경

프론트가 Swagger 문서를 보기 불편하다는 피드백으로 현황 조사. `springdoc-openapi-starter-webmvc-ui:3.0.3`, 컨트롤러 7개 전부 `@Tag` 보유, DTO 28/28 `@Schema` 보유 — 기본 골격은 갖춰져 있으나 아래 문제들이 확인됨.

## 문제 1 — `@ApiResponse` 전무 (해결됨)

- **현상:** 코드베이스 전체 `@ApiResponse`/`@ApiResponses` 사용 0건 (grep 확인). 에러 코드는 `@Operation description`의 "주요 에러: CODE — 상황" 자유 텍스트로만 존재.
- **영향:** Swagger UI Responses 탭에 상태코드별 에러 응답 스키마·예시가 없음. Try-it-out으로 실패 응답의 실제 JSON 모양(`ErrorResponse`, `FieldError`)을 확인할 수 없음.
- **결정:** 컨트롤러 7개 전 엔드포인트에 `@ApiResponses`를 추가. 각 엔드포인트의 기존 "주요 에러" 텍스트 + `TripAuthorizationInterceptor`(`@TripMemberOnly`/`@TripOwnerOnly`)가 실제로 던지는 코드 + `@Valid` 검증 400을 HTTP 상태별로 그룹핑, `ErrorResponse` 스키마 + 실제 코드/메시지를 담은 `@ExampleObject`로 문서화.
- **구현 메모:** 프로젝트의 성공 envelope 클래스와 springdoc `@ApiResponse` 어노테이션이 simple name(`ApiResponse`)이 같아 import 충돌 — 워크어라운드(FQN) 대신 성공 envelope 클래스를 `SuccessResponse`로 rename해 충돌 자체를 제거함(문제 6 참고).
- 적용 파일: `TripController`, `TripMemberController`, `AuthController`, `UserController`, `UserScheduleController`, `GoogleCalendarController`, `DevAuthController`.

## 문제 6 — `ApiResponse` 클래스명이 springdoc 어노테이션과 충돌 (해결됨)

- **현상:** `com.tripfit.tripfit.common.api.ApiResponse`(성공 envelope record)가 springdoc의 `@ApiResponse` 어노테이션과 simple name이 겹침. 문제 1 작업 초기엔 어노테이션을 FQN(`@io.swagger.v3.oas.annotations.responses.ApiResponse`)으로 우회했으나, 이는 표준 관행이 아니라 워크어라운드.
- **결정:** 실제로 흔히 쓰이는 해법(라이브러리 어노테이션명은 고정이므로 자체 클래스명을 바꿈)을 따라 성공 envelope를 **`SuccessResponse`**로 rename — 같은 패키지의 `ErrorResponse`(실패 envelope)와 대칭. 영향 범위는 컨트롤러 7개 + 클래스 파일 자체뿐(서비스·테스트 미참조)이라 blast radius 작음.
- **동반 변경:** `docs/architecture.md`, `docs/architecture/api-response.md`, `docs/specs/auth-social-login.md`, `.claude/rules/spring-boot-java.md`의 클래스명 표기 갱신. 어노테이션 import는 FQN 대신 정상 `import io.swagger.v3.oas.annotations.responses.ApiResponse;`로 되돌림.

## 문제 2 — request/response 완성 예시 부재 (부분 해결)

- **현상:** DTO 필드 단위 `@Schema(example=...)`는 있으나(28/28), record 전체를 하나의 성공/실패 JSON 샘플로 묶어 보여주는 장치가 없음. Swagger의 "Example Value"가 필드 조합으로만 생성됨.
- **영향:** 프론트가 완성된 요청/응답 JSON을 한눈에 볼 수 없음.
- **해결된 부분:** 문제 1 작업으로 모든 에러 응답에는 실제 코드·메시지가 담긴 완성 JSON 예시(`@ExampleObject`)가 생김.
- **남은 부분:** 성공 응답·요청 DTO는 여전히 필드별 `example`의 자동 조합에만 의존 — record 전체를 묶은 명시적 예시는 없음. 필요성이 낮다고 판단되면(필드별 example 조합으로 충분) 이대로 종료, 아니면 후속으로 결정.

## 문제 3 — `OpenApiConfig.java` Info 블록 (해결됨)

파일: `src/main/java/com/tripfit/tripfit/common/config/OpenApiConfig.java`

- **현상 A (헤딩-내용 불일치):** `## 인증` 헤딩 아래에 실제 인증 설명과 "API description 읽는 순서(목적→호출시점→전제→결과)" 안내가 섞여 있었음.
- **현상 B (톤 혼재):** "~습니다"체 격식 문장과 "불가", "안 옴" 같은 축약 메모가 섞여 있었음.
- **현상 C (정책과 긴장):** "여행방 멤버십 상태(JOINED/RESPONDED)" 블록이 상세한 비즈니스 상태 머신 설명을 통짜로 담고 있었음.
- **결정:** 세계적으로 흔한 OpenAPI 관례(전역 Info는 개요·인증·읽는 법만 짧게, 비즈니스 로직 상세는 엔드포인트별 `@Operation`에)를 따름. 헤딩 분리(`## 인증` / `## API 설명 읽는 법` / `## 필독`), 톤 통일, 멤버십 상태 상세 설명은 "필독 — 관련 API 설명 참고" 한 줄로 축소.
- **동반 변경:** `.claude/rules/client-platform.md`의 "멤버십 JOINED/RESPONDED" 행 amend — Swagger 전역 `Info`·`@Tag`는 "요약 한 줄 + 필독 포인터만", 상세는 `glossary.md`·`trip-room-api`·관련 `@Operation`에만 두도록 정책 변경.

## 문제 4 — `@Tag` 설명의 표기법 (해결됨)

파일: `TripController.java`, `TripMemberController.java`

- **현상:** Trip·Trip Members 두 태그만 `=`(정의)·`∧`(AND) 같은 축약 기호를 써서 멤버십 상태 로직을 압축 서술. 다른 5개 태그(Auth, Auth (Dev), User, User Schedule, Google Calendar)는 전부 평이한 한 문장.
- **영향:** `spring-boot-java.md` OpenAPI 규칙("구현자 메모·이슈 트래커용 문자열이 아니다")과 어긋남. 같은 멤버십 로직이 이미 `OpenApiConfig` Info + 각 엔드포인트 `@Operation`의 전제/결과 섹션에 프로즈로 존재해 세 번째 중복본이었음.
- **결정:** 다른 5개와 동일하게 "이 API 묶음이 무엇을 다루는지" 한 문장으로 축소, 멤버십 상세는 제거.

  ```java
  @Tag(name = "Trip", description = "여행방 생성·목록·상세·참여·일정 확인·Pin")
  @Tag(name = "Trip Members", description = "여행방 참여자 목록·그룹 달력·내보내기")
  ```

## 문제 5 — Dev 전용 API 노출 (확인 완료 — 문제 없음)

- `DevAuthController`·`DevAuthService` 모두 `@Profile({"local", "dev"})`로 확인. prod 프로필에서는 빈 자체가 등록되지 않아 Swagger에도 노출되지 않음. 코드 변경 불필요.

## 남은 항목

- 문제 2 "남은 부분"(성공 응답 완성 예시) 필요 여부 — 별도 요청 시 진행.
