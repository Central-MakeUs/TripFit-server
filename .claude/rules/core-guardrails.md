# Core — 가드레일 (⛔ STOP)

**구현·기본값 변경·커밋 전에 반드시 멈춰야 하는 것**만 모았다. 여기 걸리면 다른 어떤 규칙·워크플로보다 먼저 중단하고 사용자에게 묻는다.

작업을 **어떤 순서로** 진행하는지(트랙 1개 선택 → 게이트 4개 통과)는 `core-workflow.md`가 담당한다.

**형제 규칙:** `core-workflow.md` · `core-scope.md` · `core-followup.md` · `core-tools.md` · `tripfit-release.md`
Java·ErrorCode 상세: `spring-boot-java.md` · Git: `.github/CONTRIBUTING.md`

## ⛔ STOP (모든 규칙보다 우선)

구현·기본값 변경·커밋 전 확인. **위반 시 중단·사용자 질문.**

### 1. 문서·구현 정합

1. **문서·스펙·결정 vs 구현** — `docs/specs/`(Approved), `docs/decisions/`(확정), `docs/product/`, `docs/architecture.md`, `deploy/README.md`와 다른 TTL·API 계약·enum·한도·env 이름·패키지 경로를 **조용히 맞추지 않는다**.
2. **문서 vs 문서** — PRD vs 스펙, 스펙 vs 와이어프레임, decisions vs architecture, 스펙 vs `.env.example` 등 **서로 다른 값·용어·경로**면 한쪽을 임의 선택하지 말고 충돌을 짧게 목록화해 확인 요청.
3. **임의 개선 금지** — "더 나은 기본값", "업계 일반값", "코드가 간단해짐"만으로 문서와 다른 수치·구조를 바꾸지 않는다. 변경 시 **문서 amend 또는 사용자 명시 승인**.
4. **불확실하면 질문** — 가정을 숨기지 않는다. 질문 없이 진행한 선택은 **잘못된 작업**.
5. **구현 상태 보고 전 코드 우선 확인** — 스펙 Must Have 체크박스(`[ ]`/`[x]`)·"미구현"·"대기 중"·"`#n` 선행 필요" 같은 서술은 기능이 merge된 뒤 스펙 동기화가 누락되면 stale해진다. 사용자·프론트에게 "이 API·enum·이벤트가 아직 구현 안 됨/트리거 안 됨"이라고 **부정적으로 단정**해 답하기 전에, 관련 Controller·Service(이벤트 발행부)·테스트를 직접 grep/Read로 확인한다. `docs/specs/*.md` 문구 하나만 근거로 구현 여부를 판단하지 않는다 — 필요하면 `gh issue view`로 실제 이슈 상태도 함께 확인.
6. **"Swagger에 있다"는 소스 어노테이션이 아니라 실제 생성 문서로 확인** — DTO·enum에 `@Schema`가 있다고 해서 Swagger에 실제로 노출된다고 단정하지 않는다. `@ApiResponse`에서 제네릭 wrapper(`SuccessResponse<T>`)를 `schema = @Schema(implementation = SuccessResponse.class)`처럼 raw 타입으로 지정하면 springdoc이 실제 `data` 타입(리스트·필드·enum)을 못 읽어 스키마가 통째로 사라진다(`useReturnTypeSchema = true` 필요 — `spring-boot-java.md` 참고, `NotificationController` 사고 사례). "프론트가 필요한 값이 Swagger에 이미 있다"고 답하기 전에 로컬 `/v3/api-docs`, 배포 서버 `/v3/api-docs`, 또는 `docs/api/openapi.json`을 실제로 열어 해당 스키마·enum이 진짜 노출되는지 확인한다.

**절대 금지:** 문서와 다른 access/refresh TTL, API 필드, 에러 코드, env 키를 임의 구현·커밋. **에러 코드·`@TripActivity`·권한 어노테이션을 “다음 커밋에” 미루기.** **스펙 문서만 보고 "미구현"이라고 사용자에게 보고 — 코드 미확인 상태로 구현 상태 단정.** **`@Schema` 존재만 보고 "Swagger에 이미 노출된다"고 보고 — 실제 생성된 OpenAPI 문서 미확인.**

### 2. ErrorCode · AOP/Interceptor — 같은 턴 즉시 갱신

API·BR 실패 케이스·권한 게이트·`last_activity_at` touch를 **추가·변경하면 같은 PR·같은 턴**에 끝낸다. “나중에” 금지.

| 변경 | 같은 턴에 필수 |
|------|----------------|
| 새 실패 분기·HTTP/`code` | `{Domain\|Feature}ErrorCode` + `TripFitException` throw + **스펙 에러 표** + `@Schema` |
| L1 touch ([`trip-last-activity-at.md`](../../docs/specs/trip/trip-last-activity-at.md)) | public 유스케이스 `@TripActivity` (create는 엔티티 초기값) |
| 멤버/방장 전용 API | `@TripMemberOnly` / `@TripOwnerOnly` + Interceptor 계약 유지 |
| 폐기된 `code`·게이트 | enum·throw·스펙·Swagger **삭제** (아래 레거시 절) |

**금지:** throw/`code`만 넣고 enum·스펙 미갱신 · touch인데 `@TripActivity` 누락(또는 수동 `touchLastActivity` 재도입) · Draft 전용 코드를 Approved 전 enum에 미리 넣기 · Filter/Interceptor에서 envelope와 다른 ad-hoc JSON

SSOT: [`docs/architecture/api-response.md`](../../docs/architecture/api-response.md) · `spring-boot-java.md` ErrorCode·AOP 절

### 3. DB 스키마 — 마이그레이션 금지 (상용 보존 데이터 없음)

1. Flyway / Liquibase / `V*__*.sql` / 데이터 보존 마이그레이션 **작성·커밋 금지**.
2. 스키마 SSOT = **JPA 엔티티(최신 하나)** + Hibernate `ddl-auto` (`docs/architecture.md`).
3. 로컬·dev DB **폐기·재생성** 허용 (`docker compose down -v` 등). orphan·구 스키마 호환 레이어 금지.
4. “나중에 Flyway V2” 식 예정 코드/파일·주석 추가 금지. prod 보존이 필요해지면 **그때** decisions + 마이그레이션 별도 결정.

### 4. 레거시·정책 불일치 코드 제거 (호환 레이어 금지 · **같은 변경에서 즉시**)

현행 Approved 스펙·BR·decisions·구현 계약과 다른 코드·경로·문서를 “호환용”으로 남기지 않는다.
**dev·상용 보존 데이터 없음** → 듀얼 패스·구 클라/DB 호환·orphan 유지 **불필요·금지**.

1. **삭제 대상:** 폐기 API/path · 정책상 폐기 enum/`ErrorCode`/필드 · 구 상수·검증(예: 대체된 730일 A1) · 대체된 Repository 메서드·벌크 쿼리 · 구 스키마 매핑 · 호환 if · `@Deprecated` 방치 · stale `@Schema`/메시지 · “나중에 지울” TODO · 스펙·OpenAPI의 **현행 계약으로 적힌** 구 수치·구 API명
2. **시점 (필수):** 정책 amend·경로 교체 구현과 **같은 커밋/같은 PR/같은 턴**. “다음 커밋에 정리”·“일단 새 경로만 추가” **금지**
3. **교체 = 구경로 삭제:** 새 구현을 넣으면 **호출되지 않는 구 메서드·상수·테스트 assert·문서 ‘현행’ 문구**를 같은 변경에서 제거. “요청 밖 dead code라서 언급만”으로 **넘기지 않는다** — 이번 변경이 대체한 코드는 **요청 범위**
4. **하지 않음:** 구 클라/DB 호환 어댑터 · orphan 컬럼 · deprecated 방치 · soft/hard·live/snapshot 등 **정책상 폐기된 이중 분기**
5. **예외 — 진짜 요청 밖:** 이번 정책과 **무관한** 기존 dead code만 언급. **정책 불일치·이번 교체 잔존은 이 절이 우선 → 삭제**
6. **이력 문서:** 스펙 Changelog·과거 체크리스트의 “당시 A1=730” 등은 OK. **‘현행 코드/계약’** 으로 적힌 구 값은 §1·본 절로 **즉시 amend**

### 5. API 계약 변경 — `Breaking-Change-Reason` 트레일러 (같은 커밋 필수)

프론트가 **조금이라도 대응해야 하는** API 계약 변경은 CI의 `oasdiff breaking` 판정(좁은 스키마 기준)을 기다리지 않고 **변경을 만드는 커밋 시점에 직접** 기록한다. "필드 하나 추가일 뿐"·"optional이라 breaking 아님"·"enum 값만 늘렸을 뿐"이라는 이유로 생략하지 않는다.

**대상 (하나라도 해당하면 필수):**

- 요청/응답 필드 **추가·삭제·이름변경·타입변경·필수화**(optional 추가 포함)
- enum 값 **추가·삭제·이름변경**
- `ErrorCode` **신규·변경·삭제**, HTTP 상태 변경
- 경로·HTTP 메서드 **변경·삭제**, 필드 의미(semantics)만 바뀌어 프론트 처리 로직이 달라지는 경우

**필수 조치:** 위 변경이 포함된 커밋 본문에 `Breaking-Change-Reason: <한 줄 사유>` 트레일러 추가. 형식·예시·Discord 알림 흐름: [`docs/api/README.md`](../../docs/api/README.md) "왜 변경했는가" 절.

**같은 턴 체크 (ErrorCode·AOP §2와 동일 패턴):** DTO·enum·`ErrorCode`·`@RequestMapping` 경로를 수정하는 파일을 커밋에 담기 **직전에** 이 절을 재확인한다. 커밋을 만든 뒤 사용자가 지적해서야, 또는 CI가 "⚠️ 사유 미기재"를 띄운 뒤에야 트레일러를 추가하는 흐름은 **금지** — 이미 늦은 대응이다.

**금지:** 트레일러 없이 커밋 · oasdiff `breaking` 카테고리(스키마 파괴적 변경)에만 해당한다고 임의로 좁혀 해석 · "나중에 CI 알림 뜨면 추가" 미루기.

### 6. 보안·아키텍처 성격 로직 변경 — `docs/how-it-works.md` 같은 턴 갱신

인증·세션·토큰 저장 방식, 결제, 개인정보 저장·암호화, 그 외 "이게 바뀐 걸 사용자가 한참 뒤에야 알면 곤란한" 성격의 로직을 바꾸면, 그 작업과 **같은 턴**에 [`docs/how-it-works.md`](../../docs/how-it-works.md)의 해당 절도 쉬운 말로 고친다(없던 주제면 새 절 추가). "나중에 정리"·"스펙에 이미 적었으니 됐다" 금지 — ErrorCode·AOP(§2)와 동일한 패턴.

**대상(예):** 토큰·세션 저장 위치·전달 방식, 비밀번호·시크릿 처리, 결제 흐름, 대량 개인정보 접근·삭제 로직.

**금지:** `docs/specs/`에만 적어두고 `how-it-works.md`는 방치 · "사소한 변경"이라며 임의로 갱신 생략 · 기술 용어를 그대로 옮겨적기(사용자가 다른 문서 없이도 읽히게 — `core-reporting.md` 준수).

## 금지 (요약)

위 절들과 `core-workflow.md` 게이트의 **스캔용 목록**이다. 조건·근거는 각 절이 SSOT이므로 여기서 다시 설명하지 않는다 — 항목이 걸리면 해당 절을 편다.

| 축 | 하지 않는다 |
|----|-------------|
| **STOP** | §1 문서 충돌 무시 · §2 ErrorCode/AOP 미갱신 · §3 마이그레이션 작성 · §4 교체 후 구 경로 방치 · §5 `Breaking-Change-Reason` 누락 · §6 `how-it-works.md` 미갱신 |
| **게이트** | G1 리서치 없이 추측 구현·블로그 인용 · G2 확인 없이 이슈/브랜치/PR 생성 · G3 `doc-reviewer` 생략 · 이슈 번호 없는 브랜치명(CONTRIBUTING) |
| **환경** | `git push --force`(main/master) · `rm -rf` · 운영 DB 파괴 · `.env`·API 키 커밋 |
