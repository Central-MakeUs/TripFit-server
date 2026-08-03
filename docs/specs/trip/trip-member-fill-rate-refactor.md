# 여행방 모집 현황 필드 정리 (memberFillRate 공식 변경 · joinedMemberCount 제거 · 상세 멤버 프리뷰 추가)

> 상태: Implemented
> MVP: In scope
> 관련 BR: D-MEMBER-FILL(`schedule-participation-onboarding.md`), D8, C-1(`kakao-invite-share.md`)

## 목표

여행방 상세·홈카드·멤버목록 3개 API 응답의 "모집 현황" 관련 필드(`joinedMemberCount`/`activeMemberCount`/`memberFillRate`/멤버 프리뷰)를 사용자 확정 결정에 맞춰 정리한다.

## 배경

- 이슈 [#60](https://github.com/Central-MakeUs/TripFit-server/issues/60)에서 발견: `TripDetailResponse`에 `TripHomeCardResponse`엔 있는 멤버 프리뷰가 없고, 와이어프레임이 말하는 "응답률" 개념이 어떤 필드인지 불명확.
- 사용자 확정(2026-07-28 대화):
  1. `TripDetailResponse`에도 `membersPreview`/`membersPreviewOverflow` 추가 — FE 상세 화면에 프리뷰 목록 존재 확인됨.
  2. "응답률" = "모집 충원율"(`memberFillRate`)로 개념 통합, 공식은 `activeMemberCount ÷ memberCount`로 변경(기존 `joinedMemberCount ÷ memberCount`에서 전환).
  3. `joinedMemberCount` 필드는 3개 DTO(`TripDetailResponse`/`TripHomeCardResponse`/`TripMembersResponse`) 전부에서 API 노출 제거. 총 참여 인원이 필요하면 FE는 `membersPreview.size() + membersPreviewOverflow`(또는 `TripMembersResponse.members` 배열 크기)로 유도.
  4. 필드명 `memberFillRate`는 그대로 유지(개명 안 함) — FE가 이미 이 필드명을 쓰고 있어 계약 변경 최소화.
- 스펙 조사 중 발견해 같이 amend하기로 확정한 기존 "확정"/Approved 문서 충돌:
  - `docs/specs/trip/kakao-invite-share.md`(Approved·종결) **C-1**: `n(미join 인원) = memberCount - joinedMemberCount`를 FE가 상세 API 필드로 직접 계산 → `joinedMemberCount` 제거로 문구 갱신 필요.
  - `docs/specs/trip/schedule-participation-onboarding.md` **D-MEMBER-FILL**(확정): `memberFillRate = joinedMemberCount / memberCount`로 명시돼 있어 공식·필드 노출 여부 갱신 필요.

## 요구사항

### Must Have

- [x] `TripDetailResponse`에 `membersPreview`(`List<MemberPreviewResponse>`)·`membersPreviewOverflow`(`int`) 추가 — 규칙은 홈카드와 동일(방장 우선 → `joinedAt` DESC, 최대 4명, `overflow = 총 참여 인원 - 4`, 최소 0)
- [x] `TripDetailResponse`/`TripHomeCardResponse`/`TripMembersResponse` 3개 모두에서 `joinedMemberCount` 필드 제거 (레포지토리 count 쿼리 자체는 overflow 계산·D8 정원 cap 판정용으로 내부에는 유지 — API 미노출만 의미)
- [x] `TripServiceSupport.memberFillRate(...)`: 인자를 `activeMemberCount` 기반으로 변경 — `activeMemberCount ÷ memberCount`
- [x] `TripServiceSupport.toDetail`: 단일 트립 멤버 프리뷰 조회 추가 — 기존 `tripMemberRepository.findMemberPreviewsByTripIds(Collection<UUID>)`를 `List.of(tripId)`로 재사용(신규 쿼리 작성 금지)
- [x] `TripMemberQueryService.listMembers`: `memberFillRate` 계산을 `activeMemberCount` 기준으로 변경, `joinedMemberCount` 로컬 변수는 응답 조립에서 제거(내부 계산에만 필요하면 유지)
- [x] 3개 DTO `@Schema` 설명 갱신 — `memberFillRate` 새 공식 명시("activeMemberCount 기준"으로 문구 정정), `joinedMemberCount` 필드·설명 삭제
- [x] Swagger 200 성공 예시(JSON) 갱신 — `TripController`/`TripMemberController`
- [x] 문서 amend: `kakao-invite-share.md`(C-1 공식), `schedule-participation-onboarding.md`(D-MEMBER-FILL 표), `docs/architecture/erd.md`(카운트 설명), `docs/product/flows/trip-create-join-guide.md`(필드 표), `docs/specs/trip/trip-room-api.md`(응답 예시·필드 설명)
- [x] 이슈 [#60](https://github.com/Central-MakeUs/TripFit-server/issues/60) 이 스펙으로 종결(구현 완료 후 클로즈)

### Nice to Have

- (없음)

### Out of Scope (이번 스펙에서 하지 않음)

- `memberFillRate` 필드명 변경 — 사용자 확정으로 유지
- 정원 동시성 hold([#35](https://github.com/Central-MakeUs/TripFit-server/issues/35)) — 별개 이슈

## API / 인터페이스

요청 변경 없음. 응답 필드만 변경.

| DTO (API) | 변경 |
|-----------|------|
| `TripDetailResponse` (`GET /trips/{tripId}` 등) | + `membersPreview`, `membersPreviewOverflow` / − `joinedMemberCount` / `memberFillRate` 공식 변경 |
| `TripHomeCardResponse` (`GET /trips`) | − `joinedMemberCount` / `memberFillRate` 공식 변경 (프리뷰는 기존 유지) |
| `TripMembersResponse` (`GET /trips/{tripId}/members`) | − `joinedMemberCount` / `memberFillRate` 공식 변경 |

성공 예시 (`TripDetailResponse` 발췌):

```json
{
  "data": {
    "activeMemberCount": 3,
    "memberFillRate": 0.5,
    "membersPreview": [
      {"userId": "3f2e2c1a-...", "profileImageUrl": "https://...", "role": "OWNER"},
      {"userId": "9a1b2c3d-...", "profileImageUrl": null, "role": "MEMBER"}
    ],
    "membersPreviewOverflow": 0
  }
}
```

## 데이터 모델

- DB 스키마 변경 없음 (컬럼 추가·삭제 아님) — `trip_member` 테이블 그대로, API 응답 필드 조정만.
- ERD 참조: `docs/architecture/erd.md:352` 설명 문구만 갱신 (joinedMemberCount API 미노출, memberFillRate 공식 변경 반영).

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| D-MEMBER-FILL (amend) | `memberFillRate = activeMemberCount ÷ memberCount`, `joinedMemberCount` API 미노출 | `TripServiceSupport.memberFillRate`, `schedule-participation-onboarding.md` |
| C-1 (amend) | `n(미join 인원) = memberCount - (membersPreview.size() + membersPreviewOverflow)` | FE 계산 로직, `kakao-invite-share.md` |
| D8 (변경 없음) | 신규 join 정원 초과 체크는 repository count로 서버 내부 판정 — API 노출 여부와 무관 | `TripServiceSupport`/join 처리 서비스 |

## 검증 시나리오

### 정상

- [x] `GET /trips/{tripId}` 응답에 `membersPreview`(최대 4, 방장 우선 → joinedAt DESC) + `membersPreviewOverflow` 포함, `joinedMemberCount` 없음
- [x] `GET /trips`, `GET /trips/{tripId}/members` 응답에도 `joinedMemberCount` 없음
- [x] 3개 API 모두 `memberFillRate`가 `activeMemberCount / memberCount`로 계산됨
- [x] 멤버 전원 `ACTIVE`일 때(방장 confirm 이후) 값이 정상 계산됨

### 엣지 · 실패

- [x] 참여 인원이 정원보다 4명 넘게 많을 때 `membersPreviewOverflow`가 올바르게 계산됨(예: 참여 5명 → overflow 1) — `overflow = Math.max(0, joinedMemberCount - MEMBERS_PREVIEW_LIMIT)` 로직 유지
- [x] 방장만 `SCHEDULE_PENDING`(아직 confirm 전)인 방 — `activeMemberCount`가 참여 인원보다 작아 `memberFillRate`가 기존 공식(참여 인원 기준) 대비 낮게 나옴 — 사용자 승인된 의도된 동작
- [x] `memberCount`가 `null`/`0` — `memberFillRate` 0.0 (기존 가드 로직 유지)

### 수동 / 통합

- [x] `./gradlew test`

## 완료 기준

- [x] `./gradlew test` 통과
- [x] `./gradlew build` 성공
- [x] 3개 DTO Swagger 200 예시 갱신 확인
- [x] `kakao-invite-share.md`, `schedule-participation-onboarding.md`, `erd.md`, `trip-create-join-guide.md`, `trip-room-api.md` amend 완료
- [x] 이슈 [#60](https://github.com/Central-MakeUs/TripFit-server/issues/60) 링크·종결

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| `memberFillRate` 값이 방장 `SCHEDULE_PENDING` 구간에서 기존(참여 인원 기준)보다 낮게 나오는 변화 — FE 표시 임팩트 | 확정 (사용자 승인) | FE 쪽 공지 필요 여부는 스펙 밖 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-28 | 초안 — 이슈 #60 논의 결과 반영, 사용자 결정(멤버 프리뷰 추가/공식 변경/joinedMemberCount 제거/필드명 유지) 확정 |
