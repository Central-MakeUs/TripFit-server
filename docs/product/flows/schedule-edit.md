# 일정·조건 입력 플로우

> NotebookLM 기획 자료 정리본.  
> 신규 trip 확인·`RESPONDED`: [`trip-join.md`](trip-join.md) · [`schedule-participation-onboarding.md`](../../specs/schedule-participation-onboarding.md)

- **목적:** 이미 `RESPONDED`인 멤버가 전역 일정(정기·개별)을 수정
- **액터:** 방장, 참여자
- **사전 조건:** 해당 여행방 멤버 · 권장 `RESPONDED` (방 안 진입 후)

**단계:**

1. 여행방 상세·마이페이지에서 「내 일정 수정」
2. 정기 CRUD / 개별 `PATCH /personal` (`items` upsert · **`deletedDates`** 삭제)
3. **저장** — `RESPONDED` **유지** (D-PERSONAL-6). 구 「일정 제출하기」/submit **없음**

**성공 종료 조건:** effective 달력·추천 입력 반영. 기간·일수 변경 시 추천 초기화 (BR-TRIP-010)

**예외 / 분기:**

- 본인 데이터만 수정 (BR-TRIP-004)
- 정기·개별 **둘 다 0행**(CLEAR) → `is_all_free=true` (BR-USER-011). 개별 삭제는 `deletedDates`
- 방장만 trip 메타 수정 (BR-TRIP-009)
- `TERMINATED` 후 메타·추천·초대 제한

**MVP 포함 여부:** In
