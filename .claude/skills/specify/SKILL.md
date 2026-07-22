---
name: specify
description: 요구사항을 정리하고 docs/specs/에 스펙 파일을 생성한다. 기능 추가, 리팩터링 계획, 아키텍처 결정이 필요할 때 사용.
---

# Specify Workflow

"바로 구현" 대신 **문서 → 승인 → 코드** 순서를 강제하는 계획 스킬입니다.

## When to Use

- 새 REST API·엔티티·인증 흐름
- DB 스키마 변경 (엔티티·컬럼·관계)
- 3개 이상 파일에 걸친 리팩터
- BR-* / PRD 해석이 엇갈릴 때

**생략 가능:** 오타, 단일 테스트, 로그 한 줄, 명확한 버그 핫픽스.

## Steps

1. **의도 정리** — 사용자 요청을 한 문단으로 미러링
2. **범위 확인** — `docs/product/mvp.md`에 포함되는지 표시
3. **클라이언트 전제** — API·인증·푸시·링크면 `docs/product/platform.md`와 충돌 없는지
4. **모호함 해소** — 불명확하면 `AskUserQuestion` (API shape, 권한, 엣지 케이스)
5. **스펙 작성** — `docs/specs/{kebab-case}.md` ([템플릿](references/spec-template.md))
6. **충돌 검토** — `docs/architecture.md`, `erd.md`, 기존 specs
7. **승인 대기** — 구현 시작 전 사용자 OK

## Spec Naming

- kebab-case: `trip-room-create.md`, `user-social-login.md`
- 한 파일 = 한 기능 또는 한 리팩터 단위
- 대형 기능은 `trip-schedule-phase1.md`처럼 단계 분리

## Spec Must Include

- Must Have / Nice to Have (체크리스트)
- 영향 받는 BR-* 번호 (없으면 "해당 없음")
- API 표 또는 "API 없음" (앱·React 클라이언트 계약 — `platform.md` 참고)
- 완료 기준 (`./gradlew test`, 수용 조건)
- `[미정]` 항목과 결정 필요자

## Output (사용자에게)

```
📄 docs/specs/{name}.md
• (핵심 결정 1)
• (핵심 결정 2)
• (핵심 결정 3)
승인해 주시면 구현을 시작합니다.
```

## After Approval

- 스펙의 완료 기준을 구현 중 체크
- 설계 변경 시 스펙을 먼저 수정한 뒤 코드 반영
