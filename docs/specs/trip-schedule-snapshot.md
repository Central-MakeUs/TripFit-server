# 만료(TERMINATED) 여행방 일정 Snapshot

> wave: 2  
> 상태: **Draft**  
> MVP: In scope (만료 방 조회 정합)  
> GitHub: **[#38](https://github.com/Central-MakeUs/TripFit-server/issues/38)**  
> 선행: [`trip-home-schedulers.md`](trip-home-schedulers.md) (#27), [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) (#17), [`trip-schedule-calendar-window.md`](trip-schedule-calendar-window.md)  
> related: [`trip-room-api.md`](trip-room-api.md) (#12), [`trip-recommendation.md`](trip-recommendation.md) (#13)  
> 관련 BR: **BR-USER-008** (전역 일정 — **본 확정과 충돌**)

## 목표

이미 **만료된(TERMINATED) 여행방**을 조회할 때, 만료 시점에 반영돼 있던 일정 정보가 **snapshot처럼** 그대로 보이도록 한다.  
이후 사용자가 전역 `regular_schedule`을 삭제·수정해도 **만료 방의 조회 결과는 바뀌지 않는다**.

## 제품 확정 (2026-07-21)

| ID | 확정 |
|----|------|
| **S1** | 만료된 여행방 조회 시: **만료 시점 전**에 입력된 일정 데이터를 **그대로** 조회 |
| **S1-예** | 만료 후 정기 일정을 삭제해도, 그 만료 방에는 **삭제 전 내용이 남아 있음** (live 전역 일정이 아님) |

## 배경

- 일정은 User 전역(`regular` / `personal`, trip FK 없음) — [`schedule-unified.md`](schedule-unified.md), BR-USER-008.
- `members/schedule-calendar`는 **조회 시점 live resolve** (#17). TERMINATED여도 지금 전역 일정을 다시 펼친다 → 삭제 후 만료 방이 비거나 달라짐.
- #27은 `status=TERMINATED` + Pin 해제만 하고 **일정 freeze는 없음**.

## 요구사항

### Must Have

- [ ] freeze **시점** 확정 (제안: `end_range < today`로 TERMINATED 전환과 **동일 배치** — #27 job 확장, 또는 lazy 최초 TERMINATED 조회 시)
- [ ] freeze **대상** 확정: 멤버별 effective days / regular+personal 원본 / 둘 다
- [ ] 저장 모델 (신규 테이블 또는 JSON 컬럼) + ERD amend
- [ ] `GET .../members/schedule-calendar` — `TERMINATED`(및 정책상 동등한 만료)면 **snapshot 읽기**, live resolve 금지
- [ ] ONGOING/CONFIRMED는 현행 live resolve 유지 (본 스펙 기본안)
- [ ] 예: TERMINATED 후 regular 삭제 → 해당 trip calendar는 삭제 전 effective 유지
- [ ] `./gradlew test`

### Nice to Have

- [ ] snapshot 메타(`frozenAt`) 응답 노출
- [ ] CONFIRMED(일정 확정) 시에도 freeze할지 — 제품 확장

### Out of Scope

- 조회 **윈도우(+2년)** — [`trip-schedule-calendar-window.md`](trip-schedule-calendar-window.md)
- snapshot **재생성·수동 갱신** API
- Google Calendar sync
- 추천(#13)이 TERMINATED 방에서 live/snapshot 중 무엇을 쓸지 — **X8**로 분리 확정

## API / 인터페이스 (초안)

| Method | Path | Auth | 동작 |
|--------|------|------|------|
| GET | `/api/v1/trips/{tripId}/members/schedule-calendar` | JWT + member | trip 만료 → **snapshot**; 그 외 → live (#17) |

쓰기 API(`PATCH .../personal`, regular CRUD)는 **전역 live만** 변경. 만료 방 snapshot은 갱신하지 않음.

## 데이터 모델 (초안 — Approved 전 선택)

옵션 (구현 전 하나 확정):

| 옵션 | 개요 | 장점 | 단점 |
|------|------|------|------|
| **A** | `trip_member_schedule_snapshot` (멤버×날짜 effective 행) | 조회 단순 | 행 수 큼 (~2년×멤버) |
| **B** | trip 또는 trip_member에 **effective JSON** blob | 스키마 단순 | 부분 갱신·쿼리 어려움 |
| **C** | freeze 시점 regular/personal **복사본** + resolve 재실행 | 원본 추적 | resolve 버전 드리프트 리스크 |

Flyway 금지 — 엔티티 + `ddl-auto`, 로컬 DB 리셋 ([`harness-workflow`](../../.cursor/rules/harness-workflow.mdc)).

## 비즈니스 규칙

| BR | 현행 | 본 확정 영향 |
|----|------|--------------|
| **BR-USER-008** | 일정 변경 → **모든 참여 방에 동일** | **만료 방 예외**: snapshot 고정 → BR amend 또는 “ONGOING만 전역”으로 재정의 필요 (**X6**) |

## 검증 시나리오

### 정상

- [ ] ONGOING: live resolve (regular 삭제 시 방 달력 즉시 반영)
- [ ] TERMINATED 전환 후: 동일 GET이 freeze된 내용 반환
- [ ] TERMINATED 이후 regular 삭제: 만료 방 calendar **불변**, 본인 `GET .../users/schedule/calendar`는 삭제 반영

### 예외

- [ ] snapshot 없는 TERMINATED(배치 전·마이그레이션 공백) — 정책: lazy freeze vs 400 vs live fallback (**X7**)

## 충돌 (문서·구현 정합 — 해소 전 구현 금지)

| ID | 충돌 | 한쪽 | 다른쪽 | 해소 방향 (초안) |
|----|------|------|--------|------------------|
| **X6** | **BR-USER-008** | “모든 참여 방에 동일” (전역 live) | 만료 방은 삭제 후에도 **옛 데이터** | BR-USER-008을 “**진행 중(ONGOING) 방**에만 전역 동일”로 amend 제안 · 사용자 승인 필요 |
| **X7** | **#27 배치 vs freeze** | #27 = status/Pin만 | S1 = 일정 데이터 고정 | TERMINATED job에 snapshot 단계 추가 **또는** 별 job · 트랜잭션 경계 결정 |
| **X8** | **추천 #13** | C1: live resolve 재사용 | TERMINATED·과거 방 추천/재조회 시 snapshot? | #13 Out 또는 “TERMINATED는 추천 불가”와 정합 |
| **X9** | **CONFIRMED** | 일정 확정 후에도 live | 만료만 snapshot이면 CONFIRMED 중 전역 수정이 방 달력에 반영됨 | 제품: CONFIRMED도 freeze? 만료만? |
| **X10** | **personal 전역** | personal도 user 전역 | snapshot에 personal 예외 포함 여부 | freeze 대상에 personal 포함 권장 (effective 전체) |
| **X11** | **OpenAPI 공개** | `members/schedule-calendar` — **Hidden 해제·공개** | snapshot은 그 API **읽기 동작** 변경 | 공개와 **독립**. Hidden 공개 완료 후에도 snapshot(#38)은 별도 Approved 필요 |
| **X12** | **조회 윈도우 W1** | +2년 윈도우 | snapshot 저장량 | freeze 범위를 W1과 맞출지, 희망 `startRange`~`endRange`만 저장할지 결정 |

## 완료 기준

- [ ] X6·X7·X9·X12 사용자 확정 후 **Approved**
- [ ] ERD · `docs/specs/README.md` · #27/#12 이슈·스펙 링크
- [ ] Must Have · `./gradlew test`

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-21 | **Draft** — 제품 확정 S1 · 충돌 X6~X12 문서화 |
