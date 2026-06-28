# TripFit Server Architecture

## Overview

Spring Boot 기반 단일 모듈 Gradle 프로젝트. 현재는 기본 스캐폴딩 단계이며, 기능 추가 시 아래 레이어 구조를 따릅니다.

## Package Layout (목표)

```
com.tripfit.tripfit
├── TripfitApplication.java    # 진입점
├── config/                    # Spring 설정, Security, CORS 등
├── domain/                    # 엔티티, 도메인 서비스
├── repository/                # JPA / 데이터 접근
├── service/                   # 비즈니스 로직
├── controller/                # REST API
└── dto/                       # 요청·응답 DTO
```

## Layer Rules

- **Controller**: HTTP 입출력만. 비즈니스 로직 금지.
- **Service**: 트랜잭션 경계, 도메인 규칙.
- **Repository**: DB 접근만.
- **DTO**: API 계약. 엔티티를 그대로 노출하지 않음.

## Configuration

- `src/main/resources/application.properties` — 기본 설정
- 환경별 설정은 `application-{profile}.properties`로 분리 예정
- 민감 정보는 환경 변수 또는 외부 시크릿으로 주입

## Testing

- 단위 테스트: `src/test/java/` 동일 패키지 구조
- `./gradlew test`로 CI·로컬 검증
- 새 API·서비스는 해당 레이어 테스트 추가 권장

## Design Reference

- Figma Wireframe v1: [figma-wireframe-v1.md](../product/design/figma-wireframe-v1.md)
- ERD 설계 시 와이어프레임의 리소스 초안(trip_room, availability 등) 참고

## Data Model

- ERD·테이블 정의: [erd.md](architecture/erd.md) — MVP 6테이블 (`user`, `user_condition`, `trip`, `trip_member`, `member_schedule`, `recommendation`)
- DB: PostgreSQL, snake_case 단수형 테이블명, Soft Delete (`deleted_at`)

## Specs

기능 설계 문서는 `docs/specs/{feature-name}.md`에 작성합니다. `specify` 스킬 템플릿 참고.
