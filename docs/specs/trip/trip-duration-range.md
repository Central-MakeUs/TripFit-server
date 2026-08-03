# 여행 일수(n박 m일) 검증 범위 확장

> 상태: Implemented (hotfix, 사용자 승인 하에 브랜치·이슈 생성 없이 main 직접 반영 — 2026-07-26)
> MVP: In scope (기존 Approved `trip-room-api.md` D9 amend)
> 관련 BR: BR-TRIP-001, BR-TRIP-008

## 목표

`durationNights`(n박) 하나에 `durationDays`(m일)가 정확히 `n+1`인 조합만 허용하던 규칙을, 프론트 일정 입력 UI(박 선택 후 일 필드를 눌러 하루 더 늘리는 인터랙션)에 맞춰 `n+1`~`min(n+2, T)` 범위로 넓힌다. `T` = 희망 기간 일수(`endRange - startRange + 1`, BR-TRIP-008과 동일 정의).

## 배경

- 문의: POST `/api/v1/trips`에서 `durationNights=3, durationDays=5`(3박5일, n+2)를 보내면 400 `INVALID_INPUT`이 발생. 요청자는 "n박 기준 (n+1)일·(n+2)일 모두 유효해야 한다"는 정책을 전제로 문의.
- 조사 결과 **현재 구현은 버그가 아니라 기존 문서와 정확히 일치**한다 — 아래 3곳 모두 `nights == days - 1` 정확한 등식만 명시:
  - `docs/product/business-rules/trip.md` BR-TRIP-001 (line 7): "`nights == days - 1`·`days ≥ 1`·`nights ≥ 0`"
  - `docs/specs/trip/trip-room-api.md` D9 + `POST /trips` 필드 표(line 56, 205): "`nights == days - 1`"
  - `docs/product/glossary.md` "여행 일수"
  - 구현 `TripServiceSupport.resolveDurationDays()` (`durationNights != durationDays - 1`이면 400)
- 사용자 확인 결과, `n+2`까지 허용은 **확정된 정책 변경**(기존 문서가 아직 반영 못 한 상태)이며, 당일치기(`nights=0`)도 예외 없이 동일 규칙(`days ∈ {1,2}`)을 적용하기로 확정.
- 구조적 이슈: 현재 `Trip` 엔티티는 `durationDays`만 컬럼으로 저장하고 `durationNights`는 응답 시 `durationDays - 1`로 파생한다(`TripServiceSupport.durationNights()`, `Trip.java:67-68`). `n+2`를 허용하면 이 파생이 깨진다 — 예: 입력 `nights=3, days=5`를 저장 후 재조회하면 `nights = days-1 = 4`로 계산되어 "4박5일"로 응답이 바뀐다. 사용자는 **`durationNights`를 컬럼으로 영속화**하는 방향으로 해결하기로 확정.

## 요구사항

### Must Have

- [x] BR-TRIP-001 amend — 검증 규칙을 `nights == days - 1` → `nights ≥ 0` ∧ `days ≥ nights + 1` ∧ `days ≤ min(nights + 2, T)` (T=BR-TRIP-008 희망 기간 일수)로 갱신, 확정 이력 항목 추가
- [x] BR-TRIP-008 문구에 T 정의(희망 기간 일수 = `end_range - start_range + 1`) 명시 통일 (신규 규칙과 같은 T 재사용 확인)
- [x] `trip.duration_nights` 컬럼 추가 (int, nullable — `duration_days`와 동일하게 미정 시 null). `Trip.java`: 필드 + `@Schema` + 생성자 파라미터 추가
- [x] `TripServiceSupport.resolveDurationDays(nights, days)` → 등식 검증에서 범위 검증으로 변경 (당일치기 `nights=0` 포함 예외 없이 동일 규칙 적용)
- [x] `TripCommandService.createTrip`/`patchTrip` — 검증 통과한 `durationNights`도 함께 엔티티에 저장 (`Trip.setDurationNights`)
- [x] `TripServiceSupport.durationNights(Integer durationDays)` 파생 헬퍼 삭제, `toHomeCard`/`toDetail` 호출부(`TripServiceSupport.java:75, 107`)를 `trip.getDurationNights()` 조회로 교체 (레거시 삭제 — 같은 PR)
- [x] DTO `@Schema` 문구 갱신 — `CreateTripRequest`/`PatchTripRequest`(`nights==days-1` 문구) · `TripHomeCardResponse`/`TripDetailResponse`(`durationDays-1 파생(DB 저장 없음)` 문구, `TripDetailResponse.java:33` 등)를 새 규칙·영속화 사실에 맞게 수정
- [x] `docs/specs/trip/trip-room-api.md` — D9 갱신, `POST /trips` 필드 표(line 205)·`PATCH` 절, 완료 기준 체크리스트(line 106), 변경 이력 추가
- [x] `docs/product/glossary.md` "여행 일수" 항목 갱신
- [x] `docs/architecture/erd.md` — `trip` 표에 `duration_nights` 컬럼 추가, 하단 제약 문구(line 324) 갱신
- [x] 단위 테스트 — `resolveDurationDays`(또는 개명된 검증 메서드) 경계값: `n+1`/`n+2` 통과, `n+3` 실패, `n=0`일 때 `days=1`/`2` 통과, `days > T` 실패
- [x] `./gradlew test` 통과

### Nice to Have

- (없음)

### Out of Scope (이번 스펙에서 하지 않음)

- BR-TRIP-010(PATCH 시 `duration_days` 변경 → `recommendation` hard delete 훅) 로직 자체는 변경하지 않음 — 트리거 조건(“일수 변경”) 그대로 유지
- 기존 dev DB 데이터 마이그레이션 — 상용 보존 데이터 없음(dev), 컬럼 추가는 `ddl-auto` + 필요 시 DB 리셋으로 처리 (`harness-workflow.md` STOP §3)

## API / 인터페이스

신규 엔드포인트 없음. 기존 `POST /api/v1/trips`, `PATCH /api/v1/trips/{tripId}` 요청 검증 로직과 응답 필드 의미만 변경.

| Method | Path | 변경 내용 |
|--------|------|-----------|
| POST | `/api/v1/trips` | `durationNights`+`durationDays` 검증 범위 확장, 응답 `durationNights`가 파생값 대신 저장값 반환 |
| PATCH | `/api/v1/trips/{tripId}` | 동일 |
| GET | `/api/v1/trips`, `/api/v1/trips/{tripId}` | 응답 `durationNights`가 저장값 반환 (기존 파생값과 `n+1` 조합에서는 결과 동일) |

### 검증 규칙 (신규)

```
입력: durationNights(n), durationDays(m)

둘 다 null            → 미정 (검증 스킵)
한쪽만 null           → 400 INVALID_INPUT
n < 0                 → 400 INVALID_INPUT
m < n + 1             → 400 INVALID_INPUT
m > n + 2             → 400 INVALID_INPUT
m > T (희망 기간 일수) → 400 INVALID_INPUT   (BR-TRIP-008, 기존과 동일)
그 외 (n+1 ≤ m ≤ min(n+2, T)) → 통과, (n, m) 그대로 저장
```

예: `nights=3`일 때 `days=4`(n+1) 또는 `days=5`(n+2) 모두 허용(단, `T ≥ days`). `nights=0`일 때 `days=1` 또는 `days=2` 모두 허용.

## 데이터 모델

- ERD 참조: `docs/architecture/erd.md` `trip` 테이블
- 변경 컬럼:

```
trip.duration_nights  int  nullable   -- n박. duration_days와 쌍으로 null 또는 값. 신규 영속 컬럼(기존엔 파생값)
trip.duration_days    int  nullable   -- 기존 유지. m일
```

- Soft delete 정책 변경 없음. FK 없음(단순 값 컬럼).

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-TRIP-001 (amend) | `durationNights`+`durationDays` 쌍 검증: `n≥0`, `n+1 ≤ m ≤ min(n+2, T)` | `TripServiceSupport.resolveDurationDays` |
| BR-TRIP-008 | `m ≤ T`(희망 기간 일수), 미정이면 스킵 — 기존 규칙 유지, T 정의 재사용 | 동일 |

## 검증 시나리오

### 정상

- [x] `nights=3, days=4`(n+1), `T≥4` → 200, 저장값 그대로 응답 (`TripServiceSupportTest.resolveDurationDays_nightsPlusOne_returnsDays`)
- [x] `nights=3, days=5`(n+2), `T≥5` → 200 (신규 허용) (`TripServiceSupportTest.resolveDurationDays_nightsPlusTwo_returnsDays`, `TripServiceTest.createTrip_allowsNightsPlusTwoDays` — `durationNights`/`durationDays` 저장값 캡처 확인)
- [x] `nights=4, days=5`(n+1), `T≥5` → 200 (동일 검증 로직으로 커버)
- [x] `nights=0, days=1`(당일치기, n+1) → 200 (`TripServiceTest.createTrip_allowsDayTrip_zeroNightsOneDay`)
- [x] `nights=0, days=2`(n+2, 당일치기 확장) → 200 (신규 허용) (`TripServiceSupportTest.resolveDurationDays_dayTrip_allowsOneOrTwoDays`)
- [x] 둘 다 null → 200, `duration_days`/`duration_nights` 둘 다 null 저장 (`TripServiceSupportTest.resolveDurationDays_bothNull_returnsNull`, `TripServiceTest.createTrip_allowsUndecidedDuration`)

### 엣지 · 실패

- [x] `nights=3, days=6`(n+3, 범위 밖) → 400 `INVALID_INPUT` (`TripServiceSupportTest.resolveDurationDays_nightsPlusThree_throwsInvalidInput`, `TripServiceTest.createTrip_rejectsMismatchedNightsAndDays`)
- [x] `nights=3, days=3`(m < n+1) → 400 `INVALID_INPUT` (`TripServiceSupportTest.resolveDurationDays_daysLessThanNightsPlusOne_throwsInvalidInput`)
- [ ] `nights=3, days=5`인데 `T=4`(범위 내이나 기간 초과) → 400 `INVALID_INPUT` (BR-TRIP-008) — 기존 `validateTripMeta`의 `resolvedDays > rangeDays` 분기 재사용, 전용 테스트는 미추가
- [x] 한쪽만 null → 400 `INVALID_INPUT` (기존과 동일) (`TripServiceSupportTest.resolveDurationDays_onlyOneSideNull_throwsInvalidInput`)
- [x] `nights=-1` → 400 `INVALID_INPUT` (기존과 동일) (`TripServiceSupportTest.resolveDurationDays_negativeNights_throwsInvalidInput`)
- [x] PATCH도 동일 규칙 적용 확인 (`patchTrip`이 동일 `TripServiceSupport.resolveDurationDays` 호출 — 코드 공유로 보장)

### 수동 / 통합 (해당 시)

- [ ] `POST /trips`로 3박5일 생성 → `GET /trips/{tripId}` 재조회 시 `durationNights=3`(파생 4로 뒤바뀌지 않음) 확인 — 실제 서버 기동 후 수동 확인은 미실시(단위 테스트로 저장값 캡처만 확인)

## 완료 기준

- [x] `./gradlew test` 통과
- [x] `./gradlew build` 성공
- [x] OpenAPI(Swagger) — `CreateTripRequest`/`PatchTripRequest`/`TripHomeCardResponse`/`TripDetailResponse` `@Schema` 문구가 신규 규칙과 영속화 사실을 반영
- [x] BR-TRIP-001·BR-TRIP-008·`trip-room-api.md`·`glossary.md`·`erd.md` 동기화

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| dev DB 기존 row의 `duration_nights` | 확정 | 컬럼 추가 시 기존 row는 `NULL` — 상용 보존 데이터 없음(dev)이므로 마이그레이션 불필요, 필요 시 리셋 |
| `nights=0`일 때 `days=2` 허용 | 확정 | 사용자 확정 — 일반 규칙과 동일 적용, 예외 없음 |
| PATCH 시 `nights`만 바꾸고 `days` 유지 등 "한쪽만 변경" | 미변경 | 기존과 동일하게 **쌍 입력만 허용**(한쪽만 null이면 400) — 이번 스펙에서 새로 허용하지 않음 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-26 | 초안 — durationDays 유효 범위 n+1→n+2 확장 문의에서 시작, durationNights 컬럼 영속화 포함 |
