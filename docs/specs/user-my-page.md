# 마이페이지 이름 수정 · 알림 설정

> wave: 1 (이름 수정) · 3 (알림 설정 필드 — `#21` D8 amend)  
> implements: — (마이페이지 UI에서 이름 변경) · BR-USER-005(알림 on/off)  
> 결정: [`docs/decisions/007-user-profile-onboarding.md`](../decisions/007-user-profile-onboarding.md) — `first_name`/`last_name` SSOT  
> 선행: [`user-onboarding.md`](user-onboarding.md) — 온보딩 필수 이름 입력  
> 상태: Approved (2026-07-23 알림 설정 필드 amend — 아래 "변경 이력")  
> 승인: 2026-07-09 (이름 PATCH) · 2026-07-23 (알림 설정 필드 추가 + partial update 전환)  
> deferred: 회원 탈퇴 → [`user-account-withdrawal.md`](user-account-withdrawal.md) (Implemented, `#47` 브랜치)

## 목표

온보딩 이후 **마이페이지**에서 사용자가 성·이름을 수정하고, 알림 수신 여부(BR-USER-005)를 설정할 수 있게 한다.

## 배경

- 온보딩 최초 입력: `PATCH /api/v1/users/profile` ([`user-onboarding.md`](user-onboarding.md))
- 마이페이지 재수정: 별도 엔드포인트로 UI 의도를 분리 (동일 컬럼·검증 재사용)
- Figma: [`figma-wireframe-v1.md`](../product/design/figma-wireframe-v1.md) — 마이페이지(설정·탈퇴·캘린더 연동)
- **2026-07-23:** [`notification.md`](notification.md)(`#21`) D8 — 알림 on/off를 별도 `/users/me/...` 엔드포인트로 새로 만들지 않고, 이 스펙의 `PATCH /users/my-page`에 `notificationEnabled` 필드로 편입 (이 프로젝트가 과거 `/users/me/*` 경로를 `/users/*`로 통일한 이력과 일치). 필드 하나만 보내는 경우를 지원해야 하므로 **전 필드를 optional(partial update)로 전환**

## 요구사항

### Must Have (wave 1 — 이름 PATCH)

- [x] `PATCH /api/v1/users/my-page` — `{ firstName, lastName }` (JWT 필수)
- [x] `first_name`/`last_name` 갱신 (trim 적용)
- [x] 응답: 갱신된 `user` 요약 DTO (`UserSummaryResponse`)
- [x] `./gradlew test` 통과

### Must Have (wave 3 — 알림 설정 amend, `#21` D8)

- [ ] `UpdateMyPageRequest`에 `notificationEnabled`(Boolean, nullable) 필드 추가
- [ ] `firstName`/`lastName`/`notificationEnabled` **전부 optional로 전환** — 요청에 없는(= null) 필드는 미변경, 있는 필드만 반영(partial update)
- [ ] `firstName`/`lastName`이 **필드로 포함됐지만 값이 blank**면 기존과 동일하게 400
- [ ] 세 필드 **전부 없음**(빈 patch) → 400 (`VALIDATION_ERROR`, 최소 1개 필드 필요)
- [ ] `User.notificationEnabled` 컬럼 반영 (default `true`) — 엔티티는 [`notification.md`](notification.md) 데이터 모델 참고
- [ ] 응답 `UserSummaryResponse`에 `notificationEnabled` 포함
- [ ] 기존 이름 PATCH 테스트(둘 다 필수였던 케이스) → partial update 계약에 맞게 갱신

### Out of Scope (이번 스펙)

- 프로필 이미지 변경 ([`user-profile-image-s3-mirror.md`](user-profile-image-s3-mirror.md) — wave 4)
- 캘린더 연동 API
- 회원 탈퇴 → [`user-account-withdrawal.md`](user-account-withdrawal.md)(Implemented, `#47` 브랜치, Wave 2 Nice)로 위임
- `user_condition` CRUD

## API

### `PATCH /api/v1/users/my-page`

| 항목 | 값 |
|------|-----|
| Auth | Bearer JWT **필수** |
| 용도 | 마이페이지에서 성·이름 수정 |

**Request (partial update — 2026-07-23부터)**

```json
{
  "firstName": "철수",
  "lastName": "김",
  "notificationEnabled": false
}
```

| 필드 | 필수 | 설명 |
|------|------|------|
| firstName | N | 이름 (포함 시 공백 불가). 생략하면 기존 값 유지 |
| lastName | N | 성 (포함 시 공백 불가). 생략하면 기존 값 유지 |
| notificationEnabled | N | 알림 수신 여부(BR-USER-005). 생략하면 기존 값 유지 |

세 필드 모두 생략된 요청(빈 객체)은 400.

**Response `200`** — 갱신된 `user` 요약 ([`user-onboarding.md`](user-onboarding.md) DTO 확장, `notificationEnabled` 포함)

**에러**

| HTTP | code | 상황 |
|------|------|------|
| 400 | `VALIDATION_ERROR` | 포함된 firstName/lastName이 blank, 또는 세 필드 모두 생략 |
| 401 | `AUTH_EXPIRED` 등 | JWT 없음·만료 |
| 403 | `AUTH_FORBIDDEN` | 사용자 없음 |

## 온보딩 profile API와의 관계

| API | 시점 | 필드 |
|-----|------|------|
| `PATCH /users/profile` | 온보딩 **최초** 이름 입력 | firstName, lastName |
| `PATCH /users/my-page` | 마이페이지 **이름 수정** | firstName, lastName |

저장 컬럼·검증·응답 DTO는 동일. 재로그인 시 소셜 `nickname`으로 덮어쓰지 않음 ([`007`](../decisions/007-user-profile-onboarding.md)).

## 검증 시나리오

- [ ] 이름 입력 완료 사용자 → my-page PATCH(firstName+lastName) → first/last 갱신
- [ ] 포함된 firstName 또는 lastName이 blank → 400
- [ ] JWT 없음 → 401
- [ ] `notificationEnabled`만 포함한 PATCH → first/last는 기존 값 유지, `notificationEnabled`만 갱신
- [ ] firstName만 포함한 PATCH → lastName·`notificationEnabled` 기존 값 유지
- [ ] 빈 객체(`{}`) PATCH → 400
- [ ] `notificationEnabled=false` 이후 BR-NOTI-001~005·009 전부 미발송 확인 (`notification.md` 검증 시나리오와 연동)

## 관련 문서

| 문서 | 변경 |
|------|------|
| [`user-onboarding.md`](user-onboarding.md) | profile vs my-page 구분 참조 |
| [`erd.md`](../architecture/erd.md) | `user.first_name`, `user.last_name`, `user.notification_enabled` |
| [`notification.md`](notification.md) | BR-USER-005 게이트가 참조하는 컬럼 소유자 (`#21` D8) |
| GitHub #10 | 범위·완료 기준에 my-page API 추가 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-23 | `#21` D8 — `notificationEnabled` 필드 추가, **전 필드 optional(partial update)로 전환** (기존 firstName/lastName 필수 계약 변경) |
| 2026-07-09 | Approved — 마이페이지 이름 PATCH |
| 2026-07-13 | 경로 `/users/me/*` → `/users/*` |
