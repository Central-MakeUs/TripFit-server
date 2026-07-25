# 추천 4모드 · TOP 3 · 확정·취소

> wave: 2  
> implements: BR-TRIP-007, BR-TRIP-010 (API·DTO·ERD·상태전이만 — 계산 로직은 `#50`)  
> deferred: BR-NOTI-004 확정 알림 (wave 3), **추천 계산 로직(후보 윈도우·모드별 스코어링·ALL_ATTEND 필터·동점) → [`trip-recommendation-algorithm.md`](trip-recommendation-algorithm.md) (#50, BR-TRIP-005·011·012)**  
> 상태: Draft  
> 선행: [`schedule-unified.md`](schedule-unified.md) (#11), [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) (#17), [`trip-room-api.md`](trip-room-api.md) (#12), **[#22](https://github.com/Central-MakeUs/TripFit-server/issues/22)** (RESPONDED·sparse·submit)

## 목표

방장이 **4가지 추천 모드**로 TOP 3 후보를 받고, 후보 선택 또는 직접 날짜 입력으로 일정을 확정·취소한다. wave 2 MVP 완료 기준의 **추천·확정** 축.

**2026-07-24 범위 분리:** 이 스펙은 **API 설계·요청/응답 껍데기·DTO·ERD·상태 전이·hard DELETE 트리거**만 담당한다. `POST /recommendations`가 실제로 반환할 **TOP 3 계산 로직**(후보 윈도우 생성·모드별 스코어링·`ALL_ATTEND` 필터·동점 처리)은 [`trip-recommendation-algorithm.md`](trip-recommendation-algorithm.md)(`#50`)로 분리했다. 이 스펙만으로 구현하는 동안 `POST /recommendations`는 **플레이스홀더 값**으로 응답해 API 계약만 검증한다.

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

### 확정 취소(unconfirm) 사유 (2026-07-24 확정, 기획자 답변)

방장이 `unconfirm` 호출 시 라디오 버튼으로 사유 1개 선택(프론트) → 백엔드는 enum으로 저장. `OTHER` 선택 시 직접 입력 텍스트 필수.

| enum | 한글 (UI) |
|------|-----------|
| `NEW_SCHEDULE_ADDED` | 새로운 일정이 생겼어요 |
| `ATTENDEE_AVAILABILITY_CHANGED` | 참석 가능한 인원이 변경되었어요 |
| `RECOMMENDATION_UNSATISFACTORY` | 추천된 일정이 마음에 들지 않아요 |
| `WANT_OTHER_RECOMMENDATION` | 다른 조건으로 다시 추천받고 싶어요 |
| `TRIP_PLAN_CHANGED` | 여행 계획이 변경되었어요 |
| `OTHER` | 기타 (직접 입력 — `reasonDetail` 필수) |

`Trip.unconfirmReason`/`Trip.unconfirmReasonDetail`에 **최신값만** 저장(덮어쓰기, 이력 아님). 구 `cancel_reason`(취소·**삭제** VOC, wave 4)과는 별개 — 여행방 **삭제** 시 VOC 사유는 여전히 미정.

### 관련 문서

| 문서 | 내용 |
|------|------|
| `docs/product/flows/trip-confirm.md` | 확정 플로우 |
| `docs/product/business-rules/trip.md` | BR-TRIP-005~012 |
| `docs/architecture/erd.md` | `recommendation`, `last_recommendation_mode` |

## 요구사항

### Must Have

- [ ] `RecommendationMode` enum (4값 + `trip.last_recommendation_mode`)
- [ ] `POST /api/v1/trips/{tripId}/recommendations` — `{ mode }` → **계산은 `#50`(`RecommendationEngine`)에 위임** → 기존 rows **hard DELETE** → 결과 TOP 3 INSERT. `#50` 완료 전까지는 플레이스홀더 결과로 계약만 검증
- [ ] `GET /api/v1/trips/{tripId}/recommendations` — 현재 저장된 TOP 3 (+ `mode`, `generatedAt` `[제안]`)
- [ ] `POST /api/v1/trips/{tripId}/confirm` — 방장만 (BR-TRIP-007): `{ recommendationRank }` 또는 `{ startDate, endDate }`
- [ ] confirm → `status=CONFIRMED`, `confirmedStartDate`/`confirmedEndDate` 설정
- [ ] `POST /api/v1/trips/{tripId}/unconfirm`("확정 취소") — 방장만, `status=CONFIRMED`일 때만 호출 가능 (아니면 409 `TRIP_NOT_CONFIRMED`) → `status=ONGOING`으로 되돌리고 `confirmedStartDate`/`confirmedEndDate`를 `null`로 초기화. **새 `TripStatus` 값을 추가하지 않음** — 기존 `ONGOING`으로 단순 복귀(2026-07-24 확정, 근거: `src/new_decision.md` Q1)
- [ ] unconfirm 요청 body에 **사유 필수** — `reason`(enum `UnconfirmReason`) + `reason=OTHER`면 `reasonDetail`(string) 필수 (아니면 400 `INVALID_UNCONFIRM_REASON`). `Trip.unconfirmReason`/`unconfirmReasonDetail`에 최신값 덮어쓰기 (2026-07-24 확정, 기획자 답변)
- [ ] unconfirm 시 `#38` 확정 스냅샷(freeze 결과)을 폐기하고 `ONGOING` 라이브 조회로 되돌림 — 이후 재확정 전까지는 스냅샷 없이 라이브 데이터 사용
- [ ] unconfirm 시 기존 `recommendation` TOP 3 hard DELETE (BR-TRIP-010과 동일 정책 — 재확정하려면 추천을 다시 계산해야 함)
- [ ] `POST .../recommendations` · confirm — **`status=ONGOING`만** (D4 → 409 `TRIP_NOT_ONGOING`)
- [ ] confirm 성공 시 **일정 snapshot** (#38 R-freeze — 동일 TX). 추천 재실행은 CONFIRMED/EXPIRED에서 불가(X8)
- [ ] trip PATCH(기간·일수) / DELETE / mode POST 시 recommendation hard DELETE (BR-TRIP-010)
- [ ] `./gradlew test` — 상태 전이·hard DELETE 트리거 단위 테스트 (모드별 rank·동점·`ALL_ATTEND` 필터 테스트는 `#50` 소관)

### Nice to Have

- [ ] `score`, `reason`, `riskNote` 자동 생성 (한국어 템플릿 `[제안]`)
- [ ] RESPONDED 미만 참여자 있어도 추천 가능 (경고 필드 `[제안]`)

### Out of Scope

- **추천 계산 로직 전체**(후보 윈도우 생성·`#17` resolve 집계·모드별 스코어링·`ALL_ATTEND` 필터·BR-TRIP-012 동점) — `#50`([`trip-recommendation-algorithm.md`](trip-recommendation-algorithm.md))
- 알림 발송 (BR-NOTI-004) — wave 3
- 여행방 **삭제** 시 VOC 사유 — `unconfirm` 사유와 별개, 미정(wave 4)
- 가격·날씨 등 외부 데이터
- 공휴일 API — 주말만 `[제안]` 또는 static `[미정]` (`#50` 소관)

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/api/v1/trips/{tripId}/recommendations` | JWT + owner | 모드별 TOP 3 재계산·저장 |
| GET | `/api/v1/trips/{tripId}/recommendations` | JWT + member | 저장된 TOP 3 조회 |
| POST | `/api/v1/trips/{tripId}/confirm` | JWT + owner | 일정 확정 |
| POST | `/api/v1/trips/{tripId}/unconfirm` | JWT + owner | 확정 취소 (CONFIRMED→ONGOING) |

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

### `POST .../unconfirm`

```json
{
  "reason": "RECOMMENDATION_UNSATISFACTORY"
}
```

`reason=OTHER`인 경우:

```json
{
  "reason": "OTHER",
  "reasonDetail": "직접 입력 텍스트"
}
```

성공 시 `204 No Content` — 확정된 여행방을 다시 조율 중(ONGOING) 상태로 되돌린다.

### 주요 에러 코드

| HTTP | code | 조건 |
|------|------|------|
| 400 | `INVALID_RECOMMENDATION_MODE` | enum 밖 |
| 400 | `NO_RECOMMENDATION_CANDIDATES` | ALL_ATTEND 등 후보 0건 |
| 403 | `TRIP_FORBIDDEN` | 방장 아님 |
| 409 | `TRIP_ALREADY_CONFIRMED` | 중복 confirm |
| 409 | `TRIP_NOT_ONGOING` | recommendations/confirm 호출 시 상태가 ONGOING이 아님(CONFIRMED/EXPIRED) |
| 409 | `TRIP_NOT_CONFIRMED` (신규) | unconfirm 호출 시 상태가 CONFIRMED가 아님 |
| 400 | `INVALID_UNCONFIRM_REASON` (신규) | `reason` enum 밖 또는 `OTHER`인데 `reasonDetail` 없음 |
| 404 | `RECOMMENDATION_NOT_FOUND` | rank 없음 |

## 데이터 모델

- `Trip.lastRecommendationMode` — JPA 컬럼 추가
- `Trip.unconfirmReason` / `Trip.unconfirmReasonDetail` — JPA 컬럼 추가, unconfirm 시 최신값 덮어쓰기 (이력 아님)
- `recommendation` — 기존 엔티티, trip_id FK, hard DELETE only

### BR-TRIP-010 트리거

| 이벤트 | 동작 |
|--------|------|
| POST recommendations (mode 변경) | DELETE all + INSERT 3 |
| PATCH trip **duration** | DELETE all, `lastRecommendationMode=null` `[제안]` (기간은 create 후 불변) |
| DELETE trip | DELETE all |
| confirm | recommendation 유지 `[제안]` (확정 후 조회용) |
| unconfirm | recommendation hard DELETE — 재확정하려면 재계산 필요 |

## 알고리즘 (구현 가이드)

**계산 로직 전체가 `#50`([`trip-recommendation-algorithm.md`](trip-recommendation-algorithm.md))로 이동했다.** 이 스펙은 `#50`이 반환하는 `List<RecommendationCandidate>`를 저장·조회하는 것까지만 다룬다.

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-TRIP-005 | 4모드 TOP 3 계산 | `#50` `RecommendationEngine` |
| BR-TRIP-007 | owner confirm/unconfirm | TripConfirmService |
| BR-TRIP-010 | hard DELETE | RecommendationRepository.deleteByTripId |
| BR-TRIP-011 | ALL_ATTEND filter | `#50` `RecommendationEngine` |
| BR-TRIP-012 | tie-break | `#50` `RecommendationEngine` |

## 검증 시나리오

### 정상

- [ ] POST(플레이스홀더 or `#50` 연결 후 실값) → 3 rows, GET 동일
- [ ] mode 변경 POST → 이전 rows 삭제됨(hard DELETE)
- [ ] confirm rank 1 → CONFIRMED + dates
- [ ] confirm custom dates → CONFIRMED
- [ ] unconfirm → ONGOING, `confirmedStartDate`/`confirmedEndDate` null, 기존 recommendation hard DELETE, snapshot 폐기

### 엣지 · 실패

- [ ] 참여자 confirm → 403
- [ ] PATCH trip endRange → GET recommendations empty
- [ ] unconfirm 호출 시 상태가 CONFIRMED 아님 → 409 `TRIP_NOT_CONFIRMED`
- [ ] 참여자가 unconfirm 호출 → 403 `TRIP_FORBIDDEN`
- [ ] unconfirm `reason` 누락 → 400 `INVALID_UNCONFIRM_REASON`
- [ ] unconfirm `reason=OTHER`인데 `reasonDetail` 없음 → 400 `INVALID_UNCONFIRM_REASON`
- [ ] `ALL_ATTEND` 후보 없음 → 400 `NO_RECOMMENDATION_CANDIDATES` — 실제 필터링은 `#50`, 이 스펙은 예외→HTTP 매핑만

### 단위 테스트 (필수, 이 스펙 범위)

- [ ] hard DELETE 후 count=0
- [ ] confirm/unconfirm 상태 전이(`ONGOING`↔`CONFIRMED`)
- [ ] (모드별 rank·동점 comparator·`ALL_ATTEND` 필터 단위 테스트는 `#50` 소관)

## 완료 기준

- [ ] `./gradlew test` 통과 (RecommendationServiceTest 등)
- [ ] OpenAPI 반영
- [ ] `#50` 연결 완료 시점에 wave 2 MVP 완료 기준(방장이 4모드 중 하나로 **실제 계산된** TOP 3 확인 후 확정) 충족 — 이 스펙만으로는 API 계약까지만 검증

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| BR-TRIP-005 가중치 w1/w2/w3 | `[미정]` → `#50` | MVP는 상대 순위만 맞으면 됨 — 튜닝은 prod 전 |
| 연차 산출 규칙 | `[제안]` → `#50` | IMPOSSIBLE on workday → 1일; `halfVacationAvailable` 반영 `[미정]` |
| regular vs personal 병합 | **Implemented** (#17) | 추천은 resolve **재사용** (C1), 호출은 `#50` |
| 공휴일 데이터 | `[미정]` → `#50` | KR 공휴일 static table vs API |
| confirm 후 recommendation 유지 | `[제안]` | UI 재조회용 |
| NOTI on confirm | wave 3 | stub 없음 |
| `TripStatus.CANCELED` 제거 | **#48 Implemented** | 이 스펙이 유일한 프로듀서였던 `cancel`(→CANCELED) API를 삭제하고 `unconfirm`으로 교체 완료. enum 값 삭제 자체도 `#48`에서 코드로 실행 완료 |
| unconfirm 사유 입력 | 확정 (2026-07-24, 기획자 답변) | 라디오 6종(`OTHER`는 직접입력) — `UnconfirmReason` enum 신설. 구 `cancel_reason`(VOC, wave4) 개념과는 분리 — 여행방 **삭제** 시 VOC 사유는 여전히 미정. 실제 `Trip.unconfirmReason`/`unconfirmReasonDetail` 필드·API 구현은 본 스펙(`#13`)에서 진행 |
| `TERMINATED` → `EXPIRED` 리네임 | **#48 Implemented** | `#27`/`#37`/`#38` 등 관련 스펙 문구도 함께 `EXPIRED`로 갱신 완료 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-24 | 사용자 요청으로 범위 분리 — 이 스펙은 API 설계·DTO·ERD·상태 전이·hard DELETE 트리거만, **추천 계산 로직 전체는 `#50`([`trip-recommendation-algorithm.md`](trip-recommendation-algorithm.md))로 이동** |
| 2026-07-24 | **#48 Implemented** — `TripStatus.CANCELED` enum 삭제, `TERMINATED` → `EXPIRED` 리네임. 본 스펙 코드 참조도 `EXPIRED`로 동기화 |
| 2026-07-24 | `unconfirm` 사유 입력 필수로 확정(기획자 답변) — `UnconfirmReason` enum 6종 + `reasonDetail`(`OTHER`). 관련 문서(`mvp.md`·`waves.md`·`erd.md`·`trip-room-api.md`·`figma-wireframe-v1.md`·`#48`) wave 재분류 동기화 |
| 2026-07-24 | `src/new_decision.md` 확정 반영 — `cancel`(→`CANCELED`) API를 **삭제**, `unconfirm`(CONFIRMED→ONGOING, 새 Status 없음) API로 교체. 관련 에러 코드·시나리오 갱신 |
| 2026-07-08 | 초안 |
| 2026-07-17 | #17 resolve 재사용(C1) · trip-room D4 ONGOING만 · calendar Implemented |
| 2026-07-13 | AVAILABILITY → `regular`/`personal` + `uncertain` |
