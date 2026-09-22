# 개별 일정 = 슬롯 단위 오버라이드 (O1)

> 상태: Approved (#67) — **O1.1~O1.3:** `action` 필드 도입(`OVERRIDE`\|`CLEAR`\|`UNCERTAIN`) → 폐기 이력, 아래 "계약 개정" 절 참고 · **O1.4 amend (2026-07-30, 현재 계약):** `action` 필드·`CLEAR` 액션 전면 제거. 요청 아이템을 `scheduleDate` + 선택적 `slots`/`uncertain` 필드 하나짜리 flat record로 통합. "값 조합(3슬롯 POSSIBLE)으로 CLEAR를 추론"하던 O1.3 설계가 실제로는 **개별 오버라이드가 항상 정기를 이긴다는 핵심 규칙을 깨는 버그**였음을 발견해 CLEAR 자체를 삭제
> MVP: In scope (`docs/product/mvp.md` — "참여자 일정 입력(오전/오후/저녁 단위, 미정 상태 포함)")
> 관련 BR: BR-TRIP-002, BR-TRIP-003, BR-TRIP-004, BR-USER-008
> supersedes: [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) **S1**(개별 존재 시 그 날 전체 대체) · R1(병합 규칙 1) — **R2(정기 복수 IMPOSSIBLE 우선)는 그대로 유지**
> related: [`schedule-unified.md`](schedule-unified.md)

## 목표

정기 일정에 이미 반영된 정보(예: "9~18시 근무라 아침 불가능")가, 사용자가 개별 일정으로 다른 슬롯(예: 오후) 하나만 커스터마이즈하는 순간 **조용히 사라지는 문제**를 구조적으로 없앤다. 개별 일정을 "그 날 전체를 대체하는 행"이 아니라 "**정기+구글이 계산한 기본값 중, 사용자가 명시적으로 손댄 슬롯만 덮어쓰는 오버라이드**"로 재정의한다. **개별 오버라이드는 항상 정기(+구글)를 이긴다** — 이 규칙은 오버라이드 row가 실제로 존재하는 동안에만 적용되므로, "언제 row를 지울지" 판단 로직이 이 규칙을 실수로 깨지 않도록 하는 것이 O1.4의 핵심 관심사다(아래 "계약 개정 이력 — O1.4" 참고).

## UI 동작 확인 (2026-07-29, 사용자 확인 완료)

마이페이지 개별 일정 편집 바텀시트의 실제 동작은 아래와 같다 — 이 절의 내용은 사용자가 직접 확인·확정한 내용이다.

1. **`uncertain`은 슬롯 버튼과 독립된 토글**이다 — "이 날 일정이 바뀔 수도 있어요(불확실해요)"를 켜고 끄는 스위치이며, 아침/오후/저녁 슬롯 버튼과는 별개의 컨트롤이다.
2. **`uncertain=true`가 되면 그 날짜의 아침/오후/저녁 슬롯 버튼 자체가 프론트에서 비활성화(block)된다** — 유저는 그 상태에서 슬롯을 아예 편집할 수 없다. 백엔드도 이 불변식을 뒷받침해야 한다: `uncertain`을 켜고 끄는 동작이 슬롯 오버라이드 값을 건드리면 안 된다.
3. **`uncertain=false`로 다시 끄면, 그 전에 등록해둔 아침/오후/저녁 오버라이드가 그대로 다시 노출되고 다시 수정 가능해진다.** 즉 `uncertain=true`인 동안 슬롯 오버라이드 값은 **어딘가에 그대로 보존**돼 있어야 하며, 꺼지는 순간 다시 드러난다 — 삭제되거나 초기화되면 안 된다.

**이 3번 요구사항이 O1.2로 재설계하게 된 직접적인 이유였다.** 지금은 O1.4의 단일 flat 레코드(`slots`/`uncertain` 각각 선택적 필드)가 이 요구사항을 그대로 만족한다 — `uncertain`만 보낸 요청은 `slots`를 아예 안 건드린다.

## 배경

### 현재(S1) 모델의 문제

`schedule-calendar-resolve.md`의 S1은 "그 날짜에 `personal_schedule` 행이 있으면 슬롯 3개+`uncertain` **전부**를 그 행 값으로 쓰고, 정기는 아예 안 본다"는 규칙이다. `personal_schedule`의 슬롯 3개 컬럼이 `NOT NULL`이라, 클라이언트는 한 슬롯만 고치고 싶어도 나머지 두 슬롯 값을 **반드시 채워서** 보내야 한다.

문제는 이 "나머지 두 슬롯 값"을 채우는 책임이 전적으로 **그 순간 그 화면을 만든 프론트 코드**에 있고, 서버는 이를 검증도 보정도 하지 않는다는 것이다. 마이페이지 캘린더 편집 화면처럼 "먼저 `GET /calendar`로 프리필 후 편집"하는 화면이라면 문제가 없지만, 정기 일정 존재를 모르는 **다른 화면**(예: 별도 설정 페이지, 온보딩 등)이 개별 일정을 저장하면 나머지 슬롯이 기본값(`POSSIBLE`)으로 채워져 정기의 `IMPOSSIBLE` 정보가 그 날짜에 한해 사라진다 — 서버 쪽에 아무 안전장치가 없다.

### 왜 지금 고치는가

- dev 환경, 상용 보존 데이터 없음 → 스키마·계약을 자유롭게 바꿀 수 있는 지금이 비용이 가장 싸다.
- 실제로 `personal_schedule`의 슬롯 컬럼(`morning_status`/`afternoon_status`/`evening_status`)은 **DB 레벨에서는 이미 nullable이다** — `SlotStatuses`(정기·개인 공용 `@Embeddable`)의 `@Column`에 `nullable = false`가 없다. NOT NULL을 강제하는 건 **애플리케이션 검증**(`validatePersonalItem`, DTO `@NotNull`)과 **병합 알고리즘(S1)** 뿐이다. 즉 **DB 마이그레이션이 전혀 필요 없고**, 애플리케이션 로직만 바꾸면 된다.

## 대안 비교

| | **A. in-place nullable (권장)** | B. 슬롯별 별도 오버라이드 테이블 |
|---|---|---|
| 구조 | `personal_schedule`은 그대로 두고, 슬롯 3개를 "값 있으면 오버라이드, `null`이면 오버라이드 없음"으로 재해석 | `schedule_slot_override(user_id, date, slot enum, status)` — 오버라이드가 있는 슬롯만 행으로 존재 |
| `uncertain`(day-level) | 기존 row에 그대로 공존 가능 | 슬롯 단위 테이블과 `uncertain`(day 단위)의 소속이 안 맞아 **테이블을 2개**로 쪼개야 함 |
| 스키마 변경 | **없음** (컬럼이 이미 nullable) | 신규 테이블 생성 + 기존 `personal_schedule`과의 관계 재정의 |
| 조회 로직 | 날짜당 최대 1행 조회는 지금과 동일 | 날짜당 0~3행을 GROUP BY로 슬롯별로 모아야 함 — 조회·매핑 복잡도 증가 |
| upsert 로직 | 슬롯 하나만 `null`로 보내면 그 슬롯만 해제 — 기존 upsert 흐름 그대로 확장 | 슬롯마다 존재 여부 판단 후 insert/update/delete 분기 — 로직·트랜잭션 증가 |
| 표현력 | B와 동일(오버라이드 유무를 완전히 표현 가능) | A와 동일 |

**결론: A.** 표현력은 동일한데 B는 테이블을 늘리고 조회·쓰기 로직만 복잡해진다 — "정규화가 이론적으로 더 낫다"는 이점이, 슬롯이 정확히 3개로 고정된 이 도메인에서는 실익이 없다. 게다가 A는 **스키마 변경이 아예 없다.**

## 요구사항

### Must Have

- [ ] `personal_schedule` 슬롯 3개(`morning_status`/`afternoon_status`/`evening_status`) 검증을 nullable 허용으로 완화 (`PersonalSchedule` 엔티티는 이미 nullable 저장 가능 — DTO·서비스 검증만 변경)
- [ ] `ScheduleCalendarResolver` 병합 알고리즘을 아래 "병합 알고리즘(O1)"대로 재작성 — 슬롯 단위로 `개별 오버라이드 > (정기 ⊕ 구글)` 우선순위 적용
- [ ] **(O1.4)** `PersonalScheduleItem`을 단일 flat record로 정의 — `scheduleDate`(필수) + `slots`(선택, 있으면 3필드 전부 필수) + `uncertain`(선택 `Boolean`). `action` 필드·sealed interface·`ClearItem` **전부 삭제**
- [ ] **(O1.4)** `PersonalSchedule` 엔티티의 부분 업데이트 메서드 유지: `applySlots(morning, afternoon, evening)`(슬롯만 갱신, `uncertain` 불변) / `applyUncertain(boolean)`(`uncertain`만 갱신, 슬롯 불변)
- [ ] **(O1.4)** `ScheduleService.upsertPersonal` — 아이템마다 find-or-create 후 `slots`가 있으면 `applySlots`, `uncertain`이 있으면 `applyUncertain` 호출(둘 다 있으면 둘 다). **row를 삭제하는 코드 경로 자체를 제거** — `isDeleteSignal`·값 조합 추론 삭제
- [ ] **(O1.4)** 검증: `slots`와 `uncertain`이 둘 다 없는 아이템 → `400 INVALID_INPUT`(뭘 바꾸라는 건지 없음). 같은 `scheduleDate`가 `items` 배열에 2번 이상 → `400 INVALID_INPUT`(모호)
- [ ] `GET /calendar`, `GET /trips/{tripId}/members/schedule-calendar` 응답 **모양은 불변** (여전히 날짜별 3슬롯+`uncertain`, 값은 항상 `POSSIBLE`/`IMPOSSIBLE`로 확정돼 내려감 — nullable은 저장 계층에서만 의미가 있고 응답에는 노출 안 함)
- [ ] **(O1.4)** `PATCH /personal` 응답의 `id`는 **이제 항상 non-null** — 삭제 경로가 없으므로 처리된 모든 날짜는 반드시 row를 가진다
- [ ] `ScheduleCalendarResolveService` 단위 테스트 갱신 — 부분 오버라이드·전체 오버라이드·구글 병합 조합 케이스
- [ ] **(O1.4)** `uncertain` 토글이 슬롯을 보존하는지 검증하는 단위 테스트 — `uncertain=true` → 슬롯 불변 확인 → `uncertain=false` → 슬롯 여전히 불변 확인(UI 동작 확인 3번 시나리오)
- [ ] **(O1.4, 버그 회귀 테스트)** 정기 패턴이 일부 슬롯을 `IMPOSSIBLE`로 계산하는 날짜에 사용자가 슬롯 3개를 전부 `POSSIBLE`로 명시 오버라이드해도 **삭제되지 않고 그대로 저장**되는지 확인 — "개별이 항상 정기를 이긴다" 규칙이 깨지지 않는지 검증(`schedule-personal-override-scenarios.md` 시나리오 12 참고)
- [ ] `docs/architecture/erd.md`의 `personal_schedule` 컬럼 nullable 표기(N→Y) + 의미 갱신
- [ ] `schedule-unified.md`, `schedule-calendar-resolve.md` 개정(S1 폐기 반영, 본 스펙 링크) — **미반영 상태, 아래 "리스크·미결정" 참고**
- [ ] Swagger `@RequestBody` 예시를 O1.4 플랫 구조로 갱신(슬롯만/uncertain만/둘 다)
- [ ] 커밋에 `Breaking-Change-Reason:` 트레일러 — `action` 필드·`CLEAR` 액션 삭제, 요청 아이템 구조가 폴리모픽 3종에서 flat 1종(`slots`/`uncertain` 선택적 필드)으로 전환

### Nice to Have

- [ ] `PersonalScheduleItemResponse`에 "이 슬롯이 오버라이드인지 자동계산인지" 구분 필드 (지금은 Out — PATCH 응답도 최종 확정값만 내려줘도 충분)

### Out of Scope (이번 스펙에서 하지 않음)

- `personal_schedule` 테이블/엔티티/API 경로 리네임 (아래 "리스크·미결정" 참고 — 리네임 없이 진행)
- 슬롯별 오버라이드 별도 테이블 분리(대안 B, 채택 안 함)
- 추천(#13) 쪽 반영 — 추천은 `ScheduleCalendarResolver` 재사용 원칙(C1, 기존 스펙)만 유지되면 자동으로 새 알고리즘을 따름, 별도 작업 없음
- **(O1.4, 확정)** 개별 오버라이드를 "정기값으로 되돌리기(초기화)" 하는 기능 — API에서 완전히 제거됨. 한 번 저장된 날짜는 이후 정기 패턴이 바뀌어도 계속 그 값으로 고정되며, 이 화면·API로는 되돌릴 방법이 없다(아래 "계약 개정 이력 — O1.4" 참고)

## API / 인터페이스

| Method | Path | 변경 |
|--------|------|------|
| PATCH | `/api/v1/users/schedule/personal` | **(O1.4)** `items`의 각 항목은 `scheduleDate`(필수) + `slots`(선택, 객체) + `uncertain`(선택, boolean) — `action` 필드·`CLEAR`는 **삭제됨**. 같은 `scheduleDate` 중복은 `400`. row 삭제 경로 없음(응답 `id`는 항상 non-null) |
| GET | `/api/v1/users/schedule/calendar` | **응답 모양·필드 불변.** 내부 계산 로직만 교체 |
| GET | `/api/v1/trips/{tripId}/members/schedule-calendar` | **응답 모양·필드 불변.** 내부 계산 로직만 교체 |

`PATCH /personal` 응답(`PersonalScheduleItemResponse`)의 슬롯 값도 DB에 저장된 원본이 아니라 **`GET /calendar`와 동일하게 정기+구글까지 반영한 최종 확정값**(`POSSIBLE`/`IMPOSSIBLE`)으로 내려준다.

## 계약 개정 이력 — O1 → O1.1 → O1.2 → O1.3 → O1.4

> ⚠️ 아래 O1.1~O1.3 절은 **폐기된 설계의 역사적 기록**이다. 현재 계약은 맨 아래 "O1.4" 절을 따른다.

### O1.1에서 무엇을 시도했었는가 (폐기됨)

최초 O1 설계에서는 별도 필드 없이 **"슬롯 3개가 동시에 `null`이고 `uncertain=false`"라는 값 조합 자체**를 삭제(CLEAR) 신호로 판정했다(`isDeleteSignal`이 슬롯 개수를 셈). 이건 실제로 헷갈림을 유발했다 — 슬롯 1~2개만 `null`이면 삭제가 아니고, 3개 전부 `null`이면 삭제라는 **불연속적 경계**가 있어 FE·BE 모두 실수하기 쉬웠다. 이 저장소는 이미 한 번 같은 종류의 실수를 한 적이 있다 — 예전에 `personal_schedule` 삭제를 위한 별도 `deletedDates` 필드가 있었는데, 이를 없애고 "슬롯 3개 다 `POSSIBLE`"이라는 값 조합으로 삭제를 표현하도록 합친 적이 있다(S1 시절, `schedule-unified.md` 2026-08-05 변경 이력). O1은 그 매직값을 "3개 다 `POSSIBLE`"에서 "3개 다 `null`"로 바꿨을 뿐, 같은 패턴의 문제를 그대로 물려받았다.

이를 해결하려고 O1.1에서 `PersonalScheduleItem.action`(`OVERRIDE`\|`CLEAR`) 필드를 도입해 "슬롯 개수 세기"를 없앴다. 하지만 O1.1은 `uncertain`을 여전히 `OVERRIDE`의 일부 필드로 취급했고, `existing.apply(...)`가 4필드를 통째로 덮어쓰는 구조를 유지했다 — 그 결과 "UI 동작 확인" 절 3번 요구사항(uncertain 토글이 슬롯을 안 건드려야 함)을 채우지 못한다는 게 드러났다. O1.2가 이를 해결했다.

### O1.2 — `action` 3종 + 액션별 폴리모픽 DTO (폐기됨)

`action`마다 필요한 필드만 갖는 별도 타입(`OverrideItem`/`ClearItem`/`UncertainItem`)을 sealed interface로 정의하고, Jackson 폴리모픽 역직렬화(`@JsonTypeInfo` + `@JsonSubTypes`, `action`을 discriminator로)로 구분했다. `PersonalSchedule`에 `applySlots`/`applyUncertain` 부분 업데이트를 분리해 3번 요구사항을 해결했다.

### O1.3 — OVERRIDE 페이로드 구성 기준 + 동일 scheduleDate 복수 item (폐기됨)

O1.2까지는 "OVERRIDE의 슬롯 null = 안 건드림"의 판정 기준이 스펙에 없었다. "baseline(프리필값) 대비 diff"를 검토했으나, `GET /calendar`가 슬롯 값의 오버라이드/기본값 출처를 구분해 내려주지 않아 다른 슬롯의 기존 오버라이드를 조용히 삭제하는 사고를 유발한다는 게 드러나 폐기했다. 대신 **OVERRIDE는 항상 3슬롯을 명시값으로 재전송**하고, **CLEAR는 "3슬롯 모두 POSSIBLE + uncertain=false"라는 최종 값 조합**으로 FE가 판정해 명시적으로 보내도록 했다. `uncertain`+슬롯이 한 세션에서 동시에 바뀌는 경우를 위해 같은 `scheduleDate`에 `OVERRIDE`+`UNCERTAIN` item 2개를 한 PATCH에 담아 보내는 규칙과, 충돌 조합(`CLEAR`+그 외, 같은 action 중복) 400 검증도 추가했다.

**O1.3이 폐기된 이유(2026-07-30 발견):** "3슬롯 모두 POSSIBLE + uncertain=false"는 정기 일정이 없는 날짜(주말 등)에서만 "오버라이드 없음"과 우연히 같은 값이다. **정기 일정이 있는 날짜(예: 9-6 근무, 정기값이 `IMPOSSIBLE/IMPOSSIBLE/POSSIBLE`)에서는 전혀 다른 의미다** — 사용자가 "오늘은 휴가라 하루 종일 여행 가능해요"라며 슬롯 3개를 명시적으로 `POSSIBLE`로 선언하면, 이 값 조합은 CLEAR로 오인되어 오버라이드 row가 삭제되고 정기값(`IMPOSSIBLE/IMPOSSIBLE/POSSIBLE`)으로 조용히 되돌아간다. 이는 **"개별 오버라이드는 항상 정기를 이긴다"는 이 스펙의 핵심 규칙을 정면으로 깨는 버그**다(방금 유저가 명시적으로 선언한 값이 무시됨). 병합 알고리즘(override-wins) 자체는 안 바뀌었지만, CLEAR가 오버라이드 row를 지워버려 그 규칙이 적용될 대상 자체를 없애버린 것이 원인이다.

### O1.4 — `action`·`CLEAR` 완전 삭제, flat 아이템으로 통합 (현재 계약)

O1.3의 버그를 근본적으로 없애기 위해, **"값 조합으로 삭제를 결정하는 경로 자체를 삭제**"했다. CLEAR가 없으므로 `action` 필드도 더 이상 discriminator로서 의미가 없다(`OVERRIDE`/`UNCERTAIN` 두 종류만 남으면 사실상 "둘 다 가능한 하나의 update"와 동일) — 그래서 `action` 필드 자체를 없애고, 슬롯 변경과 uncertain 변경을 **한 아이템 안에서 각각 독립적으로 선택**할 수 있게 합쳤다.

```java
public record PersonalScheduleItem(
    @NotNull LocalDate scheduleDate,
    @Valid SlotUpdate slots,      // nullable — 있으면 3필드 전부 필수(슬롯을 건드림), 없으면 슬롯 안 건드림
    Boolean uncertain              // nullable — 있으면 uncertain 갱신, 없으면 안 건드림
) {}

public record SlotUpdate(
    @NotNull ScheduleStatus morningStatus,
    @NotNull ScheduleStatus afternoonStatus,
    @NotNull ScheduleStatus eveningStatus
) {}
```

| 필드 조합 | 동작 | DB 영향 |
|---|---|---|
| `slots`만 있음 | 슬롯 3개 갱신 | `applySlots`. `is_uncertain` 불변(신규 row면 `false`로 생성) |
| `uncertain`만 있음 | uncertain만 갱신 | `applyUncertain`. 슬롯 컬럼 불변(신규 row면 슬롯 전부 `null`로 생성) |
| 둘 다 있음 | 슬롯·uncertain 둘 다 갱신 | 서로 다른 컬럼이라 한 아이템 안에서 동시 처리, 순서 무관 |
| 둘 다 없음 | 잘못된 요청 | `400 INVALID_INPUT` |

**row는 절대 삭제되지 않는다** — 삭제 코드 경로 자체가 없다. 한 번 저장된 날짜는 이후 정기 패턴이 바뀌어도 계속 그 값으로 고정된다(이미 O1.3에서 확정한 트레이드오프, 이제 예외 없이 전체 적용). 같은 `scheduleDate`가 `items` 배열에 2번 이상 오면(어느 게 이기는지 모호하므로) `400 INVALID_INPUT`.

**예시:**

```json
// 슬롯만 변경 (건드린 슬롯만이 아니라 항상 3개 다 명시 — 이유는 아래 "부분 오버라이드" 시나리오 참고)
{ "items": [ { "scheduleDate": "2026-08-06", "slots": { "morningStatus": "IMPOSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE" } } ] }

// uncertain만 변경 — slots 키 자체가 없음
{ "items": [ { "scheduleDate": "2026-08-06", "uncertain": true } ] }

// 슬롯+uncertain 동시 변경 — 아이템 1개로 끝
{ "items": [ { "scheduleDate": "2026-08-06", "slots": { "morningStatus": "IMPOSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "IMPOSSIBLE" }, "uncertain": false } ] }
```

**같이 제거된 것 — 슬롯 단위 부분 null-복원:** O1.2 당시 `OverrideItem`은 슬롯 필드가 개별적으로 nullable이라 "슬롯 하나만 `null`로 보내 그 슬롯만 정기값으로 복원"하는 것이 이론상 가능했다. 하지만 O1.3이 확정한 "이 화면은 항상 3슬롯을 명시값으로 재전송한다"는 규칙 때문에 실제로는 단 한 번도 쓰인 적이 없는 죽은 경로였다 — 그래서 O1.4에서 `SlotUpdate`의 3필드를 `@NotNull`로 바꿔 이 경로 자체를 없앴다. 이제 슬롯을 건드릴 때는 항상 3개 다 유효한 값이어야 한다.

## 프론트 요청 가이드 — item 1개 기준 가능한 조합 (O1.4)

DTO는 슬롯 3개를 최상위 필드로 흩어놓지 않고 중첩 객체 `SlotUpdate` 하나로 묶는다 — "슬롯을 일부만 보내는" 모양 자체가 DTO 구조상 존재하지 않는다. `uncertain`은 `boolean`(원시형)이 아니라 `Boolean`(nullable 래퍼)이어야 한다 — 원시형이면 "안 보냄"과 "명시적으로 `false`"를 서버가 구분할 수 없어 O1.4의 "안 보내면 안 건드림" 계약 자체를 표현할 수 없다.

```java
public record PersonalScheduleItem(
    @NotNull LocalDate scheduleDate,
    @Valid SlotUpdate slots,   // null 허용 — 있으면 3필드 전부 필수
    Boolean uncertain          // boolean 아님 — null 허용
) {}

public record SlotUpdate(
    @NotNull ScheduleStatus morningStatus,
    @NotNull ScheduleStatus afternoonStatus,
    @NotNull ScheduleStatus eveningStatus
) {}
```

### 가능한 요청 (유효)

| # | 요청(item 1개) | 서버 동작 |
|---|---|---|
| 1 | `{ scheduleDate, slots: {morning, afternoon, evening} }` (`uncertain` 생략) | `applySlots` — 슬롯 3개 갱신. `uncertain`은 기존 값 유지(신규 row면 `false`로 생성) |
| 2 | `{ scheduleDate, uncertain }` (`slots` 생략) | `applyUncertain` — `uncertain`만 갱신. 슬롯은 불변(신규 row면 슬롯 전부 `null`로 생성) |
| 3 | `{ scheduleDate, slots: {...}, uncertain }` | 둘 다 갱신 — 한 항목 안에서 순서 무관하게 동시 처리 |

### 거부되는 요청 (400 `INVALID_INPUT`, `CommonErrorCode` 사용 — 개별 일정 전용 `ScheduleErrorCode`는 없음)

| # | 요청 | 검증 위치 |
|---|---|---|
| 4 | `slots`도 `uncertain`도 없음 | `ScheduleService.validatePersonalItem` (수동 — 필드 조합은 Bean Validation으로 "둘 중 하나는 필수" 표현이 마땅치 않아 서비스에서 처리) |
| 5 | `slots`는 보냈는데 3필드 중 하나라도 없음/`null` | `SlotUpdate` 필드의 `@NotNull` + 부모의 `@Valid` 캐스케이딩 (자동 — 프레임워크가 400 발생) |
| 6 | `slots.*Status`가 `POSSIBLE`/`IMPOSSIBLE` 외의 값 | `ScheduleService`의 수동 검증(enum 값 제한은 Bean Validation만으로 표현 불가 — `ScheduleStatus`에 다른 값이 추가될 수 있음) |
| 7 | 같은 `scheduleDate`가 `items` 배열에 2회 이상 | `ScheduleService.validatePersonalItem` 또는 리스트 레벨 헬퍼(수동 — 아이템 간 비교라 단일 필드 애너테이션으로 불가) |
| 8 | `items`가 빈 배열 | `@NotEmpty` (자동) |

**표현 자체가 불가능한 것(DTO 구조로 원천 차단):** 슬롯 3개 중 1~2개만 채우고 나머지를 `null`로 보내 "그 슬롯만 부분 편집"하는 모양 — `slots`가 중첩 객체라 이런 요청은 5번(필드 누락 400)으로 걸러지며, "슬롯 개수/값 조합으로 삭제를 암묵 추론"하던 O1~O1.3의 경로는 애초에 존재하지 않는다.

## 데이터 모델

- ERD 참조: `docs/architecture/erd.md` `personal_schedule` 절
- **스키마 변경 없음** — `SlotStatuses`(공용 `@Embeddable`)의 슬롯 3개 컬럼은 이미 nullable. `PersonalSchedule` 엔티티 컬럼 자체는 변경 불필요.
- 변경 대상은 **애플리케이션 계층**뿐:
  - `ScheduleService.validatePersonalItem` — `slots`/`uncertain` 둘 다 없으면 `400`, `slots`가 있으면 3필드 모두 `requireSlotStatus`(POSSIBLE/IMPOSSIBLE) 검증
  - `ScheduleService.upsertPersonal` — 삭제 코드 경로 완전 제거(`isDeleteSignal` 삭제). 같은 `scheduleDate` 중복 검증 추가
  - `UpdatePersonalScheduleRequest.PersonalScheduleItem` → **flat record**(`scheduleDate`, `slots`, `uncertain`)로 교체, sealed interface·`action` 삭제
  - `PersonalSchedule` 엔티티 — `applySlots(...)`/`applyUncertain(...)` 유지(부분 업데이트), `apply(...)`(4필드 동시 덮어쓰기)는 없음
  - `ScheduleCalendarResolver` — 변경 없음(resolver는 이미 저장된 슬롯 값만 보고 계산하므로 쓰기 계약 변경과 무관)
- ERD 문서 갱신: `personal_schedule.morning_status`/`afternoon_status`/`evening_status`의 Nullable 컬럼을 `N`→`Y`로, 설명을 "POSSIBLE/IMPOSSIBLE(오버라이드) — null이면 정기+구글 기본값을 그대로 씀"으로 갱신

## 병합 알고리즘 (O1)

날짜 `date`, 슬롯 `slot`(MORNING/AFTERNOON/EVENING)마다 아래 순서로 계산한다. **R2(정기 복수 겹침 시 슬롯별 IMPOSSIBLE 우선)는 기존 그대로 유지.**

```text
function resolveSlot(date, slot, regulars, personal, googleBusy):
  # 1. 정기 계산 (기존 R2 그대로)
  matched = regulars.filter(r => weekday(date) in r.daysOfWeek)
  regularValue = combineImpossibleWins(matched, slot)   # IMPOSSIBLE 우선, 매칭 없으면 null

  # 2. 정기 ⊕ 구글 (기존 Google 병합 그대로, OR)
  if regularValue == IMPOSSIBLE or googleBusy(date, slot) == true:
    base = IMPOSSIBLE
  elif regularValue == POSSIBLE or googleBusy(date, slot) == false:
    base = POSSIBLE
  else:
    base = null   # 정기 매칭도 없고 구글 신호도 없음

  # 3. 개별 오버라이드가 최종 승자
  override = personal?.slotValue(slot)   # personal 없거나 그 슬롯이 null이면 null
  final = override ?? base ?? POSSIBLE   # 끝까지 아무 신호도 없으면 "미입력≠불가능" 정책상 POSSIBLE

  return final

function resolveDay(date, regulars, personal, googleBusyMap):
  if personal == null and no regular matches date and no google data for date:
    return omit (sparse)   # 아무 정보도 없는 날짜
  slots = { MORNING: resolveSlot(...), AFTERNOON: resolveSlot(...), EVENING: resolveSlot(...) }
  uncertain = personal?.uncertain ?? false
  return { date, slots, uncertain }
```

핵심 차이(기존 S1 대비): **개별 오버라이드는 슬롯 단위로만 정기를 이긴다.** 그 날짜에 personal row가 있어도, 오버라이드 안 된 슬롯은 여전히 "정기 ⊕ 구글" 계산값을 쓴다.

**⚠️ 이 규칙은 오버라이드 row가 실제로 존재하는 동안에만 적용된다.** O1.3이 "값 조합으로 row를 삭제"하는 경로를 뒀다가, 사용자가 명시적으로 설정한 값이 삭제 조건과 우연히 일치하는 바람에 이 override-wins 규칙 자체가 무력화되는 버그가 났다(위 O1.3 폐기 사유 참고). O1.4는 삭제 경로 자체를 없애 이 문제를 근본적으로 차단한다 — row가 한 번 생기면 지워지지 않으므로, override-wins 규칙이 항상 그대로 적용된다.

## 유저 시나리오

페르소나(유저 A) 기반의 "캘린더 조회 → 수정 → 결과" 시나리오와 엣지 케이스를 담았던 프론트 공유용 별도 문서(`schedule-personal-override-scenarios.md`)는 더 이상 쓰이지 않아 삭제했다 — 이 스펙(O1.4 계약, `병합 알고리즘`·`계약 개정 이력`)이 유일한 SSOT다.

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-TRIP-002 | 슬롯 단위 가능/불가 — 개별은 이제 "슬롯 단위 오버라이드" | `ScheduleCalendarResolver` |
| BR-TRIP-003 | `uncertain`은 여전히 날짜 단위(슬롯별 아님), 슬롯 오버라이드와 독립적으로 켜고 끌 수 있으며 슬롯 값에 영향을 주지 않는다 | `PersonalSchedule.uncertain` |
| BR-TRIP-004 | 그룹/타인 조회 = 정기+개별+구글을 합친 최종 슬롯값만 (원본 레이어 노출 안 함, 기존 R4 유지) | `MemberScheduleCalendarResponse` |
| BR-USER-008 | 정기·개별 모두 User 전역, trip은 조회 컨텍스트만 (불변) | — |

## 검증 시나리오

### 정상

- [ ] 정기만 있고 개별 없음 → 정기 계산값 그대로(기존과 동일)
- [ ] 정기(아침 IMPOSSIBLE) + `slots`(오후만 IMPOSSIBLE, 나머지도 명시) → **아침 IMPOSSIBLE(정기와 동일하게 명시 오버라이드) / 오후 IMPOSSIBLE(오버라이드) / 저녁은 명시값** — 이번 스펙의 핵심 시나리오
- [ ] 정기 복수 겹침(R2=A) + 개별 오버라이드 없음 → 기존 R2 결과 그대로
- [ ] **(O1.4)** `uncertain=true` → 슬롯 컬럼 불변 확인(기존 오버라이드가 있었다면 그대로), `uncertain`만 true로 반영
- [ ] **(O1.4)** `uncertain=false` → 슬롯 컬럼 여전히 불변, `uncertain`만 false로 반영(`schedule-personal-override-scenarios.md` 시나리오 4~5 왕복)
- [ ] **(O1.4)** 슬롯 오버라이드가 없는 날짜에 `uncertain=true`만 전송 → 신규 row 생성(슬롯 전부 `null`), 슬롯은 정기+구글 계산값 그대로 노출
- [ ] **(O1.4)** 한 아이템에 `slots`+`uncertain`을 같이 전송 → 슬롯·`uncertain` 둘 다 반영(시나리오 6)
- [ ] **(O1.4, 버그 회귀)** 정기 패턴이 일부 슬롯을 `IMPOSSIBLE`로 계산하는 날짜에 슬롯 3개를 전부 `POSSIBLE`로 명시 오버라이드 → **삭제되지 않고 그대로 저장**(시나리오 12)

### 엣지 · 실패

- [ ] **(O1.4)** `slots`와 `uncertain`이 둘 다 없는 아이템 → `400 INVALID_INPUT`
- [ ] `items` 비어 있음 → `400 INVALID_INPUT`(기존과 동일)
- [ ] `slots`가 있는데 3필드 중 하나라도 없거나 `POSSIBLE`/`IMPOSSIBLE` 외의 값 → `400 INVALID_INPUT`
- [ ] **(O1.4)** 같은 `scheduleDate`가 `items` 배열에 2번 이상 → `400 INVALID_INPUT`(시나리오 15)
- [ ] **(O1.4)** 어떤 입력으로도 `personal_schedule` row가 삭제되지 않는지 확인(삭제 API·경로 자체가 없음을 코드 레벨에서 확인)

### 수동 / 통합

- [ ] `PATCH /personal`(부분 오버라이드) → `GET /calendar` 라운드트립으로 나머지 슬롯이 정기값 유지되는지 확인
- [ ] `uncertain=true` → `uncertain=false` 왕복 후 `GET /calendar`로 슬롯 오버라이드가 그대로인지 확인(시나리오 4~5 통합 검증)
- [ ] 여행방 멤버 달력(`GET /trips/{tripId}/members/schedule-calendar`)도 동일한 부분 오버라이드가 반영되는지 확인(공용 resolver 재사용 검증)
- [ ] **(O1.4)** 정기 패턴 변경 후 오버라이드된 날짜는 안 따라가고, 오버라이드 없는 날짜는 새 패턴을 따르는지 라운드트립으로 확인(시나리오 11)

## 완료 기준

- [ ] `./gradlew test` 통과 (`user.schedule.*`, `trip.service.TripMemberQueryService*`, `trip.service.TripScheduleSnapshotService*`)
- [ ] `./gradlew build` 성공
- [ ] 위 검증 시나리오 전부 테스트로 커버
- [ ] **(O1.4)** 부분 오버라이드 유지·정기 변경 트레이드오프·버그 픽스·uncertain 단독/동시·중복 날짜 400·sparse↔비sparse 전환 시나리오 전부 통합 테스트로 커버 — **응답 슬롯 값(아침/오후/저녁)은 사용자 검수 완료된 고정 기준**, 구현 세부사항이 바뀌어도 이 값들은 동일해야 함
- [ ] OpenAPI `@Schema`(`PersonalScheduleItem` flat record, `slots`/`uncertain` 선택적 필드) 반영, `@RequestBody` 예시 갱신
- [ ] `docs/architecture/erd.md`·`schedule-unified.md`·`schedule-calendar-resolve.md` 동기화
- [ ] 커밋 본문에 `Breaking-Change-Reason` 트레일러

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| `personal_schedule` 테이블·엔티티·API 경로(`/schedule/personal`) 리네임 여부 | **유지(확정)** | 이름 그대로 간다. FE 경로·문서 리네임 비용 대비 실익 없음 — 나중에 실제로 혼동이 반복되면 재검토 |
| **개별 오버라이드를 정기값으로 되돌리는(초기화) 기능** | **없음(O1.4에서 API에서 완전 제거, 확정)** | 한 번 저장된 날짜는 이후 정기 패턴이 바뀌어도 계속 고정되며, 되돌릴 방법이 없다. 필요해지면 별도 스펙(전용 리셋 버튼 또는 "진짜 정기 기본값"을 알려주는 API)으로 재검토 |
| `PATCH` 응답(`PersonalScheduleItemResponse`)의 슬롯 값 | **최종 확정값(옵션 A, 확정)** | `GET /calendar`처럼 정기+구글까지 반영한 확정값으로 내려준다. nullable은 저장 계층 전용 — API 응답에는 노출 안 함 |
| `PATCH` 응답의 `id` | **항상 non-null(O1.4, 확정)** | 삭제 경로가 없으므로 처리된 날짜는 반드시 row를 가짐 |
| "슬롯 null 개수/값 조합으로 삭제를 암묵적으로 구분"하던 O1~O1.3의 여러 시도 | **전부 폐기(O1.4에서 최종 해결)** | O1(슬롯 개수) → O1.1/O1.2(`action` 필드 도입) → O1.3(값 조합 CLEAR, 그러나 정기 있는 날 override-wins 규칙을 깨는 버그 발견) → O1.4(CLEAR·`action` 삭제로 근본 해결) |
| `schedule-unified.md` | **반영 완료(2026-07-30)** | O1.4 flat 구조(`slots`/`uncertain`)·삭제 경로 없음으로 개정 완료 |
| `docs/product/fe-context/schedule-calendar-merge.md` | **반영 완료(2026-07-30)** | 규칙 2·규칙 4를 O1.4 flat 구조·삭제 경로 없음으로 개정 완료 |
| 현재 코드 상태 | **O1 이전 단계(확인 완료, 2026-07-30 재확인) — 미착수** | `PersonalScheduleItem`이 여전히 슬롯 3필드가 최상위에 있는 구 flat record(`uncertain`도 `boolean` 원시형)이고, `isDeleteSignal`이 "슬롯 3개 전부 `null` && `uncertain=false`"를 삭제 신호로 판정해 실제로 row를 삭제하는 코드가 남아 있음. `PersonalSchedule.apply()`도 4필드 동시 덮어쓰기(`applySlots`/`applyUncertain` 분리 없음). 문서(본 스펙·`schedule-unified.md`·`schedule-calendar-merge.md`)는 O1.4로 갱신됐으나 실제 구현은 아직 착수 전 — DTO·엔티티·서비스·테스트 반영이 남은 작업 |
| `schedule-calendar-resolve.md` | **영향 없음(확인 완료)** | 이 문서는 읽기(GET) 병합만 다루고, 쓰기 계약 변경과 무관 |
| `docs/architecture/erd.md` | **영향 없음(확인 완료)** | 쓰기 계약은 DB 컬럼이 아니라 애플리케이션 로직이라 ERD엔 등장하지 않는 게 정상 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-29 | Draft 초안 — S1(개별 전체 대체) → O1(슬롯 단위 오버라이드) 전환. 대안 비교(in-place nullable vs 별도 테이블) 포함 |
| 2026-07-29 | **Approved** — 리네임 안 함(이름 유지) · "기본값으로 되돌리기" 버튼 없음(Out of Scope) · PATCH 응답은 최종 확정값(옵션 A) |
| 2026-07-29 | "유저 시나리오(상세)" 절 추가(S-1~S-11) — API 호출·DB row·조회 응답을 단계별로 추적, 구글 캘린더 연동 케이스 포함 |
| 2026-07-29 | **O1.1 amend** — `action`(`OVERRIDE`\|`CLEAR`) 필드 도입. 슬롯 null 개수로 삭제를 암묵 판정하던 계약을 폐기하고 항목마다 의도를 명시 |
| 2026-07-29 | **O1.2 amend** — `action`에 `UNCERTAIN` 추가 + 액션별 폴리모픽 DTO(`OverrideItem`/`ClearItem`/`UncertainItem`). uncertain 토글이 슬롯을 보존해야 한다는 요구사항(UI 동작 확인 3번)을 만족시키려고 `applySlots`/`applyUncertain` 부분 업데이트로 분리 |
| 2026-07-29 | **O1.3 amend** — OVERRIDE는 항상 3슬롯 명시 재전송(baseline-diff는 다른 슬롯의 기존 오버라이드를 조용히 삭제하는 사고를 유발해 폐기) + CLEAR는 값 조합으로 판정 + 동일 `scheduleDate` 복수 item(OVERRIDE+UNCERTAIN) 처리 규칙 |
| 2026-07-30 | S-12~S-21 시나리오 확정(사용자 검수 완료) — O1.3 쓰기 모델을 조회→수정→조회 흐름으로 끝까지 검증 |
| 2026-07-30 | **O1.4 amend — `action` 필드·`CLEAR` 액션 완전 삭제.** O1.3의 "3슬롯 POSSIBLE+uncertain false → CLEAR" 규칙이 정기 일정이 있는 날짜에서는 "오버라이드 없음"과 다른 값이라는 게 드러남 — 유저가 정기 스케줄을 뒤집어 명시적으로 "하루 종일 가능해요"를 선언해도 이 값 조합과 우연히 일치하면 CLEAR로 오인되어 오버라이드가 삭제되고 정기값으로 되돌아가는 버그 발견("개별은 항상 정기를 이긴다"는 핵심 규칙 위반). 근본 해결로 CLEAR 자체를 제거 — 이제 `personal_schedule` row는 절대 삭제되지 않는다. `action`이 discriminator로서 의미가 없어져(OVERRIDE/UNCERTAIN 두 종류만 남으면 사실상 하나) `PersonalScheduleItem`을 flat record(`scheduleDate` + 선택적 `slots`/`uncertain`)로 통합, 동일 날짜에 슬롯+uncertain을 한 아이템에 같이 담을 수 있어 O1.3의 "복수 item 그룹핑" 메커니즘도 통째로 제거됨. O1.2 당시부터 실사용된 적 없던 슬롯 단위 부분 null-복원(`OverrideItem`의 개별 슬롯 null)도 함께 제거 — `SlotUpdate`의 3필드를 `@NotNull`로 전환. 유저 시나리오 전체를 O1.4 계약에 맞게 재작성·재검수, 옛 S-4(부분 null 복원)·S-5(CLEAR)·S-18/S-19(action 충돌 400)·S-21(idempotent delete)은 제거하고 번호를 S-1~S-17로 정리, 버그 회귀 검증 시나리오(S-13) 추가 |
| 2026-07-30 | **유저 시나리오 분리** — 스펙 안에 있던 "유저 시나리오(상세)"(S-1~S-17)를 [`docs/product/fe-context/schedule-personal-override-scenarios.md`](../../product/fe-context/user-schedule/schedule-personal-override-scenarios.md)로 분리·재작성. 페르소나(유저 A) 기반 서술로 바꾸고 "캘린더 조회(수정 전) → 수정 → 결과·캘린더 표시" 3단계 형식으로 통일, 시나리오 1~18로 확장(uncertain+슬롯 동시 변경 유지 확인, 무변경 저장 시 저장 버튼 비활성, `slots` 부분 필드 누락 400 등 엣지 케이스 추가). 스펙 본문은 이 문서로의 포인터 + 짧은 요약만 남기고, 이 스펙(O1.4 계약)을 계속 SSOT로 유지 |
| 2026-07-30 | **문서 정합 보완** — "프론트 요청 가이드"(item 1개 기준 가능한 조합 3종 + 거부되는 요청 5종 표) 절 신설. `schedule-unified.md`·`docs/product/fe-context/schedule-calendar-merge.md`를 O1.4 flat 구조·삭제 경로 없음으로 개정하고 "리스크·미결정" 표의 해당 항목을 반영 완료로 갱신. 현재 코드 상태(여전히 구 `isDeleteSignal`/`apply()` 4필드 덮어쓰기, O1.4 미착수)를 재확인·기록 — 시나리오 14 "무변경 시 저장 버튼 비활성" 관련 후속 논의는 `[미정]` [#2](https://github.com/Central-MakeUs/TripFit-server/issues/2)로 별도 등록(`schedule-personal-override-scenarios.md` 시나리오 14 참고) |
