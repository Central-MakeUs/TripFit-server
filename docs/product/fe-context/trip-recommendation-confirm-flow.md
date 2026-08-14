# 추천 → 확정 → 취소 흐름 (방장 뷰)

> 근거: `docs/specs/trip-recommendation.md`(#13), `docs/specs/trip-recommendation-algorithm.md`(#50). 2026-07-30 화면 확인 기반.
>
> 핵심 전제: 추천 후보·근거·피드백은 **방장 전용**이고, 참여자는 **확정된 일정만** 볼 수 있다(상세는 `trip-recommendation.md` "권한 정리" 절).

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
| 4 | *(선택)* `PUT /api/v1/trips/{tripId}/recommendations/{rank}/feedback` `{ status, reason?, reasonDetail? }` | "도움이 됐어요/안 됐어요" upsert. `NOT_HELPFUL`이면 `reason` 필수, `OTHER`면 `reasonDetail` 필수 |
| 5-A | 마음에 안 들면: 「다시 추천받기」 → 모드 선택 화면으로 복귀 → 1번부터 반복 | 새 모드로 재호출하면 이전 추천 hard DELETE(이전 근거는 다시 못 봄) — 단 그때 남긴 피드백은 스냅샷으로 계속 보존됨 |
| 5-B | 마음에 들면: `POST /api/v1/trips/{tripId}/confirm` `{ recommendationRank }` 또는 `{ startDate, endDate }` | `status: ONGOING → CONFIRMED`. `confirmedStartDate`/`confirmedEndDate`와 확정 시점 통계(`confirmedAttendCount`/`confirmedVacationMemberCount`/`confirmedUncertainCount`)를 함께 저장, 멤버 일정 스냅샷 freeze(#38), `TripConfirmedEvent` 발행 |
| 6 | `GET /api/v1/trips/{tripId}` | "일정이 확정됐어요" 화면 — **방장·참여자 모두** 동일 데이터로 조회 |
| 7 | *(마음이 바뀌면)* 사유 폼 제출 → `POST /api/v1/trips/{tripId}/unconfirm` `{ reason, reasonDetail? }` | `status: CONFIRMED → ONGOING`. `confirmed*` 필드 전부 `null`(참여자도 더 이상 못 봄), 기존 추천 TOP 3 hard DELETE, 스냅샷 폐기 → 1번부터 재시작 가능 |

## 참여자가 볼 수 있는 것 / 없는 것

| 대상 | 참여자 접근 |
| --- | --- |
| 후보 카드 목록(`GET .../recommendations`) | ❌ 403 `TRIP_FORBIDDEN` |
| 추천 근거 상세(`GET .../recommendations/{rank}`) | ❌ 403 `TRIP_FORBIDDEN` |
| 피드백 저장(`PUT .../feedback`) | ❌ 403 `TRIP_FORBIDDEN` |
| 확정된 일정(`GET /trips/{tripId}`의 `confirmed*` 필드) | ✅ (`CONFIRMED`일 때만 값 있음, 그 외 `null`) |

## 핵심 포인트

- 후보·근거·피드백은 방장의 "작업 중" 도구다 — 참여자는 확정 전엔 이 도메인에서 아무것도 조회할 수 없다.
- 확정되는 순간 `Trip.confirmed*` 필드가 채워지고, 그 뒤로는 방장·참여자 구분 없이 동일한 `GET /trips/{tripId}` 하나로 확인한다.
- 확정 취소는 새 `TripStatus` 값 없이 그냥 `ONGOING`으로 되돌아간다 — 취소 사유는 매번 최신값만 남고(이력 아님) 이전 추천 TOP 3는 사라진다(재추천 필요).
- 「다시 추천받기」는 별도 API가 아니라 같은 `POST .../recommendations`를 다른(또는 같은) 모드로 다시 호출하는 것이다.
