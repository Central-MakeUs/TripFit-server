# 여행방 참여 플로우

> 상세: [`trip-create-join-guide.md`](trip-create-join-guide.md) · [#39](https://github.com/Central-MakeUs/TripFit-server/issues/39)

## 참여자

1. 초대 링크 → (미멤버) **정기→개별** (수정/Skip)
2. `POST /api/v1/trips/join` `{ inviteCode }` → INSERT **`RESPONDED`** (+ row0이면 `is_all_free`)
3. 정원 full → 409 · 이미 RESPONDED → idempotent · JOINED(방장)이 join 재호출 → `SCHEDULE_CONFIRM_REQUIRED`

## 방장 (참고)

생성은 [`trip-create.md`](trip-create.md) — create=`JOINED` → confirm=`RESPONDED`.

## 모집 현황

`memberFillRate = joinedMemberCount / memberCount` · `respondedCount`는 RESPONDED만.

**MVP:** In
