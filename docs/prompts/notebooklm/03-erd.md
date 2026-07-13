# NotebookLM 프롬프트 03 — ERD 설계

> **생성 대상:** `docs/architecture/erd.md`  
> **입력:** 노트북 기획 자료 + **01·02단계 NotebookLM 출력** (아래 `[선택]`에 붙여넣기)  
> **Cursor 후속:** [../cursor-import-checklist.md](../cursor-import-checklist.md)

---

아래 블록 전체를 NotebookLM에 복사해 붙여넣으세요.

```
당신은 TripFit 백엔드의 데이터 모델 설계자입니다.
이 노트북의 기획 자료와, 아래에 붙여넣은 PRD·MVP·비즈니스 규칙·플로우(있는 경우)를 근거로 ERD 문서를 작성해 주세요.

[선택: 01·02단계 NotebookLM 출력 전체를 여기에 붙여넣기. repo 파일 아님.]

## 백엔드 확정 사항 (기획서에 없어도 ERD에 포함)

아래는 이미 백엔드에서 확정된 설계입니다. 기획 자료와 모순되면 ERD에 반영하되 `[충돌: 기획]`으로 표시하고 삭제하지 마세요.

- **`refresh_token` 테이블** — wave 1 소셜 로그인·JWT (opaque refresh, user_id FK, token UNIQUE, family_id, expires_at)
- **`user` 컬럼** — `first_name`, `last_name`(유저 PATCH 필수), `is_optional_onboarding_completed`, `is_schedule_registered`, `is_google_calendar_connected`
- **`user.profile_image_url`** — wave 1: provider CDN URL. wave 4: TripFit S3 미러(B안) `[충돌 가능]`
- **Soft delete** — `user`, `trip` 등 `deleted_at`
- **알림 이력 테이블** — MVP Out, BR-NOTI-*는 §5 향후 확장에만

## 공통 규칙

1. 기획 자료에 없는 엔티티·컬럼은 추측하지 말고 `[미정]` 또는 `[제안]`으로 표시하세요.
2. MVP Out of Scope 기능은 ERD 본문에 넣지 말고, §5 "향후 확장"에만 간략히 언급하세요.
3. Java/Spring/JPA 구현 코드는 작성하지 마세요. 논리·물리 모델 문서만 작성하세요.
4. 테이블·컬럼명: **snake_case**, **단수형** 테이블명 (`trip`, `trip_member`).
5. 한국어. 상단: `> NotebookLM 기획 자료 정리본. 비즈니스 규칙은 docs/product/business-rules/ 참고.`
6. 출력: `## docs/architecture/erd.md` 제목 아래 파일 전체.
7. **백엔드 확정 사항**(위 `refresh_token`, user 온보딩 컬럼 등)은 기획과 충돌해도 삭제하지 말고 `[충돌: 기획]` 표시.

---

## 작성할 파일: docs/architecture/erd.md

### 필수 섹션

#### 1. 개요
- 데이터 모델 설계 목적
- 설계 원칙: snake_case, soft delete(`deleted_at`), UUID v4 PK (`char(36)`), BR-* 반영
- 대상 DB: **MySQL 8.0** (예약어 `user` 등 주석)

#### 2. Mermaid ERD
- `erDiagram` — cardinality (`||--o{` 등)
- MVP In Scope 테이블 전부 포함

#### 3. 테이블 정의 (MVP In Scope)

**기획 핵심 6테이블 (필수):**
- `user`
- `user_condition`
- `trip`
- `trip_member`
- `member_schedule`
- `recommendation`

**인증 (백엔드 확정 — Must):**
- `refresh_token` — 위 "백엔드 확정 사항" 참고

각 테이블:
- 한 줄 설명
- **관련 BR** 또는 **관련 결정** (예: `BR-TRIP-001`, `백엔드 확정: refresh_token`)
- 컬럼 표: `| 컬럼 | 타입 | Nullable | PK/FK | 설명 |`
- 인덱스·UNIQUE·제약 (BR 반영, 예: BR-TRIP-008 duration 검증 `[제안]`)

**enum·상태값 (문서에 명시):**
- `member_schedule.time_slot`: MORNING, AFTERNOON, EVENING
- `member_schedule.status`: POSSIBLE, IMPOSSIBLE, TBD
- `trip.status`: ONGOING, CONFIRMED, CANCELED (UI 상태 매핑은 `[미정]`)
- `trip_member.role`: OWNER, MEMBER
- `trip_member.status`: JOINED, RESPONDED
- `user.provider`: KAKAO, GOOGLE, APPLE

#### 4. 관계 요약
| From | To | 관계 | 설명 |

#### 5. MVP 범위와의 매핑
- In Scope: MVP 기능 → 테이블
- Out of Scope (향후): `notification`, `trip_expense`, `reservation` 등 이름만

#### 6. 미정 / 기획 확인 필요
| 항목 | 내용 |

포함 권장:
- 비회원 참여 → `trip_member.user_id` Nullable vs 게스트 (BR-USER-002)
- 알림 이력 테이블 — BR-NOTI-* 별도 설계
- trip soft delete cascade

#### ## 기획 메모 (NotebookLM)
- MVP 핵심 테이블 6개 요약
- 기획 확인 필요 TOP 3

---

## 설계 체크리스트

- BR-*가 컬럼·관계·제약에 반영되었는가?
- glossary 용어와 컬럼명이 일치하는가?
- 플로우 단계에 필요한 상태·FK가 있는가?
- 다대다는 조인 테이블(`trip_member`)로 분리했는가?
- 알림(BR-NOTI-*)은 본 ERD 범위 외로 분리했는가?

---

## 출력 형식

---
## docs/architecture/erd.md
(전체 마크다운 — Mermaid 블록 포함)

---

## 마지막 요약 (짧게)

1. MVP In Scope 테이블 목록 (refresh_token 포함 여부)
2. `[제안]` 컬럼·테이블과 이유
3. `[충돌: 기획]` — 백엔드 확정 사항과 기획 모순 (있으면)
4. BR 중 ERD로 표현되지 않은 항목
5. 기획 확인 필요 TOP 3
```
