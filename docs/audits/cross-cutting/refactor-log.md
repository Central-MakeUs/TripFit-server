# cross-cutting Refactor Log

## 2026-08-05 — 2차 라운드 (A-1, A-2, B-1, B-2 반영)

감사 문서: [`audit-round2.md`](audit-round2.md)

2차 감사 기준 A(반드시 수정) 2건, B(유지보수성) 2건 전부 반영. 사용자 승인: "A-1·A-2 진행", "B-1은 코드까지 적용", "B-2 진행". B-1은 원 스펙(`docs/specs/cross-cutting/social-integration-structured-logging.md`)의 "전사 로깅 정책 확장은 Out of Scope" 결정과 충돌 여지가 있어 착수 전 별도로 사용자 확인을 받았다.

### 쉽게 설명하면 (`plain-language-reporting.md`)

- **A-1**: API 성공 응답을 만드는 공통 코드(`SuccessResponse`)에, 실제로는 아무도 안 쓰는 방법이 하나 더 있었는데, 그 방법은 파라미터를 넣는 순서와 실제 저장되는 순서가 서로 뒤바뀌어 있는 함정이 있었어요. 지금은 안 써서 문제가 없었지만, 나중에 누가 처음 이걸 쓰면 오해하기 쉬운 상태라 아예 지웠습니다.
- **A-2**: 프로젝트 설계도 역할을 하는 문서 2개(`architecture.md`, `auth-social-login.md`)가 1차 리팩토링 때 있었던 변경(설정 파일 삭제·이동)을 반영하지 못한 채로 남아 있었어요. 실제 코드와 설계도가 어긋나 있으면 다음에 코드를 보는 사람이 잘못된 그림을 보고 판단할 수 있어서, 설계도를 실제 코드에 맞게 고쳤습니다.
- **B-1**: 소셜 로그인 관련 로그는 이미 이메일 같은 개인정보를 가려서(마스킹) 기록하고 있었는데, 그 외 "예상 못한 서버 오류"를 전부 받아 처리하는 공통 안전망 코드는 그 가림 처리 없이 그대로 로그를 남기고 있었어요. 이 로그도 결국 같은 곳(Loki, 로그를 모아 보는 시스템)으로 나가기 때문에, 이 공통 안전망에도 똑같이 개인정보 가림 처리를 적용했습니다. 이 과정에서 기존 마스킹 로직이 "예외 메시지에 넘긴 인자"만 가리고 실제로 로그에 찍히는 예외 객체 자체(스택트레이스 포함 부분)는 안 가려지는 허점을 발견해서, 소셜 로그인 쪽에서 이미 쓰던 "스택트레이스는 보존하면서 메시지만 가리는" 안전한 방법을 공통 유틸(`PiiMasker`)로 옮겨 양쪽에서 재사용하도록 정리했어요.
- **B-2**: 소셜 로그인 마스킹의 핵심 규칙("이 경로로만 채우게 해서 반드시 가려지게 한다")을 검증하는 테스트가 하나도 없었어요. 이번에 그 규칙과, 로그에 붙는 부가 정보(제공자·행동·에러 사유 등)가 정확한 이름으로 잘 채워지고 로그가 끝나면 깨끗이 지워지는지를 확인하는 테스트를 추가했습니다.

### 반영 항목

| # | 요약 | 변경 파일 |
|---|------|-----------|
| A-1 | `SuccessResponse.of(T, String, String)` — 전체 코드베이스 미사용 + 파라미터 순서 함정을 가진 오버로드 삭제 | `common/api/SuccessResponse.java` |
| A-2 | `docs/architecture.md`·`docs/specs/auth/auth-social-login.md`의 `common/` 패키지 트리를 실제 코드와 일치시킴(`WebConfig` 삭제·`OpenApiConfig` 이동 반영, `logging/`·`security/` 서브패키지 추가) | `docs/architecture.md`, `docs/specs/auth/auth-social-login.md` |
| B-1 | `GlobalExceptionHandler.handleUnexpectedException()`에 `PiiMasker` 마스킹 적용. 기존 `SocialIntegrationLog`가 private으로 갖고 있던 "스택트레이스 보존 + 메시지만 마스킹" 로직(`maskMessages`/`MaskedThrowable`)을 `PiiMasker.maskThrowable(Throwable)` public 메서드로 승격해 두 곳에서 공용으로 사용 — 로직 중복 제거 + `common` 전 도메인 catch-all 안전망도 동일한 마스킹 보장을 갖도록 확장 | `common/logging/PiiMasker.java`, `common/logging/SocialIntegrationLog.java`, `common/exception/GlobalExceptionHandler.java`, `common/exception/GlobalExceptionHandlerTest.java`(마스킹 검증 케이스 추가) |
| B-2 | `SocialLogContext.withProviderError()`의 마스킹 강제 계약, `SocialIntegrationLog.toMdcFields()`의 MDC 키 매핑(정확한 이름으로 채워지는지·로깅 종료 후 제거되는지)을 검증하는 테스트 신설 | `common/logging/SocialLogContextTest.java`(신규) |

### 검증 결과

- `./gradlew compileJava compileTestJava` — 통과
- `./gradlew test` (전체) — 통과, 실패 0건
- **`oasdiff` API 계약 검증:**
  1. `./gradlew test --tests OpenApiSpecExportTest` → `build/openapi/openapi.json` 생성 성공
  2. `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` → **"No changes detected"**
  3. `oasdiff diff docs/api/openapi.json build/openapi/openapi.json` → **`{}`** (diff 0건)

**결론: cross-cutting(common) 도메인 API 응답·요청·에러코드·엔드포인트 스펙은 리팩토링 전/후로 100% 동일함을 실제 실행으로 증명함.**

### 남겨둔 C/D 항목

`audit-round2.md`의 C 2개(`GlobalExceptionHandler.handleMessageNotReadable`/`handleTypeMismatch` 로그 미기록 — 400 클라이언트 오류라 불필요, 1차 C 재확인), D 1개(1차 D 전부 재확인, 상황 변화 없음) — 이번 라운드에서 변경하지 않음. 이유는 `audit-round2.md` 해당 절 참고.

## 2026-08-05 — 1차 라운드 (A-1~A-3, B-1, B-2 반영)

감사 문서: [`audit.md`](audit.md)

### 반영한 항목

| 항목 | 내용 | 변경 파일 |
|------|------|-----------|
| A-1 | 죽은 CORS 설정(`WebConfig`) 삭제 — `SecurityConfig`가 이미 CORS를 전담 처리 중이라 실행되지 않던 코드였고, 두 파일이 각자 다른 허용 origin 목록을 갖고 있던 drift도 함께 제거됨 | `common/config/WebConfig.java` (삭제, -23줄) |
| A-2 | `SocialTokenCrypto.secretKey` 필드에 `volatile` 추가 — double-checked locking의 JMM 위반(동시 요청 초기 구간 레이스 컨디션 가능성) 제거 | `common/security/SocialTokenCrypto.java` (+1줄) |
| A-3 | `SocialIntegrationLog`가 `Throwable`을 로깅할 때 메시지를 `PiiMasker`로 마스킹하도록 변경(cause 체인 포함, 스택트레이스는 보존) — 기존엔 예외 객체를 직접 로깅하는 경로(18곳)에서 이메일 등 PII가 마스킹 없이 구조화 로그(Loki)로 나갈 수 있었음 | `common/logging/SocialIntegrationLog.java` (+31/-4줄, 18개 호출부는 변경 없음) |
| B-1 | `OpenApiConfig`를 `common/config/`에서 `auth/config/`로 이동 — `common`이 `auth` 도메인(`@AuthorizedUser`)에 역방향 의존하던 구조 해소. ArchUnit 룰(`commonPackageDoesNotDependOnOtherDomains`) 신설로 재발 방지(기존부터 있던 `SocialLogContext`→`user.domain.SocialProvider` 공유 enum 의존은 명시적으로 예외 처리) | `common/config/OpenApiConfig.java` → `auth/config/OpenApiConfig.java` (이동), `test/architecture/ArchitectureTest.java` (+18줄) |
| B-2 | `common` 패키지 보안 핵심 클래스 단위 테스트 신설 — 기존엔 테스트가 전혀 없었음 | `test/common/security/SocialTokenCryptoTest.java`(신규), `test/common/logging/PiiMaskerTest.java`(신규), `test/common/logging/SocialIntegrationLogTest.java`(신규, A-3 마스킹 검증), `test/common/exception/GlobalExceptionHandlerTest.java`(신규) |

### 검증 결과

- `./gradlew test` — 전체 통과 (32개 신규/변경 테스트 포함, 기존 테스트 회귀 없음)
- `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` — breaking change 없음
- `oasdiff diff docs/api/openapi.json build/openapi/openapi.json` — 변경 전(main, stash로 확인)과 변경 후 diff 내용이 완전히 동일(`PATCH /api/v1/trips/{tripId}/recommendations/{rank}/feedback` 요청 바디 설명 문구 "PUT"→"PATCH" 1건, 이번 리팩토링과 무관하게 이미 존재하던 `docs/api/openapi.json` 스냅샷 drift) — **이번 라운드가 만든 API 계약 변화는 0건**임을 확인. 해당 drift 자체는 `trip/recommendation` 영역이라 이번 `cross-cutting` 범위 밖이므로 별도 후속으로 남김(아래 참고)

### 남겨둔 C/D 항목과 이유

`audit.md`의 C(3건)·D(5건) 참고 — 요약:

- **C**: `SocialTokenCryptoProperties`/`FcmProperties` Lombok 미사용 스타일 차이(유출 위험 없음, 기존 전례 있음), `SocialLogContext` wither 보일러플레이트(규모상 Builder 전환은 과잉), `SocialTokenCrypto.decrypt()` 방어 코드 미추가(실사용 경로 없음), `common/domain` Lombok 사용과 `spring-boot-java.md` "Lombok 미사용" 문서 간 drift(코드가 아닌 문서 쪽 후속 필요)
- **D**: `JpaConfig`/`SchedulingConfig` 통합 안 함, `equals`/`hashCode` 미추가, `ErrorCode.toErrorResponse()` 미추가, `CommonErrorCode` 상수 선추가 안 함, 미발생 예외용 핸들러 미추가, `PiiMasker` 스코프 확장 안 함 — 각 이유는 `audit.md` D절 참고

### 참고 — 범위 밖 발견 사항 (이번 라운드에서 손대지 않음)

- `docs/api/openapi.json`이 `trip/recommendation` 피드백 API의 Javadoc 설명 문구와 살짝 어긋나 있음(엔드포인트가 실제로는 `PATCH`인데 문서 문구에 구 `PUT` 표현이 남아 있었다가 코드에서는 이미 고쳐진 상태 — 스냅샷만 재생성 안 됨). `cross-cutting` 감사 범위·이번 세션 작업과 무관해 그대로 두었음 — 사용자 확인 후 별도로 `docs/api/openapi.json` 재생성 필요.

## 도메인 감사 완료

이로써 `docs/audits/README.md` 진행 현황의 6개 도메인(auth, user, user-schedule, trip, notification, cross-cutting) 감사·구현이 모두 1라운드 완료됨.
