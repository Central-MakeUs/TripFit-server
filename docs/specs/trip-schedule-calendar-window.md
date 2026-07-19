# 여행방 일정 조회 윈도우 (+2년)

> wave: 2  
> 상태: **Draft**  
> MVP: In scope (여행방 일정 조회 UX)  
> GitHub: **[#37](https://github.com/Central-MakeUs/TripFit-server/issues/37)**  
> 선행: [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) (#17 Implemented), [`trip-room-api.md`](trip-room-api.md) (#12)  
> related: [`trip-schedule-snapshot.md`](trip-schedule-snapshot.md)  
> 관련 BR: BR-USER-008 (충돌 — 아래 §충돌)

## 목표

여행방 컨텍스트에서 일정(effective) 조회 가능 기간을 **해당 방의 시작 앵커 ~ +2년**으로 고정한다.

## 제품 확정 (2026-07-21)

| ID | 확정 |
|----|------|
| **W1** | 조회는 **해당 여행방의 시작일이 있는 달**을 기준으로 **+2년**까지 |
| **W1-예** | 일수 공식 예시 SSOT: 앵커 `2026-07-20` → 조회 범위 **`2026-07-20` ~ `2028-07-19`** (`end = start.plusYears(2).minusDays(1)`) |

> **구현 착수 전** §충돌 **X1**(앵커가 “달 1일”인지 `startRange` 당일인지)을 해소해야 한다. 본 Draft는 제품 문장·예시를 **둘 다 확정안 원문**으로 보존한다.

## 배경

- #17 **A1**은 `GET /users/schedule/calendar`의 **요청 `startDate`~`endDate` 구간 길이 ≤ 730일**이다. “여행방 시작 기준 +2년 윈도우”와 의미가 다르다.
- `GET .../members/schedule-calendar`는 현재 **`trip.startRange`~`endRange`(희망 기간)** 만 live resolve한다. 희망 기간(수일~수주)과 “+2년 조회 가능”은 별 축이다.

## 요구사항

### Must Have

- [ ] W1 앵커·끝일 계산식을 스펙 **Approved**로 단일화 (X1 해소 후)
- [ ] 적용 API·검증 위치 확정 (아래 제안 표)
- [ ] 기존 A1(730일 구간 상한)과의 관계 문서·코드 정리 (#17 amend)
- [ ] `./gradlew test` — 윈도우 경계·초과 400

### Nice to Have

- [ ] OpenAPI description에 윈도우 공식 명시

### Out of Scope

- TERMINATED snapshot 저장 — [`trip-schedule-snapshot.md`](trip-schedule-snapshot.md)
- 추천 후보 윈도우(#13) — 별도; 본 스펙은 **일정 조회** 축
- Google Calendar (wave 4)

## API / 인터페이스 (초안)

| Method | Path | 변경 방향 (초안) |
|--------|------|------------------|
| GET | `/api/v1/trips/{tripId}/members/schedule-calendar` | **1순위** — 응답·조회 기간을 W1 윈도우로 (현행 `startRange`~`endRange` **대체 또는 상한**) |
| GET | `/api/v1/users/schedule/calendar` | **2순위** — trip 없이 본인 달력. A1 유지 vs W1 도입은 X2 |

요청에 `startDate`/`endDate`를 계속 받을지, 서버가 trip 기준 윈도우만 쓸지는 Approved 시 확정.

## 데이터 모델

- API 전용 규칙. **신규 테이블 없음** (본 스펙).
- ERD 변경 없음.

## 비즈니스 규칙

| BR | 적용 | 비고 |
|----|------|------|
| BR-USER-008 | 전역 live 일정 | W1만으로는 BR 변경 없음. snapshot과 결합 시 충돌 → snapshot 스펙 |
| (A1 #17) | 구간 ≤730일 | W1과 **관계 재정립 필요** (§충돌 X3) |

## 검증 시나리오

### 정상

- [ ] 앵커 `A`에 대해 응답/허용 범위가 `A` ~ `A.plusYears(2).minusDays(1)`
- [ ] 예: `2026-07-20` → `2028-07-19` 포함 조회 가능

### 예외

- [ ] 윈도우 밖 요청 → 400 `INVALID_INPUT` (계약 확정 후)
- [ ] 비멤버 → 기존 권한 오류 유지

## 충돌 (문서·구현 정합 — 해소 전 구현 금지)

| ID | 충돌 | 한쪽 | 다른쪽 | 해소 방향 (초안) |
|----|------|------|--------|------------------|
| **X1** | **앵커 정의** | 문장: “시작일이 **있는 달** 기준” | 예시: **당일** `2026-07-20`부터 | 달 1일 vs `startRange` vs “오늘” 중 **하나**를 Approved 시 선택 |
| **X2** | **적용 API** | W1은 “여행방” 문맥 | #17 본인 `GET .../users/schedule/calendar`는 trip 없음 | 멤버 calendar만 W1 / 본인 calendar는 A1 유지 **또는** query에 `tripId` 추가 — 선택 필요 |
| **X3** | **A1=730일** | `DAYS.between(start,end) ≤ 730` (요청 구간 상한) | W1은 **서버 앵커 기준 고정 윈도우** (~730일 span과 비슷하나 **의미·검증 축이 다름**) | A1 deprecate·amend 또는 “본인 calendar만 A1 / 방 calendar는 W1” 이원화 |
| **X4** | **희망 기간 vs 조회 윈도우** | `members/schedule-calendar` = `startRange`~`endRange` | W1 = 시작 기준 +2년 (희망 `endRange`보다 **길 수 있음**) | 조회 윈도우 ≠ 희망 여행 기간임을 스펙·OpenAPI에 명시. 추천(#13) 입력 범위와 혼동 금지 |
| **X5** | **payload** | A1이 CPU·payload 완화 목적 | W1도 ~2년 sparse resolve → 동일 비용 | sparse 유지 · 상한은 W1로 대체 가능하나 N+1·멤버수 곱은 별도 |

## 완료 기준

- [ ] X1~X3 사용자 확정 반영 후 상태 **Approved**
- [ ] 이슈 Must Have 체크 · `./gradlew test`
- [ ] [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) A1 절 amend
- [ ] [`trip-room-api.md`](trip-room-api.md) D2 / schedule-calendar 기간 절 동기화

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-21 | **Draft** — 제품 확정 W1·예시 · 충돌 X1~X5 문서화 |
