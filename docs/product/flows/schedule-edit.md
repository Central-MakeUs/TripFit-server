# 일정·조건 입력 플로우

> NotebookLM 기획 자료 정리본.
> 신규 trip 확인·`ACTIVE`: [`trip-create-join-guide.md`](trip-create-join-guide.md) · [`schedule-participation-onboarding.md`](../../specs/trip/schedule-participation-onboarding.md)

- **목적:** 이미 `ACTIVE`인 멤버가 전역 일정(정기·개별)을 수정
- **액터:** 방장, 참여자
- **사전 조건:** 해당 여행방 멤버 · 권장 `ACTIVE` (방 안 진입 후)

**단계:**

1. 여행방 상세·마이페이지에서 「내 일정 수정」
2. 정기 CRUD / 개별 `PATCH /personal` (`items` upsert — `slots`/`uncertain` 각각 선택 갱신, **삭제 경로 없음**, O1.4)
3. **저장** — `ACTIVE` **유지** (D-PERSONAL-6). 구 「일정 제출하기」/submit **없음**

**성공 종료 조건:** 정기+개별 합친 달력·추천 입력 반영. 기간·일수 변경 시 추천 초기화 (BR-TRIP-010)

**예외 / 분기:**

- 본인 데이터만 수정 (BR-TRIP-004)
- **정기 일정을 전부 삭제하고, 개별 일정도 한 번도 등록한 적 없어 둘 다 0행**이 되어도 서버는 아무 플래그도 세우지 않는다 (2026-08-18 `#113` — `is_all_free`·BR-USER-011 폐지). 이미 참여 중인 방에서 튕기지 않는다. **개별 일정은 O1.4 이후 삭제 불가**(`schedule-slot-override.md`) — 한 번이라도 개별 일정을 등록한 유저는 그 row로 이미 `canEnterRoom` 조건(정기 OR 개별 OR `is_all_free`)을 만족하므로 `is_all_free` 전환 자체가 필요 없다(상세: `schedule-participation-onboarding.md` D-JOIN-CLEAR `[미정]` 참고)
- 방장만 trip 메타 수정 (BR-TRIP-009)
- `EXPIRED` 후 메타·추천·초대 제한

**MVP 포함 여부:** In
