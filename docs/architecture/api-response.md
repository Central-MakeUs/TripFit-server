# API 응답 규약

> **상태: 확정**  
> 백엔드 REST API의 JSON envelope SSOT. Controller·`GlobalExceptionHandler`·`docs/specs/`는 이 문서를 따른다.

백엔드 구현·Agent 작업 시 참고: `.cursor/rules/spring-boot-java.mdc`, `client-platform.mdc`  
프론트 협의 맥락: `docs/product/platform.md`

## 확정 항목

| 항목 | 규약 |
|------|------|
| Body 필드 | `data`, `message`, `code` (Body에 **`status` 없음**) |
| 성공 시 `message` | **선택** — 단순 조회·로그인 등은 `{ "data": ... }` 만으로도 OK |
| `success` 필드 | **사용 안 함** — `response.ok`(HTTP status)로 판단 |
| 검증 오류 | 400 + `INVALID_INPUT` + `errors[]` (`field`, `message`) |
| 목록·페이지네이션 | `data.items` + `data.pageInfo` — offset vs cursor **`[미정]`** |

## 기본 원칙

1. **성공·실패는 HTTP Status Code로 판단** — Body에 `status`·`success` **넣지 않음** (헤더와 중복·모순 방지)
2. **HTTP Status는 REST 의미대로** (200, 201, 400, 401, 403, 404, 409, 500)
3. **Body envelope 통일** — `data` / `message` / `code` 조합 (아래 표)
4. **`code` 유지** — 동일 HTTP status라도 프론트 분기용 (`TRIP_NOT_FOUND` vs `USER_NOT_FOUND` 등)
5. **경로 prefix**: `/api/v1/...`
6. **필드명**: JSON `camelCase`

### Body 필드 요약

| 필드 | 성공 | 실패 | 설명 |
|------|------|------|------|
| `data` | ✅ (대부분) | ❌ 보통 생략 | 비즈니스 payload |
| `message` | 선택 | ✅ 권장 | 사용자 표시·토스트용 (한국어 기본) |
| `code` | 선택 | ✅ 권장 | `SCREAMING_SNAKE_CASE` enum 문자열 |
| `errors` | — | 400 검증 시 | `{ field, message }[]` |

## Envelope 구조

### 성공 — 단건·조회 (가장 단순)

```json
{
  "data": {
    "tripId": "550e8400-e29b-41d4-a716-446655440000",
    "title": "제주 3박 4일"
  }
}
```

조회 등 **프론트가 메시지를 쓰지 않는 API**는 `message`·`code` 없이 `data`만 반환해도 됨.

### 성공 — 메시지·코드 포함 (선택)

```json
{
  "code": "COMMON_SUCCESS",
  "message": "조회가 완료되었습니다.",
  "data": {
    "tripId": "550e8400-e29b-41d4-a716-446655440000",
    "title": "제주 3박 4일"
  }
}
```

생성·수정 등 **사용자 피드백이 필요할 때** `message`·`code`를 붙인다.

### 실패 (일반)

HTTP `404 Not Found` + Body:

```json
{
  "code": "TRIP_NOT_FOUND",
  "message": "여행방을 찾을 수 없습니다."
}
```

### 검증 오류 (400)

HTTP `400 Bad Request` + Body:

```json
{
  "code": "INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다.",
  "errors": [
    { "field": "title", "message": "제목은 필수입니다." }
  ]
}
```

| 필드 | 설명 |
|------|------|
| `errors` | **400 + `INVALID_INPUT`일 때만** |
| `errors[].field` | 요청 JSON 필드명 (`camelCase`) |
| `errors[].message` | 해당 필드 사용자 메시지 |

### 목록·페이지네이션 — `[미정]`

커서 vs offset은 프론트·스펙에서 확정. **제안** 구조:

```json
{
  "code": "COMMON_SUCCESS",
  "data": {
    "items": [
      { "tripId": "550e8400-e29b-41d4-a716-446655440000", "title": "제주 3박 4일" }
    ],
    "pageInfo": {
      "nextCursor": "abc123",
      "hasNext": true
    }
  }
}
```

`totalCount`, `sort`, `filter`, `summary` 등은 확장 시 **`data` 안**에 추가 (envelope 최상위에 `pageInfo` 두지 않음).

합의 전에는 목록 API pagination **구현·가정 금지** (`docs/specs/`에 명시 후 진행).

## HTTP Status 사용 기준

| 상황 | HTTP Status | `code` 예시 |
|------|-------------|-------------|
| 조회 성공 | 200 OK | (생략 가능) 또는 `COMMON_SUCCESS` |
| 생성 성공 | 201 Created | `COMMON_SUCCESS` |
| 잘못된 요청·검증 실패 | 400 Bad Request | `INVALID_INPUT` |
| 인증 실패 | 401 Unauthorized | `UNAUTHORIZED`, `LOGIN_REQUIRED`, `TOKEN_EXPIRED` |
| 권한 없음 | 403 Forbidden | `FORBIDDEN` |
| 리소스 없음 | 404 Not Found | `*_NOT_FOUND` |
| 충돌 (중복·상태 불가) | 409 Conflict | `*_CONFLICT` |
| 서버 오류 | 500 Internal Server Error | `INTERNAL_ERROR` |

성공·실패 판단은 **`response.ok`** / HTTP status. Body의 `code`는 **세부 분기**용.

```ts
// 프론트 분기 예 (합의용)
switch (body.code) {
  case 'LOGIN_REQUIRED':
    // 로그인 이동
    break;
  case 'TOKEN_EXPIRED':
    // refresh
    break;
  case 'TRIP_NOT_FOUND':
    // toast
    break;
}
```

## `code` 네이밍

- `SCREAMING_SNAKE_CASE`
- 공통: `COMMON_SUCCESS`, `INVALID_INPUT`, `UNAUTHORIZED`, `FORBIDDEN`, `INTERNAL_ERROR`
- 도메인: `{리소스}_{상황}` — 예: `TRIP_NOT_FOUND`, `TRIP_ALREADY_CONFIRMED`

## 백엔드 구현 가이드

아래 구조를 따른다:

```
com.tripfit.tripfit
├── common/
│   ├── api/
│   │   ├── ApiResponse.java       # data, message, code (status 필드 없음)
│   │   ├── FieldError.java        # field, message
│   │   ├── PageResponse.java      # items + pageInfo (목록용)
│   │   ├── PageInfo.java
│   │   └── ErrorResponse.java     # code, message, errors? (실패 전용)
│   └── exception/
│       ├── ErrorCode.java           # interface
│       ├── CommonErrorCode.java
│       ├── TripFitException.java
│       └── GlobalExceptionHandler.java
└── {domain}/
    ├── exception/                   # AuthErrorCode, TripErrorCode, ...
    └── dto/
```

- HTTP status는 **ResponseEntity / @ResponseStatus** 로만 설정 — Body에 duplicate 하지 않음
- Controller는 합의된 envelope만 반환 — Entity 직접 반환 금지
- `@Valid` 실패 → HTTP 400 + `INVALID_INPUT` + `errors`
- 500은 `message`에 스택 노출 금지 — 일반 메시지 + 서버 로그

## 스펙·OpenAPI

- `docs/specs/{feature}.md`에 성공·실패 JSON 예시 — **이 초안 기준, 합의 후 갱신**
- springdoc은 첫 API 공개 시 envelope 반영

## 클라이언트 참고안 (합의용 — 프론트 미구현)

```ts
// 제안 예시 — 프론트 스택 확정 후 팀이 재작성
const response = await fetch(url, options);
const body = await response.json();

if (response.ok) {
  return body.data;
}
if (response.status === 400 && body.errors) {
  // errors[].field → errors[].message 폼 매핑
}
// body.code 분기 또는 body.message 표시
throw new Error(body.message ?? '요청 처리 중 오류가 발생했습니다.');
```

프론트 repo 반영 코드·라이브러리는 **프론트 팀이 정한 뒤** 이 문서에 링크를 추가한다.
