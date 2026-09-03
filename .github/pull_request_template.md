<!--
제목: `{Type}: {한글 설명}` — 핵심 키워드를 넣어 30자 이내 평서문으로.
본문 작성 기준: .claude/rules/doc-writing.md — 결과를 먼저, 배경은 나중에.
-->

## Summary

<!-- 이 PR이 merge되면 무엇이 달라지는지 결과부터 1~3줄. 구현 방법·경위가 아니라
     리뷰어가 "그래서 뭐가 바뀌나"를 바로 알 수 있게 씁니다. 용어는 docs/product/glossary.md 기준. -->

-

## Related

<!-- Closes #이슈번호 — merge 시 이슈 자동 close -->

- Closes #
- Spec: <!-- docs/specs/xxx.md (해당 시) -->

## Test plan

- [ ] `./gradlew test`
- [ ] `./gradlew build` (해당 시)
- [ ] 배포 변경 시: `./scripts/verify-deploy.sh` 또는 `verify-deploy-app.sh`

## Checklist

- [ ] 요청 범위만 수정 (drive-by 리팩터링 없음)
- [ ] `docs/product/release-milestones.md` Milestone(`MVP 출시`/`출시 이후`) 확인 (해당 시)
- [ ] DB·인증·다파일 변경 시 `docs/specs/` 링크 또는 스펙 반영
- [ ] API 계약 변경(필드·enum·ErrorCode·경로 추가/삭제/변경) 시 커밋 본문에 `Breaking-Change-Reason:` 트레일러 포함 — 해당 없으면 체크 후 "N/A"

## Risk / Rollback

<!-- 되돌리기 어려운 변경(DB 스키마, 배포 설정 등)이면 롤백 방법을 한 줄로 -->

-
