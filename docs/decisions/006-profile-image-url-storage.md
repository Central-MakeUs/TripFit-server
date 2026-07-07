# 006 — 프로필 이미지 URL 저장 (A안 → B안)

- **상태:** 확정 (wave 1 = A안, wave 4 = B안 예정)
- **날짜:** 2026-07-07
- **관련:**
  - [`docs/specs/auth-social-login.md`](../specs/auth-social-login.md) — wave 1 login·user upsert
  - [`docs/specs/user-profile-image-s3-mirror.md`](../specs/user-profile-image-s3-mirror.md) — wave 4 B안 Draft
  - Issue #1 (wave 1 구현), Issue #9 (wave 4 B안)

## 맥락

소셜 로그인 시 Google·Kakao는 프로필 이미지 **외부 URL**을 제공한다. Apple id_token에는 프로필 이미지가 없다.

TripFit `user.profile_image_url`에 무엇을 저장할지, MVP(wave 1)와 이후(wave 4) 전략을 나눠 확정한다.

## 결정

### wave 1 — **A안: provider URL 그대로 DB 저장** (확정)

- verifier가 추출한 `OAuthProfile.profileImageUrl`(Google `picture`, Kakao `profile_image_url` 등)을 **변환 없이** `user.profile_image_url`에 저장한다.
- API 응답(`UserSummaryResponse.profileImageUrl`)도 동일 URL을 그대로 반환한다.
- 재로그인 시 provider가 새 URL을 주면 **덮어쓴다** (non-blank일 때만).

**이유:** 구현·운영이 가장 단순하고, wave 1 목표(로그인 → JWT → 프로필 노출)에 충분하다.

### wave 4 — **B안: 서버 미러링 → TripFit S3 URL 저장** (예정)

- login/upsert 시 provider URL에서 이미지를 **서버가 다운로드**해 TripFit S3(또는 동등 객체 스토리지)에 업로드한다.
- DB·API에는 **TripFit이 서비스하는 URL**만 저장·반환한다.
- 상세: [`user-profile-image-s3-mirror.md`](../specs/user-profile-image-s3-mirror.md), Issue #9.

**이유:** provider URL 만료·핫링크 차단·외부 의존성·개인정보 URL 노출 리스크를 줄인다.

## 고려한 대안

| 대안 | 장점 | 단점 | 채택 |
|------|------|------|------|
| **A — URL passthrough (wave 1)** | 코드·인프라 없음, 즉시 사용 | URL 깨짐·provider 정책 변경에 취약 | **✓ wave 1** |
| **B — S3 미러링 (wave 4)** | 안정적 CDN URL, 통제 가능 | S3·비동기·실패 처리·비용 | **✓ wave 4 예정** |
| 클라이언트가 S3 presigned upload | 서버 부하 적음 | 앱·웹 계약 복잡, 소셜 URL → 업로드 UX | ✗ |
| DB에 바이너리(BLOB) | 외부 URL 없음 | DB 비대, CDN 불리 | ✗ |

## 트레이드오프 · wave 1 리스크 (A안)

- Google/Kakao CDN URL은 **언제든 무효화**될 수 있다 → 깨진 아바타 가능.
- URL에 **세션·토큰 쿼리**가 포함될 수 있다 → 로그·Referer 유출 주의 (wave 4 B안에서 완화).
- Apple은 wave 1에서도 `profileImageUrl` null이 일반적.

## 코드·스키마 (wave 1)

- 컬럼명 `profile_image_url` **유지** — wave 4 B안에서도 TripFit URL 문자열을 같은 컬럼에 저장 (rename 불필요).
- `AuthService.upsertUser`는 미러링 없이 passthrough만 수행한다.
- B안 도입 시 `ProfileImageStorage` 같은 Strategy 인터페이스로 A→B 교체를 검토한다 (005 verifier Strategy 패턴과 유사).

## 후속 작업

- [x] wave 1: A안 코드·스펙·ERD 반영
- [ ] wave 4: B안 스펙 확정 + S3·IAM·Issue #9 구현
- [ ] B안 전환 시 기존 provider URL 일괄 마이그레이션 배치 (선택)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-07 | 초안 — A안 wave 1 확정, B안 wave 4 예정 |
