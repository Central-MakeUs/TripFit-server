# 추천 4모드 · TOP 3 · 확정·취소

> wave: 2  
> implements: BR-TRIP-005, BR-TRIP-007, BR-TRIP-010, BR-TRIP-011, BR-TRIP-012  
> deferred: BR-NOTI-004 확정 알림 (wave 3), `cancel_reason` VOC (wave 4), 추천 가중치 수치 튜닝  
> 상태: Draft  
> 선행: [`schedule-unified.md`](schedule-unified.md) (#11), [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) (#17), [`trip-room-api.md`](trip-room-api.md) (#12), **[#22](https://github.com/Central-MakeUs/TripFit-server/issues/22)** (RESPONDED·sparse·submit)

## 목표

방장이 **4가지 추천 모드**로 TOP 3 후보를 받고, 후보 선택 또는 직접 날짜 입력으로 일정을 확정·취소한다. wave 2 MVP 완료 기준의 **추천·확정** 축.

## 배경

- **ERD:** `recommendation` (rank 1~3), `trip.last_recommendation_mode`, `trip.confirmed_*`, `TripStatus`
- **JPA:** `Recommendation` 엔티티 존재, Service·API 없음
- **BR-TRIP-005:** wave 2 **4모드 전부** — BASIC, ALL_ATTEND, SAVE_VACATION, CERTAIN
- **BR-TRIP-010:** 모드·기간·일수 변경·trip delete → `recommendation` **hard DELETE**
- **저장 정책:** trip당 **현재 모드 TOP 3만** 유지 (이전 모드 결과는 DELETE)

### 추천 모드

| enum | 한글 (UI) | 요약 |
|------|-----------|------|
| `BASIC` | 기본 | 참석↑ · 연차↓ · TBD↓ 균형 |
| `ALL_ATTEND` | 모두 참석 | BR-TRIP-011 하드 필터 후 불가 최소화 |
| `SAVE_VACATION` | 휴가 아끼기 | 연차 소모 최소화 |
| `CERTAIN` | 확실하게 가기 | TBD 최소화 |

### 관련 문서

| 문서 | 내용 |
|------|------|
| `docs/product/flows/trip-confirm.md` | 확정 플로우 |
| `docs/product/business-rules/trip.md` | BR-TRIP-005~012 |
| `docs/architecture/erd.md` | `recommendation`, `last_recommendation_mode` |

## 요구사항

### Must Have

- [ ] `RecommendationMode` enum (4값 + `trip.last_recommendation_mode`)
- [ ] `POST /api/v1/trips/{tripId}/recommendations` — `{ mode }` → 계산 → 기존 rows **hard DELETE** → TOP 3 INSERT
- [ ] `GET /api/v1/trips/{tripId}/recommendations` — 현재 저장된 TOP 3 (+ `mode`, `generatedAt` `[제안]`)
- [ ] 후보 윈도우: `[startRange, endRange]` 내 **연속 `durationDays`일** 슬라이딩 `[제안]`
- [ ] **입력 resolve:** #17 `ScheduleCalendarResolveService` **재사용** (C1 — 별도 병합 로직 금지). 멤버×날짜 effective
- [ ] TBD = 날짜 단위 `uncertain` (CERTAIN 모드 · U1 달력과 동일)
- [ ] **ALL_ATTEND:** `memberCount` 미달 후보 **제외** (BR-TRIP-011)
- [ ] **동점:** BR-TRIP-012 — 1) 연차 적은 순 2) 기간 긴 순 3) 주말·공휴일 포함 순 `[제안]`
- [ ] `POST /api/v1/trips/{tripId}/confirm` — 방장만 (BR-TRIP-007): `{ recommendationRank }` 또는 `{ startDate, endDate }`
- [ ] confirm → `status=CONFIRMED`, `confirmedStartDate`/`confirmedEndDate` 설정
- [ ] `POST /api/v1/trips/{tripId}/cancel` — 방장만 → `status=CANCELED` (**`cancel_reason` null**, wave 4)
- [ ] `POST .../recommendations` · confirm · cancel — **`status=ONGOING`만** (D4 → 409 `TRIP_NOT_ONGOING`)
- [ ] confirm 성공 시 **일정 snapshot** (#38 R-freeze — 동일 TX). 추천 재실행은 CONFIRMED/TERMINATED에서 불가(X8)
- [ ] trip PATCH(기간·일수) / DELETE / mode POST 시 recommendation hard DELETE (BR-TRIP-010)
- [ ] `./gradlew test` — 모드별·동점·hard filter 단위 테스트

### Nice to Have

- [ ] `score`, `reason`, `riskNote` 자동 생성 (한국어 템플릿 `[제안]`)
- [ ] RESPONDED 미만 참여자 있어도 추천 가능 (경고 필드 `[제안]`)

### Out of Scope

- 알림 발송 (BR-NOTI-004) — wave 3
- `cancel_reason` body — wave 4
- 가격·날씨 등 외부 데이터
- 공휴일 API — 주말만 `[제안]` 또는 static `[미정]`

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/api/v1/trips/{tripId}/recommendations` | JWT + owner | 모드별 TOP 3 재계산·저장 |
| GET | `/api/v1/trips/{tripId}/recommendations` | JWT + member | 저장된 TOP 3 조회 |
| POST | `/api/v1/trips/{tripId}/confirm` | JWT + owner | 일정 확정 |
| POST | `/api/v1/trips/{tripId}/cancel` | JWT + owner | 일정 취소 (CANCELED) |

### `POST .../recommendations` 요청

```json
{
  "mode": "ALL_ATTEND"
}
```

### `POST .../recommendations` 응답

```json
{
  "data": {
    "mode": "ALL_ATTEND",
    "items": [
      {
        "rank": 1,
        "startDate": "2026-08-03",
        "endDate": "2026-08-06",
        "reason": "6명 전원 참석 가능",
        "riskNote": null,
        "score": 0.91
      }
    ]
  }
}
```

### `POST .../confirm` — 후보 선택

```json
{
  "recommendationRank": 1
}
```

### `POST .../confirm` — 직접 입력 (BR-TRIP-007)

```json
{
  "startDate": "2026-08-04",
  "endDate": "2026-08-07"
}
```

직접 입력 시 `durationDays`와 일수 일치 검증 `[제안]`.

### 주요 에러 코드

| HTTP | code | 조건 |
|------|------|------|
| 400 | `INVALID_RECOMMENDATION_MODE` | enum 밖 |
| 400 | `NO_RECOMMENDATION_CANDIDATES` | ALL_ATTEND 등 후보 0건 |
| 403 | `TRIP_FORBIDDEN` | 방장 아님 |
| 409 | `TRIP_ALREADY_CONFIRMED` | 중복 confirm |
| 409 | `TRIP_NOT_ONGOING` | CANCELED/TERMINATED |
| 404 | `RECOMMENDATION_NOT_FOUND` | rank 없음 |

## 데이터 모델

- `Trip.lastRecommendationMode` — JPA 컬럼 추가
- `recommendation` — 기존 엔티티, trip_id FK, hard DELETE only

### BR-TRIP-010 트리거

| 이벤트 | 동작 |
|--------|------|
| POST recommendations (mode 변경) | DELETE all + INSERT 3 |
| PATCH trip **duration** | DELETE all, `lastRecommendationMode=null` `[제안]` (기간은 create 후 불변) |
| DELETE trip | DELETE all |
| confirm / cancel | recommendation 유지 `[제안]` (확정 후 조회용) |

## 알고리즘 (구현 가이드)

> 가중치 수치는 `[미정]`. wave 2는 **모드별 분기 + 테스트 가능한 deterministic 점수**를 목표로 한다.

### 공통

1. 후보: `durationDays` **필수**(null이면 추천 불가). `startRange`~`endRange`에서 길이=`durationDays`인 모든 `[startDate, endDate]`
2. 각 후보·각 멤버·각 슬롯: **#17 resolve** effective 집계 (S1·R2=A)
3. TBD: `personal_schedule.uncertain=true`인 날짜 (CERTAIN 모드)
4. 정기 일정 연차: `maxVacationDays`·`VacationApplyPeriod`·반차·공휴일 휴무 필드 참고 (BR-TRIP-006). 필요일 추정 `[제안]` — workday IMPOSSIBLE → +1 (복수 행 집계 `[미정]`)

### 모드별

| 모드 | 점수화 `[제안]` | 필터 |
|------|-----------------|------|
| BASIC | `w1*attendRate - w2*vacationDays - w3*tbdRate` | 없음 |
| ALL_ATTEND | BASIC과 동일 sort | **가능 인원 < memberCount 제외** |
| SAVE_VACATION | `-vacationDays` primary | 없음 |
| CERTAIN | `-tbdCount` primary | 없음 |

### BR-TRIP-012 동점

동일 `score` 시 comparator chain: `vacationDays ASC` → `durationDays DESC` → `weekendHolidayCount DESC`

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-TRIP-005 | 4모드 TOP 3 | RecommendationService |
| BR-TRIP-007 | owner confirm/cancel | TripConfirmService |
| BR-TRIP-010 | hard DELETE | RecommendationRepository.deleteByTripId |
| BR-TRIP-011 | ALL_ATTEND filter | candidate filter |
| BR-TRIP-012 | tie-break | comparator |

## 검증 시나리오

### 정상

- [ ] BASIC POST → 3 rows, GET 동일
- [ ] mode SAVE_VACATION POST → 이전 BASIC rows 삭제됨
- [ ] ALL_ATTEND — target 6, 5명만 가능한 후보 제외
- [ ] confirm rank 1 → CONFIRMED + dates
- [ ] confirm custom dates → CONFIRMED

### 엣지 · 실패

- [ ] 참여자 confirm → 403
- [ ] ALL_ATTEND 후보 없음 → 400 `NO_RECOMMENDATION_CANDIDATES`
- [ ] PATCH trip endRange → GET recommendations empty
- [ ] cancel → CANCELED, cancelReason null

### 단위 테스트 (필수)

- [ ] 고정 fixture 멤버·`regular_schedule`/`personal_schedule` → 모드별 rank 1 기대값
- [ ] 동점 comparator 순서
- [ ] hard DELETE 후 count=0

## 완료 기준

- [ ] `./gradlew test` 통과 (RecommendationServiceTest 등)
- [ ] OpenAPI 반영
- [ ] wave 2 MVP 완료 기준: 방장이 4모드 중 하나로 TOP 3 확인 후 확정 가능

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| BR-TRIP-005 가중치 w1/w2/w3 | `[미정]` | MVP는 상대 순위만 맞으면 됨 — 튜닝은 prod 전 |
| 연차 산출 규칙 | `[제안]` | IMPOSSIBLE on workday → 1일; `halfVacationAvailable` 반영 `[미정]` |
| regular vs personal 병합 | **Implemented** (#17) | 추천은 resolve **재사용** (C1) |
| 공휴일 데이터 | `[미정]` | KR 공휴일 static table vs API |
| confirm 후 recommendation 유지 | `[제안]` | UI 재조회용 |
| NOTI on confirm | wave 3 | stub 없음 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-08 | 초안 |
| 2026-07-17 | #17 resolve 재사용(C1) · trip-room D4 ONGOING만 · calendar Implemented |
| 2026-07-13 | AVAILABILITY → `regular`/`personal` + `uncertain` |
