# AI 코딩 에이전트 — NotebookLM 출력 repo 반영 체크리스트

NotebookLM에서 **01~04 출력**을 받은 뒤, AI 코딩 에이전트(또는 PR)에서만 수행합니다.  
NotebookLM에는 repo 내용을 넣지 않습니다.

## 1. 파일 저장

NotebookLM 출력의 `## docs/...` 블록을 아래 경로에 덮어씁니다.

```
docs/product/prd.md
docs/product/mvp.md
docs/product/glossary.md
docs/product/business-rules/trip.md
docs/product/business-rules/user.md
docs/product/business-rules/notification.md
docs/product/flows/README.md
docs/product/flows/trip-create.md
docs/product/flows/trip-join.md
docs/product/flows/schedule-edit.md
docs/product/flows/trip-confirm.md
docs/architecture/erd.md
docs/product/design/figma-wireframe-v1.md   ← 04 실행 시만
```

## 2. 건드리지 않을 파일 (충돌 시 사용자 확인)

| SSOT | 확인 포인트 |
|------|-------------|
| `docs/product/waves.md` | mvp wave 매핑 표와 wave 1~4 정의 일치 |
| `docs/product/platform.md` | API base, WebView, 소셜 로그인 역할 분담 |
| `docs/decisions/001` | 앱 SDK 토큰 → REST 검증 |
| `docs/decisions/004`, `docs/specs/auth-token-rotation.md` | refresh_token·RTR |
| `docs/decisions/006` | profile_image_url A안/B안 |
| `docs/decisions/007`, `docs/specs/user-onboarding.md` | first_name, onboarding boolean |
| `docs/specs/auth-social-login.md` | TTL, `POST /api/v1/auth/login` |
| JPA 엔티티 (`src/main/java/...`) | ERD 컬럼·enum 정합 |

## 3. 정합 검사 (AI 코딩 에이전트 또는 수동)

- [ ] prd In/Out ↔ mvp 체크리스트
- [ ] mvp wave 표 ↔ `waves.md` MVP 매핑
- [ ] 각 flow 단계 ↔ BR-ID 존재
- [ ] erd 테이블·enum ↔ BR-* (TRIP, USER, NOTI)
- [ ] glossary 용어 ↔ erd 컬럼명
- [ ] NotebookLM ERD vs 이미 구현된 `user`, `refresh_token`, `trip` 엔티티

## 4. 충돌 처리 (harness 최우선)

기획 문서(NotebookLM) vs 엔지니어링 SSOT가 다르면:

1. **임의로 한쪽 선택 금지**
2. 충돌 목록을 사용자에게 짧게 보고
3. 기획 amend / `decisions` amend / `specs` amend 중 선택 후 반영

## 5. `[미정]` · P0

| 우선순위 | 의미 | 예 |
|----------|------|-----|
| P0 | wave 다음 구현 블로cker | BR-USER-002 웹 참여 로그인 여부 |
| P1 | wave 3·4 전 확인 | 알림 발송 주기 |
| P2 | 문서만 | 카피·부가 설명 |

P0는 구현 전 기획자 확인.

## 6. 구현 연결

- ERD·BR 변경이 DB/API에 영향 → `specify` 스킬로 `docs/specs/` 작성 → 승인 → 구현
- `./gradlew test`

## Agent에게 시킬 때 (한 줄)

> NotebookLM 출력(붙여넣기)을 docs에 반영하고, waves/platform/decisions/specs/엔티티와 충돌·갭 목록만 정리해줘. 충돌은 임의 해결하지 마.
