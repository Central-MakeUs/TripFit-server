---
name: specify
description: 요구사항을 정리하고 docs/specs/에 스펙 파일을 생성한다. 기능 추가, 리팩터링 계획, 아키텍처 결정이 필요할 때 사용.
---

# Specify Workflow

요구사항을 문서화한 뒤 구현합니다. "해줘" 함정을 피하기 위한 계획 스킬입니다.

## Steps

1. 사용자 의도를 미러링해서 한 문단으로 정리
2. 모호한 점이 있으면 `AskQuestion`으로 확인
3. `docs/specs/[feature-name].md`에 스펙 작성 (템플릿: [references/spec-template.md](references/spec-template.md))
4. `docs/architecture.md`와 충돌 여부 검토
5. 사용자 승인 후에만 구현 시작

## Spec Naming

- kebab-case: `user-auth.md`, `trip-schedule.md`
- 한 스펙 = 한 기능 또는 한 리팩터링 단위

## Output

스펙 작성 후 사용자에게:
- 파일 경로
- 핵심 결정 사항 3줄 요약
- "승인 후 구현 진행" 안내
