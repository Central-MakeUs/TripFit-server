# 사용자 일정 상태 응답 정리 — **Superseded** (구현하지 않음)

> 상태: **Superseded (2026-08-18)** — Draft(2026-08-17)로 작성됐으나 승인 없이 폐기.
> 대체 스펙: [`trip/trip-join-schedule-gate.md`](../trip/trip-join-schedule-gate.md)
> MVP: In scope였던 문제(방 입장 플로우 분기)는 대체 스펙이 모두 흡수했다.

## 이 문서를 남겨두는 이유

설계는 폐기됐지만 **원인 분석은 유효**하고, 대체 스펙(A안)이 그 분석 위에 서 있다. 같은 진단을 다시 하지 않기 위해 기록만 남긴다. **여기 적힌 API·컬럼·enum은 구현 대상이 아니다.**

## 유효한 진단 (대체 스펙이 이어받음)

- **프론트가 서버 계산을 재구현하고 있었다.** 서버에 입장 가능 여부를 판단하는 `UserSummaryService.canEnterRoom`(`isAllFree || hasPreSchedule`)이 있는데 어떤 응답에도 실리지 않아, 프론트가 403을 미리 피하려고 같은 식을 손으로 다시 짰다(`hasSavedSchedule = hasPreSchedule || isAllFree`).
- **그 재구현이 실제 사고로 이어졌다.** QA 이슈 1(정기 일정 없음 사용자가 빠져나올 수 없는 빈 정기 화면에 갇힘) · 이슈 2(P1, 기존 일정 보유 참여자가 일정 확인 없이 방 입장)는 모두 이 조합식을 분기 조건으로 쓰다 생긴 문제다. 수정 지점은 [`fe-context/trip/trip-room-create-join.md`](../../product/fe-context/trip/trip-room-create-join.md) "이미 확인된 위반 2건" 절.
- **`isAllFree`는 이름과 동작이 어긋나 있다.** "전부 free 선언"으로 읽히지만 사용자가 선언하는 API는 없고, 일정 0건인 채로 `join`/`activate`를 호출하는 순간 서버가 자동으로 켠다. 이슈 1이 "방을 두 번 입장해야 재현된다"는 기묘한 조건을 가진 이유가 여기 있다.
- **정기 일정 유무 분기에 `hasPreSchedule`을 쓰면 안 된다.** 정기 0건·개별 1건 이상인 사용자에게 "입력하신 일정을 확인해주세요"를 띄우고 정기 화면이 텅 빈 막다른 길을 만든다.

## 폐기된 설계와 폐기 사유 (2026-08-18 사용자 확정)

| 폐기된 제안 | 폐기 사유 |
|---|---|
| `User.regularScheduleDeclaredNone` 컬럼 + `RegularScheduleState` enum(`NOT_ANSWERED`/`DECLARED_NONE`/`REGISTERED`) + `UserSummaryResponse.regularScheduleState` | **"정기 일정이 있나요?" 질문은 정기 0건이면 방마다 매번 다시 뜬다.** 저장된 이전 답으로 얻는 것은 라디오 버튼 프리필뿐이고 사용자는 어차피 다시 답해야 한다. 그 프리필 하나에 DB 컬럼 1 + 신규 API 1 + enum 1 + `ErrorCode` 1이 붙는다. 게다가 "이미 답했으니 넘어가도 된다"는 방향이라 "매 방 입장 시 일정 재확인 강제(건너뛰기 불가)" 규칙과 반대편으로 흐르기 쉽다 |
| `PATCH /api/v1/users/schedule/regular/declaration` + `UpdateRegularDeclarationRequest` + `REGULAR_SCHEDULE_EXISTS`(409) | 위 컬럼을 쓰기 위한 API — 컬럼과 함께 폐기 |
| `UserSummaryResponse.canEnterRoom` 노출 | **방향이 반대였다.** 프론트가 게이트 식을 재구현한 문제의 해법은 "게이트 값을 하나 더 노출"이 아니라 **"게이트를 하나로 줄이기"**다. 대체 스펙 J-7에서 전역 게이트 자체를 삭제하고, 방 입장 라우팅의 유일한 기준을 `myMemberStatus`로 통일한다 |
| `UserSummaryResponse.hasRegularSchedule` 삭제(→ `regularScheduleState`로 대체) | `hasRegularSchedule`은 **유지**한다. 정기 일정 유무 분기에 필요한 유일한 값이고, `regularScheduleState`가 폐기되면서 대체재가 없어졌다 |

## 대체 스펙이 이어받은 것

| 이 문서가 제기한 문제 | 대체 스펙에서의 해결 |
|---|---|
| 프론트가 `hasPreSchedule \|\| isAllFree`를 재구현 | **J-7 — 전역 게이트(`is_all_free`·`canEnterRoom`·`SCHEDULE_ENTRY_REQUIRED`) 완전 삭제.** 재구현할 식 자체가 사라진다 |
| 정기 유무 분기 값이 없음 | `hasRegularSchedule` 유지 (2026-08-17 작업 트리에 이미 구현) |
| 일정 확인 없이 방 입장 가능(이슈 2, P1) | **J-1 — 참여자 `join`을 `SCHEDULE_PENDING`으로 앞당겨 서버가 강제** |
| `isAllFree`가 자동으로 켜져 두 번째 입장부터 동작이 달라짐 | J-7로 컬럼째 삭제 — 재현 조건 자체가 소멸 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-18 | **Superseded 전환** — 사용자가 A안(전역 게이트 삭제 + `hasRegularSchedule` 유지) 확정. 선언 저장·`regularScheduleState`·`canEnterRoom` 노출 전부 폐기하고, 유효한 진단만 남겨 `trip-join-schedule-gate.md`로 이관 |
| 2026-08-17 | 최초 작성 — QA 이슈 1·2 원인 분석에서 출발. 프론트가 `hasPreSchedule\|\|isAllFree`를 재구현하던 구조를 서버 노출로 대체하려 함 |
