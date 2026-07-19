# 여행방 참여·생성 플로우

> SSOT: [`schedule-participation-onboarding.md`](../../specs/schedule-participation-onboarding.md)

## 방장

1. 「방 생성」→ **정기→개별** 일정 플로우 (수정/Skip)  
2. **수정 시** 정기 CRUD / 개별 bulk upsert patch  
3. **방 생성 폼** (이름·기간·인원 등)  
4. `POST /trips` → owner `trip_member` **`RESPONDED`** (+ row0이면 `is_all_free`) → 방 상세  
5. **`JOINED` 없음** · submit **없음**

## 참여자

1. 초대 링크 → (미멤버) **정기→개별** 플로우 (수정/Skip)  
2. **수정 시** 동일 patch  
3. **`POST /api/v1/trips/join`** `{ inviteCode }` → INSERT **`RESPONDED`** (+ row0이면 `is_all_free`)  
4. 정원 full → 409 (MVP · hold [#35](https://github.com/Central-MakeUs/TripFit-server/issues/35))  
5. 이미 멤버 → 방 상세 (BR-USER-010)

## 모집 현황

`memberFillRate = joinedMemberCount / memberCount`

## Prefill

프론트 UX — 백엔드 계약 아님.

**MVP:** In
