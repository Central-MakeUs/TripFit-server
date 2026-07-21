# 여행방 생성 플로우

> 상세: [`trip-create-join-guide.md`](trip-create-join-guide.md) · [#39](https://github.com/Central-MakeUs/TripFit-server/issues/39)

- **목적:** 방장이 여행방을 만들고 일정 확인 후 입장·초대 준비
- **액터:** 방장(총대)
- **사전 조건:** 로그인 + 프로필 이름 (BR-USER-001)

**단계:**

1. 홈에서 「여행방 신규 생성하기」
2. 방 생성 폼 (이름·기간·일수·인원·선택 여행지)
3. `POST /trips` → OWNER **`JOINED`** + inviteCode (`needsScheduleConfirm=true`)
4. **정기→개별** 일정 확인 (수정/Skip) — `canEnterRoom`이어도 강제
5. `POST /trips/{tripId}/schedule/confirm` → **`RESPONDED`**
6. 방 상세 · 초대 공유

**예외:** confirm 전 이탈 → 재진입 시 일정 플로우. 상세 API는 `SCHEDULE_CONFIRM_REQUIRED`.

**MVP 포함 여부:** In
