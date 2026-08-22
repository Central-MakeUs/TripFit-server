# OpenAPI 응답 스키마 제네릭 해석 (`SuccessResponse<T>`)

> 상태: Approved
> MVP: N/A — 제품 기능이 아닌 개발 인프라/도구 (Wave 분류 대상 아님)
> 관련 BR: 해당 없음

## 목표

컨트롤러가 실제로 반환하는 `SuccessResponse<T>`의 `T`(예: `TripDetailResponse`)가 `/v3/api-docs` 스펙에 필드 단위로 노출되게 해서, `oasdiff` 같은 스펙 비교 도구가 응답 필드 변경(rename·제거·타입 변경)을 실제로 감지할 수 있게 한다.

## 배경

- [`api-contract-diff-ci.md`](api-contract-diff-ci.md)(Approved) 구현·운영 중 발견: `4b1aa57b` 이후 실제로 있었던 8건의 API 변경(TripMemberStatus enum 개명, `memberFillRate` 공식 전환, `joinedMemberCount` 제거 등, 2026-07-28 대화)을 `oasdiff breaking`이 **1건**(`/users/my-page` 경로 삭제)만 잡아냈다.
- 원인을 코드·생성된 스펙으로 직접 확인:
  - 컨트롤러는 `ResponseEntity<SuccessResponse<TripDetailResponse>>`처럼 제네릭에 구체 타입을 박아 리턴한다(`TripController.java` 등).
  - 그런데 `docs/api/openapi.json`에서 모든 200 응답 스키마는 예외 없이 `{"$ref": "#/components/schemas/SuccessResponse"}` 하나뿐이고, `SuccessResponse.data` 속성 자체가 `{"description": "응답 본문"}`만 있고 `type`/`properties`가 없다 — springdoc(`springdoc-openapi-starter-webmvc-ui:3.0.3`)이 `SuccessResponse<T>`의 `T`를 풀어내지 못하고 있다는 뜻.
  - 반대로 **요청 바디**(`CreateTripRequest` 등)는 제네릭이 아니라 파라미터에 구체 타입으로 바로 오므로 정상적으로 필드까지 스펙에 반영되고, `oasdiff`도 요청 쪽 breaking은 실제로 잡는다(같은 조사에서 확인).
- 즉 이 문제는 `api-contract-diff-ci.md`가 감지하는 대상의 근본적인 구멍이다 — CI 로직이 아니라 **스펙 생성 자체**가 원인.

## 요구사항

### Must Have

- [x] **원인 1차 검증**: `GET /trips/{tripId}`에서 `@ApiResponses`를 완전히 제거해 springdoc이 반환 타입만으로 스펙을 생성하게 했더니 `SuccessResponseTripDetailResponse`(정확한 필드 포함)가 자동 생성됨 → springdoc 3.0.3 자체는 `ResponseEntity<SuccessResponse<T>>` 제네릭을 **정확히 해석할 수 있다.** 원인은 버전 한계가 아니라, 21개 엔드포인트의 `@ApiResponse(content = @Content(schema = @Schema(implementation = SuccessResponse.class)))`가 이 자동 해석 결과를 **명시적으로 raw 타입으로 덮어쓰고 있었던 것**
- [x] **해결 방식 선택**: 아래 "해결 방식 후보" 참고 — `useReturnTypeSchema = true`(swagger-core/springdoc 공식 속성) 채택
- [x] **전 엔드포인트 반영**: 21개 성공 응답 전부 `schema = @Schema(implementation = SuccessResponse.class)` 제거 + `useReturnTypeSchema = true` 추가. `docs/api/openapi.json` 기준 2xx 응답에 제네릭 `SuccessResponse` 참조 0건, 각 T의 실제 필드(`TripDetailResponse.activeMemberCount`, `memberFillRate`, `membersPreview` 등) 노출 확인
- [x] **`ErrorResponse`도 동일 패턴인지 확인** — 전체 스펙에서 non-2xx 응답의 schema `$ref`는 전부 `ErrorResponse` 하나로, 제네릭 문제 없음(원래도 구체 타입 참조라 영향 없었음을 재확인)
- [x] **oasdiff 회귀 검증**: `TripDetailResponse.memberFillRate`를 로컬에서 임시 제거 후 `oasdiff breaking` 실행 → `TripDetailResponse`를 참조하는 5개 엔드포인트 전부에서 `response-optional-property-removed`를 정확히 감지. `useReturnTypeSchema` 적용 전(수정 전 상태)에는 이 detection이 전혀 불가능했음
- [x] `docs/api/openapi.json` 스냅샷 재생성 (구현 완료 커밋에 포함)

### 해결 방식 후보 (검증 결과)

| 방식 | 결과 |
|------|------|
| **A. 전역 커스터마이저**(`GlobalOpenApiCustomizer`, 리플렉션으로 `RequestMappingHandlerMapping` 순회 + 컨트롤러 반환 타입에서 `T` 추출 + `ModelConverters` 직접 호출로 스키마 조립) | 프로토타입으로 동작은 확인했으나, springdoc 내부 API에 의존하고 OAS 3.1 nullable 렌더링을 위해 `ModelConverters.getInstance(true)` 같은 우회가 추가로 필요했음 — **채택 안 함** (유지보수 부담 대비 이점 없음) |
| **B. 엔드포인트별 수동 `@Schema(implementation=X.class)`** | `SuccessResponse<T>`는 Java 제네릭이라 `T`별 실제 클래스가 없어 `implementation=`으로 직접 참조 불가 — **적용 불가능한 방식이었음** |
| **B′. `useReturnTypeSchema = true`(채택)** | swagger-core 2.2.x부터 제공되는 공식 `@ApiResponse` 속성. springdoc 3.0.3의 `GenericResponseService`/`MethodAttributes`가 이 값을 읽어, 해당 응답의 스키마를 **컨트롤러 메서드의 실제 반환 타입**으로 해석한다. 기존 `schema=` 줄만 지우고 이 속성을 추가하면 됨 — 커스텀 코드 없음, 손으로 채운 `@ExampleObject`도 그대로 유지됨, nullable도 별도 처리 없이 정확히 렌더링됨. 단건·리스트 래핑 DTO(`TripListResponse`, `TripMembersResponse`)·204 No Content 전부 대표 케이스로 검증 |

**최종 결정**: B′(`useReturnTypeSchema = true`). A(전역 커스터마이저)는 프로토타입까지 구현했으나 공식 속성으로 완전히 대체 가능해 폐기 — 관련 커밋 없음(작업 트리에서만 존재했다가 롤백).

### Nice to Have

- (없음)

### Out of Scope (이번 스펙에서 하지 않음)

- 응답 DTO 필드 자체의 재설계·추가/제거 — 이 스펙은 "스펙에 정확히 반영되게" 하는 것이지 API 계약을 바꾸는 것이 아님
- Swagger UI(사람이 보는 화면)의 표시 개선 — `swagger-openapi-docs.md`(Draft) 별도 스펙 소관
- `SuccessResponse` envelope 구조 자체를 바꾸는 것(예: 제네릭 대신 매 DTO를 개별 클래스로 분리) — envelope는 [`docs/architecture/api-response.md`](../architecture/api-response.md) SSOT, 이번 스펙은 스펙 **생성** 정확도만 다룸

## API / 인터페이스

API 계약 변경 없음 — 기존 API의 OpenAPI **스펙 표현**만 정확해짐.

## 데이터 모델

DB·엔티티 변경 없음. 변경 대상은 각 컨트롤러의 `@ApiResponse` 애너테이션(`schema=` 삭제 + `useReturnTypeSchema = true` 추가)과 `docs/api/openapi.json` 스냅샷뿐. `common/config/` 변경 없음(전역 커스터마이저 폐기).

## 비즈니스 규칙

해당 없음.

## 검증 시나리오

### 정상

- [x] `GET /trips/{tripId}` 응답 스키마에 `TripDetailResponse`의 실제 필드(`activeMemberCount`, `memberFillRate`, `membersPreview` 등)가 노출됨
- [x] `GET /trips`(리스트 래핑 DTO `TripListResponse`), `GET /trips/{tripId}/members`(리스트 래핑 DTO `TripMembersResponse`), `GET /auth/me` 등 대표 엔드포인트 확인

### 엣지 · 실패

- [x] 응답 DTO에 없는 필드가 스펙에 섞이지 않음 — `useReturnTypeSchema`는 실제 반환 타입만 그대로 반영하므로 과도하게 넓은 스키마 이슈 없음
- [x] `@JsonInclude(NON_NULL)` DTO의 nullable 표기 확인 — `TripDetailResponse.destination`(`nullable=true`) 등이 `"type": ["string", "null"]`로 정확히 렌더링(OAS 3.1 스타일, 기존 `SuccessResponse.message`와 동일한 표기)
- [x] 204 No Content(`leaveTrip` 등)는 `useReturnTypeSchema` 대상이 아니므로 기존 `content` 없는 형태 그대로 유지 확인
- [x] Page/Slice 페이징 제네릭 — 이 코드베이스에 사용처 없음(grep 확인), 해당 케이스 자체가 존재하지 않아 검증 대상에서 제외

### 수동 / 통합 (해당 시)

- [x] 로컬에서 `TripDetailResponse.memberFillRate`를 임시 제거 → `oasdiff breaking`이 이를 참조하는 5개 엔드포인트 전부에서 `response-optional-property-removed`를 잡는지 확인 후 되돌림(diff에 남기지 않음)

## 완료 기준

- [x] `./gradlew test` 통과
- [x] `./gradlew build` 성공
- [x] 대표 엔드포인트 3개 이상 스펙에 실제 필드 노출 확인(검증 시나리오)
- [x] oasdiff 회귀 시나리오(응답 필드 제거 감지) 통과
- [x] `docs/api/openapi.json` 재생성 커밋

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| springdoc 3.0.3이 `ResponseEntity<Wrapper<T>>` 제네릭을 못 푸는 정확한 원인 | 해결 | springdoc 자체는 정상 해석 가능. 원인은 각 컨트롤러의 `@Schema(implementation = SuccessResponse.class)` 명시적 override였음 |
| 해결 방식 확정 | 해결 | `useReturnTypeSchema = true`(B′) 채택 — "해결 방식 후보" 표 참고 |
| GitHub 이슈 번호 | 이슈 미생성(사용자 요청, 2026-07-28) — 구현도 이슈·브랜치 없이 `main`에서 직접 진행(사용자 명시 승인, 2026-07-28) | — |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-28 | 초안 — `4b1aa57b` 이후 API 변경 Discord 수동 요약 작업 중 발견한 `SuccessResponse<T>` 제네릭 미해석 문제를 스펙화(이슈 미생성) |
| 2026-07-28 | Approved — 원인 검증(springdoc 정상 동작, 컨트롤러 측 override가 원인) 및 해결 방식을 `useReturnTypeSchema = true`로 확정. 프로토타입했던 전역 커스터마이저(A)는 공식 속성으로 완전 대체되어 폐기. 21개 엔드포인트 전부 적용 완료 |
