# 프로필 이미지 S3 미러링 (B안)

> wave: 4  
> 선행: wave 1 [`auth-social-login.md`](auth-social-login.md) (A안 URL passthrough)  
> 결정: [`docs/decisions/006-profile-image-url-storage.md`](../decisions/006-profile-image-url-storage.md) — **B안 예정**  
> 상태: Draft

## 목표

wave 1 A안(provider URL 그대로 DB 저장)을 **B안**으로 확장한다.

1. 소셜 login/upsert 시 provider 프로필 이미지 URL에서 **서버가 다운로드**
2. TripFit **S3**(또는 동등 객체 스토리지)에 업로드
3. `user.profile_image_url` 및 API 응답에는 **TripFit CDN/공개 URL**만 저장·반환

## 배경

- wave 1: [`006`](../decisions/006-profile-image-url-storage.md) A안 — `AuthService`가 `OAuthProfile.profileImageUrl`을 그대로 저장
- 문제: provider URL 만료, hotlink 제한, 외부 의존, URL에 민감 쿼리 포함 가능
- Apple: id_token에 이미지 없음 → B안 적용 대상은 주로 Google·Kakao

### 관련 문서

| 문서 | 내용 |
|------|------|
| `auth-social-login.md` | login·upsert·`UserSummaryResponse` |
| `006-profile-image-url-storage.md` | A/B 결정 |
| `005-auth-social-verifier-strategy.md` | verifier → `OAuthProfile` 경계 |

## 범위

| 포함 (B안) | 제외 |
|------------|------|
| login upsert 훅에서 이미지 미러링 | 사용자 직접 아바타 업로드 API |
| S3 bucket·IAM·공개 URL 정책 | 이미지 리사이즈·얼굴 인식 |
| provider URL → S3 실패 시 fallback 정책 | wave 1 A안 코드 제거 전 마이그레이션 배치 (별도 작업) |
| 기존 `profile_image_url` 컬럼 재사용 | 컬럼 rename |

## 설계 초안

```
OAuthProfile.profileImageUrl (provider URL)
        ↓
ProfileImageMirrorService.mirror(userId, sourceUrl)   ← 신규
        ↓
S3 putObject → TripFit public URL
        ↓
user.profile_image_url = TripFit URL
```

### 실패·엣지

| 케이스 | 정책 `[제안]` |
|--------|----------------|
| download 4xx/timeout | 기존 DB URL 유지 또는 null (로그인은 성공) |
| sourceUrl null (Apple) | skip |
| 동일 URL 재로그인 | S3 중복 업로드 skip (ETag/URL hash) |
| provider URL → TripFit URL 전환 후 재로그인 | source URL 변경 시에만 re-mirror |

### 인프라 `[미정]`

- bucket 이름·리전·CloudFront 여부
- 객체 키: `profiles/{userId}/{uuid}.jpg` 등
- env: `AWS_REGION`, `S3_PROFILE_BUCKET`, (선택) `CDN_BASE_URL`

## API 영향

- **login / GET /auth/me** 응답 shape **변경 없음** — `profileImageUrl` 값만 TripFit 도메인 URL로 바뀜
- 프론트: URL 호스트만 변경; 필드명 동일

## 완료 기준 (wave 4)

- [ ] S3 업로드 + `ProfileImageMirrorService` 단위·통합 테스트
- [ ] `AuthService.upsertUser` (또는 전용 listener)에서 B안 호출
- [ ] `deploy/` env·IAM 문서
- [ ] (선택) 기존 A안 URL 일괄 마이그레이션 스크립트

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-07 | Draft — B안 wave 4 스펙 초안 |
