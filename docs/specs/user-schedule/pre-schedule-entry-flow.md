# 사전 일정 입력 — 최초/갱신 판정과 진입 플로우

> 상태: **Approved** (2026-08-19, 사용자 승인) — 구현 완료·merge 대기 (브랜치 `feat/112-pre-schedule-entry-flow`, base `feat/114-join-schedule-pending`)
> MVP: In scope (`docs/product/mvp.md` "개인별 정기 일정 및 연차·휴일 정보 설정")
> 관련 BR: **BR-USER-007 개정** (방 입장 전 사전 일정 입력 완료 필요) · BR-USER-006 무변경

## 목표

사전 일정 입력을 **최초 입력 / 갱신 입력** 두 상태로 나누고, 그 판정을 **`사전 신청일`(`users.vacation_apply_period`) 값의 존재 여부 단 하나**로 확정한다. 정기·개별 일정의 데이터 존재 여부는 판정에서 완전히 제외한다.

## 배경

- QA 결과 **회원가입 단계에서 일정을 받는 것보다, 실제 일정 정보가 필요한 시점(여행방 입장·수정·마이페이지)에 받는 쪽이 사용성이 좋다**는 기획 판단(2026-08-19). 회원가입에서 사전 일정 단계를 빼고, 모든 일정 입력 플로우에서 **건너뛰기를 없앤다**.
- 기존 확정(2026-08-16)은 **정기 일정 유무**로 "입력 플로우 vs 확인 플로우"를 갈랐다. 그러나 정기·개별 일정은 사용자에 따라 실제 데이터가 0건일 수 있어, **"아직 입력을 안 한 사람"과 "입력을 끝냈지만 막힌 일정이 없는 사람"을 구분할 수 없다.** 이 기획을 엎고 단일 마커로 전환한다.
- `사전 신청일`이 마커가 될 수 있는 이유: 연차·휴일 정보 4개 값 중 **유일하게 nullable**이다. 나머지 3개(연차 일수 `2` / 반차 `false` / 공휴일 `true`)는 기본값이 항상 채워져 있어 "저장한 적 있음"을 구분할 수 없다.
- 이 값을 바꾸는 경로는 서버 전체에 **2개뿐**이다 — `PATCH /users/schedule/vacation-policy`, 회원 탈퇴 시 초기화. 갱신 → 최초로 되돌아가는 길은 **탈퇴뿐**이며 이는 의도된 동작(재가입 = 신규 가입 경험, 2026-08-19 확인).
- 선행 스펙: [`vacation-policy-user-migration.md`](vacation-policy-user-migration.md)(`#52` — 4개 값을 `User`로 이동) · [`schedule-participation-onboarding.md`](../trip/schedule-participation-onboarding.md)(D-JOIN-TRIP-FLOW) · [`trip-join-schedule-gate.md`](../trip/trip-join-schedule-gate.md)(`#113`/`#114`)

## 판정 규칙 (SSOT)

```
users.vacation_apply_period IS NULL      → 최초 입력
users.vacation_apply_period IS NOT NULL  → 갱신 입력
```

- 정기 일정 행 수 · 개별 일정 행 수 · 구글 캘린더 연동 여부 · 이름 입력 여부를 **읽지 않는다.**
- **"정기·개별이 0건인데 모든 날 free한 사람"** = `사전 신청일 있음` + `일정 0건`. 앞의 값이 "입력을 완료했는가"에, 뒤의 사실이 "막힌 날이 있는가"에 각각 답한다 — 두 질문을 하나의 값으로 합치지 않는다.
**4가지 조합 전수 확인 (2026-08-19 사용자 확인):**

| `사전 신청일` | 정기·개별 일정 | 판정 | 첫 화면 |
|---|---|---|---|
| 있음 | 있음 | **갱신** | `일정 변경이 있나요?` |
| 있음 | 없음 | **갱신** | `일정 변경이 있나요?` |
| 없음 | 없음 | **최초** | `정기 일정이 있나요?` |
| 없음 | 있음 | **최초** | `정기 일정이 있나요?` |

일정 행 수는 어느 칸에도 영향을 주지 않는다 — 판정식이 `사전 신청일` 하나만 읽으므로 4가지가 자동으로 성립한다.

- 마이페이지에서 **정기 일정만 저장하고 이탈**하면 `사전 신청일`이 없으므로 **최초 입력**이다(2026-08-19 의도 확인). 이때 최초 플로우의 `없어요`를 선택하면 남아 있던 정기 일정을 **전부 삭제**한다(아래 S-3).

## 진입 경로별 시작 화면 (기획 「사전 일정 입력 최종 플로우」 §6 그대로)

**진입 경로는 3개다 — 여행방 입장 · 여행방 내 수정 · 마이페이지.** 회원가입에는 사전 일정 단계가 없다. 어느 경로에도 **건너뛰기 버튼이 없다.**

| 진입 경로 | 입력 상태 | 시작 화면 | 플로우 | `다음` 버튼 |
|---|---|---|---|---|
| **여행방 입장** | 최초 | `정기 일정이 있나요?` | 정기 여부 → 정기 일정(있어요일 때) → 연차·휴일 정보 → 개별 일정 → 입장(`activate`) | **입력 후 활성화** |
| **여행방 입장** | 갱신 | `일정 변경이 있나요?` | 변경 여부(안내) → 정기 일정 수정 → 연차·휴일 정보 → 개별 일정 → 입장(`activate`) | 기본 활성화 |
| **여행방 내 수정** | 갱신 | 수정 메뉴 | 정기 일정 / 연차·휴일 정보 또는 개별 일정 수정 | 기본 활성화 |
| **마이페이지 — `기본정보 관리`** | 최초/갱신 | `기본정보 관리` | 정기 일정 → 연차·휴일 정보 (개별 일정 없음) | 기본 활성화 |
| **마이페이지 — `내 일정 입력하기`** | 최초/갱신 | `내 일정 입력하기` | 개별 일정 입력/수정 | 기본 활성화 |

- **최초/갱신 질문 화면(`정기 일정이 있나요?` / `일정 변경이 있나요?`)은 여행방 입장 경로에만 있다.** 마이페이지·여행방 내 수정은 입력 상태와 무관하게 해당 화면으로 바로 들어가고 `다음`도 기본 활성화다 — 최초 입력 사용자라고 해서 마이페이지에서 질문 화면을 띄우지 않는다.
- **`다음` 비활성화는 여행방 입장 × 최초 입력 조합에서만** 쓴다(현재 동작 유지). 서버 쪽 대응 게이트는 `activate`의 403 `PRE_SCHEDULE_REQUIRED` 하나다.

**두 가지 경계 케이스 (기획 명시):**

| # | 경로 | 판정 |
|---|---|---|
| 1 | 마이페이지 `기본정보 관리`에서 정기+연차를 저장하고 여행방에 입장 | **갱신 입력** — 연차 저장으로 사전 신청일이 채워졌다 |
| 2 | 마이페이지 `내 일정 입력하기`(개별 일정)만 하고 여행방에 입장 | **최초 입력** — 개별 일정은 판정에 쓰이지 않는다 |

## 변경 범위 (기존 Approved 스펙 amend)

### ADDED

- `UserSummaryResponse.hasCompletedPreSchedule` — `사전 신청일 != null` 파생 boolean (DB 컬럼 아님)
- `DELETE /api/v1/users/schedule/regular` — 본인 정기 일정 **일괄 삭제**(멱등)
- `UserErrorCode.PRE_SCHEDULE_REQUIRED` (403) — `activate` 시 사전 일정 입력 미완료
- `User.resetVacationPolicy()` — 탈퇴 스크럽 전용 초기화
- `docs/product/glossary.md` — 정기 일정 / 개별 일정 / 연차·휴일 정보 / 최초 입력 / 갱신 입력 (+ 구 용어 매핑 «개인 일정 → 개별 일정» · «연차 조건 → 연차·휴일 정보»)

### MODIFIED

- `UpdateVacationPolicyRequest` 4개 필드: **선택(생략 시 기본값 대체)** → **전부 필수**(`@NotNull`, `nullable = false`, 미전송 시 400)
- `User.applyVacationPolicy(Integer, …, Boolean, Boolean)`(null → 기본값 대체) → **null 대체 없는 시그니처**
- `TripCommandService.activateMembership` — `SCHEDULE_PENDING → ACTIVE` 전환 시 **사전 일정 입력 완료 게이트** 추가
- `UserSummaryService.toSummary` — 정기 EXISTS 쿼리 대신 `user.getVacationApplyPeriod() != null` 사용
- **BR-USER-007** — 방 진입 → 일정 플로우 → `activate` 절차에 **"`activate`에는 사전 일정 입력 완료가 필요하다"** 조건 추가
- `schedule-participation-onboarding.md` D-JOIN-TRIP-FLOW — 「정기 일정 유무 2분기」 → 「최초/갱신 2분기」
- `user-onboarding.md` — 회원가입 온보딩 단계에서 **② 사전 일정** 제거(캘린더 연동만 남김), 건너뛰기 서술 정리

### REMOVED

- `UserSummaryResponse.hasRegularSchedule` 필드 + `@Schema` + Controller `@ApiResponse` 예시 JSON **6종**(`AuthController` 2 · `UserController` 2 · `GoogleCalendarController` 2)
- `UserSummaryResponse.hasPreSchedule` 필드 + `@Schema` + 위 예시 JSON 6종 — 원래 용도(전역 입장 게이트 `canEnterRoom = isAllFree OR hasPreSchedule`)가 `#113`에서 삭제된 뒤 **서버 어디에서도 소비되지 않는다**
- `UserSummaryService.hasRegularSchedule(UUID)` · `hasPreSchedule(UUID)` 메서드 + `RegularScheduleRepository`·`PersonalScheduleRepository` 의존성
- `RegularScheduleRepository.existsByUserId` · `PersonalScheduleRepository.existsByUserId` — 위 두 파생 필드 전용이라 함께 죽는다
- `TripServiceTest`의 `verify(regularScheduleRepository, never()).existsByUserId(...)` assert 2건
- `schedule-participation-onboarding.md` D-BR006-C의 `hasRegularSchedule` 파생 규칙 · 「분기 판단에 `hasPreSchedule`을 쓰지 말 것」 경고문(분기 자체가 사라짐)
- `trip-join-schedule-gate.md` 값별 역할 표의 `hasRegularSchedule` 행 + 완료 기준 「`hasRegularSchedule`은 유지」 항목
- `user-onboarding.md`·`fe-context/user/user-onboarding.md`의 **회원가입 사전 일정 단계·건너뛰기** 서술
- `schedule-participation-onboarding.md`에 남아 있는 **구 `is_all_free = true` 표**(`#113`에서 컬럼이 삭제됐는데 현행 계약처럼 읽히는 잔존 문구 — STOP §4)

## 요구사항

### Must Have

- [x] **P-1** `UpdateVacationPolicyRequest` 4개 필드 전부 필수화 — `@NotNull` + `@Schema(nullable = false)`, 미전송 시 400 `INVALID_INPUT`
- [x] **P-2** `User.applyVacationPolicy`에서 null 기본값 대체 로직 제거 + 탈퇴 초기화용 `resetVacationPolicy()` 분리 (`scrubPiiForWithdrawal`이 호출)
- [x] **P-3** `UserSummaryResponse` 정리 — `hasRegularSchedule`·`hasPreSchedule` **둘 다 삭제**, `hasCompletedPreSchedule` **하나만** 추가 (`#112`/`#115` 대체). 파생 EXISTS 쿼리 2개가 사라져 login·me·profile 응답마다 DB 조회가 2건 줄어든다
- [x] **P-4** `DELETE /api/v1/users/schedule/regular` 신규 — 본인 정기 일정 전체 삭제, 0건이어도 204(멱등)
- [x] **P-5** `activate` 게이트 — `SCHEDULE_PENDING → ACTIVE` 전환 시 `사전 신청일`이 null이면 403 `PRE_SCHEDULE_REQUIRED`. **이미 `ACTIVE`인 멱등 재호출은 게이트를 통과**(상태 전환이 없으므로)
- [x] **P-6** `UserErrorCode.PRE_SCHEDULE_REQUIRED` 추가 + `TripController.activate` `@ApiResponse` 403 예시 갱신
- [x] **P-7** `docs/product/glossary.md`에 5개 용어 등록
- [x] **P-8** 스펙·BR·프론트 가이드 동기화 (아래 "문서 동기화" 표)
- [x] **P-9** **용어 전면 통일** — Swagger 설명(`@Schema`/`@Operation`/Javadoc) · 코드 주석 · `docs/` 전체를 용어집 기준으로 치환 (아래 "용어 통일 범위")
- [x] **P-10** 커밋에 `Breaking-Change-Reason` 트레일러 (계약 변경 4건)
- [x] **P-11** `REMOVED` 항목 실제 삭제 확인 (grep 0건)

### Nice to Have

- [ ] (없음)

### Out of Scope (이번 스펙에서 하지 않음)

- **프론트(`TripFit-client`) 코드 변경** — 이 저장소 범위 밖
- **추천 알고리즘 변경** — "ACTIVE 멤버 전원 = 응답 참여자", "일정 0건 = 모든 슬롯 가능" 판정은 **그대로**. 이번 게이트는 그 계산의 전제를 서버가 보장하게 만들 뿐이다
- **마이페이지 `기본정보 관리`에서 정기+연차를 한 번에 저장** — 프론트 화면 규칙
- **상용 DB 마이그레이션** — 보존 데이터 없음(`ddl-auto` + 필요 시 리셋)
- `사전 신청일`을 실제 연차 계산에 반영 — `#105`에서 이미 Out of Scope

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| DELETE | `/api/v1/users/schedule/regular` | JWT | **(신규)** 본인 정기 일정 전체 삭제. 0건이어도 204 |
| PATCH | `/api/v1/users/schedule/vacation-policy` | JWT | **(계약 변경)** 4개 필드 전부 필수 — 하나라도 누락 시 400 |
| POST | `/api/v1/trips/{tripId}/activate` | JWT | **(계약 변경)** 사전 일정 입력 미완료 시 403 `PRE_SCHEDULE_REQUIRED` |
| POST | `/api/v1/auth/login` · GET `/auth/me` · PATCH `/users/profile` · PATCH `/users/onboarding/name` · POST·DELETE `/users/google-calendar` | JWT | **(계약 변경)** `hasRegularSchedule` 삭제 → `hasCompletedPreSchedule` 추가 |

`PATCH /users/schedule/vacation-policy` 요청 — **4개 전부 필수**:

```json
{
  "maxVacationDays": 2,
  "vacationApplyPeriod": "ONE_WEEK_BEFORE",
  "halfVacationAvailable": false,
  "holidayRest": true
}
```

필드 누락 (400):

```json
{
  "code": "INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다.",
  "errors": [{ "field": "vacationApplyPeriod", "reason": "필수 값입니다." }]
}
```

`activate` 실패 (403):

```json
{
  "code": "PRE_SCHEDULE_REQUIRED",
  "message": "사전 일정 입력을 완료해야 여행방에 입장할 수 있습니다."
}
```

요약 응답(`UserSummaryResponse`) 변화:

```diff
- "hasRegularSchedule": false,
- "hasPreSchedule": false,
+ "hasCompletedPreSchedule": false,
```

## 데이터 모델

**스키마 변경 없음.** 신규 컬럼·테이블 없이 기존 `users.vacation_apply_period`(nullable enum)를 마커로 사용한다. `docs/architecture/erd.md`는 해당 컬럼 설명만 갱신(“최초/갱신 판정 마커”).

| 값 | 의미 |
|---|---|
| `NULL` | 사전 일정 입력 미완료 (최초 입력) |
| `ANY` / `ONE_WEEK_BEFORE` / `TWO_WEEKS_BEFORE` / `ONE_MONTH_BEFORE` | 입력 완료 (갱신 입력) |

`ANY`(상관없음)와 `NULL`(미설정)은 **서로 다른 값**이라는 기존 규칙이 이 판정의 전제다 — 프론트 가이드에 이미 명문화돼 있으며 유지한다.

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| **BR-USER-007** (개정) | 방 진입(`POST /trips` · `POST /trips/join`)=`SCHEDULE_PENDING` → 일정 플로우 → `activate`=`ACTIVE`. **`activate`는 `사전 신청일`이 저장돼 있어야 통과** | `TripCommandService.activateMembership` |
| BR-USER-006 (무변경) | 방 안 리소스 접근 = 그 방의 `trip_member.status = ACTIVE` | `TripAuthorizationInterceptor` |
| BR-USER-004 (무변경) | 탈퇴 시 연차 정책 기본값 복귀 → 재가입 시 **최초 입력** | `User.scrubPiiForWithdrawal` |

## 검증 시나리오

### 정상

- [x] 신규 가입 직후 `GET /auth/me` → `hasCompletedPreSchedule = false` (최초 입력)
- [x] `PATCH /vacation-policy`로 4개 값 저장 → `GET /auth/me` → `hasCompletedPreSchedule = true` (갱신 입력)
- [x] `없어요` 경로: `DELETE /regular` → `PATCH /vacation-policy` → 개별 일정 미입력 → `activate` **성공**(일정 0건이어도 통과)
- [x] `있어요` 경로: 정기 저장 → 연차 저장 → 개별 저장 → `activate` 성공
- [x] 개별 일정만 저장한 사용자 → `hasCompletedPreSchedule = false` (기획 case 2)
- [ ] 마이페이지 기본정보 관리 완주 후 여행방 입장 → 갱신 입력으로 진입 (기획 case 1)

### 엣지 · 실패

- [x] `PATCH /vacation-policy`에서 `vacationApplyPeriod` 누락 → **400**, 저장 전 값 보존(덮어쓰기 없음)
- [x] 나머지 3개 필드 각각 누락 → **400**
- [x] `사전 신청일` 미저장 상태로 `activate` → **403 `PRE_SCHEDULE_REQUIRED`**
- [x] 이미 `ACTIVE`인 멤버가 `activate` 재호출 → **200**(멱등, 게이트 미적용)
- [x] `DELETE /regular` — 정기 0건 사용자 호출 → **204**
- [x] `DELETE /regular` — 삭제 후 개별 일정 행은 **보존**되고, 진행 중(ONGOING) 여행방 달력에는 즉시 반영
- [x] 탈퇴 → 같은 소셜 계정 재로그인 → `hasCompletedPreSchedule = false` (최초로 복귀)
- [x] `DELETE /regular`는 타인 정기 일정을 지우지 않는다 (userId 스코프)

### 수동 / 통합

- [x] 생성된 `/v3/api-docs`에서 `hasRegularSchedule`이 **사라지고** `hasCompletedPreSchedule`이 노출되는지 확인 (소스 `@Schema` 존재만으로 판단 금지 — 하네스 STOP §1.6)
- [x] `hasRegularSchedule` 참조가 코드에 **0건**임을 grep으로 확인

## 용어 통일 범위

용어집(`glossary.md`)을 SSOT로 삼아 **표기**를 통일한다. **코드 식별자·API 경로·JSON 필드명은 바꾸지 않는다** — 프론트 재작업만 유발하고 얻는 것이 없다.

| 대상 | 처리 |
|------|------|
| `docs/**` 현행 서술 | "개인 일정" → **개별 일정** (22개 파일) · "연차 조건"·"연차·반차·공휴일 휴무 설정"·"근무·연차" → **연차·휴일 정보** (4개 파일) |
| Swagger 설명 — `@Schema` / `@Operation` / Javadoc | 같은 치환 (11개 파일) |
| 코드 주석 (`//`) | 같은 치환 |
| **변경 이력·Changelog의 과거 문장** | **손대지 않음** — 그 시점의 기록. 대신 용어집에 «개인 일정(구 용어) → 개별 일정» 매핑 한 줄을 남겨 과거 문서를 읽을 때 혼동이 없게 한다 |
| **바꾸지 않음** | 클래스 `PersonalSchedule`·테이블 `personal_schedule`·경로 `/users/schedule/personal`·JSON 필드명 |

## 문서 동기화

| 문서 | 갱신 내용 |
|------|-----------|
| `docs/product/glossary.md` | 5개 용어 등록 · 기존 "개인 일정" → **개별 일정** 표기 통일 · "연차 조건" → **연차·휴일 정보** |
| `specs/trip/schedule-participation-onboarding.md` | D-JOIN-TRIP-FLOW를 최초/갱신 2분기로 개정 · D-BR006-C에서 `hasRegularSchedule` 제거 · 잔존 `is_all_free` 표 삭제 |
| `specs/trip/trip-join-schedule-gate.md` | 값별 역할 표에서 `hasRegularSchedule` 행 → `hasCompletedPreSchedule` |
| `specs/user/user-onboarding.md` | 회원가입 온보딩에서 ② 사전 일정 단계·건너뛰기 제거 |
| `specs/user-schedule/vacation-policy-user-migration.md` | 4개 필드 필수화 반영 (구 "생략 시 기본값" 계약 amend) |
| `product/business-rules/user.md` | BR-USER-007 개정 + 변경 이력 |
| `product/fe-context/user-schedule/vacation-policy.md` | 필수/nullable 표 · 400 케이스 · 마커 의미 |
| `product/fe-context/user/user-onboarding.md` | 회원가입 단계 축소 · 최초/갱신 진입 규칙 |
| `product/fe-context/trip/trip-room-create-join.md` | 방 입장 플로우 2분기 그림 · 403 `PRE_SCHEDULE_REQUIRED` |
| `docs/architecture/erd.md` | `users.vacation_apply_period` 설명(판정 마커) |
| `docs/specs/README.md` | 본 스펙 등록 (**Approved** 상태로) |
| `product/flows/trip-create-join-guide.md` | 방장·참여자 2단계 통일(`#114`) 반영 + 사전 일정 플로우 최초/갱신 2분기 + `activate` 게이트 + 구 "일정 먼저 → join = 즉시 ACTIVE"·hold 서술 제거 (2026-08-19 추가 — `#114` 당시 갱신되지 않고 남아 있던 문서) |
| `specs/user/user-account-withdrawal.md` | 탈퇴 스크럽 목록에 **연차·휴일 정보 4개 값 초기화** 추가 (재가입 시 최초 입력 복귀 근거) |
| `specs/auth/auth-social-login.md` | login 응답 예시 JSON의 `hasRegularSchedule`·`hasPreSchedule` → `hasCompletedPreSchedule` |
| `architecture/api-response.md` | 403 `code` 목록에 `PRE_SCHEDULE_REQUIRED` 추가 (`SCHEDULE_*` 패턴에 걸리지 않는 이름) |
| `specs/user-schedule/schedule-state-response.md` | Superseded 문서에 남아 있던 「`hasRegularSchedule`은 유지」 판단이 현행처럼 읽히던 부분 정정 |
| `specs/trip/schedule-participation-onboarding.md` | D-JOIN-TRIP-FLOW 「대상」 표의 참여자 행에 남아 있던 구 `(hold) → 플로우 → join(ACTIVE)` 정정 |
| `.claude/rules/spring-boot-java.md` | 파생 필드 예시를 삭제된 `hasPreSchedule` → `hasCompletedPreSchedule`로 교체 |
| `product/fe-context/user/user-onboarding.md` · `fe-context/trip/trip-room-create-join.md` | **진입 경로별 시작 화면 표**(기획 §6) 추가 · 갱신 입력 × 정기 0건 막다른 길 방지 규칙 추가 |

## 완료 기준

- [x] `./gradlew test` 통과
- [x] `./gradlew build` 성공
- [x] 위 검증 시나리오 전항 확인
- [x] OpenAPI/Swagger 반영 (`/v3/api-docs` 실제 확인)
- [x] `REMOVED` 항목 실제 삭제 확인 (grep 0건)
- [ ] 커밋에 `Breaking-Change-Reason` 트레일러 포함

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| `hasPreSchedule` 존치 여부 | **확정 — 삭제** (2026-08-19) | 원래 용도인 전역 입장 게이트가 `#113`에서 사라진 뒤 서버 소비처 0건. 값 두 개를 `\|\|`로 엮다 QA 이슈 1·2가 났던 표면을 없앤다 |
| 신규 필드 이름 | **확정 — (A) `hasCompletedPreSchedule: boolean`** (2026-08-19 사용자 결정) | (B) `preScheduleEntryMode` enum은 두 값짜리라 관리 비용만 늘어 채택하지 않음 |
| 기존 dev 데이터 | 확정 | `사전 신청일`이 null인 채 이미 `ACTIVE`인 멤버는 게이트가 전환 시에만 적용되므로 영향 없음. 보존 데이터 없어 리셋도 허용 |
| `DELETE /regular` 호출 시점 | 확정 | **`없어요`를 누른 즉시**(2026-08-19 사용자 결정). 이후 이탈 시 정기가 지워진 채로 남는 것을 감수 |
| 새 이슈·브랜치 | **확정** (2026-08-19) | 새 이슈 없이 `#112` 번호 재사용 — 이 작업이 `#112`(`hasRegularSchedule` 노출)를 대체한다. 브랜치 `feat/112-pre-schedule-entry-flow`, base는 스택 tip `feat/114-join-schedule-pending`(#111·#112·#113·#114 포함). `main`에는 아직 그 4개가 없어 base로 쓸 수 없다 |
| 탈퇴 시 연차 초기화 중복 | **확인 필요** | 같은 동작을 하는 커밋 `bf933dd`(#52 후속)가 스택 상위(`feat/2-auth-refresh-redis-cookie` 이상)에 이미 있는데 우리 base에는 없다. 본 PR은 `resetVacationPolicy()`로 구현했고 `applyVacationPolicy` 시그니처도 바뀌어, 두 갈래가 만나면 `User.java`에서 충돌한다 — **우리 버전을 채택**하면 된다 |
| `#121`(인증 단독 PR)과의 관계 | 확인 완료 | 기능상 무관. 다만 로그인 응답 예시 JSON을 양쪽이 건드려 `AuthController`·`AuthServiceTest` 등에서 머지 시 충돌 가능 — 기계적 정리 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-19 | 초안 — 기획 「사전 일정 입력 최종 플로우」(2026-08-19) 기반. 정기 일정 유무 2분기를 폐기하고 `사전 신청일` 단일 마커로 전환 |
| 2026-08-19 | `hasPreSchedule` 삭제 확정 · 용어 전면 통일(P-9) 범위 추가 · 4가지 조합 전수 표 추가 |
| 2026-08-19 | **Approved + 구현 완료** — 코드·테스트·문서 반영, `./gradlew test` 503건 통과, 생성된 `/v3/api-docs`로 계약 확인 |
| 2026-08-19 | **기획 원문 재대조 + stale 문서 추가 정리** — 기획 §6 「진입 경로별 정리」 표(마이페이지 2개 진입점·여행방 내 수정 포함)를 스펙·fe-context에 명문화, `flows/trip-create-join-guide.md`(`#114` 반영 누락)·탈퇴 스펙·`api-response.md`·`auth-social-login.md` 예시 등 잔존 stale 8건 수정 |
