# activate API 질문 답변

> ⚠️ 2026-07-28 업데이트: `POST .../schedule/confirm`이 `POST .../activate`로 rename됨(`TripStatus.CONFIRMED`와 이름이 겹쳐 혼동을 유발해서 변경). 에러 코드도 `SCHEDULE_CONFIRM_REQUIRED` → `SCHEDULE_ACTIVATION_REQUIRED`로 바뀜. 아래는 새 이름 기준으로 갱신한 내용.
>
> 근거: `docs/specs/trip/schedule-participation-onboarding.md` (D-JOIN-TRIP-FLOW, D-JOIN-MEMBER, D-PERSONAL-6)

## 질문 1 — "일정 데이터를 저장하는 게 아니라 상태 전환 전용 API"가 맞나요?

**네, 맞습니다.**

- `POST /api/v1/trips/{tripId}/activate`는 정기/개별 일정 데이터 자체를 쓰지 않고, **방장의 `trip_member.status`를 `SCHEDULE_PENDING` → `ACTIVE`로 전환**하는 API입니다.
- 정기·개별 일정(`regular_schedule`, `personal_schedule`)은 방(trip) 단위가 아니라 **User 전역 데이터**라서, 방 생성 직후(`SCHEDULE_PENDING`) 방장이 "정기 → 개별" 일정 확인 화면을 거친 뒤 **마지막에 한 번** 호출해 상태를 확정하는 구조예요.
- 실제로 일정을 입력했는지/수정했는지 여부와 무관하게, **일정 확인 플로우를 끝내고 저장(또는 Skip) 버튼을 눌렀을 때 항상 마지막에 한 번 호출**하는 게 맞습니다.
  - 서버는 activate 시점에 일정 건수를 보지 않습니다(2026-08-18 이전에는 0건이면 `is_all_free=true`를 세팅했으나 해당 컬럼은 삭제됨). 즉 데이터 변경 여부와 무관하게 activate 호출 자체는 항상 필요합니다.

## 질문 2 — "새 방을 만들 때마다 기존 전역 일정을 이 방에도 적용할지 확인받는 절차" 맞나요?

**네, 그 이해가 맞습니다.**

- 스펙의 D-JOIN-TRIP-FLOW 목적이 정확히 이겁니다: "수정되었으면 고치고, 아니면 Skip. 전역 전부 free·기존 일정이 있어도 **신규 trip마다** 플로우 노출 (프리패스 금지)".
- 즉 이미 다른 방에서 일정을 등록해둔 사용자여도, **새 방을 만들 때마다** 정기→개별 확인 화면을 다시 보여주고, 그 화면을 다 통과(또는 Skip)한 뒤 activate를 호출해야 `ACTIVE`로 넘어갑니다.
- 전역 일정이 있어도 "그대로 적용됨" 확인 절차 없이 곧장 방에 들어가는 프리패스는 금지되어 있습니다.

## 질문 3 — activate는 방장이 최초로 SCHEDULE_PENDING → ACTIVE로 넘어갈 때만 필요하고, 이후 마이페이지 등에서 일정 수정 시엔 재호출 불필요한 게 맞나요?

**네, 맞습니다.**

- `SCHEDULE_PENDING` 상태는 **방장이 방을 생성한 직후에만** 존재합니다. 멤버(참여자)는 `POST /trips/join` 시 바로 `ACTIVE`로 INSERT되고, 중간에 `SCHEDULE_PENDING`을 거치지 않습니다 (D-JOIN-MEMBER).
- 방장이 activate를 호출해 `ACTIVE`가 된 이후에는, 그 방에 대해 activate를 다시 호출할 일이 없습니다. `trip_member.status`는 한 번 `ACTIVE`가 되면 이 플로우에서 되돌아가지 않습니다.
- 이후 마이페이지 등에서 정기/개별 일정을 수정하는 경우는 **D-PERSONAL-6 "개인 일정 수정 — 나비효과 없음"** 규칙이 적용됩니다: `ACTIVE` 상태는 그대로 유지되고, 알림도 없고, 방 UI 갱신 유도도 없습니다. 즉 일정 수정 API(`PATCH /users/schedule/personal`, 정기 CRUD)만 호출하면 되고 `activate`를 다시 호출할 필요는 없습니다.

## 요약

| 질문 | 답 |
|------|-----|
| activate = 상태 전환 전용, 데이터 저장 아님 | ✅ 맞음 |
| 새 방마다 기존 전역 일정 적용 확인 절차 | ✅ 맞음 (프리패스 금지) |
| activate는 방장 최초 SCHEDULE_PENDING→ACTIVE 1회만 | ✅ 맞음, 이후 일정 수정은 activate 재호출 불필요 |

## API 호출 순서 — 최초 입장

방장·참여자 각각의 전체 호출 순서(엔드포인트·응답·수정 시 PATCH 경로 포함)는 [`trip-room-create-join.md`](trip-room-create-join.md) 규칙 2·규칙 3·규칙 4 표가 SSOT — 여기서 중복 서술하지 않는다.

**핵심 차이만 요약:** 방장은 `POST /trips`에서 먼저 `SCHEDULE_PENDING` row가 생기고, 맨 마지막에 별도 API(`activate`)로 `ACTIVE` 전환한다. 참여자는 row 생성 자체가 없다가 `POST /trips/join` **한 번**에 "row 생성 + `ACTIVE` 전환"이 동시에 일어나 activate에 대응하는 별도 API가 필요 없다.
