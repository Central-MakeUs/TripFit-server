# 마이페이지 이름 수정

> wave: 1  
> implements: — (마이페이지 UI에서 이름 변경)  
> 결정: [`docs/decisions/007-user-profile-onboarding.md`](../decisions/007-user-profile-onboarding.md) — `first_name`/`last_name` SSOT  
> 선행: [`user-onboarding.md`](user-onboarding.md) — 온보딩 필수 이름 입력  
> 상태: Approved  
> 승인: 2026-07-09

## 목표

온보딩 이후 **마이페이지**에서 사용자가 성·이름을 수정할 수 있게 한다.

## 배경

- 온보딩 최초 입력: `PATCH /api/v1/users/profile` ([`user-onboarding.md`](user-onboarding.md))
- 마이페이지 재수정: 별도 엔드포인트로 UI 의도를 분리 (동일 컬럼·검증 재사용)
- Figma: [`figma-wireframe-v1.md`](../product/design/figma-wireframe-v1.md) — 마이페이지(설정·탈퇴·캘린더 연동)

## 요구사항

### Must Have (wave 1 — 본 스펙)

- [ ] `PATCH /api/v1/users/my-page` — `{ firstName, lastName }` (JWT 필수)
- [ ] `first_name`/`last_name` 갱신 (trim 적용)
- [ ] 응답: 갱신된 `user` 요약 DTO (`UserSummaryResponse`)
- [ ] `./gradlew test` 통과

### Out of Scope (이번 스펙)

- 프로필 이미지 변경 ([`user-profile-image-s3-mirror.md`](user-profile-image-s3-mirror.md) — wave 4)
- 알림 허용·탈퇴·캘린더 연동 API
- `user_condition` CRUD

## API

### `PATCH /api/v1/users/my-page`

| 항목 | 값 |
|------|-----|
| Auth | Bearer JWT **필수** |
| 용도 | 마이페이지에서 성·이름 수정 |

**Request**

```json
{
  "firstName": "철수",
  "lastName": "김"
}
```

| 필드 | 필수 | 설명 |
|------|------|------|
| firstName | Y | 이름 (공백 불가) |
| lastName | Y | 성 (공백 불가) |

**Response `200`** — 갱신된 `user` 요약 ([`user-onboarding.md`](user-onboarding.md) DTO와 동일)

**에러**

| HTTP | code | 상황 |
|------|------|------|
| 400 | `VALIDATION_ERROR` | blank 이름·성 |
| 401 | `AUTH_EXPIRED` 등 | JWT 없음·만료 |
| 403 | `AUTH_FORBIDDEN` | 사용자 없음 |

## 온보딩 profile API와의 관계

| API | 시점 | 필드 |
|-----|------|------|
| `PATCH /users/profile` | 온보딩 **최초** 이름 입력 | firstName, lastName |
| `PATCH /users/my-page` | 마이페이지 **이름 수정** | firstName, lastName |

저장 컬럼·검증·응답 DTO는 동일. 재로그인 시 소셜 `nickname`으로 덮어쓰지 않음 ([`007`](../decisions/007-user-profile-onboarding.md)).

## 검증 시나리오

- [ ] 이름 입력 완료 사용자 → my-page PATCH → first/last 갱신
- [ ] blank firstName 또는 lastName → 400
- [ ] JWT 없음 → 401

## 관련 문서

| 문서 | 변경 |
|------|------|
| [`user-onboarding.md`](user-onboarding.md) | profile vs my-page 구분 참조 |
| [`erd.md`](../architecture/erd.md) | `user.first_name`, `user.last_name` |
| GitHub #10 | 범위·완료 기준에 my-page API 추가 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-09 | Approved — 마이페이지 이름 PATCH |
| 2026-07-13 | 경로 `/users/me/*` → `/users/*` |
