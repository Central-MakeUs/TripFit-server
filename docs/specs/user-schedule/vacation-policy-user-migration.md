# 연차·휴일 정보 — RegularSchedule → User 이동

> 상태: **Implemented** (`#52` Closed, PR #111 merge 완료) — 원 승인일 2026-08-16
> **2026-08-19 amend:** `PATCH /users/schedule/vacation-policy`의 4개 필드가 **전부 필수**가 됐다(구 "생략 시 기본값 대체" 폐지, 누락 시 400). 그중 `vacationApplyPeriod`(사전 신청일)는 **사전 일정 입력 완료 마커**로 쓰여, 값이 지워지면 사용자가 최초 입력 상태로 되돌아간다 — 전체 교체 요청에서 값이 빠지면 마커가 지워지므로 필수화가 그 회귀를 막는다. SSOT: [`pre-schedule-entry-flow.md`](pre-schedule-entry-flow.md)
> MVP: In scope (`docs/product/mvp.md` "개인별 정기 일정 및 연차·휴일 정보 설정")
> 관련 BR: N/A (스키마 리팩토링, 정책 변경 없음)

## 목표

`maxVacationDays`·`vacationApplyPeriod`·`halfVacationAvailable`·`holidayRest` 4개 필드는 "사람 1명"에게 붙는 값인데 `RegularSchedule`(정기 일정, user당 N행) 각 행에 저장돼 있어 데이터 모델상 행마다 다른 값을 가질 수 있는 구조였다. 이 4개를 `User`로 옮겨, 스키마가 실제 의미("사용자당 하나의 연차 정책")를 강제하도록 한다.

## 배경

- `#52` 이슈, 발견 계기는 `#105`(연차/반차 자동 반영). 상세 배경은 `#52` 본문 참고.
- 프론트(`TripFit-client`)는 이미 이 4개를 "정기 일정과 분리된" 개념으로 다루고 있음을 확인했다 — `apps/web/components/basic-info/basicInfo.const.ts`의 마법사 스텝 그룹이 `['annualLeaveCount', 'leaveNoticeDays', 'includeHalfDayHoliday']`로 `regularScheduleDetail`과 이미 별도 그룹이다. 다만 저장 시(`useSaveRegularSchedule.ts`) 백엔드에 맞춰 이 값을 모든 `RegularSchedule` 요청에 중복으로 실어 보내고, 읽을 때도 `items[0]`(첫 번째 행) 기준으로 역산한다(`mapRegularSchedule.ts`) — 이 역시 백엔드 스키마 위치에 맞춘 우회다.
- 현재 main 기준 대표 행 우회 로직은 `RegularSchedule.policySource`/`restsOnHolidays`(static) 한 곳에 모여 있고, `RecommendationEngine`(연차 계산)·`ScheduleCalendarResolver`(공휴일 판정)가 이를 소비한다.
- **범위:** 이번 스펙은 **백엔드 코드**(엔티티·API 계약·`Breaking-Change-Reason`)까지만 다룬다. 상용 DB의 실 데이터 컬럼 이동(마이그레이션 실행)은 프론트 대응이 끝난 뒤 별도 진행 — 로컬/dev는 기존과 동일하게 `ddl-auto`로 스키마를 다시 만든다.

## 변경 범위 (기존 Approved 스펙 amend)

`schedule-unified.md`(필드 위치) · `schedule-holiday-rest.md`(H1, `policySource` 소비) · `trip-recommendation-algorithm.md`(연차 시뮬레이션)에 각각 아래 delta가 적용된다.

### ADDED

- `User` 엔티티: `maxVacationDays`(int, default 2, 0~10) · `vacationApplyPeriod`(enum, nullable) · `halfVacationAvailable`(boolean, default false) · `holidayRest`(boolean, default true)
- `GET /api/v1/users/schedule/vacation-policy` — 4개 필드 조회
- `PATCH /api/v1/users/schedule/vacation-policy` — 4개 필드 전체 교체
- `VacationPolicyResponse`(신규 DTO) · `UpdateVacationPolicyRequest`(신규 DTO)

### MODIFIED

- `RecommendationEngine.applyVacationSimulation`/`vacationDaysForSpan`/`matchingRegulars` — `RegularSchedule.policySource(regulars)`로 대표 행을 추론하던 것을, 호출부에서 이미 로드된 `User`(`TripMember.getUser()`, N+1 없음)를 직접 전달받아 사용하도록 변경
- `ScheduleCalendarResolver.resolve(...)` — `regulars`에서 `restsOnHolidays`를 추론하던 것을 `holidayRest`(또는 `User`)를 파라미터로 받도록 변경. 호출부 `ScheduleService.getCalendar`류는 `userLookupService.requireUser(userId)`로 `User`를 추가 조회

### REMOVED

- `RegularSchedule`: `maxVacationDays`·`vacationApplyPeriod`·`halfVacationAvailable`·`holidayRest` 컬럼 + `DEFAULT_MAX_VACATION_DAYS`·`MAX_VACATION_DAYS_LIMIT` 상수 + `policySource`·`restsOnHolidays` static 메서드
- `CreateRegularScheduleRequest`/`UpdateRegularScheduleRequest`/`RegularScheduleResponse`: 4개 필드 전부 제거
- `schedule-holiday-rest.md`의 "`#52` 완료 시 함께 정리" 블록쿼트(구현 완료 후 실제로 정리됨을 반영해 문구 갱신)

## 요구사항

### Must Have

- [ ] `User` 엔티티에 4개 필드 추가 (기본값은 현재 `RegularSchedule`과 동일)
- [ ] `RegularSchedule`에서 4개 필드·관련 상수·`policySource`/`restsOnHolidays` 제거
- [ ] `CreateRegularScheduleRequest`/`UpdateRegularScheduleRequest`/`RegularScheduleResponse`에서 4개 필드 제거
- [ ] 신규 `GET`·`PATCH /api/v1/users/schedule/vacation-policy` — `UserScheduleController`에 추가, PATCH는 4개 필드 전체 교체(부분 patch 아님). ~~"생략 시 기본값" 시맨틱~~ → **2026-08-19 전부 필수·누락 시 400**으로 개정 ([`pre-schedule-entry-flow.md`](pre-schedule-entry-flow.md) P-1)
- [x] ~~**신규 엔드포인트는 `isAllFree`를 건드리지 않는다**~~ (2026-08-18 `#113`으로 `isAllFree` 자체가 삭제돼 해당 없음) — `clearAllFreeOnScheduleAdded`/`markAllFreeIfNoSchedules` 호출 금지. 연차 설정 저장은 "일정 등록"이 아니므로 방 입장 조건(`hasPreSchedule OR isAllFree`)에 영향을 주면 안 된다. 넣으면 이미 방에 있는 사용자가 일정만 수정해도 `isAllFree`가 풀려 `SCHEDULE_ENTRY_REQUIRED`로 튕긴다(재입장 join이 뒤따르지 않는 `GroupCalendarSection`·`RoomDetailSection` 수정 흐름)
- [ ] `UserSummaryResponse`에 **연차 4개 값 자체는 넣지 않는다** — 같은 값이 두 곳에 생기면 연차 저장 후 `/auth/me` 캐시가 낡는다. (2026-08-19: 값이 아니라 **저장 여부**만 `hasCompletedPreSchedule` boolean으로 노출된다 — 중복 소스 문제는 그대로 없다)
- [ ] `RecommendationEngine`·`ScheduleCalendarResolver` 호출부를 `User` 직접 참조로 교체 (대표 행 추론 로직 완전 삭제)
- [ ] `Breaking-Change-Reason` 트레일러 (커밋)
- [ ] `docs/architecture/erd.md` — `users`/`regular_schedule` 테이블 갱신
- [ ] `schedule-holiday-rest.md`·`trip-recommendation-algorithm.md`의 "#52 완료 시 정리" 안내문을 완료 상태로 갱신
- [ ] `#52` 이슈 완료 기준 체크

### Nice to Have

- [ ] (없음)

### Out of Scope (이번 스펙에서 하지 않음)

- 상용 DB 실 데이터 마이그레이션 실행 — 프론트 대응 완료 후 별도 진행(이 스펙은 코드 계약까지만)
- `vacationApplyPeriod` 실제 계산 로직 반영 — `#105`에서 이미 Out of Scope로 확정, 이번에도 저장·응답만 이동하고 계산 로직은 손대지 않음
- 프론트(`TripFit-client`) 코드 변경 — 이 저장소 범위 밖, 프론트가 직접 대응

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| GET | `/api/v1/users/schedule/vacation-policy` | JWT | **(신규)** 연차·휴일 정보 조회 |
| PATCH | `/api/v1/users/schedule/vacation-policy` | JWT | **(신규)** 위 설정 전체 교체 |
| GET | `/api/v1/users/schedule/regular` | JWT | **(계약 변경)** 응답에서 4개 필드 제거 |
| POST | `/api/v1/users/schedule/regular` | JWT | **(계약 변경)** 요청에서 4개 필드 제거 — title/daysOfWeek/startTime/endTime만 |
| PATCH | `/api/v1/users/schedule/regular/{id}` | JWT | **(계약 변경)** 상동 |

`/auth/login`·`/auth/me`·`/users/profile`·`/users/onboarding/name`의 `UserSummaryResponse`는 **변경 없음**.

PATCH 요청 / GET·PATCH 응답(`data`) 공통 형태:

```json
{
  "maxVacationDays": 2,
  "vacationApplyPeriod": "ONE_WEEK_BEFORE",
  "halfVacationAvailable": false,
  "holidayRest": true
}
```

**프론트 데이터 로딩 대응:** 지금 클라이언트는 `GET /users/schedule/regular` 응답의 `items[0]`에서 이 값을 역산한다(`MyScheduleSection.tsx`·`mapRegularSchedule.ts`의 `getLeaveNoticeDaysFromRegularSchedules` 등). 변경 후에는 정기 일정 목록과 **나란히** `GET /users/schedule/vacation-policy`를 호출해 읽는다.

## 데이터 모델

- ERD 참조: `docs/architecture/erd.md` `users`/`regular_schedule` 절
- `users` 테이블에 4개 컬럼 추가(컬럼명은 `RegularSchedule`과 동일하게 `max_vacation_days`·`vacation_apply_period`·`is_half_vacation_available`·`is_holiday_rest`), `regular_schedule`에서 동일 4개 컬럼 제거
- 로컬/dev: `ddl-auto`로 스키마 재생성 (마이그레이션 파일 작성 금지 — `harness-workflow.md` STOP §3)
- 상용: 이 스펙 범위 밖 (별도 진행)

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| 해당 없음 | 필드 위치만 이동, 값 계산 로직·정책 자체는 무변경 | — |

## 검증 시나리오

### 정상

- [ ] `PATCH /users/schedule/vacation-policy` 저장 후 `GET /users/schedule/vacation-policy`가 같은 값 반환
- [ ] 신규 유저(첫 로그인) 기본값 — `maxVacationDays=2`·`halfVacationAvailable=false`·`holidayRest=true`·`vacationApplyPeriod=null`

### 피그마 화면 흐름 (2026-08-16 확인)

- [x] ~~**회원가입 — "정기 일정 없어요"**: 연차 스텝을 묻지 않으므로 `vacation-policy`를 호출하지 않는다~~ → **2026-08-19 폐기.** 회원가입 온보딩에서 사전 일정 단계 자체가 사라졌다(이름 → Google 캘린더까지). 정기 일정 0건이어도 추천·달력에 영향이 없다는 부분(`applyVacationSimulation`의 `regulars.isEmpty()` early return)은 그대로 유효하다
- [x] ~~**회원가입 — 건너뛰기**: 아무 API도 호출되지 않고 입장 조건·기본값 모두 기존과 동일~~ → **2026-08-19 폐기.** 회원가입에서 사전 일정을 받지 않으므로 이 시나리오가 성립하지 않는다. 신규 유저의 4개 값은 기본값(`vacationApplyPeriod=null` 포함)으로 남고, 그 상태가 곧 **최초 입력** 판정이다
- [x] ~~**방 입장 — 정기 0건 + "없어요" + 개별 일정 미입력**: 연차 스텝도 **묻지 않는다**~~ → **2026-08-19 폐기.** `없어요` 경로도 연차·휴일 정보를 거치며, 이때 저장되는 **사전 신청일**이 사전 일정 입력 완료 마커가 된다. 일정 row가 0건이어도 `hasCompletedPreSchedule=true`이고 `activate`를 통과한다 ([`pre-schedule-entry-flow.md`](pre-schedule-entry-flow.md))
- [x] **방 입장 — 갱신 입력**(구 "정기 1건 이상" 경로): `GET /users/schedule/regular`와 `GET /users/schedule/vacation-policy`를 나란히 호출해 기존 값을 프리필, 수정 시 각각 저장. 분기 기준은 **정기 일정 건수가 아니라 `hasCompletedPreSchedule`**(=사전 신청일 저장 여부)이다 — 2026-08-19 변경. 연차를 저장하면 `hasCompletedPreSchedule`이 `true`가 된다(구 `hasPreSchedule`·`isAllFree`는 각각 2026-08-19·`#113`으로 삭제)
- [x] ~~**이미 참여 중인 방에서 일정만 수정**: `vacation-policy` 저장 후에도 `isAllFree`가 유지되어 `SCHEDULE_ENTRY_REQUIRED`로 튕기지 않음~~ → 2026-08-18 `#113`으로 전역 게이트가 삭제돼 이 시나리오 자체가 성립하지 않는다(연차 저장이 방 접근에 영향을 줄 경로 없음)
- [ ] **정기 일정 전체 삭제**: 연차 설정은 `users`에 남는다(기존엔 행과 함께 사라짐). 재등록 시 이전 설정이 그대로 조회됨
- [ ] 정기 일정 여러 개 등록된 유저 — 연차 시뮬레이션·공휴일 판정이 기존과 동일한 결과 (회귀 없음, `RecommendationEngineTestSetScenarioTest` 등 기존 테스트 통과)
- [ ] 정기 일정이 0개인 유저도 `User` 필드로 연차 시뮬레이션·공휴일 판정 정상 동작 (기존엔 `regulars.isEmpty()`면 시뮬레이션 스킵 — 이 조건은 유지, 단 값 조회는 `User`에서)

### 엣지 · 실패

- [ ] `maxVacationDays` 0~10 범위 밖 → 400 INVALID_INPUT
- [ ] `RegularSchedule` CRUD 요청에 옛 4개 필드를 보내도(구 프론트 캐시 등) 무시(역직렬화 시 알 수 없는 필드 — 기존 Jackson 설정 확인)되고 에러 없이 처리되는지 확인

### 수동 / 통합 (해당 시)

- [ ] `docs/api/openapi.json` 재생성 후 4개 필드 위치가 실제로 바뀌었는지 확인 (harness-workflow.md STOP §1-6)

## 완료 기준

- [ ] `./gradlew test` 통과
- [ ] `./gradlew build` 성공
- [ ] OpenAPI/Swagger 반영
- [ ] `REMOVED` 항목 실제 삭제 확인 (`policySource`/`restsOnHolidays`/구 필드 전부)
- [ ] `Breaking-Change-Reason` 트레일러 포함 커밋

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| `vacation-policy` 엔드포인트 위치·이름 | 확정 (본 스펙) | `UserController`(`/users/profile`)가 아니라 `UserScheduleController`(`/users/schedule/*`)에 배치 — 프론트 UI가 이미 정기 일정과 같은 "기본 정보 관리" 마법사 흐름에서 다루고, 마이페이지 프로필(성·이름·알림)과는 별개 화면이기 때문 |
| 전용 GET vs `UserSummaryResponse` 포함 | 확정 (본 스펙, 2026-08-16 피그마 흐름 검토) | 전용 `GET`. 두 곳에 두면 연차 저장 후 `/auth/me` 캐시가 낡고, 전용 GET이면 `/auth` 계약이 무변경이라 프론트 대응 범위도 작다 |
| 백엔드 기본 `holidayRest=true` vs 클라 기본 `holiday:false` | 확인됨 — 무해 | 클라이언트 `DEFAULT_BASIC_INFO_VALUE`는 `holiday:false`지만 마법사가 항상 명시값을 보내므로 기본값이 드러나는 건 "연차 스텝 자체를 건너뛴 경우"뿐이고, 그때는 정기 일정 row가 0건이라 판정에 쓰이지 않는다. 엔티티 기본값은 현행(`true`) 유지 |
| 연차 노출 게이트를 서버에도 걸지 여부 | **확정 — UI에서만** (2026-08-16) | 화면상 "정기 일정 + 연차"는 한 덩어리라 정기를 입력·수정할 때만 연차가 노출된다. 그러나 **서버 `PATCH /vacation-policy`에는 정기 일정 존재 조건을 걸지 않는다** — ① 같은 형태의 선행 게이트(BR-USER-006 `REGULAR_SCHEDULE_REQUIRED`)를 이미 D-BR006-5로 폐기한 전례가 있고 ② 게이트를 걸면 "정기를 전부 지웠다 재등록해도 연차 설정이 보존된다"는 본 이동의 이점이 사라진다 |
| 정기 0건 사용자의 연차 값 | 보존 · 미노출 · 미사용 | 정기를 전부 삭제해도 연차 값은 `users`에 남지만, UI 게이트 때문에 조회·수정 화면이 뜨지 않는다. 계산에도 쓰이지 않는다(`applyVacationSimulation`은 `regulars.isEmpty()`면 early return, 공휴일 판정도 제외할 정기가 없음) → **무해**. 정기를 다시 등록하면 그대로 되살아난다 |
| 구 가정 "방 입장 흐름에서 정기 0건일 때 연차 답변이 버려짐" | **철회 (2026-08-16) → 2026-08-19 상황 변경** — 이제 `없어요` 경로도 연차를 묻고 그 값이 입력 완료 마커가 된다 | 초안은 클라이언트 `basic-info/index.tsx`가 "없어요"에도 연차 3스텝을 진행한다고 보고 이를 본 변경으로 해소되는 결함으로 적었으나, Figma 흐름 확인 결과 **방 입장에서도 "없어요"면 연차를 묻지 않는다**(연차=정기와 한 덩어리). 따라서 버려지는 답변 자체가 없다 — 해당 항목은 결함이 아니었다 |
| 상용 DB 데이터 이동 방식(컬럼 이동 시 기존 값 보존) | [미정] — 별도 트랙 | 프론트 대응 완료 후 진행. 그 시점에 `AGENTS.md` "상용 보존 데이터 없음" 전제 재검토와 함께 별도 결정 필요 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-16 | 초안 |
| 2026-08-16 | **Figma 흐름 재확인 후 amend** — ① 방 입장에서도 "없어요"면 **연차를 묻지 않음**(연차=정기와 한 덩어리)으로 검증 시나리오 정정, 초안의 "정기 0건일 때 연차 답변이 버려지던 결함" 가정 **철회** ② 연차 노출 게이트는 **UI에서만**(서버 `PATCH /vacation-policy`에 정기 선행 조건 없음) 확정 ③ 정기 0건 사용자의 연차 값은 보존·미노출·계산 미사용임을 명시 ④ 정기 1건 이상 경로(`regular` + `vacation-policy` 병렬 조회) 시나리오 추가 |
| 2026-08-16 | 피그마 화면 흐름(회원가입·방 입장) 대조 후 amend — ① `UserSummaryResponse` 추가 대신 **전용 `GET`/`PATCH /users/schedule/vacation-policy`** ② 신규 엔드포인트의 **`isAllFree` 미접촉**을 Must Have로 명시 ③ 흐름별 검증 시나리오 추가 |
