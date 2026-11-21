---
name: local-track-issues
description: TripFit의 GitHub 이슈에서 범위와 완료 기준을 확인하거나, 후속 작업을 이슈·브랜치·PR에 연결할 때 사용한다.
---

# TripFit GitHub 이슈

대상은 `Central-MakeUs/TripFit-server`이다. 저장소 루트에서 `gh auth status`로 인증을 확인하고, 여러 원격이 있으므로 `gh` 명령에 `--repo Central-MakeUs/TripFit-server`를 지정한다.

- 이슈 번호로 `gh issue view 번호 --comments`를 실행해 본문과 댓글을 확인한다. 완료 조건과 우선순위 판단은 [릴리즈 기준](../../../docs/product/release-milestones.md) §2를 따른다.
- 새 이슈·브랜치·PR은 사용자가 명시적으로 요청했을 때 만든다. 이슈는 `gh issue create`, 수정은 `gh issue edit`, 댓글은 `gh issue comment`를 사용한다. 이슈 종류에 맞는 [양식](../../../.github/ISSUE_TEMPLATE/)과 [Git 규칙](../../../.github/CONTRIBUTING.md)을 따른다.
- 후속 이슈로 분리할 때 `superchaeyeons:write-spec`으로 Draft 스펙을 만든다. 관련 Approved 스펙과 스펙 인덱스를 연결하고, 확인된 Milestone·라벨과 이슈 번호를 기록한다. 신규 이슈를 포함해 priority 라벨을 부여하거나 바꾸기 전에 반드시 사용자에게 확인한다.
- 브랜치 이름은 `feat/12-trip-room-create`처럼 이슈 번호를 넣는다. PR 본문은 [TripFit PR 템플릿](../../../.github/pull_request_template.md)에 따라 `Closes #12`처럼 연결한다.
