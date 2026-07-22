# 여행방 API — 생성·참여·Pin

> wave: 2  
> implements: BR-TRIP-001, BR-TRIP-008, BR-TRIP-009, BR-TRIP-013, BR-USER-001, BR-USER-002, BR-USER-009, BR-USER-010  
> deferred: **정원 hold → [#35](https://github.com/Central-MakeUs/TripFit-server/issues/35)** [`trip-join-capacity-hold.md`](trip-join-capacity-hold.md), BR-TRIP-010 (recommendation hard DELETE — [`trip-recommendation.md`](trip-recommendation.md)), `cancel_reason` VOC (wave 4), **카카오 초대·확정·재촉 공유 → [#19](https://github.com/Central-MakeUs/TripFit-server/issues/19)** [`kakao-invite-share.md`](kakao-invite-share.md), 푸시 알림 → [#21](https://github.com/Central-MakeUs/TripFit-server/issues/21), **`last_activity_at` 전체 갱신·AOP → [#26](https://github.com/Central-MakeUs/TripFit-server/issues/26)** [`trip-last-activity-at.md`](trip-last-activity-at.md), **TERMINATED DB 전환·Pin 자동 해제 스케줄러 → [#27](https://github.com/Central-MakeUs/TripFit-server/issues/27)** [`trip-home-schedulers.md`](trip-home-schedulers.md)  
> related Implemented: 참여자 내보내기 → [#20](https://github.com/Central-MakeUs/TripFit-server/issues/20) [`trip-member-remove.md`](trip-member-remove.md)
> 상태: **Approved** (D3~D6·D8 확정 — 2026-07-17) · **D1·참여 = #22 확정** (2026-07-21) · **D5 홈 2뷰 amend** (2026-07-19) · **D5 구현 후속 defer #26·#27** (2026-07-19)  
> 선행: [`auth-social-login.md`](auth-social-login.md), [`user-onboarding.md`](user-onboarding.md), [`schedule-unified.md`](schedule-unified.md), [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) (#17 Implemented), **[#22](https://github.com/Central-MakeUs/TripFit-server/issues/22)** (참여·`is_all_free`)  
> 후속: [`trip-recommendation.md`](trip-recommendation.md)

## 목표

방장이 여행방을 만들고 초대·참여·홈(진행 중 캐러셀 / 전체 목록)·Pin까지 REST API로 제공한다. MVP 완료 기준(wave 2)의 **여행방·참여** 축을 담당한다.

## 프론트·신규 개발자 — 멤버십·공유 (필독)

Swagger Info / Trip 태그에도 동일 요약이 있다.

| 상태 | 누가 | 할 수 있는 것 | 못 하는 것 |
|------|------|---------------|------------|
| **JOINED** | **방장만** (`POST /trips` 직후) | 홈 목록 노출 · PATCH/DELETE(메타) · `schedule/confirm` | 상세·멤버·달력·Pin · **초대 공유** · create 응답 `inviteCode` |
| **RESPONDED** | 방장(confirm 후) · 멤버(`POST /join` **즉시**) | 방 입장(추가 입장 조건) · 상세(`inviteCode`) · **방장만** 공유 UI | 멤버가 JOINED가 되는 일 |

**멤버는 JOINED를 거치지 않는다.** “일정 미입력 멤버 = JOINED”는 **틀린 모델**이다.

초대 공유 상세: [`kakao-invite-share.md`](kakao-invite-share.md) (S-1 방장 · S-2 RESPONDED).

## 배경

- **구현 상태:** D5 홈 2뷰(`scope`·필터·`last_activity_at`·`pinned_at`)·`TripHomeCardResponse`/`TripDetailResponse` 분리·`membersPreview` 배치 조회 반영. **#27** TERMINATED·Pin 배치 Implemented · **#26** `last_activity_at` AOP Implemented
- **참여:** 소셜 로그인 필수 (BR-USER-002), 비회원 없음. **초대는 카카오·OS 링크 공유**(딥링크/Universal Link에 `inviteCode` 포함) — 코드 수동 입력은 보조
- **일정 데이터:** User 전역 `regular_schedule` + `personal_schedule` (BR-USER-008) — [`schedule-unified.md`](schedule-unified.md)
- **참여 완료:** 방장 — create 시 `JOINED` → `POST .../schedule/confirm` 후 `RESPONDED`. 멤버 — `POST /join` 시 `RESPONDED`. 방 안 API는 `RESPONDED` ∧ `canEnterRoom` ([#39](https://github.com/Central-MakeUs/TripFit-server/issues/39))
- **홈 UI SSOT:** 정책서 홈 — 진행 중인 여행(캐러셀) + 전체 여행 보기(리스트·필터). Pin은 **진행 중 캐러셀에만** 정렬 적용

### 관련 문서

| 문서 | 내용 |
|------|------|
| `docs/product/flows/trip-create-join-guide.md` | 생성·참여 상세(빠른 요약·시나리오·정책, JOINED→confirm 포함) |
| `docs/product/business-rules/trip.md` | BR-TRIP-001, 008, 009, 013 |
| `docs/product/design/figma-wireframe-v1.md` | 홈 캐러셀·전체 보기·Pin |
| `docs/architecture/erd.md` | `trip`, `trip_member` (`last_activity_at`, `pinned_at`) |

## 확정 사항

| ID | 항목 | 결정 | 확정일 |
|----|------|------|--------|
| **D1** | 참여 완료 | 방장 create=`JOINED` → `POST .../schedule/confirm`=`RESPONDED`. 멤버 join=`RESPONDED`. **submit 없음** — [#39](https://github.com/Central-MakeUs/TripFit-server/issues/39) | 2026-07-21 |
| **D2** | 그룹 일정 조회 | **T1** — `members/schedule-calendar`(effective) **OpenAPI 공개**. 기간=`startRange`~`endRange`. **#38** CONFIRMED/TERMINATED snapshot · **#37** 마이페이지 today+2년·칩 | 2026-07-21 |
| **D3** | `invite_code` | **6자** Crockford Base32 (`0`/`O`/`I`/`1` 제외). 링크 공유 UX — 아래 §초대 | 2026-07-17 |
| **D4** | CONFIRMED·CANCELED | 기존 멤버 재접속 idempotent · **신규 join 409** · PATCH는 **`ONGOING`만** | 2026-07-17 |
| **D5** | 홈 목록 | **2뷰** (`scope=ongoing` \| `all`) · `last_activity_at` · `pinned_at` — 아래 §홈 목록 | **2026-07-19** |
| **D6** | 이름 최대 길이 | **15자** | 2026-07-17 |
| **D8** | 인원·기간 cap | `memberCount` **1~10** (create/patch) · `joinedMemberCount >= memberCount` → 신규 join 409 · `end_range` 경과(TERMINATED) → 초대·신규 join 불가 | 2026-07-17 |
| **D9** | 일정·기간·박일 | `duration_days` **nullable**(미정). create/patch: `durationNights`+`durationDays` 쌍 또는 둘 다 null. DB는 **일만** 저장. **희망 기간은 create만·PATCH 불가**. **당일치기(0박 1일) 허용** (`nights=0`, `days=1`) | **2026-07-21** |

### D5 상세 (홈 목록 — 2026-07-19 확정)

| 뷰 | 쿼리 | 노출 | 정렬 | Pin |
|----|------|------|------|-----|
| **진행 중인 여행** (캐러셀) | `scope=ongoing` | `end_range >= today` 인 **내가 참여한** 방만 | ① `is_pinned DESC` ② Pin 그룹: `pinned_at DESC` ③ 미고정: `last_activity_at DESC` | **적용** |
| **전체 여행 보기** (리스트) | `scope=all` (기본) | 참여한 **모든** 방 (TERMINATED 포함) | `last_activity_at DESC` **만** | **정렬에 미적용** |

**전체 보기 필터** (동시 적용 가능):

| 파라미터 | 값 | 의미 |
|----------|-----|------|
| `status` | 생략/`ALL` | 전체 (종료=`TERMINATED` 포함) |
| `status` | `ONGOING` | 조율 중 — **TripStatus** · effectiveStatus |
| `status` | `CONFIRMED` | 일정 확정 — **TripStatus** (별도 filter enum 없음) |
| `ownerOnly` | `true`/`false`(기본) | `true`면 본인 `role=OWNER` 방만 |

**최근 활동 (`trip.last_activity_at`)** — D5 Must: 컬럼·정렬·create 시 초기값. **갱신·AOP SSOT:** [`trip-last-activity-at.md`](trip-last-activity-at.md) **Approved (#26)** — create/join/patch touch · #26 `@TripActivity` AOP.

- **갱신 SSOT:** [`trip-last-activity-at.md`](trip-last-activity-at.md) **L1·L2 확정** — touch: 생성 · join · PATCH · 추천(#13) · 확정(#13). **전역 일정 PATCH → touch 안 함** · ~~trip submit~~ 삭제(#22)
- **구현:** `@TripActivity` + AOP · `TripCommandService` + `TripRecommendationService`(#13)

**Pin (`trip_member`):**

- `is_pinned` + `pinned_at` (Pin ON 시 `now()`, OFF 시 `null`)
- **진행 중 캐러셀에서만** 정렬 우선순위 적용
- `end_range < today` Pin **자동 해제** — **[#27](https://github.com/Central-MakeUs/TripFit-server/issues/27) Implemented** [`trip-home-schedulers.md`](trip-home-schedulers.md): 매일 00:05 KST · TERMINATED DB UPDATE + Pin 해제 **통합 job**
- Pin 토글: `PATCH /trips/{id}/pin` (기존)

**프론트 전용 (API 범위 밖):**

- 캐러셀·페이지 인디케이터·스와이프
- 진행 중 방 3개 미만 시 「여행방 신규 생성하기」 카드
- 전체 0건 Empty → TripFit 가이드 버튼
- 말줄임·Pin Outline/Filled 아이콘·공유 팝업 메뉴

## 요구사항

### Must Have

- [ ] `POST /api/v1/trips` — 방장 생성 (BR-TRIP-001: 이름 **≤15자**, 인원 **1~10**, BR-USER-001 이름 필수)
- [ ] 생성 시 `trip_member` OWNER + **`JOINED`**, `invite_code` UNIQUE(6자), `status=ONGOING`, `last_activity_at` 초기화 · `needsScheduleConfirm`
- [ ] `POST /api/v1/trips/{tripId}/schedule/confirm` — JOINED→RESPONDED (+ row0 `is_all_free`) · idempotent (#39)
- [ ] 방 안 API (`@TripMemberOnly`): **RESPONDED** ∧ `canEnterRoom` — 아니면 `SCHEDULE_CONFIRM_REQUIRED` / `SCHEDULE_ENTRY_REQUIRED`
- [ ] `GET /api/v1/trips` — **D5** `scope=ongoing|all` · 필터·정렬 (§홈 목록) · **`TripHomeCardResponse`** (`myRole`·`membersPreview`)
- [ ] `trip.last_activity_at` 컬럼 + create/join/patch/confirm **최소** 갱신 (D5) — 전체 hook → **#26**
- [ ] `trip_member.pinned_at` 컬럼 · Pin ON/OFF 시 설정/해제 (D5)
- [ ] `GET /api/v1/trips/{tripId}` — 상세 **`TripDetailResponse`** (RESPONDED 참여자만 · `membersPreview` 없음)
- [ ] `PATCH /api/v1/trips/{tripId}` — 방장만 (JOINED 허용) · **`status=ONGOING`만** (D4) · **기간 필드 없음**(D9) · `last_activity_at` 갱신 (최소)
- [ ] 일정: `durationNights`+`durationDays` 쌍 검증 · null=미정 · DB `duration_days`만 (D9 · BR-TRIP-001)
- [ ] `durationDays` 있을 때 ≤ 기간 일수 검증 (BR-TRIP-008)
- [ ] `POST /api/v1/trips/join` — `{ inviteCode }` → MEMBER + **`RESPONDED`** (이미 RESPONDED 시 idempotent — BR-USER-010) · `last_activity_at` 갱신
- [ ] join 거부: CONFIRMED/CANCELED/TERMINATED **신규** → 409; 인원 가득 → 409 (D4·D8)
- [ ] `GET /api/v1/trips/{tripId}/members` — status·role·pinned·응답률 + 동명이인 `홍길동(2)` (BR-USER-009)
- [ ] `PATCH /api/v1/trips/{tripId}/pin` — `{ pinned: boolean }` 본인 `is_pinned` + `pinned_at`
- [ ] `DELETE /api/v1/trips/{tripId}` — 방장 soft delete (JOINED 허용), `trip_member` **연쇄 soft** (BR-TRIP-013)
- [ ] `TripMember` → `SoftDeleteEntity` 또는 `deleted_at` 추가
- [ ] `./gradlew test` 통과

### Nice to Have

- (없음)

### Deferred (#12 범위 밖 — docs·이슈 필수)

| 항목 | 이슈·스펙 |
|------|-----------|
| `last_activity_at` 전체 갱신·AOP | [#26](https://github.com/Central-MakeUs/TripFit-server/issues/26) · [`trip-last-activity-at.md`](trip-last-activity-at.md) |
| TERMINATED DB 전환 · Pin 자동 해제 스케줄러 | [#27](https://github.com/Central-MakeUs/TripFit-server/issues/27) · [`trip-home-schedulers.md`](trip-home-schedulers.md) |

### Out of Scope

| 항목 | 이슈 |
|------|------|
| 추천·확정·취소 | [#13](https://github.com/Central-MakeUs/TripFit-server/issues/13) |
| **참여·`is_all_free`·온보딩·sparse** | **[#22](https://github.com/Central-MakeUs/TripFit-server/issues/22)** (wave 1) |
| 카카오 초대·확정·재촉 공유 | [#19](https://github.com/Central-MakeUs/TripFit-server/issues/19) · [`kakao-invite-share.md`](kakao-invite-share.md) (wave 3) |
| 푸시 알림 | [#21](https://github.com/Central-MakeUs/TripFit-server/issues/21) (wave 3) |
| `cancel_reason` VOC | wave 4 |

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/api/v1/trips` | JWT + 이름 | 여행방 생성 · owner **JOINED** |
| GET | `/api/v1/trips` | JWT | 홈 목록 — **D5** `scope`·필터·정렬 |
| GET | `/api/v1/trips/{tripId}` | JWT + member **RESPONDED** | 상세 |
| PATCH | `/api/v1/trips/{tripId}` | JWT + owner (JOINED 허용) | 메타 수정 · ONGOING만 |
| DELETE | `/api/v1/trips/{tripId}` | JWT + owner (JOINED 허용) | soft delete |
| POST | `/api/v1/trips/join` | JWT | 초대 참여 · **RESPONDED** |
| POST | `/api/v1/trips/{tripId}/schedule/confirm` | JWT + member | **JOINED→RESPONDED** (#39) |
| GET | `/api/v1/trips/{tripId}/members` | JWT + member **RESPONDED** | 참여자 목록 |
| PATCH | `/api/v1/trips/{tripId}/pin` | JWT + member **RESPONDED** | Pin 토글 |
| ~~POST~~ | ~~`/api/v1/trips/{tripId}/schedule/submit`~~ | — | **삭제** |
| GET | `/api/v1/trips/{tripId}/members/schedule-calendar` | JWT + member **RESPONDED** | OpenAPI 공개 |

### `GET /trips` — 홈 목록 (D5)

**Query**

| 이름 | 필수 | 기본 | 설명 |
|------|------|------|------|
| `scope` | N | `all` | `ongoing` = 진행 중 캐러셀 · `all` = 전체 여행 보기 |
| `status` | N | `ALL` | **`scope=all`만** · `ALL` \| **TripStatus** `ONGOING` \| `CONFIRMED`. `CANCELED`/`TERMINATED` 단독 필터 400 — `ALL`에 포함 |
| `ownerOnly` | N | `false` | **`scope=all`만** · `true`면 본인이 생성(OWNER)한 방만 |

`scope=ongoing`일 때 `status`·`ownerOnly`는 **무시** (구현: `TripListQuery.parse`에서 무시).

**쿼리 파싱:** Controller는 `TripListQuery.parse(scope, status, ownerOnly)` record 사용 (`trip/dto/TripListScope` enum).

**정렬**

| scope | ORDER BY |
|-------|----------|
| `ongoing` | `tm.is_pinned DESC`, `tm.pinned_at DESC NULLS LAST`, `t.last_activity_at DESC` |
| `all` | `t.last_activity_at DESC` (**Pin 무시**) |

**필터 (`ongoing`)**

- `t.end_range >= CURRENT_DATE`
- 참여·미삭제 멤버십만

### 초대 링크 (D3)

- 서버: trip 생성 시 **`invite_code`** 6자 발급 (UNIQUE, 충돌 시 재시도)
- 클라이언트: 공유 URL — `https://tripfit.online/room/{inviteCode}` (`docs/decisions/002-domain-split-vercel-api.md` — 프론트 Vercel)
- 참여: 링크 진입 → 로그인 → `POST /trips/join` `{ inviteCode }` (코드 직접 입력 UI는 보조)
- **공유 시점:** 방장 ∧ **`RESPONDED`(방 입장 후)**. create(`JOINED`) 응답에는 `inviteCode` 없음 — [`kakao-invite-share.md`](kakao-invite-share.md) S-1·S-2
- **D8 (클라이언트):** 인원 가득·`end_range` 경과 시 공유 시트/초대 UI 비노출 + alert. 서버는 join 409로 동일 정책 보장

### `POST /trips` 요청

```json
{
  "name": "제주 3박4일",
  "startRange": "2026-08-01",
  "endRange": "2026-08-10",
  "durationNights": 3,
  "durationDays": 4,
  "memberCount": 6,
  "destination": "제주"
}
```

| 필드 | 규칙 |
|------|------|
| `name` | 최대 **15자** |
| `startRange` / `endRange` | **필수** (생성 시에만 설정) |
| `durationNights` / `durationDays` | **둘 다 null** = 일정 미정. **둘 다 값** = 확정 (`nights == days - 1`, `days ≥ 1`, `nights ≥ 0`, `days` ≤ 기간 일수). **0박 1일(당일치기) 허용**. 한쪽만 → 400 |
| `memberCount` | **1~10** 필수 |
| `destination` | nullable (미정) |

**당일치기(0박 1일):** **허용** (2026-07-21 · [#2](https://github.com/Central-MakeUs/TripFit-server/issues/2)). `durationNights=0`, `durationDays=1`. 최소 1박 강제 없음.

### `PATCH /trips/{tripId}` 요청

```json
{
  "name": "제주 3박4일",
  "durationNights": 3,
  "durationDays": 4,
  "memberCount": 6,
  "destination": "제주"
}
```

- **`startRange` / `endRange` 없음** — 희망 기간 수정 불가 (BR-TRIP-009 · D9)
- 일정·여행지 미정은 create와 동일(null 쌍 / destination null)

### `POST /trips` 응답

방장 `JOINED` — **방 입장 전**. 공유용 `inviteCode`는 **포함하지 않음** (DB에는 발급). confirm → `RESPONDED` 후 `GET/…` 상세에서 노출.

```json
{
  "data": {
    "tripId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "ONGOING",
    "myMemberStatus": "JOINED",
    "needsScheduleConfirm": true
  }
}
```

> 초대 공유: [`kakao-invite-share.md`](kakao-invite-share.md) **S-1·S-2** — 방장 ∧ RESPONDED.
### TripHomeCardResponse (홈 목록 전용)

`GET /trips` 카드 한 건. **`membersPreview` 포함** (최대 4 + overflow). 상세용 `inviteCode` 등은 **미포함**.

```json
{
  "tripId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "제주 3박4일",
  "destination": "제주",
  "startRange": "2026-08-01",
  "endRange": "2026-08-10",
  "durationDays": 4,
  "durationNights": 3,
  "memberCount": 6,
  "status": "ONGOING",
  "confirmedStartDate": null,
  "confirmedEndDate": null,
  "lastRecommendationMode": null,
  "lastActivityAt": "2026-07-19T12:00:00",
  "pinned": false,
  "myRole": "OWNER",
  "myMemberStatus": "RESPONDED",
  "respondedCount": 2,
  "joinedMemberCount": 4,
  "memberFillRate": 0.67,
  "membersPreview": [
    {
      "userId": "...",
      "profileImageUrl": "https://...",
      "role": "OWNER"
    }
  ],
  "membersPreviewOverflow": 0
}
```

**`membersPreview` 정렬:** 방장(OWNER) 먼저 → 나머지 `joined_at` **내림차순**. 최대 **4**명. 초과 시 `membersPreviewOverflow = joinedMemberCount - 4`.

**조회 구현 (#12):** trip id 목록 기준 **배치 native query** (`ROW_NUMBER` — trip당 4명, OWNER 우선·`joined_at DESC`). N+1 금지.

### TripDetailResponse (상세·join·patch·pin)

`GET/PATCH/join/pin` 응답. **`inviteCode` 포함** · **`membersPreview` 없음** (멤버 UI는 `GET .../members`).

```json
{
  "tripId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "제주 3박4일",
  "destination": "제주",
  "startRange": "2026-08-01",
  "endRange": "2026-08-10",
  "durationDays": 4,
  "durationNights": 3,
  "memberCount": 6,
  "status": "ONGOING",
  "inviteCode": "A2B3C4",
  "confirmedStartDate": null,
  "confirmedEndDate": null,
  "lastRecommendationMode": null,
  "lastActivityAt": "2026-07-19T12:00:00",
  "pinned": false,
  "myRole": "OWNER",
  "myMemberStatus": "RESPONDED",
  "respondedCount": 2,
  "joinedMemberCount": 4,
  "memberFillRate": 0.67
}
```

예시: `memberCount=6` → fillRate ≈ 4/6.

### `members/schedule-calendar` 응답 (D2)

trip `startRange`~`endRange`(**희망 기간 = 조회 기간**, #37 C2/C3). 멤버 **전원** × effective (S1·R2=A · #17).  
- **ONGOING:** live · 본인 전역 일정 수정 가능  
- **CONFIRMED / TERMINATED:** snapshot · **읽기 전용** — [`trip-schedule-snapshot.md`](trip-schedule-snapshot.md) (#38)  
- 마이페이지 today+2년·ONGOING 칩 인덱싱 — [`trip-schedule-calendar-window.md`](trip-schedule-calendar-window.md) (#37 C1)

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
| 400 | `INVALID_INPUT` | BR-TRIP-001/008 위반, name **15자** 초과, `memberCount` **1~10** 밖, 잘못된 `scope`/`status` |
| 400 | `CANNOT_REMOVE_OWNER` | 방장 내보내기 (#20) |
| 403 | `PROFILE_NAME_REQUIRED` | BR-USER-001 |
| 403 | `TRIP_FORBIDDEN` | owner 아닌 PATCH/DELETE · 멤버 내보내기 |
| 403 | `TRIP_ACCESS_DENIED` | 비참여자 |
| 403 | `SCHEDULE_CONFIRM_REQUIRED` | `JOINED` — 방 안 API (#39) |
| 403 | `SCHEDULE_ENTRY_REQUIRED` | canEnterRoom 불만족 (#22 D-JOIN-ENTRY) |
| 404 | `TRIP_NOT_FOUND` | 없음 또는 soft deleted |
| 404 | `INVITE_CODE_NOT_FOUND` | 잘못된 초대 코드 |
| 404 | `TRIP_MEMBER_NOT_FOUND` | 대상 멤버 없음·이미 soft-deleted (#20) |
| 409 | `TRIP_NOT_ONGOING` | ONGOING 아닌 trip PATCH/내보내기 (D4 · #20) |
| 409 | `TRIP_ALREADY_CONFIRMED` | CONFIRMED trip **신규** join |
| 409 | `TRIP_CANCELED` | CANCELED trip **신규** join |
| 409 | `TRIP_TERMINATED` | TERMINATED trip **신규** join · `end_range` 경과 |
| 409 | `TRIP_MEMBER_FULL` | `joinedMemberCount >= memberCount` **신규** join (D8) |

## 데이터 모델

- ERD: `trip`, `trip_member` — [`erd.md`](../architecture/erd.md)
- **`invite_code`:** 6자 Crockford Base32 (`23456789ABCDEFGHJKMNPQRSTUVWXYZ`), UNIQUE
- **추가·보강 컬럼 (D5):**
  - `trip.last_activity_at` (timestamptz, NOT NULL) — 홈 정렬용 최근 활동
  - `trip_member.pinned_at` (timestamptz, nullable) — Pin ON 시각; OFF면 null
  - (기존) `trip_member.is_pinned`, `Trip.lastRecommendationMode`, `TripMember.deletedAt`
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

- **기존 멤버** 재접속: idempotent (BR-USER-010) — CONFIRMED/CANCELED/TERMINATED에서도 조회 등 역할별 허용
- **TERMINATED:** `end_range < today` — **#27 Approved:** DB `status=TERMINATED` batch (00:05 KST) · 조회 전까지 effectiveStatus lazy 유지
- **방 입장:** `canEnterRoom` — [#22](schedule-participation-onboarding.md) · 403 `SCHEDULE_ENTRY_REQUIRED`

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-TRIP-001 | 필수 필드 · 이름 ≤15자 · 인원 1~10 · 일정/여행지 미정 허용 · n박 m일 → 일만 저장 | create/patch 검증 |
| BR-TRIP-008 | duration ≤ range (있을 때만) | create/patch |
| BR-TRIP-009 | 방장만 PATCH · ONGOING만 · **기간 수정 불가** | service |
| BR-TRIP-013 | 방장 DELETE soft | TripService |
| BR-USER-006 · BR-USER-007 | 입장·참여 (`is_all_free` · create/join `RESPONDED`) | **[#22](https://github.com/Central-MakeUs/TripFit-server/issues/22)** |
| BR-USER-010 | 재join idempotent | join service |

### PATCH trip 시 BR-TRIP-010

**`durationDays` 변경** 시 **`recommendation` hard DELETE** — hook 지점 주석/호출 (#13). (기간은 불변 → PATCH 트리거 없음)

### ~~`personal-summary`~~ (삭제)

구 `GET .../members/personal-summary`(personal-only)는 **삭제**. 그룹 조회는 `members/schedule-calendar`만 사용.

## 검증 시나리오

### 정상

- [ ] 방장 create → OWNER + inviteCode(6자) + `last_activity_at` 설정
- [ ] `GET /trips?scope=ongoing` — `end_range >= today`만 · Pin → `pinned_at` → `last_activity_at`
- [ ] `GET /trips?scope=all` — Pin 무시 · `last_activity_at DESC` · TERMINATED 포함
- [ ] `GET /trips?scope=all&status=ONGOING` · `&status=CONFIRMED` · `&ownerOnly=true` 동시 적용
- [ ] Pin ON → `pinned_at` 설정 · Pin OFF → `pinned_at` null
- [ ] join / patch → `last_activity_at` 갱신 (최소)
- [ ] 참여자 join → MEMBER **RESPONDED**
- [ ] `membersPreview` — OWNER 먼저 · 나머지 joinedAt DESC · 최대 4 + overflow · 배치 조회
- [ ] 상세/join/patch/pin → `TripDetailResponse` (`membersPreview` 없음)

### 엣지 · 실패

- [ ] name 16자 → 400
- [ ] `memberCount` 0 또는 11 → 400
- [ ] invite_code에 `0/O/I/1` 포함 생성 경로 없음
- [ ] CONFIRMED/CANCELED/TERMINATED **신규** join → 409
- [ ] CONFIRMED trip **기존 멤버** join 재호출 → idempotent 200
- [ ] `joinedMemberCount >= memberCount` 신규 join → 409
- [ ] CONFIRMED trip PATCH → 409 `TRIP_NOT_ONGOING`
- [ ] canEnterRoom 불만족 → 403 `SCHEDULE_ENTRY_REQUIRED` (#22)

## 완료 기준

- [ ] `./gradlew test` 통과
- [ ] OpenAPI 반영 (`scope`·필터·`TripHomeCardResponse` / `TripDetailResponse`)
- [ ] BR-TRIP-010 hook — #13과 통합 테스트 1건 이상
- [ ] #12 이슈 체크리스트 동기화 (D5 amend)

## 리스크·잔여

| 항목 | 비고 |
|------|------|
| TERMINATED 전환 시점 | **#27 Approved** — DB UPDATE 매일 00:05 KST · effectiveStatus lazy는 배치 전까지 · [`trip-home-schedulers.md`](trip-home-schedulers.md) |
| PATCH `startRange`/`endRange` | 정책서 vs 화면정의서 충돌 |
| 참여자 내보내기 | [#20](https://github.com/Central-MakeUs/TripFit-server/issues/20) · [`trip-member-remove.md`](trip-member-remove.md) | Nice · **Implemented** |
| User 전역 일정 수정 → 참여 trip `last_activity_at` | **#26 L2 확정 — touch 안 함** · [`trip-last-activity-at.md`](trip-last-activity-at.md) |
| 그룹 `schedule-calendar` OpenAPI 공개 | **완료** · ~~personal-summary~~ 삭제 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-22 | **FE 필독** — 멤버십·공유 오해 표(glossary) · trip-room-api 절 · Swagger Info/Tag |
| 2026-07-22 | **create 응답** — `inviteCode` 제거(JOINED=입장·공유 전). 상세(RESPONDED)만 노출 · `#19` S-2 |
| 2026-07-22 | **D7 폐기** — join 전 미리보기 기능 삭제 · 카카오 공유는 `#19` [`kakao-invite-share.md`](kakao-invite-share.md) |
| 2026-07-21 | 에러 표 — `#20` `CANNOT_REMOVE_OWNER`·`TRIP_MEMBER_NOT_FOUND` · Out of Scope에서 #20 제거 |
| 2026-07-21 | 참여자 내보내기 → [`trip-member-remove.md`](trip-member-remove.md) (#20 Approved) |
| 2026-07-21 | **#39** — D1 amend: create=`JOINED` · `schedule/confirm` · RESPONDED 게이트 |
| 2026-07-21 | schedule-calendar OpenAPI 공개 · personal-summary 삭제 · D2 갱신 |
| 2026-07-21 | **#22 정합** — submit/`JOINED`/`[미정]` stale 정리 · D1·D2·에러코드 · `RESPONDED` 예시 |
| 2026-07-21 | **D9** — 당일치기(0박 1일) **허용** 확정 (#2) |
| 2026-07-21 | **D9** — `duration_days` nullable · API n박+m일 · PATCH 기간 잠금 |
| 2026-07-08 | 초안 |
| 2026-07-13 | 일정 용어 #11 정합 |
| 2026-07-17 | 정책서 반영 |
| 2026-07-17 | **Approved** — D3~D6·D8 확정 |
| 2026-07-17 | **D1·submit·schedule-calendar → [#22](https://github.com/Central-MakeUs/TripFit-server/issues/22) deferred** · OpenAPI Hidden |
| 2026-07-19 | **D5 amend** — 홈 2뷰(`scope=ongoing\|all`) · `last_activity_at` · `pinned_at` · 전체 필터 · `membersPreview` |
| 2026-07-19 | **status 필터 enum 통합** — `TripListStatusFilter` 삭제 · `TripStatus`(+`ALL`) 재사용 |
| 2026-07-19 | **D5 구현 후속** — DTO 분리(`TripHomeCardResponse`/`TripDetailResponse`) · `TripListQuery` · `membersPreview` 배치 · **#26·#27 defer** (Pin lazy·TERMINATED 스케줄러) |
| 2026-07-19 | **#26 L1·L2** — `last_activity_at` 갱신 이벤트 · 전역 일정 PATCH touch 안 함 → [`trip-last-activity-at.md`](trip-last-activity-at.md) |
| 2026-07-19 | **#27 Implemented** — `TripHomeScheduler` · TERMINATED batch · Pin clear |