# 카카오톡 초대 링크 공유

> wave: 3  
> implements: BR-NOTI-008 (수동 공유·독촉) · 공유 카피 BR-NOTI-006/007은 `#21`과 문구 공유 가능  
> deferred: FCM/APNs → [#21](https://github.com/Central-MakeUs/TripFit-server/issues/21), join 전 미리보기 → 이슈 미배정 (`trip-room-api` D7)  
> 상태: Draft  
> MVP: In scope (Wave 3)  
> Issue: [#19](https://github.com/Central-MakeUs/TripFit-server/issues/19)

## 목표

방장이 여행방 초대 링크를 카카오톡으로 공유해, 친구가 링크로 들어와 로그인·join까지 이어지게 한다.

## 배경

- `#12` D3: `invite_code` 6자 · URL `https://tripfit.online/room/{inviteCode}` · `POST /trips/join` — **서버 기반 완료**
- 카카오 **공유 SDK·템플릿**은 주로 **프론트** (`docs/product/platform.md`)
- 기존 `#21`에 알림과 묶여 있던 카카오 공유를 **#19로 분리** (2026-07-22)
- 관련: `docs/product/business-rules/notification.md`, `docs/product/flows/trip-confirm.md`

## 요구사항

### Must Have

- [ ] 공유 URL·`inviteCode`·방 요약 필드 계약 확정 (클라 Kakao SDK 템플릿과 합의)
- [ ] 서버: 기존 Trip 상세/생성 응답의 `inviteCode`·이름·희망 기간 등으로 공유에 충분한지 확인
- [ ] 부족하면 **최소** 공유용 메타 API 추가 (스펙 Approved 후)
- [ ] D8: 인원 가득·`TERMINATED` 등 — 클라 공유 UI 비노출 · 서버 join 409 유지
- [ ] OpenAPI(해당 시) · `./gradlew test`

### Nice to Have

- [ ] OS 기본 공유 시트 fallback (카카오 미설치)

### Out of Scope

- FCM/APNs · 알림 이력 → `#21`
- join 전 방 미리보기 API → D7 · 이슈 미배정
- Universal Link / App Link 앱 패키징 → `platform.md` · `[미정]`
- 정원 hold → `#35`

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| (기존) | `GET /api/v1/trips/{tripId}` 등 | JWT | `inviteCode`·방 요약 — 공유 입력으로 재사용 가능 |
| (선택) | `[미정]` 공유 메타 전용 | JWT · `@TripOwnerOnly`? | 클라 SDK에 넘길 title/description/image URL만 |

**기본 가정:** 신규 API 없이 기존 상세 응답으로 충분하면 **API 없음** (클라 전용).

## 데이터 모델

- 신규 테이블 **없음** (기본)
- `trip.invite_code` — `docs/architecture/erd.md` · `trip-room-api` D3

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-NOTI-008 | 미응답·미가입 독촉 — 방장 카카오 수동 공유 | 클라 공유 UX · 문구 |
| D3 | 공유 URL에 `inviteCode` | 클라 · 서버 발급은 `#12` |
| D8 | 인원·기간 cap 시 초대 불가 | 클라 비노출 + 서버 409 |

## `[미정]`

- 공유 주체: **방장만** vs 멤버 전원
- 서버 전용 공유 메타 API 필요 여부
- 카카오 템플릿 ID·OG 이미지 SSOT

## 완료 기준

- [ ] 스펙 **Approved**
- [ ] `#19` Must Have 체크
- [ ] `#21` · `#31` · `trip-room-api` deferred · `docs/specs/README.md` 동기화
- [ ] `./gradlew test` (서버 변경 시)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-22 | Draft — `#19` 레거시 재사용 · `#21`에서 카카오 공유 분리 |
