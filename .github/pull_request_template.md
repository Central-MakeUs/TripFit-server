## Summary

<!-- 무엇을, 왜 변경했는지 1~3줄. Agent가 PR 생성 시 채울 수 있음. -->

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
- [ ] `docs/product/waves.md` wave 범위 확인 (해당 시)
- [ ] DB·인증·다파일 변경 시 `docs/specs/` 링크 또는 스펙 반영
- [ ] API 계약 변경(필드·enum·ErrorCode·경로 추가/삭제/변경) 시 커밋 본문에 `Breaking-Change-Reason:` 트레일러 포함 — 해당 없으면 체크 후 "N/A"

## Risk / Rollback

<!-- 되돌리기 어려운 변경(DB 스키마, 배포 설정 등)이면 롤백 방법을 한 줄로 -->

-
