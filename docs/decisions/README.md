# 기술 결정 메모 (`docs/decisions/`)

되돌리기 어렵거나 팀 합의가 필요한 **아키텍처·인프라 선택**만 짧게 남깁니다.
기능 단위 설계는 `docs/specs/`, 제품 요구는 `docs/product/`를 씁니다.

## 언제 쓰나

| 쓴다 | 안 쓴다 |
|------|---------|
| DB·스키마 전략 전환 (예: ddl-auto 정책 변경, RDS 전환) | 일반 API·CRUD 기능 |
| 인증·보안 방식 확정 | 버그 수정, 필드 추가 |
| 배포 구조 변경 (단일 EC2 → 분리, RDS 전환 검토) | S/M/T 중 **T** 작은 수정 |
| 스키마·테이블 rename 등 기술 부채 결정 | 스펙(`docs/specs/`)으로 충분한 작업 |

**Milestone·인프라 결정** 작업 시 스펙 작성 전·후에 결정 메모를 검토합니다.

## 파일 이름

`NNN-짧은-제목.md` — 예: `003-rds-mysql-cutover.md`

번호는 순서대로. 이미 긴 가이드가 있으면 중복 작성하지 않고 링크만 — 예: [ec2-split-deployment.md](../../deploy/ec2-split-deployment.md)

## 템플릿 (1페이지 이내)

H1 바로 아래 **결정 한 줄**을 먼저 둔다(2026-09-03 추가) — 결론을 보려고 `## 결정`까지 스크롤하지 않아도 되게 하기 위함이다. 기존 ADR은 소급 적용하지 않고, 새로 쓰는 것부터 이 형식을 따른다. 근거: [`.claude/rules/doc-writing.md`](../../.claude/rules/doc-writing.md) "가치를 먼저, 배경은 나중에".

```markdown
# [제목]

**결정 한 줄:** [무엇을 어떻게 하기로 했는지. 이 줄만 읽어도 결론을 알 수 있어야 한다.]

- **상태:** 제안 | 확정 | 폐기
- **날짜:** YYYY-MM-DD
- **관련:** docs/specs/..., Issue #n

## 맥락
왜 지금 결정이 필요한가.

## 결정
선택한 방법 (한두 문단).

## 고려한 대안
| 대안 | 장점 | 단점 |
|------|------|------|
| | | |

## 트레이드오프 · 후속 리스크
-

## 후속 작업
- [ ]
```

## 현재 결정 목록

| 파일 | 주제 |
|------|------|
| [`001-auth-mobile-token-verification.md`](001-auth-mobile-token-verification.md) | 모바일 소셜 토큰 서버 검증 |
| [`002-domain-split-vercel-api.md`](002-domain-split-vercel-api.md) | `tripfit.online` / `api.tripfit.online` 분리 |
| [`003-architecture-guide.md`](003-architecture-guide.md) | 도메인 레이어드 패키지 |
| [`004-auth-token-rotation.md`](004-auth-token-rotation.md) | Refresh token rotation — **2026-09-15 일부 amend**(refresh는 Redis 저장, access 블랙리스트 폐기) |
| [`005-auth-social-verifier-strategy.md`](005-auth-social-verifier-strategy.md) | OAuth verifier 전략 |
| [`006-profile-image-url-storage.md`](006-profile-image-url-storage.md) | 프로필 이미지 URL 저장 |
| [`007-user-profile-onboarding.md`](007-user-profile-onboarding.md) | 온보딩·이름 |
| [`008-trip-authorization-guard.md`](008-trip-authorization-guard.md) | `@TripMemberOnly` / `@TripOwnerOnly` |
| [`009-observability-logging.md`](009-observability-logging.md) | 로깅·모니터링 인프라 (Loki + Grafana) |
| [`010-redis-infra.md`](010-redis-infra.md) | Redis 인프라 (EC2 D) — 배치 결정 유효, **용도는 refresh token·공휴일 캐시로 변경**(2026-09-15 amend) |
| [`011-holiday-data-source.md`](011-holiday-data-source.md) | 공휴일 데이터 소스 (공공데이터포털 특일정보 API) |

기능 스펙 인덱스: [`../specs/README.md`](../specs/README.md)

## Agent · 이슈와의 관계

- Issue/PR 본문에 결정 전체를 붙이지 말고 **이 파일 링크**만 건다.
- 구현 후 결정과 코드가 어긋나면 결정 메모 또는 스펙을 먼저 수정한다.
