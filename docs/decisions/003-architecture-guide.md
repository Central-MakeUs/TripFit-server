# 003 — 도메인 기반 레이어드 아키텍처 가이드

- **상태:** 확정
- **날짜:** 2026-07-06 (2026-07-07 구조 통일)
- **관련:** `docs/architecture.md`, `.cursor/rules/spring-boot-java.mdc`

## 맥락

TripFit 백엔드는 MVP 단계의 Spring Boot 단일 Gradle 모듈이다. **풀 DDD(애그리거트·BC 등)는 적용하지 않지만**, 코드는 **도메인 단위로 묶고 내부는 계층형 레이어**로 일관되게 유지한다. JPA 연관관계(`@ManyToOne`, 지연 로딩 등)를 자유롭게 활용한다.

## 결정

1. **단일 모듈 모노리스** — MVP는 Gradle 모듈 분리 없이 하나의 API 서버.
2. **최상위 = 도메인** — `auth`, `user`, `trip`, `common`.
3. **도메인 내부 레이어** — `controller` → `dto` → `service` → `domain` → `repository` (+ 필요 시 `client`, `config`, `exception`).
4. **공통 코드** — `common/` (설정, 예외, 공유 API envelope, 베이스 엔티티).
5. **외부 연동** — OAuth·HTTP 검증 등은 `{domain}/client/`에 격리. service가 orchestration만 담당.
6. **API 계약** — Controller는 DTO만 노출. Entity를 HTTP 응답으로 직접 반환하지 않음.

## 도메인 구조

```
com.tripfit.tripfit.{domain}/
├── controller/       # @RestController
├── dto/              # 요청·응답 (controller/dto/ 중첩 금지)
├── service/          # @Transactional 유스케이스
├── domain/           # JPA @Entity, enum
├── repository/       # JpaRepository
├── client/           # 외부 API adapter (auth 등, 선택)
├── exception/        # 도메인 ErrorCode (auth 등, 선택)
└── config/           # 도메인 전용 설정 (auth 등, 선택)
```

### 패키지 배치 (실용 규칙)

| 종류 | 위치 | 예 |
|------|------|-----|
| REST | `{domain}/controller/` | `AuthController` |
| API DTO | `{domain}/dto/` | `LoginRequest`, `LoginResponse` |
| 유스케이스 | `{domain}/service/` | `AuthService`, `JwtService` |
| Entity·enum | `{domain}/domain/` | `User`, `Trip`, `RefreshToken` |
| DB 접근 | `{domain}/repository/` | `UserRepository`, `RefreshTokenRepository` |
| 외부 OAuth·HTTP | `{domain}/client/` | `GoogleTokenVerifier`, `SocialTokenVerifierRegistry` |
| 공통 베이스 | `common/domain/` | `BaseTimeEntity`, `SoftDeleteEntity` |

## 레이어 책임 (최소 규칙)

| 레이어 | 할 일 | 하지 말 것 |
|--------|-------|------------|
| **controller** | HTTP, DTO 변환, `@Valid` | 비즈니스 로직, `@Transactional` |
| **dto** | API 입출력 타입 | JPA Entity, 외부 API 호출 |
| **service** | 유스케이스 조율, 트랜잭션, repository·client 호출 | HTTP·Servlet API 직접 사용 |
| **domain** | JPA Entity, enum | DTO, 외부 API 호출 |
| **repository** | JpaRepository | Controller, Entity 정의 |
| **client** | 외부 provider 토큰 검증·HTTP | 유스케이스 orchestration |

BR-* 규칙은 service 또는 domain 중 읽기 쉬운 곳에 둔다.

## 하지 않는 것 (MVP)

- 풀 DDD(애그리거트, BC, ID-only 참조 강제 등)
- 이벤트 소싱, CQRS, 별도 Gradle 모듈 분리
- `controller/dto/` 중첩, `service/social/` 등 레이어 혼재

## 후속 작업

- [x] 도메인 기반 레이어드 구조 적용
- [x] `RefreshToken` → `auth/domain/`
- [x] 소셜 verifier → `auth/client/`
- [ ] API envelope 프론트 합의 후 `common/api` 확정
