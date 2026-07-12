# UUID Primary Key 전환

> 상태: Implemented  
> MVP: In scope (인프라·스키마)  
> 관련 BR: 해당 없음  
> wave: 1  
> implements: PK/FK UUID 통일, JWT `sub` UUID, Cursor 규칙  
> deferred: UUID v7, BINARY(16) 저장 (Flyway·데이터 보존 마이그레이션은 **도입하지 않음** — harness ⛔ DB)

## 목표

모든 테이블 PK·FK를 `bigint`에서 **UUID v4 (`CHAR(36)` / Java `UUID`)** 로 통일하고, 이후 스키마·엔티티 작성 시 UUID PK를 강제한다.

## 배경

- 현재 ERD·엔티티는 **bigint + `GenerationType.IDENTITY`**
- 클라이언트 미연결 · prod 데이터 폐기 가능 → in-place 마이그레이션 없이 스키마 리셋으로 전환
- 관련 문서: `docs/architecture/erd.md`, `docs/architecture.md`, `.cursor/rules/spring-boot-java.mdc`

## 확정 결정 (2026-07-13)

| 항목 | 결정 |
|------|------|
| 저장 | MySQL `CHAR(36)`, Java `java.util.UUID` |
| 생성 | UUID v4 — Hibernate `@UuidGenerator` (또는 `GenerationType.UUID`) |
| 기존 데이터 | local/dev/prod **폐기·재생성** (`docker compose down -v` 등). SQL 마이그레이션 없음 |
| API | `id`·path param·JWT `sub` → **UUID 문자열**. 프론트 미연결로 breaking OK |
| 규칙 | `.cursor/rules/spring-boot-java.mdc` Entity 섹션 + `erd.md` 설계 원칙 |

## 요구사항

### Must Have

- [x] 엔티티 PK: `User`, `RefreshToken`, `RegularSchedule`, `PersonalSchedule`, `Trip`, `TripMember`, `Recommendation` → `UUID` + `@UuidGenerator`
- [x] FK 컬럼 (`user_id`, `owner_id`, `trip_id` 등) → `UUID`
- [x] Repository / Service / Controller / DTO / `@AuthorizedUser` / JWT (`sub`) 타입 `Long` → `UUID`
- [x] Hibernate가 MySQL에서 UUID를 **BINARY(16)이 아닌 CHAR(36)** 으로 매핑되도록 명시 (`@JdbcTypeCode(SqlTypes.CHAR)` 또는 동등)
- [x] `docs/architecture/erd.md` — 설계 원칙·Mermaid·컬럼 표의 bigint PK/FK → uuid/`char(36)`
- [x] 관련 스펙·API 예시의 숫자 `id` → UUID 문자열 (최소: `auth-social-login`, `user-onboarding`, `schedule-unified`, `api-response.md` 예시)
- [x] `.cursor/rules/spring-boot-java.mdc` — Entity Conventions에 **PK = UUID (CHAR(36), v4)** 규칙 추가
- [x] 테스트·fixture의 `1L` / `Long` ID → `UUID`로 갱신, `./gradlew test` 통과

### Nice to Have

- [x] `docs/prompts/notebooklm/03-erd.md`의 bigint PK 문구 동기화
- [ ] `docs/decisions/`에 짧은 ADR (008) — 선택. 스펙+erd+규칙으로 충분하면 생략

### Out of Scope

- SQL 마이그레이션 / 데이터 보존 마이그레이션 (harness 금지)
- UUID v7 (시간순)
- BINARY(16) 저장
- 테이블 rename (`user` → `users` 등)

## API / 인터페이스

신규 엔드포인트 없음. **기존 계약 breaking change:**

| 위치 | 변경 전 | 변경 후 |
|------|---------|---------|
| JSON `id`, `userId`, `tripId` 등 | number (`1`) | string (`"550e8400-e29b-41d4-a716-446655440000"`) |
| Path `{tripId}` 등 | long | UUID |
| JWT `sub` | `"42"` (숫자 문자열) | UUID 문자열 |
| `@AuthorizedUser Long userId` | `Long` | `UUID` |

API 표: 해당 없음 (계약 타입만 변경)

## 데이터 모델

- ERD SSOT: `docs/architecture/erd.md` 갱신
- PK/FK 타입:

```
id, user_id, owner_id, trip_id 등 → char(36) UUID
생성: 애플리케이션(Hibernate @UuidGenerator) — DB AUTO_INCREMENT 제거
```

- Soft delete / enum 정책 변경 없음
- 스키마 적용: Hibernate `ddl-auto`. **기존 DB는 volume 삭제 후 재생성** (마이그레이션 스크립트 없음)

### 로컬/배포 리셋

```bash
# 로컬
docker compose down -v
docker compose up -d
./gradlew bootRun   # ddl-auto: update → UUID 스키마 생성

# EC2/prod: 데이터 폐기 후 앱 재기동 (ddl-auto: update)
```

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| 해당 없음 | PK 타입만 변경, 도메인 규칙 불변 | — |

## 검증 시나리오

### 정상

- [x] 로그인 후 JWT `sub`가 UUID이고, 보호 API에서 `@AuthorizedUser`로 동일 UUID 주입
- [x] `UserSummaryResponse.id` 등 API 응답 ID가 UUID 문자열
- [x] 신규 Entity persist 시 PK가 UUID v4로 자동 생성
- [x] FK 조인·조회 (`schedule.user_id`, `trip_member` 등) 정상

### 엣지 · 실패

- [x] JWT `sub`가 UUID가 아니면 인증 실패 (기존과 동일하게 invalid token 처리)
- [x] path에 잘못된 UUID 형식 → Spring 400 (타입 변환 실패)

### 수동 / 통합

- [ ] local MySQL에서 테이블 DESCRIBE → PK/FK가 `char(36)` (또는 동등 문자형)
- [ ] `docker compose down -v` 후 기동으로 orphan bigint 스키마 제거

## 완료 기준

- [x] `./gradlew test` 통과
- [x] `./gradlew build` 성공
- [x] ERD·spring-boot-java 규칙·스펙 예시가 UUID와 일치
- [x] OpenAPI `@Schema(example)` 숫자 ID → UUID 예시로 갱신

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| CHAR(36) Hibernate 매핑 어노테이션 | 구현 시 확정 | `@JdbcTypeCode(SqlTypes.CHAR)` 우선 시도 |
| ADR 008 작성 여부 | 생략 가능 | 스펙+erd+규칙으로 충분 |
| 기존 access 토큰 | 폐기 | `sub` 파싱 변경 → 재로그인 필요 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-13 | 초안 — CHAR(36)·v4·데이터 폐기·프론트 OK 확정 |
