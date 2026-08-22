# 참여자 내보내기

> wave: **2** (Nice)
> implements: (없음 — Figma ROOM_02)
> deferred: 추천 캐시 무효화 → [`trip-recommendation.md`](trip-recommendation.md) / `#13` (#3 보류), 알림 → `#21`
> 상태: **Implemented** — 2026-07-21 [#20](https://github.com/Central-MakeUs/TripFit-server/issues/20) 결정 잠금
> GitHub: **#20**
> 선행: [`trip-room-api.md`](trip-room-api.md) · `#22` · `#39` · [`trip-last-activity-at.md`](trip-last-activity-at.md)

## 목표

방장이 여행방 **MEMBER**를 soft delete로 제외하고, 갱신된 멤버 목록을 반환한다.

## 확정 (#20 · 2026-07-21)

| # | 항목 | 결정 |
|---|------|------|
| 1 | 재가입 | **허용** — soft delete 후 같은 invite로 신규 INSERT |
| 2 | 방 상태 | **`ONGOING`만** (`requireOngoingForMutation`) |
| 3 | 추천 캐시 | **보류** — 이 스펙에서 `recommendation` **미터치**. 대안 A/B/C는 `#20` |
| 4 | 응답 | **`200` + `TripMembersResponse`** |
| 5 | `last_activity_at` | **touch** (`@TripActivity`) |
| 6 | 알림 | **Out** → `#21` |

## Must Have

- [x] `DELETE /api/v1/trips/{tripId}/members/{userId}` — JWT + 방장 (`@TripOwnerOnly`)
- [x] target `MEMBER`만 soft delete (`deleted_at`)
- [x] OWNER target → `CANNOT_REMOVE_OWNER`
- [x] 없는·이미 제외된 멤버 → `TRIP_MEMBER_NOT_FOUND`
- [x] 비 ONGOING → `TRIP_NOT_ONGOING`
- [x] 성공 시 멤버 목록 + `last_activity_at` touch
- [x] user 전역 일정·`recommendation` **삭제/호출 없음**
- [x] `./gradlew test` · OpenAPI

## Out of Scope

- 멤버 자진 탈퇴
- 푸시 (`#21`)
- 추천 hard DELETE / 재계산 (`#13`, #3 보류)

## API

| Method | Path | Auth | 성공 |
|--------|------|------|------|
| DELETE | `/api/v1/trips/{tripId}/members/{userId}` | JWT + 방장 | `200` + `TripMembersResponse` |

### 에러

| 상황 | HTTP | code |
|------|------|------|
| 비방장 | 403 | `TRIP_FORBIDDEN` |
| target OWNER | 400 | `CANNOT_REMOVE_OWNER` |
| target 없음·이미 soft-deleted | 404 | `TRIP_MEMBER_NOT_FOUND` |
| trip ONGOING 아님 | 409 | `TRIP_NOT_ONGOING` |

## 데이터

- `trip_member.deleted_at`만 설정. 스키마 변경 없음.
- 재가입: 기존 join 경로 (active row 없으면 INSERT).

## 완료 기준

- [x] Must Have 전부
- [x] `#26` L1에 “참여자 내보내기” touch 행
- [x] `#20` · `docs/specs/README.md` · `trip-room-api` deferred 링크

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-07-21 | Approved — #20 결정 잠금 반영 |
| 2026-07-21 | Implemented — DELETE members/{userId} |
