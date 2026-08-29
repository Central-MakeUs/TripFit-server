# cross-cutting Refactor Log

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
