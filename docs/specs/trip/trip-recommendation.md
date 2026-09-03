# 추천 4모드 · TOP 3 · 확정·취소

> implements: BR-TRIP-007, BR-TRIP-010 (API·DTO·ERD·상태전이만 — 계산 로직은 `#50`)
> deferred: BR-NOTI-004 확정 알림 (MVP 출시), **추천 계산 로직(후보 윈도우·모드별 스코어링·동점) → [`trip-recommendation-algorithm.md`](trip-recommendation-algorithm.md) (#50, BR-TRIP-005·011·012)**
> 상태: **Implemented** (`#13` Closed) — 원 승인일 2026-07-30(화면 확인·권한 정리 반영 후 확정)
> 선행: [`schedule-unified.md`](../user-schedule/schedule-unified.md) (#11), [`schedule-calendar-resolve.md`](../user-schedule/schedule-calendar-resolve.md) (#17), [`trip-room-api.md`](trip-room-api.md) (#12), **[#22](https://github.com/Central-MakeUs/TripFit-server/issues/22)** (ACTIVE·sparse·submit)
> **2026-07-30 화면 확인:** 추천 결과 카드 UI 반영 — 아래 "요구사항"·"API / 인터페이스"·"데이터 모델" 절 참고. `ALL_ATTEND` 하드 필터·`NO_RECOMMENDATION_CANDIDATES` 에러는 [`trip-recommendation-algorithm.md`](trip-recommendation-algorithm.md) 2026-07-30 개정으로 폐기됨 — 이 스펙도 동기화
> **2026-07-30 기획 개정(추가):** 추천 후보(TOP 3 카드)·추천 근거 상세는 **방장만** 볼 수 있음. 참여자는 후보를 전혀 볼 수 없고 **확정된 일정만**(기존 `Trip.confirmedStartDate`/`confirmedEndDate`) 볼 수 있다 — `GET .../recommendations`·`GET .../recommendations/{rank}`·`PATCH .../recommendations/{rank}/feedback` 전부 `JWT + owner`로 개정(구 "JWT + member" 폐기)

## 목표

방장이 **4가지 추천 모드**로 TOP 3 후보를 받고, 후보 선택 또는 직접 날짜 입력으로 일정을 확정·취소한다. MVP 완료 기준의 **추천·확정** 축.

**2026-07-24 범위 분리:** 이 스펙은 **API 설계·요청/응답 껍데기·DTO·ERD·상태 전이·hard DELETE 트리거**만 담당한다. `POST /recommendations`가 실제로 반환할 **TOP 3 계산 로직**(후보 윈도우 생성·모드별 스코어링·동점 처리)은 [`trip-recommendation-algorithm.md`](trip-recommendation-algorithm.md)(`#50`)로 분리했다. 이 스펙만으로 구현하는 동안 `POST /recommendations`는 **플레이스홀더 값**으로 응답해 API 계약만 검증한다.

## 배경

- **ERD:** `recommendation` (rank 1~3), `trip.last_recommendation_mode`, `trip.confirmed_*`, `TripStatus`
- **JPA:** `Recommendation` 엔티티 존재, Service·API 없음
- **BR-TRIP-005:** MVP 출시 **4모드 전부** — BASIC, ALL_ATTEND, SAVE_VACATION, CERTAIN
- **BR-TRIP-010:** 모드·기간·일수 변경·trip delete → `recommendation` **hard DELETE**
- **저장 정책:** trip당 **현재 모드 TOP 3만** 유지 (이전 모드 결과는 DELETE)

### 추천 모드

| enum | 한글 (UI) | 요약 |
|------|-----------|------|
| `BASIC` | 기본 | 참석↑ · 연차↓ · TBD↓ 균형 |
| `ALL_ATTEND` | 모두 참석 | BR-TRIP-011(개정) — 하드 필터 아님, 불참률·부분참석 가중치를 크게 둬 점수로만 반영 |
| `SAVE_VACATION` | 휴가 아끼기 | 연차 소모 최소화 |
| `CERTAIN` | 확실하게 가기 | TBD 최소화 |

### 확정 취소(unconfirm) 사유 (2026-07-24 확정, 기획자 답변)

방장이 `unconfirm` 호출 시 라디오 버튼으로 사유 1개 선택(프론트) → 백엔드는 enum으로 저장. `OTHER` 선택 시 직접 입력 텍스트 필수.

| enum | 한글 (UI) |
|------|-----------|
| `NEW_SCHEDULE_ADDED` | 새로운 일정이 생겼어요 |
| `ATTENDEE_AVAILABILITY_CHANGED` | 참석 가능한 인원이 변경되었어요 |
| `RECOMMENDATION_UNSATISFACTORY` | 추천된 일정이 마음에 들지 않아요 |
| `WANT_OTHER_RECOMMENDATION` | 다른 조건으로 다시 추천받고 싶어요 |
| `TRIP_PLAN_CHANGED` | 여행 계획이 변경되었어요 |
| `OTHER` | 기타 (직접 입력 — `reasonDetail` 필수) |

`Trip.unconfirmReason`/`Trip.unconfirmReasonDetail`에 **최신값만** 저장(덮어쓰기, 이력 아님). 구 `cancel_reason`(취소·**삭제** VOC, 출시 이후)과는 별개 — 여행방 **삭제** 시 VOC 사유는 여전히 미정.

### 관련 문서

| 문서 | 내용 |
|------|------|
| `docs/product/flows/trip-confirm.md` | 확정 플로우 |
| `docs/product/business-rules/trip.md` | BR-TRIP-005~012 |
| `docs/architecture/erd.md` | `recommendation`, `last_recommendation_mode` |

## UX 흐름 (2026-07-30 화면 확인 — 추천 결과 카드 · 추천 근거 상세)

1. 방장이 **방 `ONGOING` 상태에서만** 「추천 일정 확인하기」로 모드 4개 중 하나를 골라 `POST .../recommendations` 호출
2. 서버가 해당 모드의 TOP 3를 계산·저장(hard DELETE + INSERT). 카드 목록 화면은 카드 3장을 캐러셀로 노출 — 각 카드: 순위(왕관 아이콘), 기간(`startDate`~`endDate`), `참석률 N%`, `불확실 일정 N명`, `부분 참여 N명`, `연차 일수 N일`
3. **각 카드마다** 「일정 확인하기」 버튼으로 그 카드(rank)의 **추천 근거 상세** 화면으로 진입(`GET .../recommendations/{rank}`) — 참석률 진행바, 4개 통계, 그리고 **참여자별 브레이크다운**: "주의가 필요한 인원"(불참·부분참석·불확실 일정 있음) / "참석 가능한 인원"(전체참석 & 불확실 없음, 연차 필요 여부는 별도 표시) 그룹으로 이름·상태·불확실 일수·필요 연차일수 노출
4. 카드 목록·추천 근거 상세 어느 화면에서든 「일정 확정하기」 — 그 카드의 `rank`로 `POST .../confirm`(`{ recommendationRank }`) 호출 → `status: ONGOING → CONFIRMED`. 확정 취소 시 `POST .../unconfirm` → 다시 `ONGOING`(신규 `TripStatus` 없음, 기존 값 그대로)
5. 추천 근거 상세 화면의 「이 추천이 도움이 되었나요?」 — 👍/👎 중 하나만 선택 가능한 **upsert**(`PATCH .../recommendations/{rank}/feedback`, **방장만**). 👍는 사유 없이 저장, 👎는 사유(enum, `기타`면 서술형 필수) 입력 후 저장. 방장이 이 화면을 다시 열면 이전에 남긴 선택 상태를 그대로 보여준다(`GET .../recommendations/{rank}`의 `feedback`)
6. 「다시 추천받기」 — **모드 선택 화면으로 돌아간다**(2026-07-30 확정). 신규 API 없음 — 사용자가 모드를 다시 고르면 동일한 `POST .../recommendations`를 그 모드로 재호출
7. 카카오톡 공유 버튼 — 서버 신규 API 없음. `kakao-invite-share.md`와 동일하게 프론트가 이미 조회한 `GET .../recommendations` 응답 값으로 템플릿을 조립해 카카오 SDK로 공유(위 Nice to Have 참고)
8. **확정 완료 화면("여행 일정이 확정됐어요!")** — `confirm` 성공 직후(및 이후 재방문 시에도) **방장·참여자 모두** 볼 수 있는 화면. `참석 N명`·`연차 사용 N명`·`불확실 일정 N명` + `confirmedStartDate`~`confirmedEndDate`. **신규 API를 만들지 않고 기존 `GET /trips/{tripId}`(`TripDetailResponse`)에 필드 3개를 추가**하는 쪽으로 결정했다(아래 "데이터 모델" 절) — 이 화면은 FE가 이미 방 상세를 들고 있는 시점(confirm 응답 또는 상세 재조회)에 뜨고, 별도 조회 API를 새로 추가하면 confirm 흐름에서 호출이 하나 더 늘 뿐이라 실익이 없다고 판단
9. 「일정 확정하기」 버튼을 누르면 뜨는 확정 취소(unconfirm) 사유 입력 폼(라디오 6종 + 기타 서술형) — **기존 스펙 그대로**(`UnconfirmReason` enum, 아래 "확정 취소(unconfirm) 사유" 절)와 문구·구조 동일함을 화면으로 재확인. 변경 없음

## 권한 정리 (Authorization Matrix, 2026-07-30 확정)

추천·확정 관련 API는 **역할(방장/참여자) 게이트**와 **여행방 상태 전제**가 따로 걸린다 — 둘을 혼동하지 않도록 한 표로 정리한다.

| API | 방장(Owner) | 참여자(Member) | 상태 전제 | 상태 위반 시 |
| --- | --- | --- | --- | --- |
| `POST .../recommendations` | ✅ | ❌ 403 `TRIP_FORBIDDEN` | `ONGOING`만 | 409 `TRIP_NOT_ONGOING` |
| `GET .../recommendations` | ✅ | ❌ 403 `TRIP_FORBIDDEN` | 제한 없음(아직 생성 전이면 빈 목록) | — |
| `GET .../recommendations/{rank}` | ✅ | ❌ 403 `TRIP_FORBIDDEN` | 제한 없음 | 404 `RECOMMENDATION_NOT_FOUND`(없는 rank) |
| `PATCH .../recommendations/{rank}/feedback` | ✅ | ❌ 403 `TRIP_FORBIDDEN` | 제한 없음 | 400 `INVALID_RECOMMENDATION_FEEDBACK` · 404 `RECOMMENDATION_NOT_FOUND` |
| `POST .../confirm` | ✅ | ❌ 403 `TRIP_FORBIDDEN` | `ONGOING`만 | 409 `TRIP_NOT_ONGOING` |
| `POST .../unconfirm` | ✅ | ❌ 403 `TRIP_FORBIDDEN` | `CONFIRMED`만 | 409 `TRIP_NOT_CONFIRMED` · 400 `INVALID_UNCONFIRM_REASON`(사유 누락/`OTHER`인데 상세 없음) |
| `GET /trips/{tripId}` (`confirmedStartDate`/`confirmedEndDate`/`confirmedAttendCount`/`confirmedVacationMemberCount`/`confirmedUncertainCount`) | ✅ | **✅** (유일하게 참여자도 접근) | `CONFIRMED`일 때만 값 존재, 그 외 전부 `null` | — |

**핵심 원칙 한 줄:** 추천 후보·근거·피드백은 방장의 "작업 중" 도구라 방장 전용이고, 확정된 결과(`Trip.confirmed*`)만 전원에게 공개된다. 참여자는 이 도메인에서 **읽기 권한조차 후보 단계엔 없고, 확정 이후에만** 생긴다.

## 요구사항

### Must Have

- [x] `RecommendationMode` enum (4값 + `trip.last_recommendation_mode`)
- [x] `POST /api/v1/trips/{tripId}/recommendations` — `{ mode }` → **계산은 `#50`(`RecommendationEngine`)에 위임** → 기존 rows **hard DELETE** → 결과 TOP 3 INSERT. `#50` 완료 전까지는 플레이스홀더 결과로 계약만 검증
- [x] `GET /api/v1/trips/{tripId}/recommendations` — 현재 저장된 TOP 3 (+ `mode`, `generatedAt` `[제안]`)
- [x] `POST /api/v1/trips/{tripId}/confirm` — 방장만 (BR-TRIP-007): `{ recommendationRank }` 또는 `{ startDate, endDate }`
- [x] confirm → `status=CONFIRMED`, `confirmedStartDate`/`confirmedEndDate` 설정
- [x] confirm 시 `#50`의 `classifyMembers(confirmedStartDate, confirmedEndDate, activeMembers)`를 호출해 **`Trip.confirmedAttendCount`(전체+부분참석 인원수)·`confirmedVacationMemberCount`(연차 필요 인원수)·`confirmedUncertainCount`(불확실 일정 인원수)를 그 시점 값으로 저장** — "여행 일정이 확정됐어요" 화면용(방장·참여자 모두 조회, `GET /trips/{tripId}`에 노출 — `trip-room-api.md` amend). unconfirm 시 셋 다 `null`로 초기화
- [x] `POST /api/v1/trips/{tripId}/unconfirm`("확정 취소") — 방장만, `status=CONFIRMED`일 때만 호출 가능 (아니면 409 `TRIP_NOT_CONFIRMED`) → `status=ONGOING`으로 되돌리고 `confirmedStartDate`/`confirmedEndDate`를 `null`로 초기화. **새 `TripStatus` 값을 추가하지 않음** — 기존 `ONGOING`으로 단순 복귀(2026-07-24 확정, 근거: `src/new_decision.md` Q1)
- [x] unconfirm 요청 body에 **사유 필수** — `reason`(enum `UnconfirmReason`) + `reason=OTHER`면 `reasonDetail`(string) 필수 (아니면 400 `INVALID_UNCONFIRM_REASON`). `Trip.unconfirmReason`/`unconfirmReasonDetail`에 최신값 덮어쓰기 (2026-07-24 확정, 기획자 답변)
- [x] unconfirm 시 `#38` 확정 스냅샷(freeze 결과)을 폐기하고 `ONGOING` 라이브 조회로 되돌림 — 이후 재확정 전까지는 스냅샷 없이 라이브 데이터 사용
- [x] unconfirm 시 기존 `recommendation` TOP 3 hard DELETE (BR-TRIP-010과 동일 정책 — 재확정하려면 추천을 다시 계산해야 함)
- [x] `POST .../recommendations` · confirm — **`status=ONGOING`만** (D4 → 409 `TRIP_NOT_ONGOING`)
- [x] confirm 성공 시 **일정 snapshot** (#38 R-freeze — 동일 TX). 추천 재실행은 CONFIRMED/EXPIRED에서 불가(X8)
- [x] trip PATCH(기간·일수) / DELETE / mode POST 시 recommendation hard DELETE (BR-TRIP-010)
- [x] **`GET /api/v1/trips/{tripId}/recommendations`·`GET .../recommendations/{rank}`는 방장만** (2026-07-30 기획 개정 — 구 "JWT + member" 폐기). 참여자는 후보·추천 근거를 볼 수 없고 **확정된 일정만**(`Trip.confirmedStartDate`/`confirmedEndDate`, 기존 `GET /trips/{tripId}`) 조회 가능 → 참여자 호출 시 403 `TRIP_FORBIDDEN`
- [x] `GET /api/v1/trips/{tripId}/recommendations/{rank}` — 특정 후보 상세: 참석률·4개 통계 + **참여자별 브레이크다운**(이름·`FULL_ATTEND`/`PARTIAL_ATTEND`/`NON_ATTEND`·불확실 일수·필요 연차일수) + 방장이 남긴 `feedback`(없으면 null). 참여자별 브레이크다운은 **저장하지 않고** 그때그때 `#50`의 참여자 분류 로직을 해당 rank의 `startDate`~`endDate`로 재실행해 계산(라이브 재계산 — 카드 목록의 무거운 페이로드 방지)
- [x] `PATCH /api/v1/trips/{tripId}/recommendations/{rank}/feedback` — **방장만**(2026-07-30 기획 개정 — 조회 자체가 방장 전용이라 피드백도 방장만 남길 수 있음). `{ status: "HELPFUL"|"NOT_HELPFUL", reason?, reasonDetail? }`. `status=NOT_HELPFUL`이면 `reason`(enum `RecommendationFeedbackReason`) 필수, `reason=OTHER`면 `reasonDetail` 필수(아니면 400 `INVALID_RECOMMENDATION_FEEDBACK`). unique(recommendation_id) — 방장이 같은 후보를 다시 보면 이전 선택을 덮어씀
- [x] 피드백은 **모드 변경 hard DELETE에도 살아남는다** — `recommendation_id`는 FK 제약 없는 참조값으로만 저장하고 `mode`/`rank`/`startDate`/`endDate`를 피드백 행에 스냅샷으로 같이 저장(추천 품질 분석 목적, `recommendation` 테이블과 생명주기 분리)
- [x] `./gradlew test` — 상태 전이·hard DELETE 트리거·피드백 upsert·유효성 검증 단위 테스트 (모드별 rank·동점·참여자 분류 테스트는 `#50` 소관)

### Nice to Have

- [ ] ACTIVE 미만 참여자 있어도 추천 가능 (경고 필드 `[제안]`)
- [ ] 추천 카카오톡 공유 — [`kakao-invite-share.md`](kakao-invite-share.md)와 동일 역할 분담(프론트 SDK·템플릿, 서버는 데이터만). `GET .../recommendations` 응답이 카드에 필요한 값을 이미 담고 있어 **전용 공유 API 불필요** — Out of Scope 아님, 그냥 신규 작업이 없다는 의미

### Out of Scope

- **추천 계산 로직 전체**(후보 윈도우 생성·`#17` resolve 집계·모드별 스코어링·BR-TRIP-012 동점) — `#50`([`trip-recommendation-algorithm.md`](trip-recommendation-algorithm.md))
- 알림 발송 (BR-NOTI-004) — MVP 출시
- 여행방 **삭제** 시 VOC 사유 — `unconfirm` 사유와 별개, 미정(출시 이후)
- 가격·날씨 등 외부 데이터
- 공휴일 API — 주말만 `[제안]` 또는 static `[미정]` (`#50` 소관)
- `score`/`reason`/`riskNote` 자연어 사유 자동 생성 — 화면에 없음, **폐기**(구 Nice to Have)

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/api/v1/trips/{tripId}/recommendations` | JWT + owner | 모드별 TOP 3 재계산·저장 |
| GET | `/api/v1/trips/{tripId}/recommendations` | **JWT + owner** (2026-07-30 개정, 구 member) | 저장된 TOP 3 조회 |
| POST | `/api/v1/trips/{tripId}/confirm` | JWT + owner | 일정 확정 |
| POST | `/api/v1/trips/{tripId}/unconfirm` | JWT + owner | 확정 취소 (CONFIRMED→ONGOING) |
| GET | `/api/v1/trips/{tripId}/recommendations/{rank}` | JWT + owner | 후보 1건 상세 — 참여자별 브레이크다운 + 피드백 |
| PATCH | `/api/v1/trips/{tripId}/recommendations/{rank}/feedback` | JWT + owner | "도움이 되었나요" 피드백 upsert |

### `POST .../recommendations` 요청

```json
{
  "mode": "ALL_ATTEND"
}
```

### `POST .../recommendations` 응답 (2026-07-30 카드 UI 반영 — 필드 개정)

```json
{
  "data": {
    "mode": "ALL_ATTEND",
    "items": [
      {
        "rank": 1,
        "startDate": "2026-08-03",
        "endDate": "2026-08-06",
        "attendRate": 80,
        "partialAttendCount": 1,
        "uncertainCount": 1,
        "totalVacationDays": 2.0
      }
    ]
  }
}
```

`GET .../recommendations`도 동일 `items` 구조. `attendRate`/`partialAttendCount`/`uncertainCount`/`totalVacationDays`의 계산은 `#50`([`trip-recommendation-algorithm.md`](trip-recommendation-algorithm.md) "카드 표시 지표" 절) 책임 — 이 스펙은 저장·직렬화 필드만 정의한다. 구 필드 `reason`/`riskNote`/`score`(자연어 사유·내부 점수)는 화면에 없어 **응답에서 제외**(`score`는 정렬용으로 내부 계산에만 사용, 필요 시 추후 노출 검토).

### `GET .../recommendations/{rank}` 응답 (2026-07-30 "추천 근거" 화면 반영, 신규)

```json
{
  "data": {
    "rank": 1,
    "mode": "BASIC",
    "startDate": "2026-06-12",
    "endDate": "2026-06-15",
    "attendRate": 92,
    "partialAttendCount": 1,
    "uncertainCount": 1,
    "totalVacationDays": 2.0,
    "members": [
      { "name": "김유정", "attendance": "NON_ATTEND", "uncertainDays": 0, "vacationDaysNeeded": 0.0 },
      { "name": "박효림", "attendance": "PARTIAL_ATTEND", "uncertainDays": 0, "vacationDaysNeeded": 0.0 },
      { "name": "최정연", "attendance": "FULL_ATTEND", "uncertainDays": 2, "vacationDaysNeeded": 2.0 },
      { "name": "하준수", "attendance": "FULL_ATTEND", "uncertainDays": 0, "vacationDaysNeeded": 2.0 },
      { "name": "김민서", "attendance": "FULL_ATTEND", "uncertainDays": 0, "vacationDaysNeeded": 0.0 }
    ],
    "feedback": null
  }
}
```

`members`는 응답 참여자 전원(미응답 제외) 나열 — **"주의가 필요한 인원"/"참석 가능한 인원" 그룹핑은 FE가 `attendance != FULL_ATTEND || uncertainDays > 0` 여부로 계산**(서버가 별도 그룹 필드를 만들지 않음). `feedback`은 방장이 이 후보에 이전에 남긴 피드백이 있으면 `{ "status": "NOT_HELPFUL", "reason": "TOO_FEW_ATTENDEES", "reasonDetail": null }` 형태, 없으면 `null`. (이 API 자체가 방장 전용이라 "누구의 피드백인지" 구분이 불필요 — `myFeedback`이 아니라 `feedback` 하나로 단순화)

### `PATCH .../recommendations/{rank}/feedback` 요청 (신규)

```json
{
  "status": "NOT_HELPFUL",
  "reason": "TOO_FEW_ATTENDEES"
}
```

`reason=OTHER`인 경우:

```json
{
  "status": "NOT_HELPFUL",
  "reason": "OTHER",
  "reasonDetail": "직접 입력 텍스트"
}
```

`status=HELPFUL`이면 `reason`/`reasonDetail` 불필요(있어도 무시). 성공 시 `204 No Content`.

**`RecommendationFeedbackReason` enum (2026-07-30 화면 확인)**

| enum | 한글 (UI) |
|------|-----------|
| `TOO_FEW_ATTENDEES` | 참석 인원이 너무 적어요 |
| `TOO_MANY_VACATION_DAYS` | 연차를 너무 많이 써야 해요 |
| `TOO_MANY_UNCERTAIN_SCHEDULES` | 불확실한 일정이 많이 포함됐어요 |
| `CRITERIA_MISMATCH` | 추천 기준이 제 상황과 안 맞아요 |
| `OTHER` | 기타 (직접 입력 — `reasonDetail` 필수) |

### `POST .../confirm` — 후보 선택

```json
{
  "recommendationRank": 1
}
```

### `POST .../confirm` — 직접 입력 (BR-TRIP-007)

```json
{
  "startDate": "2026-08-04",
  "endDate": "2026-08-07"
}
```

직접 입력 시 `durationDays`와 일수 일치 검증 `[제안]`.

### `POST .../unconfirm`

```json
{
  "reason": "RECOMMENDATION_UNSATISFACTORY"
}
```

`reason=OTHER`인 경우:

```json
{
  "reason": "OTHER",
  "reasonDetail": "직접 입력 텍스트"
}
```

성공 시 `204 No Content` — 확정된 여행방을 다시 조율 중(ONGOING) 상태로 되돌린다.

### 주요 에러 코드

| HTTP | code | 조건 |
|------|------|------|
| 400 | `INVALID_INPUT` | `mode`가 enum 밖(요청 JSON 파싱 단계에서 걸러짐 — **2026-07-30 구현 확정**: 전용 `INVALID_RECOMMENDATION_MODE`을 신설하는 대신 `mode`를 계속 정식 enum 타입으로 유지해 Swagger에 값 목록이 그대로 노출되게 하고, 파싱 실패는 공용 `HttpMessageNotReadableException` 핸들러가 `INVALID_INPUT`으로 통일 처리) · 추천 생성 시 여행 일수(`durationDays`) 미정 |
| 403 | `TRIP_FORBIDDEN` | 방장 아님(모든 엔드포인트 공통 — `TripAuthorizationInterceptor`) |
| 409 | `TRIP_NOT_ONGOING` | recommendations/confirm 호출 시 상태가 ONGOING이 아님(CONFIRMED/EXPIRED) — 이미 CONFIRMED인 방에 confirm 재호출도 동일 코드 |
| 409 | `TRIP_NOT_CONFIRMED` | unconfirm 호출 시 상태가 CONFIRMED가 아님 |
| 400 | `INVALID_UNCONFIRM_REASON` | `reason` enum 밖 또는 `OTHER`인데 `reasonDetail` 없음 |
| 400 | `INVALID_RECOMMENDATION_FEEDBACK` | `status=NOT_HELPFUL`인데 `reason` 없음, 또는 `reason=OTHER`인데 `reasonDetail` 없음 |
| 400 | `INVALID_CONFIRM_REQUEST` (신규) | confirm 요청에 `recommendationRank`·직접 날짜(`startDate`+`endDate`)가 둘 다 없거나 둘 다 있음 |
| 400 | `CONFIRM_DURATION_MISMATCH` (신규) | confirm 직접 입력 날짜의 일수가 `trip.durationDays`와 다름 |
| 404 | `RECOMMENDATION_NOT_FOUND` | rank 없음(GET 상세·PATCH 피드백·confirm rank 선택 공통) |
| 404 | `TRIP_NOT_FOUND` | 여행방 없음·soft deleted(모든 엔드포인트 공통) |

## 데이터 모델

- `Trip.lastRecommendationMode` — 이미 존재 (기존 컬럼)
- `Trip.unconfirmReason` / `Trip.unconfirmReasonDetail` — **추가 완료** (`Trip` 엔티티에 반영됨, `erd.md`와 일치). unconfirm 시 최신값 덮어쓰기 (이력 아님)
- `Trip.confirmedAttendCount` / `Trip.confirmedVacationMemberCount` / `Trip.confirmedUncertainCount` — **신규 컬럼**(Integer, nullable). confirm 시 `#50` `classifyMembers` 결과를 집계해 1회 저장(그 뒤 개별 일정이 바뀌어도 갱신 안 됨 — confirm 시점 스냅샷), unconfirm 시 `null`로 초기화. 응답 노출은 `trip-room-api.md`의 `TripDetailResponse` amend 절 참고 — **이 스펙에서 새 API를 만들지 않고 기존 Trip 상세에 얹기로 결정**(위 UX 흐름 8번)
- `recommendation` — 기존 엔티티, trip_id FK, hard DELETE only. **2026-07-30 카드 UI 반영 필드 개정:**
  - 삭제: `reason`(TEXT), `riskNote`(TEXT) — 자연어 사유 자동 생성 Nice to Have가 화면에 없어 폐기
  - 추가: `attendRate`(int, %) · `partialAttendCount`(int) · `uncertainCount`(int) · `totalVacationDays`(double) — 카드 표시 4개 지표, `#50` 계산 결과를 그대로 저장
  - 유지: `id`, `trip_id`, `recommendation_rank`, `start_date`, `end_date`, `score`(내부 정렬용, 응답 미노출), `created_at`
  - **ERD(`docs/architecture/erd.md`) `recommendation` 테이블도 같은 턴에 동기화 필요** — 아직 미반영
- **`recommendation_feedback` — 신규 엔티티(2026-07-30 "추천 근거" 화면 확인 + 같은 날 "참여자는 후보를 못 본다" 기획 개정, `#13` 범위):**
  - `id` PK, `trip_id` FK(조회 편의 — 방장은 `trip.owner_id`로 이미 식별되므로 별도 `user_id` 컬럼은 두지 않음)
  - `recommendation_id` — **FK 제약 없는 참조값**(soft reference)로만 저장. `recommendation` hard DELETE 시에도 피드백은 살아남아야 하므로 실제 FK 제약을 걸지 않음(걸면 hard DELETE가 FK violation으로 실패)
  - `mode`, `recommendation_rank`, `start_date`, `end_date` — **스냅샷**. `recommendation` 원본이 삭제된 뒤에도 "어떤 후보에 대한 피드백이었는지" 조인 없이 알 수 있어야 함(추천 품질 분석 목적)
  - `status`(enum `RecommendationFeedbackStatus`: `HELPFUL`/`NOT_HELPFUL`), `reason`(enum `RecommendationFeedbackReason`, nullable — `NOT_HELPFUL`일 때만), `reason_detail`(nullable — `reason=OTHER`일 때만)
  - `created_at`, `updated_at` — upsert이므로 `updated_at` 필요
  - UNIQUE(`recommendation_id`) — 조회·피드백 저장이 방장 전용이라 후보 1건당 피드백은 항상 1건. 재선택은 upsert(덮어쓰기), 회전(모드 변경 후 재생성된 새 `recommendation_id`)마다 새 행

### BR-TRIP-010 트리거

| 이벤트 | 동작 |
|--------|------|
| POST recommendations (mode 변경) | DELETE all + INSERT 3 |
| PATCH trip **duration** | DELETE all, `lastRecommendationMode=null` `[제안]` (기간은 create 후 불변) |
| DELETE trip | DELETE all |
| confirm | recommendation 유지 `[제안]` (확정 후 조회용) |
| unconfirm | recommendation hard DELETE — 재확정하려면 재계산 필요 |

## 알고리즘 (구현 가이드)

**계산 로직 전체가 `#50`([`trip-recommendation-algorithm.md`](trip-recommendation-algorithm.md))로 이동했다.** 이 스펙은 `#50`이 반환하는 `List<RecommendationCandidate>`를 저장·조회하는 것까지만 다룬다.

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-TRIP-005 | 4모드 TOP 3 계산 | `#50` `RecommendationEngine` |
| BR-TRIP-007 | owner confirm/unconfirm | TripConfirmService |
| BR-TRIP-010 | hard DELETE | RecommendationRepository.deleteByTripId |
| BR-TRIP-011 (개정) | ALL_ATTEND 가중치(하드 필터 아님) | `#50` `RecommendationEngine` |
| BR-TRIP-012 | tie-break | `#50` `RecommendationEngine` |

## 검증 시나리오

### 정상

- [x] POST(플레이스홀더 or `#50` 연결 후 실값) → 3 rows, GET 동일
- [x] mode 변경 POST → 이전 rows 삭제됨(hard DELETE)
- [x] confirm rank 1 → CONFIRMED + dates
- [x] confirm custom dates → CONFIRMED
- [x] confirm → `confirmedAttendCount`/`confirmedVacationMemberCount`/`confirmedUncertainCount`가 그 시점 `classifyMembers` 결과로 채워짐, `GET /trips/{tripId}`에서 방장·참여자 모두 동일 값 조회
- [x] unconfirm → ONGOING, `confirmedStartDate`/`confirmedEndDate`·`confirmedAttendCount`/`confirmedVacationMemberCount`/`confirmedUncertainCount` 전부 null, 기존 recommendation hard DELETE, snapshot 폐기
- [x] `GET .../recommendations/{rank}` → 참여자별 브레이크다운 + `feedback=null`(최초 조회)
- [x] `PATCH .../feedback` `HELPFUL` → 204, 이후 `GET .../recommendations/{rank}`의 `feedback.status=HELPFUL`
- [x] `PATCH .../feedback` 같은 rank 재호출(다른 상태) → upsert로 덮어써짐(행 1개 유지)
- [x] mode 변경으로 recommendation hard DELETE 후에도 이전 `recommendation_feedback` 행은 남아있음(스냅샷 필드로 조회 가능)

### 엣지 · 실패

- [x] 참여자 confirm → 403
- [x] 참여자가 `GET .../recommendations`·`GET .../recommendations/{rank}`·`PATCH .../feedback` 호출 → 403 `TRIP_FORBIDDEN`
- [x] PATCH trip endRange → GET recommendations empty
- [x] unconfirm 호출 시 상태가 CONFIRMED 아님 → 409 `TRIP_NOT_CONFIRMED`
- [x] 참여자가 unconfirm 호출 → 403 `TRIP_FORBIDDEN`
- [x] unconfirm `reason` 누락 → 400 `INVALID_UNCONFIRM_REASON`
- [x] unconfirm `reason=OTHER`인데 `reasonDetail` 없음 → 400 `INVALID_UNCONFIRM_REASON`
- [x] `PATCH .../feedback` `status=NOT_HELPFUL`인데 `reason` 없음 → 400 `INVALID_RECOMMENDATION_FEEDBACK`
- [x] `PATCH .../feedback` `reason=OTHER`인데 `reasonDetail` 없음 → 400 `INVALID_RECOMMENDATION_FEEDBACK`
- [x] 존재하지 않는 rank로 `GET`/`PATCH .../feedback` → 404 `RECOMMENDATION_NOT_FOUND`

### 단위 테스트 (필수, 이 스펙 범위)

- [x] hard DELETE 후 count=0
- [x] confirm/unconfirm 상태 전이(`ONGOING`↔`CONFIRMED`)
- [x] 피드백 upsert(같은 recommendation_id 재호출 시 행 1개 유지, 값만 갱신)
- [x] 피드백이 recommendation hard DELETE 이후에도 스냅샷 필드로 조회 가능한지
- [ ] (모드별 rank·동점 comparator·참여자별 분류(`attendance`/`uncertainDays`/`vacationDaysNeeded`) 단위 테스트는 `#50` 소관)

## 완료 기준

- [x] `./gradlew test` 통과 (RecommendationServiceTest 등)
- [x] OpenAPI 반영
- [x] `#50` 연결 완료 시점에 MVP 완료 기준(방장이 4모드 중 하나로 **실제 계산된** TOP 3 확인 후 확정) 충족 — 이 스펙만으로는 API 계약까지만 검증

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| BR-TRIP-005 모드별 가중치·패널티 | **확정 (2026-07-30)** → `#50` | [`trip-recommendation-scoring-source.md`](trip-recommendation-scoring-source.md) 반영 완료 — 상세는 `trip-recommendation-algorithm.md` |
| `attendRate`(카드 참석률 %) 계산식 | `[제안]` → `#50` | 화면 역산 추정값 — 기획 확정 대기 |
| regular vs personal 병합 | **Implemented** (#17) | 추천은 resolve **재사용** (C1), 호출은 `#50` |
| 공휴일 데이터 | `[미정]` | 동점 처리엔 더 이상 불필요(주말·공휴일 기준 폐기). 카드 UI에 별도 필요해지면 재논의 |
| confirm 후 recommendation 유지 | `[제안]` | UI 재조회용 |
| NOTI on confirm | MVP 출시 | stub 없음 |
| `TripStatus.CANCELED` 제거 | **#48 Implemented** | 이 스펙이 유일한 프로듀서였던 `cancel`(→CANCELED) API를 삭제하고 `unconfirm`으로 교체 완료. enum 값 삭제 자체도 `#48`에서 코드로 실행 완료 |
| unconfirm 사유 입력 | 확정 (2026-07-24, 기획자 답변) | 라디오 6종(`OTHER`는 직접입력) — `UnconfirmReason` enum 신설. 구 `cancel_reason`(VOC, 출시 이후) 개념과는 분리 — 여행방 **삭제** 시 VOC 사유는 여전히 미정. 실제 `Trip.unconfirmReason`/`unconfirmReasonDetail` 필드·API 구현은 본 스펙(`#13`)에서 진행 |
| `TERMINATED` → `EXPIRED` 리네임 | **#48 Implemented** | `#27`/`#37`/`#38` 등 관련 스펙 문구도 함께 `EXPIRED`로 갱신 완료 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-30 | **HTTP method 정정** — `.../recommendations/{rank}/feedback`을 `PUT`에서 `PATCH`로 변경. 이 요청은 리소스 전체를 대체하는 게 아니라 `status`/`reason`/`reasonDetail` 필드를 upsert하는 부분 갱신이라 `PATCH`가 맞는 의미론(컨트롤러·테스트·fe-context 동기화, Breaking-Change-Reason 대상) |
| 2026-07-30 | **Approved** — 화면 확인(카드·추천 근거·확정 완료·취소 사유)·권한 정리까지 반영 완료, 구현 착수 |
| 2026-07-30 | **권한 정리(Authorization Matrix)** 절 추가 — 역할 게이트(방장/참여자)와 상태 전제(ONGOING/CONFIRMED)를 표 하나로 정리, GitHub `#13`에도 동일 표 코멘트로 기록 |
| 2026-07-30 | "확정 완료" 화면 확인 반영 — `Trip.confirmedAttendCount`/`confirmedVacationMemberCount`/`confirmedUncertainCount` 신규 컬럼(confirm 시 `#50` `classifyMembers`로 1회 계산, unconfirm 시 null). 신규 API 대신 기존 `GET /trips/{tripId}`(`TripDetailResponse`, `trip-room-api.md`)에 필드만 추가 — 방장·참여자 모두 조회 가능(확정 후 공개 정보이므로 후보/근거와 달리 owner 게이트 없음). 확정 취소(unconfirm) 사유 입력 폼은 화면으로 재확인만, 기존 `UnconfirmReason` 스펙과 동일해 변경 없음 |
| 2026-07-30 | 기획 개정 — 추천 후보·추천 근거는 **방장만** 조회 가능(참여자는 확정 일정만). `GET .../recommendations`·`GET .../recommendations/{rank}`·`PATCH .../recommendations/{rank}/feedback` 전부 `JWT + member` → `JWT + owner`로 변경, 피드백 엔티티도 참여자별(unique(recommendation_id, user_id))에서 **방장 단일**(unique(recommendation_id), `user_id` 컬럼 제거)로 단순화, 응답 필드 `myFeedback` → `feedback` 개명 |
| 2026-07-30 | "추천 근거" 상세 화면 확인 반영 — `GET .../recommendations/{rank}`(참여자별 브레이크다운) · `PATCH .../recommendations/{rank}/feedback`(도움 여부 upsert) 신규 API, `RecommendationFeedback` 엔티티(+ `RecommendationFeedbackStatus`/`RecommendationFeedbackReason` enum) 신규 추가 |
| 2026-07-30 | 카드 UI 확인 반영 — 응답 DTO 필드 개정(`reason`/`riskNote`/`score` 제거, `attendRate`/`partialAttendCount`/`uncertainCount`/`totalVacationDays` 추가), `NO_RECOMMENDATION_CANDIDATES` 에러·`ALL_ATTEND` 하드 필터 문구 삭제(`#50` 2026-07-30 개정과 동기화), UX 흐름(모드 선택→카드→확정/재추천/공유) 절 추가 |
| 2026-07-24 | 사용자 요청으로 범위 분리 — 이 스펙은 API 설계·DTO·ERD·상태 전이·hard DELETE 트리거만, **추천 계산 로직 전체는 `#50`([`trip-recommendation-algorithm.md`](trip-recommendation-algorithm.md))로 이동** |
| 2026-07-24 | **#48 Implemented** — `TripStatus.CANCELED` enum 삭제, `TERMINATED` → `EXPIRED` 리네임. 본 스펙 코드 참조도 `EXPIRED`로 동기화 |
| 2026-07-24 | `unconfirm` 사유 입력 필수로 확정(기획자 답변) — `UnconfirmReason` enum 6종 + `reasonDetail`(`OTHER`). 관련 문서(`mvp.md`·`release-milestones.md`·`erd.md`·`trip-room-api.md`·`figma-wireframe-v1.md`·`#48`) wave 재분류 동기화 |
| 2026-07-24 | `src/new_decision.md` 확정 반영 — `cancel`(→`CANCELED`) API를 **삭제**, `unconfirm`(CONFIRMED→ONGOING, 새 Status 없음) API로 교체. 관련 에러 코드·시나리오 갱신 |
| 2026-07-08 | 초안 |
| 2026-07-17 | #17 resolve 재사용(C1) · trip-room D4 ONGOING만 · calendar Implemented |
| 2026-07-13 | AVAILABILITY → `regular`/`personal` + `uncertain` |
