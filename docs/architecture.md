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

- **Controller**: HTTP 입출력만. 비즈니스 로직 금지.
- **Service**: 트랜잭션 경계, 도메인 규칙.
- **Repository**: DB 접근만.
- **DTO**: API 계약. 엔티티를 그대로 노출하지 않음.

## Configuration

- `src/main/resources/application.yml` — 공통 (DataSource driver, Hikari, Flyway 위치)
- 프로필별: `application-{local|dev|test|prod}.yml`
- 민감 정보: 환경 변수 — `.env` (git 제외), EC2에서는 `deploy/*/.env`

| 프로필 | 용도 | ddl-auto | Flyway |
|--------|------|----------|--------|
| local | IDE / 로컬 MySQL | update | off |
| dev | Docker·EC2 | update | off |
| test | `./gradlew test` (H2) | create-drop | off |
| prod | 운영 (ERD 확정 후) | validate | on |

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

| 환경 | Compose / 스크립트 |
|------|-------------------|
| 로컬·단일 호스트 | 루트 `docker-compose.yml` + `.env` |
| EC2 App (A) | `deploy/app/` — GHCR 이미지 pull |
| EC2 MySQL (B) | `deploy/mysql/` |
| 가이드 | [ec2-split-deployment.md](architecture/ec2-split-deployment.md), [deploy/README.md](../deploy/README.md) |

CI/CD: `.github/workflows/ci-cd.yml` — PR은 test, `main` push는 test → GHCR push → EC2 A deploy.

## Specs

기능 설계: `docs/specs/{feature-name}.md` — `.cursor/skills/specify` 템플릿 참고.
