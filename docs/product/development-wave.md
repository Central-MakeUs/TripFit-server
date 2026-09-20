# TripFit 릴리즈 마일스톤 — 운영 가이드

> **이 문서가 릴리즈 계획의 SSOT입니다.**
> 2026-08-26: Wave 1~4 도메인 축을 폐지했습니다 — 아래 "0. 무엇이 바뀌었나" 참고.
> MVP 범위는 [`mvp.md`](mvp.md)가 SSOT.

---

## 0. 무엇이 바뀌었나

과거엔 "Wave"라는 도메인 축(1 소셜 로그인 · 2 MVP 로직 · 3 외부 API 연동 · 4 리팩토링·성능·런칭후UX)으로 이슈를 4단계로 나눴다. 실제 운영해보니:

- 전체 이슈의 **46%가 Wave 2**에 몰려, 분류축으로서 실질적 필터링 기능을 못 함
- Wave 4가 "기술부채·인프라 정리"와 "런칭 후 신규 기능"이라는 성격이 전혀 다른 두 묶음을 한 통에 담고 있었음
- 로그인(Wave 1) vs 로그인이 매개하는 외부 연동(Wave 3) 경계가 반복적으로 헷갈려 Google Calendar 기능이 여러 번 재분류된 사고가 있었음
- 2026-08-26에 새로 정의한 `priority: must`/`priority: could`(기능 구현·버그 수정=must, 성능 개선·구조 정리·최적화=could)가 사실상 Wave 4의 역할(기술부채 격리)을 이미 흡수해, 두 축이 같은 질문을 중복해서 묻고 있었음

그래서 Wave 축을 폐지하고, 아래처럼 목적이 겹치지 않는 축으로 재구성했다:

| 질문 | 담당 축 | 비고 |
|---|---|---|
| 이게 MVP 범위인가? | [`mvp.md`](mvp.md) In/Out | 기존 그대로 |
| 출시 전에 할지, 후에 할지? | **Milestone** `MVP 출시` / `출시 이후` | Wave 1~4를 대체 |
| 기능/버그(필수)인가, 성능·구조 개선(선택)인가? | `priority: must` / `priority: could` 라벨 | 기존 그대로, 이제 Wave와 무관하게 이슈 내용만으로 판단 |
| 스토어 심사에 필수인가? | `release: blocking` 라벨 (§4) | 기존 그대로, Milestone과 무관 |

### 변경 이력 (축 자체가 바뀐 이력)

| 시점 | 축 | 비고 |
|------|-----|------|
| 초기 | 기술 스택 도입 단계 (Redis, RTR, Docker…) | "API 몇 개 만들었는지"로 흐르는 문제 — 사용자 가치가 안 보임 |
| 2026-07 | User Journey ("로그인→여행방→추천→확정→베타→운영") | Wave가 결과 기록이 되는 문제 — 도메인 이슈가 저니 단계에 흩어짐 |
| 2026-08-03 | 도메인/기술 축 (로그인/MVP 로직/외부연동/리팩토링·성능·런칭후UX) | 판단은 단순해졌으나 Wave 2 쏠림(46%)·Wave 4 이질적 혼합·로그인·외부연동 경계 혼동이 계속됨 |
| **2026-08-26 (현재)** | **Wave 폐지 → Milestone(MVP 출시/출시 이후) + priority + `mvp.md` + `release:blocking`** | 축을 4단계 도메인 분류에서 위 표의 3개 독립 질문으로 재구성. "어느 도메인이냐"는 더 이상 릴리즈 계획의 축이 아님(필요하면 이슈 제목·검색으로 충분한 소규모 팀 규모) |

---

## 1. Milestone — `MVP 출시` / `출시 이후`

**판단:** [`mvp.md`](mvp.md)에서 In Scope로 정의된 기능인가?

- Yes → **`MVP 출시`** 마일스톤
- No (런칭 후 추가 기능, 기술부채 정리, 리팩토링 등) → **`출시 이후`** 마일스톤

**DoD:** `MVP 출시` 마일스톤의 `priority: must` 이슈 전부 Closed + [`mvp.md`](mvp.md) 완료 기준(방장이 추천 TOP 3로 최종 날짜 확정) 충족 + `./gradlew test`

**tooling·문서 메타 이슈는 Milestone 생략 가능:** CI·로깅·모니터링·문서 정리처럼 제품 기능이 아닌 순수 개발 도구성 이슈(`#63`·`#65`·`#75`·`#77`·`#88` 등)는 Milestone 없이 둬도 된다 — 억지로 끼워 맞추지 않는다.

---

## 2. `priority: must` / `priority: could`

| 라벨 | 의미 | 기준 |
|---|---|---|
| `priority: must` | 기능 구현 자체, 버그 수정 | 대부분의 이슈가 여기 해당 |
| `priority: could` | 성능 개선, 폴더/패키지 구조 정리, 리팩터, 최적화 | 명확히 이 성격일 때만 |

이슈 하나에 **둘 중 하나만** 부여한다(동시 부여 금지). 애매하면 must가 기본값 — "정말 성능개선/구조정리/최적화뿐"이라고 확신될 때만 could로 내린다. 판단이 정말 안 서면 must로 두고 사용자에게 확인.

**용어:** 이슈 본문 `## 완료 조건`(구 `## Must Have`, 2026-08-26 개명) = **그 이슈**의 완료 체크리스트 — `priority: must` 라벨과는 다른 개념. 전자는 "이 이슈가 언제 끝났다고 볼지", 후자는 "이 작업 성격이 기능/버그냐 정리/최적화냐".

---

## 3. GitHub 운영 방식

| GitHub 객체 | 역할 |
|-------------|------|
| **Milestone** (`MVP 출시` / `출시 이후`) | 시점 축. 이슈 생성 시 지정 |
| **`priority: must` / `priority: could`** | 성격 축. 이슈 생성 시 지정, 1개만 |
| **`kind:`** | feature/bug/chore/docs |
| **`release: blocking`** | 스토어 심사 필수 (§4, Milestone·priority와 독립) |

**금지:** Milestone·`priority:` 라벨 없이 이슈만 만들고 나중에 붙이기 · 한 이슈에 `priority: must`와 `priority: could` 동시 부여 · Must 미완인데 Could 먼저 착수(막히면 `meta:blocked` 또는 담당 재배정)

**스크립트:** `./scripts/github-bootstrap.sh` — 라벨·마일스톤 생성/정렬 (재실행 가능, 처음부터 다시 세팅할 때도 사용)

**현재 상태 확인** (손으로 유지하는 스냅샷 문서 없음 — 항상 GitHub이 최신 SSOT):

```bash
# MVP 출시 마일스톤 중 아직 열려 있는 must
gh issue list --milestone "MVP 출시" --label "priority: must" --state open

# MVP 출시 완료율
gh issue list --milestone "MVP 출시" --label "priority: must" --state all --json state -q \
  '[.[] | .state] | {done: (map(select(.=="CLOSED"))|length), total: length}'
```

---

## 4. 앱 배포·심사 (Release Gate) — Milestone·priority와 무관

스토어 제출·심사를 **통과하기 위해 반드시 필요한** 항목. Milestone(시점)·priority(성격)와 별개 축.

**계기:** `#5`(Apple S2S Notification webhook)가 "App Store 심사 요건"이라고 이슈 본문에 이미 적혀 있었음에도 한때 다른 곳으로 잘못 분류돼 있었다.

### 판단 기준

> 이 항목이 없으면 **스토어 제출·심사를 통과할 수 없는가?**
> - Yes → Release Gate (`release: blocking` 라벨 부여, Milestone은 다른 이슈와 동일하게 지정)
> - No → 그냥 `priority:`만으로 충분

### 현재 상태 (2026-08-03)

**열려 있는 Release Gate 항목 없음.** 과거 항목 3개([#5](https://github.com/Central-MakeUs/TripFit-server/issues/5) Apple S2S webhook · OAuth 콘솔 설정값 채우기(완료 확인 — 추적 이슈 번호는 이후 다른 용도로 재사용돼 고정 링크 없음) · [#64](https://github.com/Central-MakeUs/TripFit-server/issues/64) 탈퇴 시 provider revoke) 전부 완료.

**주의 — 이슈 번호 재사용 관행:** 이 프로젝트는 Closed 이슈 번호를 완전히 무관한 새 작업으로 재사용하는 경우가 있다(`#65`, `#86` 사례). 다른 문서·이슈 본문에서 과거 이슈 번호를 인용할 때는 링크를 걸기 전에 **현재 제목·상태를 다시 확인**한다.

**규칙:**

- 새 Release Gate 항목 발견 시 **새 이슈를 만들어** 이 절에 추가 (생성 전 `harness-workflow.md` "새 이슈·새 브랜치·새 PR 생성은 항상 먼저 확인" 절 적용)
- `release: blocking` 라벨 부여, Milestone은 다른 이슈와 동일하게 `MVP 출시`/`출시 이후` 중 지정
- Milestone `출시 이후` 후보 이슈를 만들기 **전** 위 판단 기준으로 먼저 걸러본다

---

## 5. Agent·개발자 시작 체크

1. 내 Issue에 Milestone(`MVP 출시`/`출시 이후`)과 `priority:` 라벨이 붙어 있는가?
2. `#22` 관련 후속 변경 — submit·ACTIVE·Hidden API 임의 구현 금지(SSOT는 `schedule-participation-onboarding.md`)
3. 열려있는 **Release Gate** 항목이 있는가? (§4 — 스토어 제출 전 필수)

---

## 부록 — 폐지된 것들 (역사적 기록)

- **Wave 1~4 도메인 축** (2026-08-03 도입 → 2026-08-26 폐지): 라벨 `wave:1`~`wave:4`, 마일스톤 `Wave 1 — 소셜 로그인`·`Wave 2 — MVP 로직`·`Wave 3 — 외부 API 연동`·`Wave 4 — 리팩토링·성능·런칭 후 UX` 4개는 삭제(라벨)/Close(마일스톤) 처리했다. "Q1 로그인인가 → Q5 Release Gate인가" 배치 결정 트리도 함께 폐지 — 이슈가 어느 도메인인지 더 이상 판단할 필요가 없다.
- **Wave Backlog Issue** (`#29`~`#32`, 2026-08-19경 도입 → 2026-08-26 같은 날 두 번째 개편으로 폐지): Must/Could/Out을 본문 텍스트로만 들고 있던 계획 이슈. `priority:` 라벨로 대체되며 전부 Close.
- 과거 각 Wave의 Must/Could 목록(어떤 이슈가 왜 그렇게 분류됐는지)은 git 이력의 이 문서 이전 버전 또는 각 이슈 본문에 남아 있다.

---

*최종 갱신: 2026-08-26 — Wave 1~4 도메인 축 폐지, Milestone(`MVP 출시`/`출시 이후`) 2단계 + `priority:`(기능·버그=must / 성능·구조정리=could)로 대체. 이전: 2026-08-26 Wave Backlog Issue 폐지(같은 날 첫 번째 개편) · 2026-08-03 Wave 축을 User Journey → 도메인으로 재정의(#88) · 2026-07-28 (§5 Release Gate 신설) · TripFit 백엔드 2~3명 · Spring Boot 단일 모듈*
