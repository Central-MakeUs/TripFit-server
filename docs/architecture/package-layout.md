# Package Layout Reference

TripFit 백엔드 서버의 도메인 기반 계층형(Domain-Driven Layered) 패키지 구조와 공통 컴포넌트 목록을 정의한 참조(SSOT) 문서다. 새 기능이나 클래스를 추가할 때 배치할 위치와 레이어 규칙을 확인할 때 참조한다. 시스템 전체 구조와 아키텍처 설계 원칙은 [`docs/architecture.md`](../architecture.md)를 참고한다.

## 언제 이 문서를 보는가

- 새로운 도메인 기능이나 서브패키지를 추가하기 전 적절한 패키지 위치를 확인할 때
- 공통 유틸리티·설정·예외 클래스의 소유 패키지를 조회할 때
- 도메인 간 참조 방식(제공 도메인 concrete 서비스 직접 주입 등)을 점검할 때

## 패키지 구조 트리

`com.tripfit.tripfit` 루트 아래 도메인 단위로 패키지를 분리하며, 도메인 내부는 `controller → dto → service → domain → repository` 레이어를 구성한다.

```
com.tripfit.tripfit
├── TripfitApplication.java
├── common/
│   ├── api/                        # SuccessResponse, ErrorResponse, FieldError
│   ├── config/                     # JpaConfig, SchedulingConfig
│   ├── domain/                     # BaseTimeEntity, SoftDeleteEntity
│   ├── exception/                  # ErrorCode, CommonErrorCode, TripFitException, GlobalExceptionHandler
│   ├── logging/                    # PiiMasker, SocialIntegrationLog, SocialLogContext, SocialIntegrationAction
│   └── security/                   # SocialTokenCrypto, SocialTokenCryptoProperties
├── auth/
│   ├── controller/                 # AuthController
│   ├── dto/                        # LoginRequest, LoginResponse, ...
│   ├── service/                    # AuthService, RefreshTokenService, AuthLoginPersistenceService 등
│   ├── domain/                     # RefreshToken
│   ├── repository/                 # RefreshTokenRepository
│   ├── jwt/                        # JwtService, Filter, AuthorizedUser, JwtProperties
│   ├── oauth/                      # SocialTokenVerifier*, OAuthProperties
│   ├── security/                   # SecurityConfig, AppConfig
│   └── exception/                  # AuthErrorCode
├── user/
│   ├── controller|dto|service|domain|repository|exception   # 프로필·온보딩
│   ├── googlecalendar/             # feature: Google Calendar 연동
│   │   ├── client|controller|domain|dto|exception|repository|scheduler|service
│   └── schedule/                   # feature: 정기·개별 일정
│       ├── controller|dto|service|domain|repository
│       └── exception/              # ScheduleErrorCode
├── trip/
│   ├── controller|dto|domain|exception|config|scheduler
│   ├── service/                    # TripService(facade), TripCommandService, TripQueryService, TripServiceSupport,
│   │                                #   TripDisplayNameHelper, TripHomeMaintenanceService (공용 — feature 무관)
│   ├── repository/                 # TripRepository
│   ├── event/                      # TripInfoChangedEvent 등 — trip이 발행하는 이벤트(발행 주체가 소유)
│   ├── membership/                 # feature: 참여·멤버 관리
│   │   └── controller|dto|service|domain|repository(+projection)
│   ├── recommendation/             # feature: 추천 + 피드백
│   │   ├── controller|dto|domain|service|repository
│   │   └── algorithm/              # RecommendationEngine, RecommendationCandidate, MemberAttendanceDetail (순수 계산)
│   └── schedule/                   # feature: 여행방 내 스케줄 합산/스냅샷
│       └── dto|domain|service|repository
└── notification/
    ├── controller|dto|domain|exception|config
    ├── service|repository|event    # NotificationEventListener 등
    └── scheduler/                  # ScheduleReminderBatch 등
```

## 도메인별 패키지 구성 및 역할

| 도메인 | 패키지 경로 | 주요 포함 서브패키지 | 설명 |
|---|---|---|---|
| **common** | `com.tripfit.tripfit.common` | `api`, `config`, `domain`, `exception`, `logging`, `security` | 전 도메인 공유 envelope, 설정, 베이스 엔티티, 예외 처리, 로깅 마스킹, 암복호화 |
| **auth** | `com.tripfit.tripfit.auth` | `controller`, `dto`, `service`, `domain`, `repository`, `jwt`, `oauth`, `security`, `exception` | 소셜 로그인 검증(Kakao/Google/Apple), JWT 발급/검증, Spring Security 설정 |
| **user** | `com.tripfit.tripfit.user` | 기본 레이어 + `googlecalendar/`, `schedule/` | 사용자 프로필, 온보딩, 구글 캘린더 연동, 정기/개별 일정 관리 |
| **trip** | `com.tripfit.tripfit.trip` | 기본 레이어 + `membership/`, `recommendation/`, `schedule/`, `event/` | 여행방 생성/참여, 일정 조율, 추천 알고리즘, 스냅샷 동결, 만료 스케줄러 |
| **notification** | `com.tripfit.tripfit.notification` | `controller`, `dto`, `domain`, `service`, `repository`, `event`, `scheduler`, `exception`, `config` | FCM 디바이스 토큰 관리, 비동기 트랜잭션 알림 이벤트 리스너, 리마인드 배치 |

## 패키지 배치 원칙

1. **기본 규칙**: 새 기능 추가 시 `com.tripfit.tripfit.{domain}/` 레이어 규칙(`controller`, `dto`, `service`, `domain`, `repository`, `exception`)을 따른다.
2. **Feature 서브패키지**: 도메인 안의 특정 기능 단위가 커지면 `{domain}/{feature}/` 아래에 동일한 레이어 세트를 둘 수 있다 (`user/schedule`, `user/googlecalendar`, `trip/membership`, `trip/recommendation`, `trip/schedule`).
3. **공유 코드 위치**: 같은 도메인 내 여러 기능이 공유하는 코드(예: `TripServiceSupport`, `TripDisplayNameHelper`)는 도메인 루트의 `service/`에 둔다.
4. **크로스 도메인 직접 주입**: 별도의 포트/어댑터 인터페이스 레이어(`port/out`)를 두지 않고, 제공 도메인의 concrete 서비스(`ScheduleAvailabilityService`, `UserDirectoryService` 등)를 직접 주입받아 사용한다.

## 관련 문서

- [`docs/architecture.md`](../architecture.md) — 전체 서버 아키텍처 원칙 및 계층별 책임 설명
- [`docs/decisions/003-architecture-guide.md`](../decisions/003-architecture-guide.md) — 아키텍처 가이드라인 ADR
- [`docs/architecture/erd.md`](erd.md) — DB 스키마 및 테이블 설계
- [`.claude/rules/spring-boot-java.md`](../../.claude/rules/spring-boot-java.md) — 구현 및 코딩 체크리스트
