# TripFit ERD

> NotebookLM 03 + 2026-07-08 확정 병합. 비즈니스 규칙: `docs/product/business-rules/`.  
> **구현 상태:** wave 1 UUID PK 전환 완료. wave 2 `#11` — **정기/개별 2테이블** (`regular_schedule` · `personal_schedule`). A안 단일 `schedule` 폐기. 여행방 CRUD·추천은 후속 이슈.

## 1. 개요

- **데이터 모델 설계 목적:** 다수 참여자 일정 수집·가중치 기반 추천·확정을 지원하는 MVP 데이터 구조
- **설계 원칙:**
    - **snake_case**, **단수형** 테이블명 (`users`만 복수 — MySQL 예약어 `user` 회피)
    - **Soft delete:** `users`, `trip`, `trip_member` — `deleted_at`
    - **UUID v4 PK** (`char(36)`), BR-* 및 백엔드 확정 사항 반영 — [`uuid-primary-key.md`](../specs/uuid-primary-key.md)
    - **User 전역 일정:** `regular_schedule`(정기) + `personal_schedule`(개별) — 모든 여행방에 자동 반영 (BR-USER-008)
- **대상 DB:** MySQL 8.0 (예약어 `rank` 등 — JPA `@Column` 명시. 구 `user` 테이블 → **`users`**)

## 2. Mermaid ERD (정기·개별 분리 — SSOT)

```mermaid
erDiagram
    users ||--o{ refresh_token : issues
    users ||--o{ regular_schedule : owns
    users ||--o{ personal_schedule : owns
    users ||--o{ trip_member : participates
    users ||--o{ trip : owns
    trip ||--o{ trip_member : has
    trip ||--o{ recommendation : generates

    users {
        uuid id PK
        string social_id
        string provider
        string email
        string first_name
        string last_name
        string nickname
        string profile_image_url
        boolean is_google_calendar_connected
        boolean is_all_free "default false"
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    refresh_token {
        uuid id PK
        uuid user_id FK
        string token UK
        string family_id
        datetime revoked_at
        datetime expires_at
        datetime created_at
    }

    regular_schedule {
        uuid id PK
        uuid user_id FK
        string title
        string days_of_week
        time start_time
        time end_time
        string morning_status
        string afternoon_status
        string evening_status
        int max_vacation_days "default 2, max 10"
        string vacation_apply_period "ANY|ONE_WEEK_BEFORE|TWO_WEEKS_BEFORE|ONE_MONTH_BEFORE|null"
        boolean is_half_vacation_available "default false"
        boolean is_holiday_rest "default true"
        datetime created_at
        datetime updated_at
    }

    personal_schedule {
        uuid id PK
        uuid user_id FK
        date schedule_date
        string morning_status
        string afternoon_status
        string evening_status
        boolean is_uncertain
        datetime created_at
        datetime updated_at
    }

    trip {
        uuid id PK
        uuid owner_id FK
        string name
        string destination
        date start_range
        date end_range
        int duration_days "nullable — 일정 미정"
        int member_count
        string invite_code
        string status
        string last_recommendation_mode
        string cancel_reason "wave 4"
        date confirmed_start_date
        date confirmed_end_date
        datetime last_activity_at
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    trip_member {
        uuid id PK
        uuid trip_id FK
        uuid user_id FK
        string role
        string status "JOINED|RESPONDED"
        boolean is_pinned
        datetime pinned_at
        datetime joined_at
        datetime responded_at "null if JOINED"
        datetime deleted_at
        datetime created_at
        datetime updated_at
    }

    recommendation {
        uuid id PK
        uuid trip_id FK
        int recommendation_rank
        date start_date
        date end_date
        text reason
        text risk_note
        float score
        datetime created_at
    }
```

## 3. 테이블 정의 (MVP In Scope)

### `users`

- **관련 BR:** BR-USER-001, BR-USER-003(wave 4)
- **관련 결정:** [`007-user-profile-onboarding.md`](../decisions/007-user-profile-onboarding.md), [`006-profile-image-url-storage.md`](../decisions/006-profile-image-url-storage.md)
- **테이블명:** `users` (MySQL 예약어 `user` 회피). Java 엔티티는 `User`.

| 컬럼 | 타입 | Nullable | PK/FK | 설명 |
|------|------|----------|-------|------|
| id | char(36) | N | PK | UUID v4 |
| social_id | varchar | N | | |
| provider | varchar | N | | KAKAO, GOOGLE, APPLE |
| email | varchar | Y | | UNIQUE 아님 |
| first_name | varchar | Y | | PATCH profile 필수 |
| last_name | varchar | Y | | PATCH profile 필수 |
| nickname | varchar | Y | | 소셜 prefill, fallback 없음 |
| profile_image_url | varchar | Y | | wave 1 CDN / wave 4 S3 B안 |
| is_google_calendar_connected | boolean | N | | default false |
| is_all_free | boolean | N | | default false. 전부 free 선언. row≥1이면 false 강제 |
| created_at | timestamptz | N | | |
| updated_at | timestamptz | N | | |
| deleted_at | timestamptz | Y | | Soft delete |

**API 파생·컬럼:** `hasPreSchedule` = EXISTS(regular) OR EXISTS(personal) (파생). **`users.is_all_free`** boolean default `false` — login/me `isAllFree`. 입장 = 정기 OR 개별 OR `is_all_free` ([`schedule-participation-onboarding.md`](../specs/schedule-participation-onboarding.md)). ~~`is_schedule_registered`~~ **제거**.

### `refresh_token`

wave 1+. [`004-auth-token-rotation.md`](../decisions/004-auth-token-rotation.md), [`auth-token-rotation.md`](../specs/auth-token-rotation.md)

| 컬럼 | 타입 | Nullable | PK/FK | 설명 |
|------|------|----------|-------|------|
| id | char(36) | N | PK | UUID v4 |
| user_id | char(36) | N | FK → users.id | |
| token | varchar(255) | N | | UNIQUE |
| family_id | char(36) | N | | UUID |
| revoked_at | timestamptz | Y | | wave 4 RTR |
| expires_at | timestamptz | N | | |
| created_at | timestamptz | N | | |

### `regular_schedule` (정기 일정)

User 소유. 출근·수업·회의 등 **복수 행**. **trip FK 없음** (BR-USER-008).

- **관련 BR:** BR-TRIP-006, BR-USER-006, BR-USER-008

| 컬럼 | 타입 | Nullable | PK/FK | 설명 |
|------|------|----------|-------|------|
| id | char(36) | N | PK | UUID v4 |
| user_id | char(36) | N | FK → users.id | |
| title | varchar | N | | 출근·수업·회의 등 표시명 |
| days_of_week | varchar | Y | | `MON,TUE,...` |
| start_time | time | Y | | |
| end_time | time | Y | | |
| morning_status | varchar | Y | | 계산: MORNING 슬롯 POSSIBLE/IMPOSSIBLE |
| afternoon_status | varchar | Y | | AFTERNOON |
| evening_status | varchar | Y | | EVENING |
| max_vacation_days | int | N | | default **2**, 허용 **0~10** |
| vacation_apply_period | varchar | Y | | enum: `ANY` · `ONE_WEEK_BEFORE` · `TWO_WEEKS_BEFORE` · `ONE_MONTH_BEFORE`. default **null** |
| is_half_vacation_available | boolean | N | | default **false** (N) |
| is_holiday_rest | boolean | N | | default **true** (Y) |
| created_at | timestamptz | N | | |
| updated_at | timestamptz | N | | |

**제약:** user당 **0..N행**. 1행 이상 → 입장 조건 1 충족 (D-JOIN-ENTRY). soft delete 없음.

### `personal_schedule` (개인 일정)

User 소유. **날짜당 1행** — 오전/오후/저녁 가능·불가 + 날짜 단위 불확실. **trip FK 없음.**

- **관련 BR:** BR-TRIP-002, BR-TRIP-003, BR-TRIP-004, BR-USER-008

| 컬럼 | 타입 | Nullable | PK/FK | 설명 |
|------|------|----------|-------|------|
| id | char(36) | N | PK | UUID v4 |
| user_id | char(36) | N | FK → users.id | |
| schedule_date | date | N | | |
| morning_status | varchar | N | | POSSIBLE / IMPOSSIBLE (`SlotStatuses`) |
| afternoon_status | varchar | N | | POSSIBLE / IMPOSSIBLE |
| evening_status | varchar | N | | POSSIBLE / IMPOSSIBLE |
| is_uncertain | boolean | N | | **날짜 전체** 불확실 (슬롯별 TBD 아님) |
| created_at | timestamptz | N | | |
| updated_at | timestamptz | N | | |

**제약:** `UNIQUE (user_id, schedule_date)`. soft delete 없음.

**시간대 (BR-TRIP-002, 확정):** MORNING `[00:00,13:00)`, AFTERNOON `[13:00,18:00)`, EVENING `[18:00,24:00)` — 공통 `TimeSlot` + `SlotStatuses` (정기와 동일)

**trip 조회:** `trip.start_range`~`end_range`로 해당 user의 `personal_schedule` 행 필터

### `trip`

- **관련 BR:** BR-TRIP-001, 007–011

| 컬럼 | 타입 | Nullable | PK/FK | 설명 |
|------|------|----------|-------|------|
| id | char(36) | N | PK | UUID v4 |
| owner_id | char(36) | N | FK → users.id | 방장 |
| name | varchar | N | | 최대 **15자** (BR-TRIP-001) |
| destination | varchar | Y | | 여행지. null = 「아직 못정했어요」 |
| start_range | date | N | | 희망 기간 시작. **생성 후 수정 불가** |
| end_range | date | N | | 희망 기간 종료. **생성 후 수정 불가** |
| duration_days | int | Y | | **m일만 저장**. null = 일정 미정. n박은 API 파생(`days-1`) · 요청 시 `durationNights`+`durationDays` 검증 |
| member_count | int | N | | **1~10** (BR-TRIP-001) |
| invite_code | varchar | N | | UNIQUE |
| status | varchar | N | | `ONGOING`, `CONFIRMED`, `CANCELED`, **`TERMINATED`** (기간 만료·종료) |
| last_recommendation_mode | varchar | Y | | BASIC, ALL_ATTEND, SAVE_VACATION, CERTAIN |
| cancel_reason | varchar | Y | | 취소·삭제 VOC. **wave 4** 구현 — Figma 플로우 있음 |
| confirmed_start_date | date | Y | | |
| confirmed_end_date | date | Y | | |
| last_activity_at | timestamptz | N | | 홈 정렬용 최근 활동. 생성·join·patch·**confirm**·추천·확정 시 갱신 ([`trip-room-api.md`](../specs/trip-room-api.md) D5 · #39) |
| created_at | timestamptz | N | | |
| updated_at | timestamptz | N | | |
| deleted_at | timestamptz | Y | | Soft delete |

**제약:** `duration_days`가 있을 때 `duration_days` ≤ `end_range - start_range + 1` (BR-TRIP-008). **당일치기(0박 1일) 허용** — `duration_days=1` (API `durationNights=0`) · [#2](https://github.com/Central-MakeUs/TripFit-server/issues/2) 확정 (2026-07-21).

### `trip_member`

방별 **참여·일정 확인** 상태. 일정 데이터는 User `personal_schedule`/`regular_schedule`에 있음 (BR-USER-007 · #39).

- **관련 BR:** BR-USER-002, BR-USER-007
- **관련 스펙:** [`trip-create-join-flow-redesign.md`](../specs/trip-create-join-flow-redesign.md) (#39), [`trip-room-api.md`](../specs/trip-room-api.md) D1

| 컬럼 | 타입 | Nullable | PK/FK | 설명 |
|------|------|----------|-------|------|
| id | char(36) | N | PK | UUID v4 |
| trip_id | char(36) | N | FK → trip.id | |
| user_id | char(36) | N | FK → users.id | NOT NULL |
| role | varchar | N | | OWNER, MEMBER |
| status | varchar | N | | **`JOINED`** = 멤버 row 있음·이 방 일정 확인 미완료(방장 create 직후, 입장 불가). **`RESPONDED`** = 확인·가입 완료(방 입장 가능, `canEnterRoom`도 필요) |
| is_pinned | boolean | N | | default false. **진행 중 캐러셀** 고정 (MVP In, wave 2 · D5) |
| pinned_at | timestamptz | Y | | Pin ON 시각. OFF면 null. Pin 그룹 내 정렬용 (D5) |
| joined_at | timestamptz | N | | 멤버 row 생성 시각 (방장=create, 멤버=join) |
| responded_at | timestamptz | Y | | 일정 확인·가입 완료 시각. **JOINED면 null**. confirm/join(RESPONDED INSERT) 시 set |
| deleted_at | timestamptz | Y | | **trip soft delete 시 연쇄 soft** |
| created_at | timestamptz | N | | |
| updated_at | timestamptz | N | | |

**활성 유일성:** `(trip_id, user_id)` where `deleted_at IS NULL` — **앱 레이어 강제** (`findByTripIdAndUserIdAndDeletedAtIsNull`). MySQL은 partial unique 미지원 → JPA `@UniqueConstraint` **없음**. soft-deleted row 재가입(신규 INSERT) 허용.

동명이인 `(2)` 표시: **DB 컬럼 없음** — BR-USER-009 조회 로직

**카운트:** `joinedMemberCount` = soft-delete 제외 전 멤버(JOINED 포함). `respondedCount` = `RESPONDED`만.

### `recommendation`

**현재 모드 TOP 3만** 유지 (BR-TRIP-005). 갱신 시 **hard DELETE** 후 INSERT (BR-TRIP-010).

- **관련 BR:** BR-TRIP-005, 011, 012

| 컬럼 | 타입 | Nullable | PK/FK | 설명 |
|------|------|----------|-------|------|
| id | char(36) | N | PK | UUID v4 |
| trip_id | char(36) | N | FK → trip.id | |
| recommendation_rank | int | N | | 1, 2, 3 (`rank` 예약어 회피) |
| start_date | date | N | | |
| end_date | date | N | | |
| reason | text | Y | | 추천 근거 |
| risk_note | text | Y | | |
| score | float | Y | | #13 순위·동점 비교 ([`trip-recommendation.md`](../specs/trip-recommendation.md)) |
| created_at | timestamptz | N | | |

**정책:** 모드 변경·trip 기간/일수 변경·trip soft delete → 해당 trip `recommendation` **hard DELETE**. `trip.last_recommendation_mode` 갱신.

## 4. 관계 요약

| From | To | 관계 | 설명 |
|------|-----|------|------|
| users | regular_schedule | 1:N | 정기 일정 (출근·수업·회의 등) |
| users | personal_schedule | 1:N | 개인 일정 (날짜당 1행) |
| users | trip_member | 1:N | 여행방별 참여 |
| users | trip | 1:N | owner_id (방장) |
| users | refresh_token | 1:N | |
| trip | trip_member | 1:N | |
| trip | recommendation | 1:N | 최대 3 (현재 모드) |

## 5. MVP 범위와의 매핑

| MVP 기능 | 테이블 |
|----------|--------|
| 소셜 로그인·프로필 | `users`, `refresh_token` |
| 정기·개별 일정 | `regular_schedule`, `personal_schedule` |
| 여행방·초대·여행지 | `trip`, `trip_member` |
| 추천 4모드·TOP3·확정 | `recommendation`, `trip.last_recommendation_mode`, `trip.confirmed_*` |

**Out of Scope (향후)**

- `notification_history` — BR-NOTI-* (wave 3+)
- `trip_expense`, `reservation` 등

## 6. 삭제·갱신 정책 (확정)

| 대상 | 정책 |
|------|------|
| `trip` soft delete | `trip_member` **연쇄 soft delete**. 정기·개별 일정·User 데이터 **유지** |
| `recommendation` | 옵션/기간 변경·모드 변경·trip delete → **hard DELETE** |
| `regular_schedule` · `personal_schedule` | User 소유 — trip 삭제와 **무관** |
| 전역 연동 | 개별·정기 변경 → 모든 참여 trip의 추천 입력 즉시 반영 (재계산은 BR-TRIP-010) |

## 7. 폐기 — A안 단일 `schedule`

2026-07-13 이전 SSOT였던 `schedule` + `row_type` 단일 테이블은 **폐기**.  
현재 SSOT는 §2~3 `regular_schedule` + `personal_schedule`. (구 B안 `user_work_profile` 명칭은 쓰지 않음 — 정기 일정은 근무 전용 아님)

## 8. 미정 / 구현 전

| 항목 | 내용 |
|------|------|
| `[미정]` | BR-TRIP-005 가중치 · BR-TRIP-012 동점 · TERMINATED **전환 시점**(lazy vs 배치) |
| wave 2 잔여 | `#12` trip CRUD · members schedule-calendar · `#13` 추천 |
| wave 4 | `trip.cancel_reason` VOC API·UI |

## 기획 메모 (NotebookLM + 확정)

1. **MVP 핵심:** `users`, `regular_schedule`, `personal_schedule`, `trip`, `trip_member`, `recommendation` + `refresh_token`
2. **2026-07-08:** TERMINATED, Pin(`is_pinned`), cancel_reason wave 4, 전역 연동
3. **2026-07-13:** A안 폐기 → 정기/개별 2테이블, 정기 N행·title·범용 시간 필드
4. **2026-07-20:** 홈 D5 — `trip.last_activity_at`, `trip_member.pinned_at` ([`trip-room-api.md`](../specs/trip-room-api.md))
5. **2026-07-21:** `#39` — `trip_member.status` **JOINED|RESPONDED** 부활 (방장 create=`JOINED` → confirm=`RESPONDED`)
6. **2026-07-21:** `trip.duration_days` **nullable**(일정 미정) · 희망 기간 생성 후 불변 · API n박+m일
6. **2026-07-21:** ERD 개선 반영 — `users` rename · `responded_at` · active UNIQUE(app) · `score`=#13 유지
7. 알림 이력 테이블 — ERD 범위 외 (wave 3)
