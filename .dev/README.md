# .dev — 개발 세션 작업 공간

AI·개발 세션 중 생기는 **임시 메모**를 둡니다. 장기 문서는 `docs/`로 옮기세요.

## 구조

```
.dev/
├── README.md           ← 이 파일
├── .gitignore          ← sessions/ 개인 로그 제외
└── templates/
    └── session-log.md  ← 세션 로그 템플릿
```

## 사용법

1. `templates/session-log.md`를 복사해 `sessions/YYYY-MM-DD-주제.md` 작성
2. 트러블슈팅·결정 사항을 짧게 기록
3. 확정된 내용은 `docs/architecture/`, `docs/specs/`, `deploy/README.md` 등으로 이관 후 세션 파일 삭제

## Git

- `sessions/` 아래 파일은 **기본적으로 커밋하지 않음** (`.gitignore`)
- 팀 공유가 필요한 결정은 반드시 `docs/`에 반영

## 관련 경로

| 경로 | 용도 |
|------|------|
| [`docs/README.md`](../docs/README.md) | 공식 문서 인덱스 |
| [`deploy/README.md`](../deploy/README.md) | 배포·환경 변수 |
| [`.claude/rules/README.md`](../.claude/rules/README.md) | AI 에이전트 규칙 |
