# 여행방 참여 플로우

> 상세: [`trip-create-join-guide.md`](trip-create-join-guide.md) · [#39](https://github.com/Central-MakeUs/TripFit-server/issues/39)

## 참여자 (멤버)

1. 초대 링크 → (미멤버) **정기→개별** (수정/Skip)
2. `POST /api/v1/trips/join` `{ inviteCode }` → INSERT **`RESPONDED` 즉시** (+ row0이면 `is_all_free`)
3. **중간 `JOINED` 없음** — “일정 넣고 join = RESPONDED 한 방”
4. 정원 full → 409 · 이미 RESPONDED → idempotent  
   방장(JOINED)이 join으로 우회 → `SCHEDULE_CONFIRM_REQUIRED` → **`schedule/confirm` 사용**

## 방장 (참고)

생성은 [`trip-create.md`](trip-create.md) — create=`JOINED`(입장·공유 불가, create에 inviteCode 없음) → confirm=`RESPONDED`(상세·공유 가능).

## 모집 현황

`memberFillRate = joinedMemberCount / memberCount` · `respondedCount`는 RESPONDED만.

**MVP:** In
