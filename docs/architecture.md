# TripFit Server Architecture

## Overview

Spring Boot 4.x 기반 단일 모듈 Gradle 프로젝트.  
도메인 엔티티·설정 스캐폴딩이 있으며, API·서비스 레이어는 기능 추가 시 아래 구조를 따릅니다.

## Package Layout

```
com.tripfit.tripfit
├── TripfitApplication.java    # 진입점
├── config/                    # Spring 설정 (JPA, OpenAPI 등)
├── domain/                    # @Entity, enums/
├── repository/                # JPA Repository (추가 예정)
├── service/                   # 비즈니스 로직 (추가 예정)
├── controller/                # REST API (추가 예정)
└── dto/                       # 요청·응답 DTO (추가 예정)
```

## Layer Rules

- **Controller**: HTTP 입출력·DTO 변환만. 비즈니스 로직·트랜잭션 금지.
- **Service** (Application): 유스케이스 조율, `@Transactional` 경계, Repository 호출. BR 검증은 Domain에 위임.
- **Domain**: 엔티티·enum·(필요 시) Domain Service — **불변식·상태 전이·BR-*** 구현.
- **Repository**: 애그리거트 루트 영속화만. Controller/Service에서 직접 SQL 금지.
- **DTO**: API 계약 (`dto/`). 엔티티를 그대로 노출하지 않음.

## DDD (전술, MVP 단일 모듈)

> 결정 근거: [`decisions/003-ddd-tactical.md`](../decisions/003-ddd-tactical.md)  
> MVP는 **바운디드 컨텍스트 1개**(TripFit) — 패키지·모듈 분리는 하지 않음.

### 애그리거트 (초안)

| 루트 | 포함 엔티티 | 비고 |
|------|-------------|------|
| `User` | `User`, `UserCondition` | 1:1 조건은 User 경유로만 변경 |
| `Trip` | `Trip`, `TripMember`, `MemberSchedule`, `Recommendation` | 여행방·참여·일정·추천은 Trip 루트 일관성 |

- 다른 애그리거트 참조는 **ID만** (`userId`, `tripId`) — 루트 객체 직접 참조·cascade 남용 금지.
- 애그리거트 간 규칙은 Application Service에서 조율 (한 트랜잭션에 여러 루트 변경 시 스펙에 명시).

### 책임 분리

| 계층 | 둘 것 | 두지 말 것 |
|------|-------|------------|
| **Domain** | 상태 전이 (`TripStatus`), BR 위반 시 예외, 엔티티 메서드 | HTTP, DTO, `@Transactional` |
| **Application (service/)** | 유스케이스 흐름, 트랜잭션, DTO↔Domain 변환 | if/else로 BR 전부 구현 (Anemic 방지) |
| **Infrastructure (repository/, config/)** | JPA, 외부 연동 설정 | 비즈니스 규칙 |

### 하지 않는 것 (MVP)

- 이벤트 소싱, CQRS, 별도 `application`/`infrastructure` Gradle 모듈
- 컨텍스트맵·ACL — 팀·도메인 확장 시 `decisions/`에 추가 검토

구현 체크리스트: `.cursor/rules/spring-boot-java.mdc`

## API Response

JSON envelope 초안 (프론트 합의 전): [`architecture/api-response.md`](architecture/api-response.md).  
확정 전에는 스펙·구현이 **제안안 기준** — 프론트와 맞춘 뒤 SSOT로 승격.

## Configuration

- `src/main/resources/application.yml` — 공통 (DataSource driver, Hikari)
- 프로필별: `application-{local|dev|test|prod}.yml`
- 민감 정보: 환경 변수 — `.env` (git 제외), EC2에서는 `deploy/*/.env`
- **Flyway 미사용** — 스키마는 JPA 엔티티 + Hibernate `ddl-auto`로 관리

| 프로필 | 용도 | ddl-auto |
|--------|------|----------|
| local | IDE / 로컬 MySQL | update |
| dev | Docker·EC2 | update |
| test | `./gradlew test` (H2) | create-drop |
| prod | 운영 | validate |

배포·검증 절차: [`deploy/README.md`](../deploy/README.md) (SSOT). 에이전트 배포 규칙: `.cursor/rules/deployment.mdc`.

## Testing

- `src/test/java/` — main과 동일 패키지 구조
- `./gradlew test` — CI·로컬 검증

## Design Reference

- Figma Wireframe v1: [figma-wireframe-v1.md](../product/design/figma-wireframe-v1.md)
- ERD 설계 시 와이어프레임 리소스 초안(`trip`, `trip_member` 등) 참고

## Data Model

- ERD: [erd.md](architecture/erd.md) — MVP 6테이블 (`user`, `user_condition`, `trip`, `trip_member`, `member_schedule`, `recommendation`)
- **런타임 DB: MySQL 8.0** — snake_case 단수형 테이블명, Soft Delete (`deleted_at`)
- 엔티티: `SoftDeleteEntity`, `BaseTimeEntity`, enum은 `domain/enums/`

## Deployment

운영 절차·환경변수·검증 스크립트는 [`deploy/README.md`](../deploy/README.md)가 SSOT입니다.  
VPC·SG·1→2 EC2 마이그레이션 심화: [`ec2-split-deployment.md`](architecture/ec2-split-deployment.md).

CI/CD: `.github/workflows/ci-cd.yml` — PR은 test, `main` push는 test → GHCR push → EC2 A deploy.

## Specs

기능 설계: `docs/specs/{feature-name}.md` — `.cursor/skills/specify` 템플릿 참고.
