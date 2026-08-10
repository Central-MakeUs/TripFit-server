# Harness Workflow

**형제 규칙 (내용 분리 SSOT):** `harness-wave.md` · `harness-follow-up.md` · `workflow-tools.md`  
Java·ErrorCode 상세: `spring-boot-java.md` · Git: `.github/CONTRIBUTING.md`

## ⛔ STOP (모든 규칙보다 우선)

구현·기본값 변경·커밋 전 확인. **위반 시 중단·사용자 질문.**

### 1. 문서·구현 정합

1. **문서·스펙·결정 vs 구현** — `docs/specs/`(Approved), `docs/decisions/`(확정), `docs/product/`, `docs/architecture.md`, `deploy/README.md`와 다른 TTL·API 계약·enum·한도·env 이름·패키지 경로를 **조용히 맞추지 않는다**.
2. **문서 vs 문서** — PRD vs 스펙, 스펙 vs 와이어프레임, decisions vs architecture, 스펙 vs `.env.example` 등 **서로 다른 값·용어·경로**면 한쪽을 임의 선택하지 말고 충돌을 짧게 목록화해 확인 요청.
3. **임의 개선 금지** — "더 나은 기본값", "업계 일반값", "코드가 간단해짐"만으로 문서와 다른 수치·구조를 바꾸지 않는다. 변경 시 **문서 amend 또는 사용자 명시 승인**.
4. **불확실하면 질문** — 가정을 숨기지 않는다. 질문 없이 진행한 선택은 **잘못된 작업**.
5. **구현 상태 보고 전 코드 우선 확인** — 스펙 Must Have 체크박스(`[ ]`/`[x]`)·"미구현"·"대기 중"·"`#n` 선행 필요" 같은 서술은 기능이 merge된 뒤 스펙 동기화가 누락되면 stale해진다. 사용자·프론트에게 "이 API·enum·이벤트가 아직 구현 안 됨/트리거 안 됨"이라고 **부정적으로 단정**해 답하기 전에, 관련 Controller·Service(이벤트 발행부)·테스트를 직접 grep/Read로 확인한다. `docs/specs/*.md` 문구 하나만 근거로 구현 여부를 판단하지 않는다 — 필요하면 `gh issue view`로 실제 이슈 상태도 함께 확인.

**절대 금지:** 문서와 다른 access/refresh TTL, API 필드, 에러 코드, env 키를 임의 구현·커밋. **에러 코드·`@TripActivity`·권한 어노테이션을 “다음 커밋에” 미루기.** **스펙 문서만 보고 "미구현"이라고 사용자에게 보고 — 코드 미확인 상태로 구현 상태 단정.**

### 2. ErrorCode · AOP/Interceptor — 같은 턴 즉시 갱신

API·BR 실패 케이스·권한 게이트·`last_activity_at` touch를 **추가·변경하면 같은 PR·같은 턴**에 끝낸다. “나중에” 금지.

| 변경 | 같은 턴에 필수 |
|------|----------------|
| 새 실패 분기·HTTP/`code` | `{Domain\|Feature}ErrorCode` + `TripFitException` throw + **스펙 에러 표** + `@Schema` |
| L1 touch ([`trip-last-activity-at.md`](../../docs/specs/trip-last-activity-at.md)) | public 유스케이스 `@TripActivity` (create는 엔티티 초기값) |
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

## 작업 분류 (시작 전 30초)

1. `docs/product/development-wave.md` 활성 Wave·Must · 요약 `docs/product/waves.md`
2. GitHub 이슈 — 범위·완료 기준 확인/생성 (**브랜치용 `#n` 확정**)
3. DB·인증·3파일+ → `specify` → `docs/specs/` → **승인 후** 구현
4. 그 외 → `AGENTS.md` + 관련 `docs/product/` 후 바로 구현

**Wave / `[미정]` / 일정 용어:** `harness-wave.md` (단정 금지 · #2 트래커)

**브랜치:** `main`에서 `{type}/{issue-number}-{description}` — 이슈 번호 생략 금지. SSOT: [`.github/CONTRIBUTING.md`](../../.github/CONTRIBUTING.md)

**스펙 신호:** DB 스키마, 3파일+, BR-*, 프로필/배포, **인증·푸시·딥링크·결제** 등 클라 연동 API

## Before Coding

1. `AGENTS.md` → `docs/architecture.md` → `docs/product/development-wave.md` → `docs/product/waves.md` → `docs/product/platform.md` → `docs/decisions/002-domain-split-vercel-api.md` → `docs/product/mvp.md`
2. 스펙 필요 시 Plan 또는 `specify` → **사용자 승인 후** 구현
3. 가정 명시 · 해석이 여러 개면 질문 · 더 단순한 방법 있으면 말하기
4. 모호·문서 충돌 → **STOP §1** (구현 시작 금지)
5. 변경 파일 목록 확정 (drive-by 리팩터 금지)
6. **`main`에서 `{type}/{issue-number}-{description}` 분기**
7. 다단계: `1. [단계] → verify: [확인]`

## While Coding

- 요청 범위만 · 인접 “개선”·깨지지 않은 리팩터 금지
- 요청 밖 기능·단일 사용 추상화·불필요 설정 금지
- STOP 재확인 — 스펙과 다른 수치·계약·env 금지
- **레거시** — 경로·상수·검증·API를 바꾸면 **구 구현·미사용 메서드·구 assert·‘현행’ 문서 문구를 같은 변경에서 삭제** (STOP §4). “나중에” 금지
- **ErrorCode·AOP** — 실패·touch·권한 변경 시 **같은 턴** (STOP §2)
- 기존 스타일 유지. 내 변경으로 생긴 unused만 정리
- 패키지·Entity·DTO·enum·JWT `@Operation`·**메서드 역할 `//` 주석**: `spring-boot-java.md` (Comments — public 유스케이스 생략 금지)
- 핵심 로직 변경 시 `./gradlew test`
- 변경한 모든 줄은 사용자 요청에 직접 연결

## After Coding

- 변경 요약 + 검증 (`./gradlew test` 등)
- 스펙 있으면 완료 기준 체크리스트 대조
- **API 추가·변경:** `docs/` 동기화 + 관련 GitHub 이슈 (`gh issue view` → `gh issue edit`) + STOP §5 대상이면 커밋에 `Breaking-Change-Reason:` 트레일러 포함 확인
- **PR 전:** `Closes #n`·PR 체크리스트를 구현·테스트와 대조 (`[x]`만 실제 완료). 수동·미구현·`[제안]`·wave 밖은 체크 금지
- 커밋·PR: CONTRIBUTING — `{Type}: {한글}`, base `main`, **Create a merge commit** (Squash 금지)
- **PR merge 확인 후:** 작업 브랜치 삭제 (원격+로컬) — CONTRIBUTING Pull Request "merge 후" 절. merge 안 된 브랜치는 삭제 금지
- **커밋 요청 시:** 주제별 **최대 3개** (구현/테스트/문서·하네스). 억지 분할 금지
- 같은 실수 2회+ → `.claude/rules/` 추가 **제안** (자동 추가 금지)
- **레거시 재점검:** 이번 PR이 대체한 구 경로·상수·문서 ‘현행’ 문구가 남았는지 확인 후 **삭제/amend**. 요청 밖·정책 무관 dead code만 언급. **정책 불일치·교체 잔존 → STOP §4 삭제**
- Entity·스키마 후 ERD 개선 → `harness-follow-up.md` 💡 ERD
- Must Have급 완료 / 사용자 요청 시 후속 제안 → `harness-follow-up.md`
- 「다른 이슈로」범위 미루기 → `harness-follow-up.md` ✅ Defer (**이슈만 만들고 끝내지 않음**)

## 금지 (요약)

- 이슈 번호 없는 브랜치명 — CONTRIBUTING 위반
- 문서·스펙·결정과 충돌하는 값을 묻지 않고 구현·커밋 — STOP §1
- **교체 후 구 경로·상수·‘현행’ 문서 방치** — STOP §4 (dev에서 호환 레이어 불필요)
- **프론트 대응이 필요한 API 계약 변경에 `Breaking-Change-Reason` 트레일러 누락** — STOP §5 (optional 필드 추가·enum 값 추가도 대상)
- `git push --force` (main/master), `rm -rf`, 운영 DB 파괴
- `.env`·API 키를 코드·커밋에 포함

## 도메인·배포 (확정 — 재질문 금지)

| 도메인 | 호스팅 | 이 repo |
|--------|--------|---------|
| `tripfit.online` | Vercel (프론트) | **없음** — `FRONTEND_IMAGE`·frontend 컨테이너 금지 |
| `api.tripfit.online` | EC2 Nginx + Spring Boot | `deploy/app/`, `deploy/nginx/` |

API: `https://api.tripfit.online` · SSOT: `docs/decisions/002-domain-split-vercel-api.md`, `deploy/README.md`
