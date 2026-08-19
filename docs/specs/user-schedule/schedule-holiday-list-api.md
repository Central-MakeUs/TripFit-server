# 공휴일 날짜 목록 조회 API

> wave: 2
> implements: 해당 없음 (판정 로직이 아니라 화면 표시 지원용 순수 조회 API)
> related: [`schedule-holiday-rest.md`](schedule-holiday-rest.md)(부모 — 본 스펙이 그 Out of Scope를 amend) · [`docs/decisions/011-holiday-data-source.md`](../../decisions/011-holiday-data-source.md) · [`trip-schedule-calendar-window.md`](../trip/trip-schedule-calendar-window.md)
> 상태: **Implemented** (#107) — 2026-08-18 승인·구현·로컬 라우팅 검증 완료
> MVP: In scope (캘린더 화면 공휴일 표시)

## 목표

프론트 캘린더 화면(마이페이지 일정 캘린더, 여행방 멤버 캘린더)이 공휴일 날짜를 빨간 글씨로 표시할 수 있도록, 특정 기간의 대한민국 공휴일 날짜 목록을 조회하는 API를 새로 만든다.

## 배경

- [`schedule-holiday-rest.md`](schedule-holiday-rest.md)(#107)는 공휴일을 "근무일 판정"(`holidayRest`)에만 반영했고, "공휴일 날짜 자체를 프론트에 알려주는 API"는 Out of Scope로 명시적으로 미뤄뒀다 — 당시엔 "프론트 요구가 확인되지 않음"이 이유였다.
- 실제 앱 화면(여행방 멤버 캘린더 스크린샷)에서 토요일·일요일은 빨간 글씨로 표시되지만 1/1(신정) 같은 평일 공휴일은 검은 글씨로 남는 것이 확인됐다 — 프론트가 공휴일 판정 근거를 서버에서 받지 못해 요일만으로 색을 칠하고 있었다. 이제 그 프론트 요구가 확정됐다.
- **1차 검토(기각):** 각 캘린더 응답의 날짜 항목(`CalendarDay`/`CalendarDayResponse`)에 `isHoliday`를 직접 끼워 넣는 방식을 먼저 검토했으나 기각했다. 두 캘린더 API(`ScheduleCalendarResolver.resolve`)는 "그 날짜에 슬롯 신호가 전혀 없으면 날짜 자체를 응답에서 생략"하는 sparse 설계([`schedule-calendar-resolve.md`](schedule-calendar-resolve.md), 본 스펙 H3)라, 하필 "여행방 멤버 전원이 그 공휴일에 아무 일정도 없는" — 가장 흔하고 가장 빨간 글씨가 필요한 — 케이스에서 날짜 항목 자체가 응답에서 빠져 `isHoliday` 정보도 함께 사라지는 구조적 결함이 있다. 이를 피하려면 여행방 확정·종료 스냅샷(`TripMemberScheduleSnapshot`)에도 신규 컬럼을 추가해야 해 구현 범위가 커진다.
- 공휴일 여부는 사용자·여행방 데이터와 무관한 **순수 참조 데이터**다(어떤 사용자가 그 날짜에 일정이 있는지와 무관하게 "그 날짜가 공휴일인지"는 고정값). 따라서 사용자별 sparse 캘린더 안에 끼워 넣기보다, 별도의 가벼운 조회 API로 분리하는 쪽이 sparse 결함도 없고 향후 새 캘린더 화면이 생겨도 자동으로 커버된다(2026-08-18 사용자 결정).

## 변경 범위 (`schedule-holiday-rest.md` amend)

### ADDED

- 신규 REST API `GET /api/v1/holidays`
- `common/holiday/controller/HolidayController.java` — 요청 검증(`startDate <= endDate`) + `HolidayQueryService` 호출
- `common/holiday/service/HolidayQueryService.java` — 기존 `HolidayProvider.findHolidaysBetween(start, end)`를 그대로 호출해 DTO로 변환 (신규 캐시·외부 API 호출 로직 없음)
- `common/holiday/dto/HolidayListResponse.java`

### MODIFIED

- `schedule-holiday-rest.md` Out of Scope 절의 "사용자가 공휴일 목록을 직접 조회하는 REST API — 프론트 요구가 확인되지 않음" → 삭제(요구 확인됨, 본 스펙으로 이관). **본 스펙 승인 시 함께 반영**
- `schedule-holiday-rest.md` API 절의 "신규 REST API 없음" 서술 → "공휴일 날짜 목록 조회는 별도 API(`schedule-holiday-list-api.md`)로 분리"로 amend. **본 스펙 승인 시 함께 반영**

### REMOVED

- 없음 — 기존 코드·문서 삭제 대상 없는 순수 추가

## 요구사항

### Must Have

- [x] `GET /api/v1/holidays?startDate=&endDate=` — 요청 구간(양 끝 포함)의 대한민국 공휴일(대체공휴일 포함) 날짜를 오름차순으로 반환
- [x] 기존 `HolidayProvider.findHolidaysBetween(start, end)`를 그대로 재사용 — 신규 캐시 구조·외부 API 호출 추가하지 않음
- [x] `startDate > endDate`면 400 `INVALID_INPUT`
- [x] 인증 필요(JWT) — 앱 전역 기본 정책(로그인 필요)과 동일하게 두고, `SecurityConfig`에 별도 `permitAll` 추가하지 않음
- [x] 응답 구조는 기존 캘린더 API(`ScheduleCalendarResponse`)와 동일한 패턴으로 `startDate`/`endDate`를 함께 echo

### Nice to Have

- (없음)

### Out of Scope (이번 스펙에서 하지 않음)

- **공휴일 이름 노출**(예: "한글날") — 현재 Redis 캐시(`holiday:kr:{year}`)가 `LocalDate`만 저장하고 이름은 버린다. 이름까지 필요해지면 캐시 스키마 확장이 별도로 필요하며, 지금은 프론트가 날짜만으로 빨간 글씨 표시가 가능하다고 확인됨(스크린샷 요구사항이 색상 표시뿐)
- **조회 구간 상한(윈도우 검증)** — 다른 캘린더 API(today~+2년 등)와 달리, 순수 참조 데이터라 사용자별 자원 소모가 없어 구간 제한을 두지 않는다. 남용 우려가 실제로 발생하면 그때 재검토
- 한국 외 국가 공휴일 — 상위 스펙과 동일하게 범위 밖
- 캘린더 응답(`CalendarDay` 등)에 `isHoliday` 필드를 직접 추가하는 방식 — 위 배경 절에서 sparse 구조상 기각. 재검토 시 별도 스펙 필요

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| GET | `/api/v1/holidays` | JWT 필요 | 구간 내 대한민국 공휴일 날짜 목록 |

**Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `startDate` | `LocalDate` (ISO-8601) | ✓ | 조회 시작일(포함) |
| `endDate` | `LocalDate` (ISO-8601) | ✓ | 조회 종료일(포함) |

성공 (200):

```json
{"data": {"startDate": "2027-01-01", "endDate": "2027-01-31", "holidays": ["2027-01-01"]}}
```

실패 (400 — `startDate > endDate`):

```json
{"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다."}
```

실패 (401):

```json
{"code": "AUTH_EXPIRED", "message": "액세스 토큰이 만료되었습니다."}
```

신규 `ErrorCode` 없음 — 기존 `CommonErrorCode.INVALID_INPUT`, `AuthErrorCode` 그대로 재사용.

> **`Breaking-Change-Reason` 해당 없음.** 완전히 새로운 경로를 추가할 뿐, 기존 API의 필드·enum·`ErrorCode`·경로는 전혀 바뀌지 않는다(오직 이 경로를 처음 쓰는 프론트만 영향받고, 안 쓰는 기존 클라이언트는 아무 변화도 없음).

## 데이터 모델

**DB 스키마 변경 없음.** Redis 키(`holiday:kr:{year}`, [`schedule-holiday-rest.md`](schedule-holiday-rest.md) 데이터 모델 절)도 기존 그대로 재사용 — 새 캐시 구조를 추가하지 않는다.

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| 해당 없음 | 판정 로직 없는 순수 조회 API | `HolidayController` |

## 검증 시나리오

### 정상

- [x] `startDate`~`endDate`가 한 해 안 — 그 구간의 공휴일만 반환 (`HolidayQueryServiceTest`)
- [x] `startDate`~`endDate`가 연도 경계를 걸침(예: `2026-12-25`~`2027-01-05`) — 두 해 캐시를 합쳐 반환 (`HolidayProvider.findHolidaysBetween` 그대로 위임 — `RedisHolidayProviderTest.findHolidaysBetween_spansTwoYears_readsBothKeysAndClipsToRange`가 이미 검증)
- [x] 공휴일이 하나도 없는 구간 — 빈 배열 (200, 에러 아님) (`HolidayQueryServiceTest`)
- [x] 대체공휴일도 포함되어 반환됨 (`getRestDeInfo` 기반이므로 원본 캐시와 동일 보장) — 위임 구조상 `HolidayApiClientTest`가 이미 검증

### 엣지 · 실패

- [x] `startDate > endDate` → 400 `INVALID_INPUT` (`HolidayQueryServiceTest`, `HolidayControllerTest`)
- [x] JWT 없음·무효·만료 → 401 — 별도 `permitAll` 추가하지 않아 `SecurityConfig`의 `anyRequest().authenticated()` 기본값으로 구조적으로 보장됨. 이 엔드포인트 전용 401 단위 테스트는 작성하지 않음(다른 API도 개별 401 테스트를 강제하지 않는 기존 관례와 동일)
- [x] 아직 동기화되지 않은 먼 미래 연도 요청 → 예외 없이 그 해만 공휴일 0건 (`HolidayQueryServiceTest.getHolidays_noHolidaysInRange_returnsEmptyListNotError`와 동일 코드 경로)

### 수동 / 통합

- [x] 로컬 서버 재기동 후 실제 라우팅·문서 확인 — (1) 토큰 없이 호출 시 401(보호됨) (2) `/v3/api-docs`에 `GET /api/v1/holidays` 경로가 실제로 등록되고 `SuccessResponseHolidayListResponse` 스키마(`startDate`/`endDate`/`holidays`)가 정확히 노출됨을 확인. **Bearer 토큰을 넣은 200 curl은 못함** — `dev-login`이 삭제된 뒤라 로컬에서 즉석 토큰을 발급할 방법이 없음(실 소셜 로그인 필요). 응답 바디 자체의 정확성은 `HolidayQueryServiceTest`·`HolidayControllerTest`(mock 기반)로 대체 검증

## 완료 기준

- [x] `./gradlew test` 통과 (497건, 실패·에러 0)
- [x] `./gradlew build` 성공 (`spotlessCheck` 포함)
- [x] OpenAPI(Swagger)에 신규 경로·DTO 반영 확인 — `@Tag`·`@Operation`·`@Schema` 전부 작성, springdoc이 컴파일 시점에 자동 인식(별도 수동 반영 불필요)
- [x] `schedule-holiday-rest.md` Out of Scope·API 절 amend 확인 (위 MODIFIED 절 실제 반영)
- [x] `docs/specs/README.md` 인덱스에 본 스펙 등록 (스펙 작성 시 완료)

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 캘린더 응답에 `isHoliday` 직접 임베드 vs 별도 API | **확정 — 별도 API** | 2026-08-18 사용자 결정. sparse 응답 결함 회피 + 스냅샷 스키마 변경 불필요 |
| 공휴일 이름 미노출 | 확정(Out of Scope) | 필요해지면 별도 스펙에서 캐시 스키마 확장부터 |
| 조회 구간 상한 없음 | 확정 | 남용 징후가 실제로 발생하면 재검토 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-18 | Draft 초안 — 여행방 멤버 캘린더 스크린샷에서 공휴일 미표시 확인 후, `isHoliday` 임베드 방식을 검토·기각하고 별도 API로 설계 |
| 2026-08-18 | **Approved** — 사용자 승인, `#107` 이슈에 후속 작업으로 등록. 브랜치 `feat/107-holiday-list-api` |
| 2026-08-18 | **Implemented** — `common/holiday/{controller,service,dto}` 추가, 단위 테스트 6건 + `./gradlew build` 전체(497건) 통과. `fe-context/user-schedule/schedule-calendar-merge.md`에 규칙 6 추가 |
| 2026-08-18 | **부수 수정(같은 브랜치)** — 테스트 작성 중 발견한 앱 전체 버그(`GlobalExceptionHandler`가 필수 `@RequestParam` 누락을 500으로 처리하던 문제)를 `MissingServletRequestParameterException` 핸들러 추가로 수정. 이 API의 `startDate`/`endDate` 누락도 이제 정상적으로 400 `INVALID_INPUT` 반환(`HolidayControllerTest.getHolidays_missingStartDate_returns400InvalidInput`) |
