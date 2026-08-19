# 추천 → 확정 → 취소 흐름 (방장 뷰) — API 레퍼런스

> 근거: `docs/specs/trip/trip-recommendation.md`(#13, Approved), `docs/specs/trip/trip-recommendation-algorithm.md`(#50, Approved). 2026-07-30 화면 확인·구현 기반.
>
> 핵심 전제: 추천 후보·근거·피드백은 **방장 전용**이고, 참여자는 **확정된 일정만** 볼 수 있다.

## 요약

| 단계 | 누가 | 화면 |
| --- | --- | --- |
| 추천 요청 | 방장만 | 모드 선택 |
| 카드 목록 확인 | 방장만 | 추천일정(카드 3장) |
| 근거 확인·피드백 | 방장만 | 추천 근거 |
| 확정 | 누르는 건 방장만, **결과는 전원 공개** | 「일정 확정하기」 |
| 확정 취소 | 방장만 | 「취소하기」(사유 폼) |

## API 호출 순서 — 방장

| # | 호출 | 결과 |
| --- | --- | --- |
| 1 | `POST /api/v1/trips/{tripId}/recommendations` `{ mode }` | 기존 추천 hard DELETE 후 TOP 3 계산·저장. `status=ONGOING`이어야 함(아니면 409 `TRIP_NOT_ONGOING`) |
| 2 | `GET /api/v1/trips/{tripId}/recommendations` | 카드 3장(`rank`·기간·참석률·부분참여·불확실·연차일수) |
| 3 | `GET /api/v1/trips/{tripId}/recommendations/{rank}` | 카드 하나 선택 시 상세 — 참여자별 브레이크다운(`attendance`/`uncertainDays`/`vacationDaysNeeded`) + 이전에 남긴 `feedback` |
| 4 | *(선택)* `PATCH /api/v1/trips/{tripId}/recommendations/{rank}/feedback` `{ status, reason?, reasonDetail? }` | "도움이 됐어요/안 됐어요" upsert. `NOT_HELPFUL`이면 `reason` 필수, `OTHER`면 `reasonDetail` 필수 |
| 5-A | 마음에 안 들면: 「다시 추천받기」 → 모드 선택 화면으로 복귀 → 1번부터 반복 | 새 모드로 재호출하면 이전 추천 hard DELETE(이전 근거는 다시 못 봄) — 단 그때 남긴 피드백은 스냅샷으로 계속 보존됨 |
| 5-B | 마음에 들면: `POST /api/v1/trips/{tripId}/confirm` `{ recommendationRank }` 또는 `{ startDate, endDate }` | `status: ONGOING → CONFIRMED`. `confirmedStartDate`/`confirmedEndDate`와 확정 시점 통계(`confirmedAttendCount`/`confirmedVacationMemberCount`/`confirmedUncertainCount`)를 함께 저장, 멤버 일정 스냅샷 freeze(#38), `TripConfirmedEvent` 발행 |
| 6 | `GET /api/v1/trips/{tripId}` | "일정이 확정됐어요" 화면 — **방장·참여자 모두** 동일 데이터로 조회 |
| 7 | *(마음이 바뀌면)* 사유 폼 제출 → `POST /api/v1/trips/{tripId}/unconfirm` `{ reason, reasonDetail? }` | `status: CONFIRMED → ONGOING`. `confirmed*` 필드 전부 `null`(참여자도 더 이상 못 봄), 기존 추천 TOP 3 hard DELETE, 스냅샷 폐기 → 1번부터 재시작 가능 |

## 참여자가 볼 수 있는 것 / 없는 것

| 대상 | 참여자 접근 |
| --- | --- |
| 후보 카드 목록(`GET .../recommendations`) | ❌ 403 `TRIP_FORBIDDEN` |
| 추천 근거 상세(`GET .../recommendations/{rank}`) | ❌ 403 `TRIP_FORBIDDEN` |
| 피드백 저장(`PATCH .../feedback`) | ❌ 403 `TRIP_FORBIDDEN` |
| 확정된 일정(`GET /trips/{tripId}`의 `confirmed*` 필드) | ✅ (`CONFIRMED`일 때만 값 있음, 그 외 `null`) |

## 핵심 포인트

- 후보·근거·피드백은 방장의 "작업 중" 도구다 — 참여자는 확정 전엔 이 도메인에서 아무것도 조회할 수 없다.
- 확정되는 순간 `Trip.confirmed*` 필드가 채워지고, 그 뒤로는 방장·참여자 구분 없이 동일한 `GET /trips/{tripId}` 하나로 확인한다.
- 확정 취소는 새 `TripStatus` 값 없이 그냥 `ONGOING`으로 되돌아간다 — 취소 사유는 매번 최신값만 남고(이력 아님) 이전 추천 TOP 3는 사라진다(재추천 필요).
- 「다시 추천받기」는 별도 API가 아니라 같은 `POST .../recommendations`를 다른(또는 같은) 모드로 다시 호출하는 것이다.
- **여행방 메타(`PATCH /trips/{tripId}`)로 희망 박/일수를 바꾸면 저장돼 있던 추천 TOP3도 같이 hard DELETE된다**(BR-TRIP-010) — 카드 목록 화면을 열어둔 채로 다른 화면(방 설정)에서 기간을 바꾸고 돌아왔다면, 캐시된 카드 목록을 그대로 보여주지 말고 `GET .../recommendations`로 다시 조회해야 한다(비어 있으면 재추천 유도).
- 피드백(`recommendation_feedback`)은 hard DELETE와 무관하게 DB에는 계속 남지만, **조회 가능한 API는 "현재 저장된 추천"의 `GET .../recommendations/{rank}` 하나뿐**이다. 재추천으로 이전 후보가 사라지면 그때 남긴 피드백도 더 이상 어떤 API로도 다시 볼 수 없다(서버 분석용으로만 보존) — FE가 "피드백 이력" 화면을 만들 수 있는 데이터가 아니다.

---

## 엔드포인트 전체 표

| Method | Path | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/trips/{tripId}/recommendations` | 방장(`@TripOwnerOnly`) | 모드별 TOP3 재계산·저장 |
| GET | `/api/v1/trips/{tripId}/recommendations` | 방장 | 저장된 TOP3 카드 목록 |
| GET | `/api/v1/trips/{tripId}/recommendations/{rank}` | 방장 | 후보 1건 상세(참여자별 브레이크다운 + 내 피드백) |
| PATCH | `/api/v1/trips/{tripId}/recommendations/{rank}/feedback` | 방장 | 도움 여부 피드백 upsert |
| POST | `/api/v1/trips/{tripId}/confirm` | 방장 | 일정 확정 |
| POST | `/api/v1/trips/{tripId}/unconfirm` | 방장 | 확정 취소 |

모든 엔드포인트는 방 존재·방장 여부를 `TripAuthorizationInterceptor`가 컨트롤러 진입 전에 검사한다 — 방이 없거나 soft-delete면 404 `TRIP_NOT_FOUND`, 방장이 아니면 403 `TRIP_FORBIDDEN`(둘 다 모든 엔드포인트 공통이라 아래 개별 표에는 생략).

## 이 도메인 밖에서 추천이 사라지는 경우 (BR-TRIP-010)

이 문서의 엔드포인트를 호출하지 않아도 아래 세 가지 트리거로 기존 추천 TOP3가 hard DELETE된다. FE는 아래 이벤트 이후 추천 화면으로 돌아오면 **캐시 없이 `GET .../recommendations`로 재조회**해야 한다(빈 배열이면 재추천 유도).

| 트리거 | 비고 |
| --- | --- |
| `PATCH /trips/{tripId}` 로 희망 박/일수(`durationNights`/`durationDays`) 변경 | 이름·정원·여행지만 바꾸고 기간이 그대로면 삭제되지 않음 |
| `DELETE /trips/{tripId}` (방 삭제) | 방 자체가 soft-delete되므로 추천도 함께 무의미해짐 |
| `POST .../recommendations` 를 다른(또는 같은) 모드로 재호출 | 이 문서 5-A 참고 |

## 1) `POST /recommendations` — 추천 생성

**요청**

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `mode` | `RecommendationMode` enum | ✅ | 아래 "추천 모드" 표 |

**응답 200** — `RecommendationListResponse`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `mode` | enum, nullable | 방금 저장한 모드 |
| `items[]` | 배열(최대 3) | 아래 |
| `items[].rank` | int | 1~3 |
| `items[].startDate` / `endDate` | date | 추천 여행 기간 |
| `items[].attendRate` | int(%) | (전체참석+부분참석 인원)/응답 참여자 수 × 100, 반올림 |
| `items[].partialAttendCount` | int | 부분 참석 인원 수 |
| `items[].uncertainCount` | int | 불확실 일정이 있는 인원 수 |
| `items[].totalVacationDays` | double | 총 연차 일수(반차=0.5) |

**에러**: 400 `INVALID_INPUT`(mode enum 밖·여행 일수 미정) · 409 `TRIP_NOT_ONGOING`

## 2) `GET /recommendations` — 카드 목록

응답 shape은 위 `POST`와 완전히 동일(`RecommendationListResponse`). 추천을 아직 한 번도 안 했으면 `mode=null`, `items=[]`.

## 3) `GET /recommendations/{rank}` — 추천 근거 상세

**응답 200** — `RecommendationDetailResponse`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `rank` | int | |
| `mode` | enum | 이 후보를 계산한 모드 |
| `startDate` / `endDate` | date | |
| `attendRate` / `partialAttendCount` / `uncertainCount` / `totalVacationDays` | 카드와 동일 | |
| `members[]` | 배열 | 응답 참여자 전원(방장 포함), **저장값 아님 — 조회 시점에 다시 계산**(카드 목록을 무겁게 만들지 않기 위함) |
| `members[].name` | string | 동명이인은 `이름(2)` |
| `members[].attendance` | `AttendanceType` enum | 아래 표 |
| `members[].uncertainDays` | int | 이 후보 기간 내 불확실로 표시한 날짜 수 |
| `members[].vacationDaysNeeded` | double | 이 참여자가 이 후보로 확정 시 필요한 연차 일수 |
| `feedback` | 객체 또는 `null` | 방장이 이전에 남긴 피드백. 없으면 `null` |
| `feedback.status` | `RecommendationFeedbackStatus` enum | |
| `feedback.reason` | `RecommendationFeedbackReason` enum, nullable | `status=HELPFUL`이면 `null` |
| `feedback.reasonDetail` | string, nullable | `reason=OTHER`일 때만 값 있음 |

**FE 그룹핑 힌트**: "주의가 필요한 인원"/"참석 가능한 인원" 그룹은 서버가 나누지 않는다 — `attendance != "FULL_ATTEND" || uncertainDays > 0`이면 "주의가 필요한 인원", 아니면 "참석 가능한 인원"으로 FE가 계산.

**에러**: 404 `RECOMMENDATION_NOT_FOUND`(해당 rank 없음 — 재추천 필요)

## 4) `PATCH /recommendations/{rank}/feedback` — 피드백 upsert

**요청** — `SaveRecommendationFeedbackRequest`

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | `RecommendationFeedbackStatus` | ✅ | |
| `reason` | `RecommendationFeedbackReason` | `status=NOT_HELPFUL`일 때 필수 | |
| `reasonDetail` | string | `reason=OTHER`일 때 필수 | 그 외엔 무시됨 |

성공 시 **204 No Content**. 같은 rank로 다시 호출하면 upsert(덮어씀).

**에러**: 400 `INVALID_RECOMMENDATION_FEEDBACK`(사유 누락/불완전) · 404 `RECOMMENDATION_NOT_FOUND`

## 5) `POST /confirm` — 일정 확정

**요청** — `ConfirmTripRequest`. `recommendationRank` **또는** (`startDate`+`endDate`) 중 **정확히 하나만**.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `recommendationRank` | int, nullable | 후보 선택 시 |
| `startDate` / `endDate` | date, nullable | 직접 입력 시 — 일수가 `trip.durationDays`와 같아야 함 |

**응답 200** — `TripDetailResponse`(기존과 동일 shape + 신규 3필드). `status`가 `CONFIRMED`로 바뀌고 아래 필드가 채워진다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `confirmedStartDate` / `confirmedEndDate` | date | |
| `confirmedAttendCount` | int | 확정 시점 참석 인원 수(전체+부분참석) |
| `confirmedVacationMemberCount` | int | 확정 시점 연차가 필요한 인원 수 |
| `confirmedUncertainCount` | int | 확정 시점 불확실 일정이 있던 인원 수 |

이 세 통계는 **확정 시점에 1회 계산해 저장** — 이후 참여자가 개인 일정을 바꿔도 재계산되지 않는다(스냅샷과 동일 시점 고정).

**에러**: 400 `INVALID_CONFIRM_REQUEST`(rank·직접날짜 둘 다 없거나 둘 다 있음) · 400 `CONFIRM_DURATION_MISMATCH`(직접 입력 일수 불일치) · 404 `RECOMMENDATION_NOT_FOUND` · 409 `TRIP_NOT_ONGOING`(이미 CONFIRMED — 재확정하려면 먼저 unconfirm)

## 6) `POST /unconfirm` — 확정 취소

**요청** — `UnconfirmTripRequest`

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | `UnconfirmReason` | ✅ | 아래 표 |
| `reasonDetail` | string | `reason=OTHER`일 때 필수 | |

성공 시 **204 No Content**. `status: CONFIRMED → ONGOING`, `confirmed*` 필드 전부 `null`, 추천 TOP3 hard DELETE, 스냅샷 폐기.

**에러**: 400 `INVALID_UNCONFIRM_REASON` · 409 `TRIP_NOT_CONFIRMED`(CONFIRMED가 아닌 방)

---

## Enum 레퍼런스

### `RecommendationMode` (추천 모드)

| 값 | 한글 | 요약 |
| --- | --- | --- |
| `BASIC` | 기본 | 4개 평가항목 동일 가중치 |
| `ALL_ATTEND` | 모두 참석 | 불참률·부분참석비율 가중치 큼(**하드 필터 아님**) |
| `SAVE_VACATION` | 휴가 아끼기 | 연차 가중치 큼 |
| `CERTAIN` | 확실하게 가기 | 불확실 인원 가중치 큼 |

### `AttendanceType` (참여자 참석 분류)

| 값 | 의미 |
| --- | --- |
| `FULL_ATTEND` | 후보 기간 전체 슬롯 참석 가능 |
| `PARTIAL_ATTEND` | 전체 슬롯 수의 50% 이상을 하나의 연속 구간으로 참석 가능(늦참·조기귀가) |
| `NON_ATTEND` | 위 두 조건 다 불만족 |

### `RecommendationFeedbackStatus`

| 값 | 의미 |
| --- | --- |
| `HELPFUL` | 도움이 되었음 |
| `NOT_HELPFUL` | 도움이 안 됨(`reason` 필수) |

### `RecommendationFeedbackReason` (`status=NOT_HELPFUL`일 때만)

| 값 | 한글 |
| --- | --- |
| `TOO_FEW_ATTENDEES` | 참석 인원이 너무 적어요 |
| `TOO_MANY_VACATION_DAYS` | 연차를 너무 많이 써야 해요 |
| `TOO_MANY_UNCERTAIN_SCHEDULES` | 불확실한 일정이 많이 포함됐어요 |
| `CRITERIA_MISMATCH` | 추천 기준이 제 상황과 안 맞아요 |
| `OTHER` | 기타 (`reasonDetail` 필수) |

### `UnconfirmReason`

| 값 | 한글 |
| --- | --- |
| `NEW_SCHEDULE_ADDED` | 새로운 일정이 생겼어요 |
| `ATTENDEE_AVAILABILITY_CHANGED` | 참석 가능한 인원이 변경되었어요 |
| `RECOMMENDATION_UNSATISFACTORY` | 추천된 일정이 마음에 들지 않아요 |
| `WANT_OTHER_RECOMMENDATION` | 다른 조건으로 다시 추천받고 싶어요 |
| `TRIP_PLAN_CHANGED` | 여행 계획이 변경되었어요 |
| `OTHER` | 기타 (`reasonDetail` 필수) |

## 에러 코드 전체 표 (이 기능 신규분)

| HTTP | code | 발생 API | 조건 |
| --- | --- | --- | --- |
| 400 | `INVALID_RECOMMENDATION_FEEDBACK` | PATCH feedback | `NOT_HELPFUL`인데 `reason` 없음 · `OTHER`인데 `reasonDetail` 없음 |
| 400 | `INVALID_CONFIRM_REQUEST` | POST confirm | `recommendationRank`·직접 날짜 둘 다 없거나 둘 다 있음 |
| 400 | `CONFIRM_DURATION_MISMATCH` | POST confirm | 직접 입력 일수 ≠ `trip.durationDays` |
| 400 | `INVALID_UNCONFIRM_REASON` | POST unconfirm | `reason` 없음 · `OTHER`인데 `reasonDetail` 없음 |
| 404 | `RECOMMENDATION_NOT_FOUND` | GET detail·PATCH feedback·POST confirm(rank) | 존재하지 않는 rank |
| 409 | `TRIP_NOT_CONFIRMED` | POST unconfirm | 방이 `CONFIRMED`가 아님 |

(`TRIP_NOT_FOUND`/`TRIP_FORBIDDEN`/`TRIP_NOT_ONGOING`은 기존 공통 코드 재사용 — 여기 표엔 신규분만.)

## 계산 로직 관련 구현 참고 (FE 문의 대비)

- **응답 참여자 기준:** 이 방의 ACTIVE 멤버 전원. 별도 "미응답 제외" 처리는 없다 — `ACTIVE`가 되려면 반드시 일정 확인 플로우를 거치므로, 일정을 하나도 입력하지 않은 멤버는 "미응답"이 아니라 "넣을 일정이 없어 전부 가능한 사람"으로 항상 응답자에 포함된다.
- **부분 참석 판정:** 오전/오후/저녁 슬롯을 하루 단위 시간순으로 나열했을 때 "가능"이 이어지는 최장 연속 구간 하나만 본다. 떨어진 여러 구간의 합산은 인정하지 않는다(중간 이탈 후 재합류 불인정).
- **연차 필요 일수 판정:** 정기 일정(근무)만으로는 불가능(IMPOSSIBLE)했던 오전·오후 슬롯을 개별 일정으로 덮어써 참석 가능하게 만든 경우에만 발생한다. **저녁 슬롯은 연차 개념에 포함하지 않는다**(반차=오전 또는 오후 하나, 종일=오전+오후 둘 다).
- **불확실 판정:** 후보 기간 내 하루라도 개별 일정에 `uncertain=true`를 표시하면 그 참여자는 불확실 인원 1명으로 카운트(며칠을 표시했든 1명). 참석 분류(전체/부분/불참)와는 독립적 — 불참으로 분류된 사람도 불확실 인원에 포함될 수 있다.
