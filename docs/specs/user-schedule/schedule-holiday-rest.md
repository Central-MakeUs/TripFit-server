# 공휴일 휴무 반영 (`holidayRest`)

> wave: 2
> implements: BR-TRIP-002, BR-TRIP-005, BR-USER-008
> related: [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) A4 · [`schedule-unified.md`](schedule-unified.md) · [`trip-recommendation-algorithm.md`](../trip/trip-recommendation-algorithm.md) · [`docs/decisions/011-holiday-data-source.md`](../../decisions/011-holiday-data-source.md) · [`vacation-policy-user-migration.md`](vacation-policy-user-migration.md)(`#52`, 4개 필드 User 이동 완료 — 본 스펙의 대표 행 조회 제거됨)
> 상태: **Implemented** (#107) — 2026-08-16 승인·구현 완료. 인증키 발급 후 실제 API 응답 대조만 남음. `#52`(2026-08-16) 완료로 대표 행 우회 로직 삭제
> MVP: In scope (달력·추천 정확도)

## 목표

`RegularSchedule.holidayRest`(공휴일 휴무 여부)를 실제 근무일 판정에 반영해, "공휴일에 쉰다"고 등록한 사용자의 달력·추천 결과에서 공휴일이 근무일로 잘못 계산되지 않게 한다.

## 배경

`holidayRest` 필드는 이미 저장·조회되지만 **어디에서도 읽히지 않는다.** 대한민국 공휴일 데이터 자체가 코드베이스에 없어 판정할 근거가 없었기 때문이다. 그 결과 평일 9~18시 근무자는 공휴일에도 오전·오후가 `IMPOSSIBLE`로 계산되고, 추천 엔진은 그 날을 여행하려면 연차가 필요하다고 잘못 판단한다.

- 기존 스펙은 [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) **A4**에서 "wave 2 Out — 요일만. 공휴일은 후속"으로 명시적으로 미뤄둔 상태였다. 본 스펙이 그 A4를 해소한다.
- 데이터 소스는 [`011-holiday-data-source.md`](../../decisions/011-holiday-data-source.md)에서 **공공데이터포털 특일 정보 API + Redis 캐싱**으로 확정됐다(static table 미채택). 본 스펙은 그 결정을 전제로 **연동 방식·판정 규칙**만 정의한다.
- `[미정]` 트래커 `#2`의 "공휴일 데이터" 항목은 위 결정으로 종결됨.

## 변경 범위 (기존 Approved 스펙 amend)

### ADDED

- 공휴일 조회 컴포넌트 `HolidayProvider` + 공공데이터포털 API 클라이언트 + 일 1회 동기화 스케줄러 (신규 패키지 `common/holiday/`)
- `ScheduleCalendarResolver.resolve(...)`에 **공휴일 날짜 집합** 파라미터 추가 (기존 `googleBusyByDate`와 동일한 주입 방식)
- 환경변수 `HOLIDAY_API_SERVICE_KEY` (`deploy/app/.env.example` + GitHub Secrets)

### MODIFIED

- [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) **A4**: (변경 전) "wave 2 Out — 요일만. 공휴일은 #13·후속" → (변경 후) **반영함** — `holidayRest=true` 정기 일정은 공휴일에 적용되지 않음. 프론트 고지 문구 "공휴일≠휴무 자동"도 함께 폐기
- [`trip-recommendation-algorithm.md`](../trip/trip-recommendation-algorithm.md) 리스크 표 "공휴일 데이터 = #107로 분리, `holidayRest`는 추천 계산에서 읽히지 않는 상태" → **반영 완료**로 amend
- `RecommendationEngine.matchingRegulars(regulars, dayOfWeek)` → 날짜·공휴일을 함께 받아, 공휴일에 쉬는 사용자면 빈 목록을 반환하는 시그니처로 변경
- `#105`의 대표 행 규칙을 `RecommendationEngine.primaryVacationSchedule`(private) → `RegularSchedule.policySource`(엔티티 static)로 이동해 달력·추천이 같은 SSOT를 쓰게 함. 적용 대상에 `holidayRest` 추가 — 기존 `maxVacationDays`·`halfVacationAvailable`과 동일 기준

### REMOVED

- `schedule-calendar-resolve.md` A4의 "wave 2 Out" 확정 방향 서술 및 프론트 고지 문구 (위 MODIFIED에서 대체 — 같은 PR에서 문구 삭제)
- 구 `matchingRegulars(List<RegularSchedule>, DayOfWeek)` 시그니처 (교체 후 잔존 금지)

## 요구사항

### Must Have

- [x] 공공데이터포털 특일 정보 `getRestDeInfo`(관공서 공휴일)로 공휴일·대체공휴일 조회
- [x] 하루 1회 스케줄러가 **올해·내년·내후년 3개년**을 동기화해 Redis에 연도별 캐싱 (앱 기동 시 1회 즉시 동기화 포함)
- [x] API 호출 실패 시 기존 캐시 유지(그날 갱신만 스킵), Redis 조회 실패 시 **fail-open**(공휴일 아님으로 간주)
- [x] `holidayRest` 판정 — `#52`(2026-08-16) 완료 후 `User.holidayRest` 직접 읽기(사람당 하나, 대표 행 개념 없음)
- [x] 공휴일에 쉬는 사용자 — 공휴일에는 **정기 일정 전체가 없는 것처럼** 처리 (달력에서 "여행 가능해요")
- [x] 공휴일에 안 쉬는 사용자 — 공휴일에도 평소 근무 요일·시간 그대로 적용
- [x] 개별 일정 오버라이드·구글 캘린더 busy는 공휴일 여부와 **무관하게** 그대로 유지
- [x] 추천 엔진의 연차 계산 — 공휴일에 쉬는 정기 일정은 연차 대상에서 제외(연차 없이 참석 가능)
- [x] `HOLIDAY_API_SERVICE_KEY` 환경변수 배선 (`.env.example` · GitHub Secrets · `application-*.yml`)

### Nice to Have

- [ ] 동기화 성공·실패 구조화 로그 (기존 `SocialIntegrationLog` 패턴 준용 여부는 구현 시 판단)

### Out of Scope (이번 스펙에서 하지 않음)

- 동점 처리 기준에 공휴일 재도입 — `trip-recommendation-scoring-source.md`가 2026-07-30에 의도적으로 폐기한 규칙(구 "연차→기간→주말·공휴일")이다. **재도입 금지**
- `halfVacationAvailable`(반차) 반영 — `#2` 별도 `[미정]`
- `vacationApplyPeriod`(연차 신청 가능 시점) 반영 — `#2` 별도 `[미정]`
- 한국 외 국가 공휴일

## API / 인터페이스

**본 스펙 자체는 신규 REST API 없음.** 기존 API의 응답 **값**만 달라진다. (공휴일 날짜 자체를 조회하는 API는 별도로 분리 — [`schedule-holiday-list-api.md`](schedule-holiday-list-api.md) `GET /api/v1/holidays`, 2026-08-18 amend)

| Method | Path | 영향 |
|--------|------|------|
| GET | `/api/v1/users/schedule/calendar` | 공휴일 날짜의 슬롯이 `IMPOSSIBLE`→`POSSIBLE`로 바뀔 수 있음 (스키마 변경 없음) |
| GET | `/api/v1/trips/{tripId}/members/schedule-calendar` | 동일 |
| PATCH | `/api/v1/users/schedule/personal` | 응답이 최종 확정값이므로 동일 영향 |
| POST | 추천 생성 | 후보 점수·`totalVacationDays`가 달라질 수 있음 |

> **`Breaking-Change-Reason` 대상.** 필드·enum·경로는 그대로지만 같은 입력에 대한 응답 값이 달라져 프론트 화면 표시가 바뀐다. 커밋 본문에 트레일러를 넣는다 (`harness-workflow.md` STOP §5 — "optional이라 breaking 아님"으로 좁혀 해석하지 않음).

### 외부 API (공공데이터포털)

```
GET https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo
  ?serviceKey={HOLIDAY_API_SERVICE_KEY}
  &solYear={YYYY}
  &numOfRows=100
  &_type=json
```

| 응답 필드 | 용도 |
|-----------|------|
| `item[].locdate` | `YYYYMMDD` 정수 → `LocalDate` 변환. 캐싱 대상 |
| `item[].isHoliday` | `"Y"`인 항목만 채택 (방어적 필터) |

- `solMonth`를 생략해 **연 단위**로 조회한다 — 한 해 공휴일이 20개 내외라 `numOfRows=100`이면 페이지네이션 불필요.
- 스케줄러 1회 실행 = 연도 3개 → **API 호출 3회**.

> ⚠️ **구현 시 재검증 필요:** 위 엔드포인트·파라미터명·응답 스키마는 문서화된 명세 기준이다. 인증키 발급 후 공공데이터포털 명세서로 실제 응답을 한 번 대조하고, 다르면 이 절을 먼저 amend한다.

## 데이터 모델

**DB 스키마 변경 없음.** `regular_schedule.is_holiday_rest`는 이미 존재하며, 지금까지 읽히지 않았을 뿐이다.

### Redis 키

| 키 | 값 | TTL |
|----|-----|-----|
| `holiday:kr:{year}` | 그 해 공휴일 `LocalDate` 목록 | **7일** |

- 연도별로 키를 나눠, 한 해 동기화가 실패해도 다른 해 캐시가 살아남게 한다.
- TTL 7일의 의도: 스케줄러가 며칠 연속 죽으면 캐시가 자연 만료돼 **오래된 데이터를 계속 믿는 대신** fail-open으로 전환된다(사람 개입 없는 self-healing). 정상 운영 시에는 하루 1회 갱신이 TTL을 계속 밀어내므로 만료되지 않는다.

### 패키지 배치

```
common/holiday/
├── HolidayProvider.java              # 인터페이스 — findHolidaysBetween(start, end)
├── RedisHolidayProvider.java         # Redis 조회 구현(fail-open) + replaceYear 캐시 쓰기
├── client/HolidayApiClient.java      # 공공데이터포털 getRestDeInfo 호출·파싱
├── scheduler/HolidaySyncScheduler.java
└── config/HolidayProperties.java
```

`common/`에 두는 이유: 대한민국 공휴일은 `user.schedule`·`trip.recommendation` 어느 도메인의 소유 데이터도 아닌 **국가 참조 데이터**이고, 두 도메인이 함께 읽는다. (`user/schedule/holiday/`에 두면 `trip`이 `user.schedule` 내부를 더 깊이 참조하게 됨.)

## 판정 규칙

### H1 — 공휴일에 적용되는 정기 일정 필터 (핵심)

`holidayRest`는 **사람 단위 설정**이다. `#52`(2026-08-16) 완료로 `User.holidayRest` 컬럼에 저장되며, `RegularSchedule`에는 이 필드가 없다.

```text
regularsAppliedOn(matched, date, holidays, holidayRest):
  if date not in holidays: return matched
  if not holidayRest: return matched
  return []      // 공휴일에 쉬는 사용자 → 그날 정기 일정 전체가 적용되지 않음
```

- **사용자 단위 all-or-nothing이다.** 공휴일에 쉬는 사용자는 그날 **모든** 정기 일정이 빠지고, 안 쉬는 사용자는 **모두** 그대로 적용된다. 회사·알바처럼 근무를 여러 개 등록해도 `holidayRest`는 사람에게 하나뿐이라 행마다 값이 갈리는 경우 자체가 없다.
- `holidayRest`의 default는 `true`이므로, 별도 설정을 하지 않은 대다수 사용자는 자동으로 "공휴일에 쉼"으로 동작한다.
- 정기 일정이 하나도 없는 사용자는 판정 자체가 불필요하다(제외할 정기가 없음).

> **`#52` 완료 (2026-08-16):** 4개 필드가 `User`로 이동하면서 대표 행(`RegularSchedule.policySource`) 조회는 삭제됐다 — `ScheduleCalendarResolver.resolve(...)`·`RecommendationEngine`은 이제 `user.isHolidayRest()`를 파라미터로 직접 전달받는다. 아래 H4 절도 이 변경을 반영해 갱신됨.

### H2 — 우선순위 (기존 O1 규칙 유지)

슬롯 하나의 최종값 순서는 **바뀌지 않는다**. H1은 "정기⊕구글" 단계의 **정기 쪽 입력만** 줄인다.

```text
개별 일정 오버라이드 (있으면 최우선)
  > 정기(H1 필터 적용) ⊕ 구글 busy
    > POSSIBLE (기본값 — "미입력≠불가능")
```

- **개별 일정**은 공휴일이어도 그대로 이긴다 — 사용자가 그 날짜를 직접 "일정 있음"으로 지정했다면 공휴일이라는 이유로 덮어쓰면 안 된다.
- **구글 캘린더 busy**도 그대로 유지한다 — 공휴일에 실제로 잡힌 약속은 여전히 참석 불가다 (2026-08-16 사용자 승인).

### H3 — sparse omit 판정

`holidayRest=true` 정기만 있던 날짜가 공휴일이어서 전부 필터링되면, 그날은 **정기 신호가 없는 날**과 동일해진다. 개별 일정·구글 busy도 없으면 기존 규칙대로 **omit**된다(응답에서 빠짐).

프론트 영향: 평일에 찍히던 점(dot)이 공휴일에는 사라진다 — 이는 "그날 제약 없음"을 뜻하며 의도된 동작이다.

### H4 — 추천 엔진 연차 계산

`RecommendationEngine`은 두 지점에서 정기 일정을 **리졸버를 거치지 않고 직접** 참조하므로, 같은 필터를 적용해야 한다. 둘 다 `matchingRegulars(...)`를 통과하므로 **이 메서드 한 곳만** 날짜·공휴일 인지형으로 바꾸면 두 경로가 함께 고쳐진다.

| 지점 | 미적용 시 증상 |
|------|----------------|
| `collectVacationOptions` | 공휴일 슬롯에 불필요한 연차 전환 후보가 생김 (실제로는 리졸버가 이미 열어둬 후보가 안 만들어지지만, 방어적으로 동일 필터 적용) |
| `vacationDaysForSpan` | **공휴일에 쉬는 사람에게 연차를 청구** — 이 메서드는 `possible[]`을 보지 않고 근무의 `slotStatuses`만 보므로, 필터 없이는 공휴일에도 종일 연차 1.0일이 계산된다. `totalVacationDays` 과대 계산 → 추천 점수 왜곡 |

`#52`(2026-08-16) 완료로 대표 행 조회는 사라졌다 — `RecommendationEngine`의 `matchingRegulars`·`applyVacationSimulation`·`vacationDaysForSpan`은 이제 `User`(호출부가 이미 들고 있는 `TripMember.getUser()`, 추가 조회 없음)에서 `holidayRest`를 직접 읽는다.

### H5 — 조회 범위와 캐시 정합

`HolidayProvider.findHolidays(start, end)`는 요청 구간이 걸친 연도들의 캐시를 합쳐 반환한다. 마이페이지 조회 윈도우가 `today~today+2년`(C1)이므로 3개년 캐싱으로 항상 커버된다.

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-TRIP-002 | 날짜×슬롯 가능/불가 판정에 공휴일을 반영 | `ScheduleCalendarResolver` |
| BR-TRIP-005 | 1인당 평균 연차일수 산출에서 공휴일 근무 제외 | `RecommendationEngine.matchingRegulars` |
| BR-USER-008 | 일정은 User 전역 — 공휴일 판정도 trip과 무관하게 동일 적용 | `common/holiday` |
| BR-TRIP-012 | **변경 없음** — 동점 처리에 공휴일을 재도입하지 않는다 | — |

## 검증 시나리오

### 정상

- [ ] 평일 9~18시 정기(`holidayRest=true`) 사용자 — 공휴일인 화요일이 omit되거나 3슬롯 `POSSIBLE`로 계산됨
- [ ] 같은 사용자의 **공휴일이 아닌** 화요일 — 기존대로 `IMPOSSIBLE`/`IMPOSSIBLE`/`POSSIBLE` (회귀 방지)
- [ ] `holidayRest=false` 사용자 — 공휴일에도 평소와 동일하게 `IMPOSSIBLE` 유지
- [ ] 정기 2행(회사+알바) 사용자, `User.holidayRest=true` — 공휴일에 **두 행 모두** 제외됨 (사람 단위 all-or-nothing)
- [ ] 공휴일에 개별 일정 오버라이드가 있으면 그 값이 그대로 최종값
- [ ] 공휴일에 구글 busy가 있으면 `IMPOSSIBLE` 유지
- [ ] 대체공휴일(예: 일요일과 겹친 공휴일의 다음 평일)도 동일하게 적용됨
- [ ] 추천 — 공휴일이 낀 후보 구간의 `totalVacationDays`가 공휴일 몫만큼 감소

### 엣지 · 실패

- [ ] Redis 조회 예외 → 예외 전파 없이 "공휴일 아님"으로 계산 (달력 API는 정상 200)
- [ ] 캐시가 비어 있음(콜드 스타트) → 동일하게 fail-open, 500 없음
- [ ] 공공데이터포털 API 500·타임아웃 → 스케줄러가 예외를 삼키고 warn 로그, 기존 캐시 유지
- [ ] 인증키 누락·무효 → 기동은 성공하되 동기화만 실패(앱 전체가 죽지 않을 것)
- [ ] 응답에 `isHoliday="N"` 항목이 섞여 오면 캐싱에서 제외

### 수동 / 통합

- [ ] 인증키 발급 후 실제 API 1회 호출해 응답 스키마가 위 "외부 API" 절과 일치하는지 대조 — 절차: [`deploy/holiday-api-setup.md`](../../../deploy/holiday-api-setup.md)
- [ ] 실제 공휴일 날짜(예: 다음 국경일)로 `GET /users/schedule/calendar` 호출해 눈으로 확인

## 완료 기준

- [x] `./gradlew test` 통과 (2026-08-16, Docker 가용 환경에서 505건 전체 통과 — 실패·오류 0)
- [x] `./gradlew build` 성공 (2026-08-16, `spotlessCheck` 포함)
- [x] 위 검증 시나리오의 정상·엣지 케이스가 단위 테스트로 존재 (`ScheduleCalendarResolverTest` 7건 · `RecommendationEngineTest` 3건 · `RecommendationEngineTestSetScenarioTest` 1건 — 2026년 10월 실제 공휴일 3일로 5인 시나리오 재현)
- [x] `schedule-calendar-resolve.md` A4 amend (REMOVED 문구 실제 삭제 확인)
- [x] `trip-recommendation-algorithm.md` 리스크 표 amend
- [x] `#2` 공휴일 항목 종결 확인 (2026-08-16 처리 완료)
- [x] `Breaking-Change-Reason:` 트레일러 — **해당 없음**으로 판정 (2026-08-16). 요청·응답 필드·enum·`ErrorCode`·경로가 전부 무변경이고, 달라지는 것은 같은 계약 위의 **데이터 값**뿐이라 프론트 처리 로직이 바뀌지 않는다. 공휴일에 정기가 빠져 그 날짜가 sparse 응답에서 omit될 수 있으나, omit 자체는 기존에도 "일정 없는 날"에서 발생하던 정상 형태다
- [x] 배포 환경변수 배선 (2026-08-16 — CI/CD `envs` 화이트리스트·`export`·`docker-compose` app 환경변수에 `HOLIDAY_API_SERVICE_KEY` 추가. 누락 상태였으면 Secret을 등록해도 컨테이너까지 전달되지 않았음)
- [x] `docs/specs/README.md` 인덱스에 본 스펙 등록 (2026-08-16, 스펙 작성 시 완료)
- [x] OpenAPI 변경 없음 확인 (스키마 무변경 — 값만 변화)

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 공휴일에 구글 캘린더 busy 유지 | **확정** | 2026-08-16 사용자 승인 — 공휴일이어도 실제 약속이 잡혀 있으면 개별 일정과 동일하게 `IMPOSSIBLE` 유지 |
| 공공데이터포털 인증키 발급 | **선행 작업** | 활용신청 승인 대기가 있을 수 있어 구현 착수 전 확보 필요. 미발급 상태로는 통합 검증 불가 |
| API 응답 스키마 실측 | `[미정]` | 위 "외부 API" 절은 명세 기준 — 실제 응답과 다르면 스펙 먼저 amend |
| 인증키 URL 인코딩 | 주의 | 공공데이터포털 인증키는 Encoding/Decoding 두 형태가 발급돼, 어느 쪽을 쓰느냐에 따라 401이 나는 흔한 함정. 구현 시 확인 |
| 근무처별로 공휴일 휴무가 다른 경우 | **Out (의도적)** | 회사는 쉬고 알바는 나가는 상황은 현실에 있지만, `#52`가 `holidayRest`를 "사람 1명에게 붙는 값"으로 이미 규정했고 프론트도 전 행에 같은 값을 쓴다. 본 스펙은 그 규정을 따르며 **행별 판정을 도입하지 않는다** — 필요해지면 `#52` 처리 시 함께 재논의 |
| `#52` 완료 시 동반 수정 | **완료 (2026-08-16)** | 4개 필드가 `User`로 이동, 본 스펙의 대표 행 조회 제거 완료 |
| 공휴일 근무자의 대체휴무 | Out | "공휴일에 일하고 다른 날 쉰다"는 케이스는 모델링하지 않음 — 사용자가 개별 일정으로 직접 입력 |
| 임시공휴일 반영 지연 | 허용 | 최대 1일(다음 동기화까지). `011` 결정에서 감수하기로 함 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-16 | Draft 초안 — 데이터 소스(`011`) 확정 이후 연동 방식·판정 규칙(H1~H5) 정의 |
| 2026-08-16 | **H1 수정** — 초안의 "정기 일정 행 단위 판정"은 오류. `#52`가 `holidayRest`를 사람 단위 값 4종으로 규정하고 `#105`가 대표 행(`primaryVacationSchedule`) 규칙을 이미 구현했으므로, 같은 기준을 따르는 **사용자 단위 all-or-nothing** 판정으로 교체. 검증 시나리오·MODIFIED·리스크 표 동반 수정 |
| 2026-08-16 | H2 구글 캘린더 busy 유지 **사용자 승인** — 잔여 확인 항목 없음 |
| 2026-08-16 | **Approved** — 전체 스펙 사용자 승인 |
| 2026-08-18 | **amend** — Out of Scope의 "공휴일 목록 조회 API 없음" 삭제, API 절 amend. 캘린더 화면에 공휴일 표시가 안 되는 프론트 요구가 확인돼 [`schedule-holiday-list-api.md`](schedule-holiday-list-api.md)로 분리 승인 (`#107`) |
