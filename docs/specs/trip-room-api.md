# 여행방 API — 생성·참여·Pin·제출

> wave: 2  
> implements: BR-TRIP-001, BR-TRIP-008, BR-TRIP-009, BR-TRIP-013, BR-USER-001, BR-USER-002, BR-USER-009, BR-USER-010  
> deferred: BR-USER-006, BR-USER-007, D1·submit·members schedule-calendar → **[#22](https://github.com/Central-MakeUs/TripFit-server/issues/22)** [`schedule-participation-onboarding.md`](schedule-participation-onboarding.md), BR-TRIP-010 (recommendation hard DELETE — [`trip-recommendation.md`](trip-recommendation.md)), `cancel_reason` VOC (wave 4), 카카오 공유 SDK ([#21](https://github.com/Central-MakeUs/TripFit-server/issues/21)), join 전 미리보기 ([#19](https://github.com/Central-MakeUs/TripFit-server/issues/19)), 참여자 내보내기 ([#20](https://github.com/Central-MakeUs/TripFit-server/issues/20))  
> 상태: **Approved** (D3~D6·D8 확정 — 2026-07-17) · **D1·submit `#22` deferred** (2026-07-17)  
> 선행: [`auth-social-login.md`](auth-social-login.md), [`user-onboarding.md`](user-onboarding.md), [`schedule-unified.md`](schedule-unified.md), [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) (#17 Implemented), **[#22](https://github.com/Central-MakeUs/TripFit-server/issues/22)** (submit·참여 완료)  
> 후속: [`trip-recommendation.md`](trip-recommendation.md)

## 목표

방장이 여행방을 만들고 초대·참여·홈 Pin·일정 제출까지 REST API로 제공한다. MVP 완료 기준(wave 2)의 **여행방·참여** 축을 담당한다.

## 배경

- **JPA 선행 반영:** `Trip` (`destination`, `TripStatus` incl. TERMINATED), `TripMember` (`user_id` NOT NULL, `is_pinned`)
- **미구현:** Controller·Service, `trip.last_recommendation_mode`, `TripMember.deleted_at`, invite_code 생성
- **참여:** 소셜 로그인 필수 (BR-USER-002), 비회원 없음. **초대는 카카오·OS 링크 공유**(딥링크/Universal Link에 `inviteCode` 포함) — 코드 수동 입력은 보조
- **일정 데이터:** User 전역 `regular_schedule` + `personal_schedule` (BR-USER-008) — [`schedule-unified.md`](schedule-unified.md)
- **참여 완료·submit:** **`[미定]`** — [#22](https://github.com/Central-MakeUs/TripFit-server/issues/22). (구) D1·BR-USER-007 확정 **철회**

### 관련 문서

| 문서 | 내용 |
|------|------|
| `docs/product/flows/trip-create.md` | 생성 플로우 |
| `docs/product/flows/trip-join.md` | 참여·제출 |
| `docs/product/business-rules/trip.md` | BR-TRIP-001, 008, 009, 013 |
| `docs/architecture/erd.md` | `trip`, `trip_member` |

## 확정 사항 (2026-07-17)

| ID | 항목 | 결정 |
|----|------|------|
| **D1** | submit 게이트 | **`[미定]`** — [#22](https://github.com/Central-MakeUs/TripFit-server/issues/22). ~~regular≥1만·personal 최소 행 없음~~ (2026-07-17 철회) |
| **D2** | 그룹 일정 조회 | **T1** — `members/schedule-calendar`(effective). **`[미定]` OpenAPI Hidden** (#22). `personal-summary` deprecate |
| **D3** | `invite_code` | **6자** Crockford Base32 (`0`/`O`/`I`/`1` 제외). 링크 공유 UX — 아래 §초대 |
| **D4** | CONFIRMED·CANCELED | 기존 멤버 재접속 idempotent · **신규 join 409** · PATCH는 **`ONGOING`만** · submit **`[미定]`** (#22) |
| **D5** | `GET /trips` 정렬 | `is_pinned DESC` → `updatedAt DESC` |
| **D6** | 이름 최대 길이 | **15자** |
| **D7** | join 전 미리보기 | `[미정]` — wave 2 Out · [#19](https://github.com/Central-MakeUs/TripFit-server/issues/19) |
| **D8** | 인원·기간 cap | `memberCount >= targetMemberCount` → 신규 join 409 · `end_range` 경과(TERMINATED) → 초대·신규 join 불가 |

## 요구사항

### Must Have

- [ ] `POST /api/v1/trips` — 방장 생성 (BR-TRIP-001: 이름 **≤15자**, BR-USER-001 이름 필수)
- [ ] 생성 시 `trip_member` OWNER + `JOINED`, `invite_code` UNIQUE(6자) 발급, `status=ONGOING`
- [ ] `GET /api/v1/trips` — 내 여행방 목록; 정렬 **`is_pinned DESC` → `updatedAt DESC`** (D5)
- [ ] `GET /api/v1/trips/{tripId}` — 상세 (참여자만)
- [ ] `PATCH /api/v1/trips/{tripId}` — 방장만 · **`status=ONGOING`만** (D4)
- [ ] `durationDays` ≤ 기간 일수 검증 (BR-TRIP-008)
- [ ] `POST /api/v1/trips/join` — `{ inviteCode }` → MEMBER + `JOINED` (이미 참여 시 idempotent — BR-USER-010)
- [ ] join 거부: CONFIRMED/CANCELED/TERMINATED **신규** → 409; 인원 가득 → 409 (D4·D8)
- [ ] `GET /api/v1/trips/{tripId}/members` — status·role·pinned·응답률 + 동명이인 `홍길동(2)` (BR-USER-009)
- [ ] `PATCH /api/v1/trips/{tripId}/pin` — `{ pinned: boolean }` 본인 `trip_member.is_pinned`
- [ ] `DELETE /api/v1/trips/{tripId}` — 방장 soft delete, `trip_member` **연쇄 soft** (BR-TRIP-013)
- [ ] `TripMember` → `SoftDeleteEntity` 또는 `deleted_at` 추가
- [ ] `./gradlew test` 통과

### Nice to Have

- [ ] Pin 자동 해제: `end_range` 경과 시 `is_pinned=false`

### Out of Scope

| 항목 | 이슈 |
|------|------|
| 추천·확정·취소 | [#13](https://github.com/Central-MakeUs/TripFit-server/issues/13) |
| **submit·참여 완료·온보딩 skip·sparse** | **[#22](https://github.com/Central-MakeUs/TripFit-server/issues/22)** (wave 1) |
| join 전 미리보기 | [#19](https://github.com/Central-MakeUs/TripFit-server/issues/19) |
| 참여자 내보내기 | [#20](https://github.com/Central-MakeUs/TripFit-server/issues/20) |
| 카카오 공유·푸시 | [#21](https://github.com/Central-MakeUs/TripFit-server/issues/21) (wave 3) |
| `cancel_reason` VOC | wave 4 |

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/api/v1/trips` | JWT + 이름 | 여행방 생성 |
| GET | `/api/v1/trips` | JWT | 내 여행방 목록 (pin → updatedAt) |
| GET | `/api/v1/trips/{tripId}` | JWT + member | 상세 |
| PATCH | `/api/v1/trips/{tripId}` | JWT + owner | 메타 수정 · ONGOING만 |
| DELETE | `/api/v1/trips/{tripId}` | JWT + owner | soft delete |
| POST | `/api/v1/trips/join` | JWT | 초대 코드(링크에서 추출)로 참여 |
| GET | `/api/v1/trips/{tripId}/members` | JWT + member | 참여자 목록 |
| PATCH | `/api/v1/trips/{tripId}/pin` | JWT + member | Pin 토글 |
| ~~POST~~ | ~~`/api/v1/trips/{tripId}/schedule/submit`~~ | — | **`[미定]` · OpenAPI Hidden (#22)** |
| ~~GET~~ | ~~`/api/v1/trips/{tripId}/members/schedule-calendar`~~ | — | **`[미定]` · OpenAPI Hidden (#22)** |

### 초대 링크 (D3)

- 서버: trip 생성 시 **`invite_code`** 6자 발급 (UNIQUE, 충돌 시 재시도)
- 클라이언트: 공유 URL — `https://tripfit.online/room/{inviteCode}` (`docs/decisions/002-domain-split-vercel-api.md` — 프론트 Vercel)
- 참여: 링크 진입 → 로그인 → `POST /trips/join` `{ inviteCode }` (코드 직접 입력 UI는 보조)
- **D8 (클라이언트):** 인원 가득·`end_range` 경과 시 공유 시트/초대 UI 비노출 + alert. 서버는 join 409로 동일 정책 보장

### `POST /trips` 요청

```json
{
  "name": "제주 3박4일",
  "startRange": "2026-08-01",
  "endRange": "2026-08-10",
  "durationDays": 4,
  "targetMemberCount": 6,
  "destination": "제주"
}
```

`destination` nullable (BR-TRIP-001 선택 입력). `name` 최대 **15자**.

### `POST /trips` 응답

```json
{
  "data": {
    "tripId": "550e8400-e29b-41d4-a716-446655440000",
    "inviteCode": "A2B3C4",
    "status": "ONGOING"
  }
}
```

### TripSummary (목록·상세 공통 필드)

```json
{
  "tripId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "제주 3박4일",
  "destination": "제주",
  "startRange": "2026-08-01",
  "endRange": "2026-08-10",
  "durationDays": 4,
  "targetMemberCount": 6,
  "status": "ONGOING",
  "inviteCode": "A2B3C4",
  "confirmedStartDate": null,
  "confirmedEndDate": null,
  "lastRecommendationMode": null,
  "pinned": false,
  "myMemberStatus": "JOINED",
  "respondedCount": 2,
  "memberCount": 4
}
```

### `members/schedule-calendar` 응답 (D2)

trip `startRange`~`endRange` 기간. 멤버 **전원** × effective day (S1·R2=A · #17).

```json
{
  "data": {
    "startDate": "2026-08-01",
    "endDate": "2026-08-10",
    "members": [
      {
        "userId": "...",
        "displayName": "홍길동",
        "role": "OWNER",
        "memberStatus": "RESPONDED",
        "days": [
          {
            "date": "2026-08-03",
            "morningStatus": "IMPOSSIBLE",
            "afternoonStatus": "IMPOSSIBLE",
            "eveningStatus": "POSSIBLE",
            "uncertain": false
          }
        ]
      }
    ]
  }
}
```

### 주요 에러 코드

| HTTP | code | 조건 |
|------|------|------|
| 400 | `INVALID_INPUT` | BR-TRIP-001/008 위반, name **15자** 초과 |
| 403 | `PROFILE_NAME_REQUIRED` | BR-USER-001 |
| 403 | `TRIP_FORBIDDEN` | owner 아닌 PATCH/DELETE |
| 403 | `TRIP_ACCESS_DENIED` | 비참여자 |
| 403 | `REGULAR_SCHEDULE_REQUIRED` | ~~submit~~ **`[미定]`** (#22) |
| 404 | `TRIP_NOT_FOUND` | 없음 또는 soft deleted |
| 404 | `INVITE_CODE_NOT_FOUND` | 잘못된 초대 코드 |
| 409 | `TRIP_NOT_ONGOING` | ONGOING 아닌 trip PATCH·submit (D4) |
| 409 | `TRIP_ALREADY_CONFIRMED` | CONFIRMED trip **신규** join |
| 409 | `TRIP_CANCELED` | CANCELED trip **신규** join |
| 409 | `TRIP_TERMINATED` | TERMINATED trip **신규** join · `end_range` 경과 |
| 409 | `TRIP_MEMBER_FULL` | `memberCount >= targetMemberCount` **신규** join (D8) |

## 데이터 모델

- ERD: `trip`, `trip_member`
- **추가 컬럼 (JPA):** `Trip.lastRecommendationMode` (enum), `TripMember.deletedAt`
- **`invite_code`:** 6자 Crockford Base32 (`23456789ABCDEFGHJKMNPQRSTUVWXYZ`), UNIQUE

### Soft delete

| 대상 | 정책 |
|------|------|
| `trip` | `deleted_at` 설정 |
| `trip_member` | trip delete 시 **연쇄** `deleted_at` |
| `regular_schedule` · `personal_schedule` | **유지** (User 전역) |

### TripStatus (wave 2)

| UI | `TripStatus` | join (신규) | PATCH |
|----|--------------|-------------|-------|
| 조율 중 | `ONGOING` | ✓ (D8 조건) | ✓ |
| 일정 확정 | `CONFIRMED` | **409** | **409** `TRIP_NOT_ONGOING` |
| 취소 | `CANCELED` | **409** | **409** |
| 종료 | `TERMINATED` | **409** | **409** |

submit·ONGOING 게이트: **`[미定]`** (#22)

- **기존 멤버** 재접속: idempotent (BR-USER-010) — CONFIRMED/CANCELED/TERMINATED에서도 조회 등 역할별 허용
- **TERMINATED:** `end_range < today` — 전환 시점(배치 vs lazy) `[미정]`

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-TRIP-001 | 필수 필드 · 이름 ≤15자 | create 검증 |
| BR-TRIP-008 | duration ≤ range | create/patch |
| BR-TRIP-009 | 방장만 PATCH · ONGOING만 | service |
| BR-TRIP-013 | 방장 DELETE soft | TripService |
| BR-USER-006 · BR-USER-007 | submit·참여 완료 | **`[미定]` → [#22](https://github.com/Central-MakeUs/TripFit-server/issues/22)** |
| BR-USER-010 | 재join idempotent | join service |

### PATCH trip 시 BR-TRIP-010

희망 기간·`durationDays` 변경 시 **`recommendation` hard DELETE** — hook 지점 주석/호출 (#13).

### `personal-summary` (deprecate)

기존 `GET .../members/personal-summary`는 personal-only(C2). **`schedule-calendar`·submit → [#22](https://github.com/Central-MakeUs/TripFit-server/issues/22) `[미定]`**

## 검증 시나리오

### 정상

- [ ] 방장 create → OWNER + inviteCode(6자)
- [ ] `GET /trips` — pinned 우선, 동일 pin 그룹 내 `updatedAt DESC`
- [ ] 참여자 join → MEMBER JOINED

### 엣지 · 실패 (#22로 submit·calendar 시나리오 이관)

- [ ] name 16자 → 400
- [ ] invite_code에 `0/O/I/1` 포함 생성 경로 없음
- [ ] CONFIRMED/CANCELED/TERMINATED **신규** join → 409
- [ ] CONFIRMED trip **기존 멤버** join 재호출 → idempotent 200
- [ ] memberCount ≥ target 신규 join → 409
- [ ] CONFIRMED trip PATCH → 409 `TRIP_NOT_ONGOING`

## 완료 기준

- [ ] `./gradlew test` 통과
- [ ] OpenAPI 반영
- [ ] BR-TRIP-010 hook — #13과 통합 테스트 1건 이상
- [ ] #12 이슈 체크리스트 동기화

## 리스크·잔여 `[미정]`

| 항목 | 비고 |
|------|------|
| TERMINATED 전환 시점 | `end_range` 경과 조건 확정 · 배치 vs lazy |
| PATCH `startRange`/`endRange` | 정책서 vs 화면정의서 충돌 |
| 참여자 내보내기 | [#20](https://github.com/Central-MakeUs/TripFit-server/issues/20) | wave 2 Out |
| join 전 미리보기 | D7 · 별도 이슈 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-08 | 초안 |
| 2026-07-13 | 일정 용어 #11 정합 |
| 2026-07-17 | 정책서 반영 |
| 2026-07-17 | **Approved** — D3~D6·D8 확정 |
| 2026-07-17 | **D1·submit·schedule-calendar → [#22](https://github.com/Central-MakeUs/TripFit-server/issues/22) deferred** · OpenAPI Hidden |
