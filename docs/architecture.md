# TripFit Server Architecture

## Overview

Spring Boot 4.x 기반 단일 모듈 Gradle 프로젝트.  
**도메인 기반 레이어드 아키텍처**로 `auth`, `user`, `trip`, `common` 단위로 코드를 묶고, 각 도메인 내부는 **Controller / DTO / Service / Domain / Repository** (+ 필요 시 Client) 레이어를 사용합니다. 풀 DDD는 적용하지 않으며, JPA 연관관계를 자유롭게 활용합니다.

> 아키텍처 결정: [`decisions/003-architecture-guide.md`](decisions/003-architecture-guide.md)

## Package Layout (Domain-Driven Layered)

도메인 단위로 코드를 묶고, 도메인 내부에서 controller → dto → service → domain → repository 레이어를 둡니다.  
공통 설정·예외·베이스 엔티티는 `common/`에 둡니다.

```
com.tripfit.tripfit
├── TripfitApplication.java
├── common/
│   ├── api/                        # ApiResponse, ErrorResponse, FieldError
│   ├── config/                     # JPA, Web, OpenAPI
│   ├── domain/                     # BaseTimeEntity, SoftDeleteEntity
│   └── exception/                  # ErrorCode, CommonErrorCode, TripFitException, GlobalExceptionHandler
├── auth/
│   ├── controller/                 # AuthController
│   ├── dto/                        # LoginRequest, LoginResponse, ...
│   ├── service/                    # AuthService, RefreshTokenService
│   ├── domain/                     # RefreshToken
│   ├── repository/                 # RefreshTokenRepository
│   ├── jwt/                        # JwtService, Filter, AuthorizedUser, JwtProperties
│   ├── oauth/                      # SocialTokenVerifier*, OAuthProperties
│   ├── security/                   # SecurityConfig, AppConfig
│   └── exception/                  # AuthErrorCode
├── user/
│   ├── controller|dto|service|domain|repository|exception   # 프로필·온보딩
│   └── schedule/                   # feature: 정기·개인 일정
│       ├── controller|dto|service|domain|repository
│       └── exception/              # ScheduleErrorCode
└── trip/
    ├── controller|dto|domain|exception|config
    ├── service/                    # TripService(facade), TripCommandService, TripQueryService, …
    └── repository/
        ├── TripRepository, TripMemberRepository, …
        └── projection/             # TripMemberCountProjection 등
```

새 기능 추가 시 `com.tripfit.tripfit.{domain}/` 레이어 규칙을 따른다. 도메인 안 기능이 커지면 `{domain}/{feature}/`에 동일 레이어를 둘 수 있다 (`user/schedule`). 상세: [`decisions/003-architecture-guide.md`](decisions/003-architecture-guide.md).

## Layer Rules (도메인 내부)

- **controller**: HTTP 입출력·DTO 변환만. 비즈니스 로직·트랜잭션 금지.
- **dto**: API 요청·응답 타입. `controller/dto/` 중첩 금지.
- **service**: 유스케이스 조율, `@Transactional`, Repository·client 호출.
- **domain**: JPA `@Entity`, enum. `@ManyToOne` 등 연관관계 사용 가능. 필드 설명은 `@Schema(description)` (springdoc) — `.claude/rules/spring-boot-java.md` OpenAPI 섹션.
- **repository**: `JpaRepository`만. Entity는 `domain/`에 둔다.
- **client**: 외부 OAuth·HTTP adapter (auth 등).
- **common**: 도메인 간 공유 설정·예외·베이스 엔티티.

### JPA

- ERD([`architecture/erd.md`](architecture/erd.md)) 기준으로 연관관계·객체 그래프 탐색 허용.
- 기본 `FetchType.LAZY`. cascade는 필요할 때만.
- API 응답에는 Entity를 직접 노출하지 않고 DTO로 변환.

구현 체크리스트: `.claude/rules/spring-boot-java.md`

## API Response

JSON envelope: [`architecture/api-response.md`](architecture/api-response.md) (확정).  
확정 전에는 스펙·구현이 **제안안 기준** — 프론트와 맞춘 뒤 SSOT로 승격.

## Configuration

- `src/main/resources/application.yml` — 공통 (DataSource driver, Hikari)
- 프로필별: `application-{local|dev|test|prod}.yml`
- 민감 정보: 환경 변수 — `.env` (git 제외), EC2에서는 `deploy/*/.env`
- **Flyway / SQL 마이그레이션 미사용·작성 금지** — 스키마는 JPA 엔티티(최신 하나) + Hibernate `ddl-auto`. **상용 보존 데이터 없음** → DB 리셋 허용. (`.claude/rules/harness-workflow.md`)

| 프로필 | 용도 | ddl-auto |
|--------|------|----------|
| local | IDE / 로컬 MySQL | update |
| dev | Docker·EC2 | update |
| test | `./gradlew test` (H2) | create-drop |
| prod | 운영 | update |

배포·검증 절차: [`deploy/README.md`](../deploy/README.md) (SSOT). 에이전트 배포 규칙: `.claude/rules/deployment.md`.

## Testing

- `src/test/java/` — main과 동일 패키지 구조
- `./gradlew test` — CI·로컬 검증

## Design Reference

- Figma Wireframe v1: [figma-wireframe-v1.md](../product/design/figma-wireframe-v1.md)
- ERD 설계 시 와이어프레임 리소스 초안(`trip`, `trip_member` 등) 참고

## Data Model

- ERD: [erd.md](architecture/erd.md) — MVP 핵심: `users`, `regular_schedule`, `personal_schedule`, `trip`, `trip_member`, `recommendation`, `refresh_token`
- **런타임 DB: MySQL 8.0** — snake_case 단수형 테이블명, Soft Delete (`deleted_at`), **PK/FK = UUID v4 (`CHAR(36)`)**
- 엔티티: `common/domain/` (베이스), 슬라이스 `domain/` (비즈니스), `repository/` (기술 영속화)

## Deployment

운영 절차·환경변수·검증 스크립트는 [`deploy/README.md`](../deploy/README.md)가 SSOT입니다.  
VPC·SG·1→2 EC2 마이그레이션 심화: [`ec2-split-deployment.md`](architecture/ec2-split-deployment.md).

CI/CD: `.github/workflows/ci-cd.yml` — PR은 test, `main` push는 test → GHCR push → EC2 A deploy.

## Specs

기능 설계: `docs/specs/{feature-name}.md` — `.claude/skills/specify` 템플릿 참고.
