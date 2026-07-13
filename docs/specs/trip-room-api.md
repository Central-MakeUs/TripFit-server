# 여행방 API — 생성·참여·Pin·제출

> wave: 2  
> implements: BR-TRIP-001, BR-TRIP-008, BR-TRIP-009, BR-TRIP-013, BR-USER-001, BR-USER-002, BR-USER-007, BR-USER-009, BR-USER-010  
> deferred: BR-TRIP-010 (recommendation hard DELETE — [`trip-recommendation.md`](trip-recommendation.md)), `cancel_reason` VOC (wave 4), 카카오 공유 (wave 3)  
> 상태: Draft  
> 선행: [`auth-social-login.md`](auth-social-login.md), [`user-onboarding.md`](user-onboarding.md), [`schedule-unified.md`](schedule-unified.md)  
> 후속: [`trip-recommendation.md`](trip-recommendation.md)

## 목표

방장이 여행방을 만들고 초대·참여·홈 Pin·일정 제출까지 REST API로 제공한다. MVP 완료 기준(wave 2)의 **여행방·참여** 축을 담당한다.

## 배경

- **JPA 선행 반영:** `Trip` (`destination`, `TripStatus` incl. TERMINATED), `TripMember` (`user_id` NOT NULL, `is_pinned`)
- **미구현:** Controller·Service, `trip.last_recommendation_mode`, `TripMember.deleted_at`, invite_code 생성
- **참여:** 소셜 로그인 필수 (BR-USER-002), 비회원 없음
- **일정 데이터:** `schedule` AVAILABILITY (전역) — [`schedule-unified.md`](schedule-unified.md)
- **참여 완료:** 「일정 제출하기」→ `trip_member.status=RESPONDED` (BR-USER-007)

### 관련 문서

| 문서 | 내용 |
|------|------|
| `docs/product/flows/trip-create.md` | 생성 플로우 |
| `docs/product/flows/trip-join.md` | 참여·제출 |
| `docs/product/business-rules/trip.md` | BR-TRIP-001, 008, 009, 013 |
| `docs/architecture/erd.md` | `trip`, `trip_member` |

## 요구사항

### Must Have

- [ ] `POST /api/v1/trips` — 방장 생성 (BR-TRIP-001, BR-USER-001 이름 필수)
- [ ] 생성 시 `trip_member` OWNER + `JOINED`, `invite_code` UNIQUE 발급, `status=ONGOING`
- [ ] `GET /api/v1/trips` — 내 여행방 목록 (참여 중, soft delete 제외)
- [ ] `GET /api/v1/trips/{tripId}` — 상세 (참여자만)
- [ ] `PATCH /api/v1/trips/{tripId}` — 방장만 이름·기간·`durationDays`·`destination`·`targetMemberCount` (BR-TRIP-009)
- [ ] `durationDays` ≤ 기간 일수 검증 (BR-TRIP-008)
- [ ] `POST /api/v1/trips/join` — `{ inviteCode }` → MEMBER + `JOINED` (이미 참여 시 idempotent — BR-USER-010)
- [ ] `GET /api/v1/trips/{tripId}/members` — 참여자 목록 + `status`, `role`, `pinned`, 응답률 요약
- [ ] `PATCH /api/v1/trips/{tripId}/pin` — `{ pinned: boolean }` 본인 `trip_member.is_pinned`
- [ ] `POST /api/v1/trips/{tripId}/schedule/submit` — BR-USER-007: CONDITION 게이트 + trip 기간 AVAILABILITY 최소 1슬롯 `[제안]` → `RESPONDED`
- [ ] `DELETE /api/v1/trips/{tripId}` — 방장 soft delete, `trip_member` **연쇄 soft** (BR-TRIP-013)
- [ ] `TripMember` → `SoftDeleteEntity` 또는 `deleted_at` 추가
- [ ] `./gradlew test` 통과

### Nice to Have

- [ ] 홈 목록 정렬: pinned 우선 → `[미정]` 이후 규칙
- [ ] `GET /api/v1/trips/by-invite/{inviteCode}` — join 전 미리보기 (인증 optional)

### Out of Scope

- 추천·확정·취소 — [`trip-recommendation.md`](trip-recommendation.md)
- `cancel_reason` 저장 — wave 4
- 그룹 달력 집계 API (wave 3 — 프론트가 members + availability-summary 조합 가능)
- NOTI — wave 3

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/api/v1/trips` | JWT + 이름 | 여행방 생성 |
| GET | `/api/v1/trips` | JWT | 내 여행방 목록 |
| GET | `/api/v1/trips/{tripId}` | JWT + member | 상세 |
| PATCH | `/api/v1/trips/{tripId}` | JWT + owner | 메타 수정 (BR-TRIP-009) |
| DELETE | `/api/v1/trips/{tripId}` | JWT + owner | soft delete |
| POST | `/api/v1/trips/join` | JWT | 초대 코드로 참여 |
| GET | `/api/v1/trips/{tripId}/members` | JWT + member | 참여자 목록 |
| PATCH | `/api/v1/trips/{tripId}/pin` | JWT + member | Pin 토글 |
| POST | `/api/v1/trips/{tripId}/schedule/submit` | JWT + member | 일정 제출 → RESPONDED |

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

`destination` nullable (BR-TRIP-001 선택 입력).

### `POST /trips` 응답

```json
{
  "data": {
    "tripId": "550e8400-e29b-41d4-a716-446655440000",
    "inviteCode": "A1B2C3",
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
  "inviteCode": "A1B2C3",
  "confirmedStartDate": null,
  "confirmedEndDate": null,
  "lastRecommendationMode": null,
  "pinned": false,
  "myMemberStatus": "JOINED",
  "respondedCount": 2,
  "memberCount": 4
}
```

### 주요 에러 코드

| HTTP | code | 조건 |
|------|------|------|
| 400 | `INVALID_INPUT` | BR-TRIP-001/008 위반, name 20자 초과 `[제안]` |
| 403 | `PROFILE_NAME_REQUIRED` | BR-USER-001 — [`user-onboarding.md`](user-onboarding.md) |
| 403 | `TRIP_FORBIDDEN` | owner 아닌 PATCH/DELETE |
| 403 | `TRIP_ACCESS_DENIED` | 비참여자 |
| 403 | `SCHEDULE_CONDITION_REQUIRED` | submit 시 CONDITION 없음 |
| 404 | `TRIP_NOT_FOUND` | 없음 또는 soft deleted |
| 409 | `TRIP_ALREADY_CONFIRMED` | CONFIRMED trip join/수정 제한 `[제안]` |
| 404 | `INVITE_CODE_NOT_FOUND` | 잘못된 초대 코드 |

## 데이터 모델

- ERD: `trip`, `trip_member`
- **추가 컬럼 (JPA):** `Trip.lastRecommendationMode` (enum), `TripMember.deletedAt`
- **invite_code:** 영숫자 6~8자 `[제안]`, UNIQUE, 생성 시 충돌 재시도

### Soft delete

| 대상 | 정책 |
|------|------|
| `trip` | `deleted_at` 설정 |
| `trip_member` | trip delete 시 **연쇄** `deleted_at` |
| `schedule` | **유지** (User 전역) |

### TripStatus (wave 2 API 노출)

| 값 | wave 2 |
|----|--------|
| ONGOING | 생성·조율 중 |
| CONFIRMED | 확정 API — [`trip-recommendation.md`](trip-recommendation.md) |
| CANCELED | 취소 API — 동 스펙 |
| TERMINATED | **enum만** — 자동 전환 조건 `[미정]` (배치/수동 wave 2 Out) |

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-TRIP-001 | 필수 필드 + destination | create 검증 |
| BR-TRIP-008 | duration ≤ range | create/patch |
| BR-TRIP-009 | 방장만 PATCH | `@PreAuthorize` / service |
| BR-TRIP-013 | 방장 DELETE soft | TripService |
| BR-USER-001 | 이름 없으면 create 403 | 공통 게이트 |
| BR-USER-002 | join JWT 필수 | Security |
| BR-USER-007 | submit → RESPONDED | ScheduleSubmitService |
| BR-USER-009 | 동명이인 displayName | members DTO |
| BR-USER-010 | 재join idempotent | join service |

### PATCH trip 시 BR-TRIP-010

희망 기간·`durationDays` 변경 시 **`recommendation` hard DELETE** — [`trip-recommendation.md`](trip-recommendation.md)에서 TripService hook 또는 이벤트로 구현. 본 스펙 Must Have에 **연동 지점 주석/호출** 포함.

## 검증 시나리오

### 정상

- [ ] 방장 create → OWNER member + inviteCode
- [ ] 참여자 join → MEMBER JOINED
- [ ] submit → RESPONDED, 재호출 idempotent
- [ ] pin true → 목록 `pinned` true
- [ ] owner delete → trip·members soft, schedule 유지

### 엣지 · 실패

- [ ] 참여자 PATCH trip → 403
- [ ] CONDITION 없이 submit → 403
- [ ] durationDays > range → 400
- [ ] CONFIRMED trip join → 409 `[제안]`

## 완료 기준

- [ ] `./gradlew test` 통과
- [ ] OpenAPI 반영
- [ ] BR-TRIP-010 hook — recommendation 스펙과 통합 테스트 1건 이상

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| TERMINATED 전환 | `[미정]` | wave 2는 enum·DTO만; 스케줄러는 wave 4 후보 |
| UI ↔ TripStatus 매핑 | `[미정]` | `TripStatus.java` TODO — 프론트 합의 |
| 홈 정렬 (pin 이후) | `[미정]` | pinned DESC, 그 다음 `[제안]` updatedAt DESC |
| submit 최소 슬롯 수 | `[제안]` | trip 기간 내 AVAILABILITY ≥ 1 |
| invite_code 형식 | `[제안]` | 6자 Base32 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-08 | 초안 |
