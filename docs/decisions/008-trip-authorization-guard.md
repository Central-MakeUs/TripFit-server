# 008 — 여행방 권한 검증 (@TripMemberOnly / @TripOwnerOnly + Interceptor)

- **상태:** 확정
- **날짜:** 2026-07-16
- **관련:** [`docs/specs/trip-room-api.md`](../specs/trip-room-api.md), [`docs/specs/schedule-participation-onboarding.md`](../specs/schedule-participation-onboarding.md), Issue **#24** (구현), #22 (설계 범위), #12 (여행방 API)

## 맥락

현재 인증은 `JwtAuthenticationFilter`(JWT 검증 → `SecurityContext`에 `userId` 주입)만 있고,
**"이 trip의 방장인가 / 참여자인가"** 도메인 권한은 `TripService` 안에서 매 메서드마다
`requireActiveMember(tripId, userId)` · `requireOwner(trip, userId)`로 확인한다.

- 기능상 검증은 **모두 동작**한다 (403 `TRIP_ACCESS_DENIED` / `TRIP_FORBIDDEN`).
- 그러나 Service마다 검증을 반복 호출해 **중복**이 있고, "이 API는 멤버/방장 전용"이 컨트롤러·OpenAPI·테스트에서 **선언적으로 드러나지 않는다**.
- NestJS의 `@UseGuards(TripMemberGuard)`에 해당하는 **선언적 권한 레이어**가 없다.

Spring에는 NestJS `Guard`(`CanActivate`)에 1:1 대응하는 개념이 없다. 매핑:

| NestJS | Spring |
|--------|--------|
| `JwtAuthGuard` | `JwtAuthenticationFilter` (구현됨) |
| `@CurrentUser()` | `@AuthorizedUser` + `AuthorizedUserArgumentResolver` (구현됨) |
| 커스텀 `RolesGuard` | **본 설계 — 커스텀 어노테이션 + `HandlerInterceptor`** |

## 결정

`{tripId}` path variable을 쓰는 API에 붙이는 **메서드 어노테이션 2종**과, 이를 읽어 권한을 강제하는
**단일 `HandlerInterceptor`**를 둔다. `TripService`의 `requireActiveMember`/`requireOwner`는
**얇은 도메인 헬퍼로 유지**하고(직접 서비스 호출 시 방어), 컨트롤러 진입 시점 검증은 인터셉터가 담당한다.

```java
// com.tripfit.tripfit.trip.config
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TripMemberOnly {}   // 참여자(soft-delete 제외) 아니면 403 TRIP_ACCESS_DENIED

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TripOwnerOnly {}    // 방장 아니면 403 TRIP_FORBIDDEN
```

```java
// TripAuthorizationInterceptor implements HandlerInterceptor
// preHandle:
//   1) HandlerMethod 아니면 통과
//   2) @TripOwnerOnly / @TripMemberOnly 없으면 통과
//   3) SecurityContext에서 userId (없으면 401 — Filter가 이미 처리, 방어적으로 재확인)
//   4) @PathVariable "tripId" 추출 (HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)
//   5) trip 존재·soft-delete 확인 → 없으면 404 TRIP_NOT_FOUND
//   6) OwnerOnly → trip.owner == userId 아니면 403 TRIP_FORBIDDEN
//      MemberOnly → tripMemberRepository.existsBy...AndDeletedAtIsNull 아니면 403 TRIP_ACCESS_DENIED
//   7) 공통 canEnterRoom (D-JOIN-ENTRY) → 아니면 403 SCHEDULE_ENTRY_REQUIRED
```

- 등록: `WebMvcConfigurer.addInterceptors`에 `addPathPatterns("/api/v1/trips/**")`.
- 실행 순서: **Filter(JWT) → Interceptor(권한) → Controller**. 인증 실패는 이미 401로 걸러진 뒤 인터셉터가 돈다.
- 예외는 기존 `TripFitException(TripErrorCode.*)`를 던져 `GlobalExceptionHandler`가 동일 envelope로 응답 → **응답 형식·에러 코드 변화 없음**.
- `join`(tripId 없음)·`POST /trips`·`GET /trips`(목록)에는 붙이지 않는다.

## 고려한 대안

| 대안 | 장점 | 단점 |
|------|------|------|
| **A. 현행 Service 검증 유지** | 추가 코드 없음, 단순 | 컨트롤러/OpenAPI에 권한 비노출, 호출 중복 |
| **B. 커스텀 어노테이션 + HandlerInterceptor (택함)** | NestJS Guard와 가장 유사, 선언적, path variable 접근 쉬움, 기존 예외/응답 재사용 | 인터셉터에서 DB 1회 조회(대개 Service가 어차피 재조회) |
| C. Spring Security `@PreAuthorize("@tripSecurity.isMember(#tripId, principal)")` | 표준, SpEL로 세밀 | SpEL 가독성↓, method security 설정 추가, 팀 학습비용 |
| D. AOP `@Around` 포인트컷 | 유연 | 인터셉터보다 무겁고 path variable 파싱 수작업 |

## 트레이드오프 · 후속 리스크

- 인터셉터 조회와 Service 조회가 **중복 DB 히트** 가능 → 필요 시 요청 스코프 캐시/`@RequestScope`로 trip 재사용 (초기엔 미도입, 단순 우선).
- 권한 검증이 **두 곳(인터셉터·Service)** 에 존재 → 인터셉터를 "1차 게이트", Service 헬퍼를 "직접 호출 방어"로 **역할 명문화** 필요.
- soft-delete·`effectiveStatus`(TERMINATED lazy 판정)는 **인터셉터 범위 밖** — 상태 기반 409(TRIP_NOT_ONGOING 등)는 계속 Service가 담당.
- `tripId` 외 리소스 소유권(예: `{userId}` 내보내기 #20)은 별도 어노테이션·정책 필요.

## 후속 작업

- [x] `TripMemberOnly` / `TripOwnerOnly` 어노테이션 추가
- [x] `TripAuthorizationInterceptor` + `WebMvcConfigurer` 등록 (`/api/v1/trips/**`)
- [x] `TripController`·`TripMemberController`에 어노테이션 부착 (get/patch/delete/pin/members/submit)
- [x] Service `requireActiveMember`/`requireOwner`는 헬퍼로 유지 (중복 제거는 후속)
- [x] 인터셉터 단위 테스트 (멤버/비멤버/방장/비방장/soft-delete/없는 tripId)
- [x] `./gradlew test` · OpenAPI에는 영향 없음(어노테이션은 런타임 권한만)
- [x] #22 canEnterRoom — `@TripMemberOnly`/`@TripOwnerOnly` 공통 인터셉터 게이트 (2026-07-21)
