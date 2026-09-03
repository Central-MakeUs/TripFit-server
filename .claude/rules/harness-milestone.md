# Harness — 릴리즈 마일스톤·우선순위·용어·[미정]

코어 STOP·코딩 흐름: `harness-workflow.md`

## 🚨 Release Gate — 앱 배포·심사 필수 체크리스트

**Milestone `출시 이후`(런칭 후 개선)와 혼동 금지.** 스토어 제출·심사를 **통과하기 위해 반드시 필요**한 항목 — 없어도 되는 개선이 아니다. 2026-07-28 도입 계기: `#5`(Apple S2S webhook)가 심사 요건이었는데도 한때 잘못 분류돼 있었음. 상세: [`release-milestones.md` §4](../../docs/product/release-milestones.md#4-앱-배포심사-release-gate--milestonepriority와-무관).

**판단 기준:** "이게 없으면 스토어 심사를 통과 못 하는가?" → Yes면 Release Gate(`release: blocking` 라벨, Milestone은 다른 이슈와 동일하게 지정), No면 `priority:`만으로 충분.

**현재 상태·과거 항목 이력·이슈 번호 재사용 관행:** [`release-milestones.md` §4](../../docs/product/release-milestones.md#4-앱-배포심사-release-gate--milestonepriority와-무관)가 SSOT — 여기서 중복 서술하지 않는다. **새 Release Gate 항목 발견 시 새 이슈를 만들어**(생성 전 `harness-workflow.md` "새 이슈·새 브랜치·새 PR 생성은 항상 먼저 확인" 절 적용) 그 문서 §4에 등록.

**에이전트 행동:** 이 파일은 always-load이므로 매 세션 로드된다. 인증·소셜로그인·배포·탈퇴 관련 파일을 다루거나 릴리즈 상태를 논의할 때, `release-milestones.md` §4에 열린 Release Gate 항목이 있으면 **먼저 묻지 않아도 짧게 리마인드**한다.

**금지:** Release Gate 항목을 `출시 이후`로 분류

## 릴리즈 축 (2026-08-26 개편 — Wave 도메인 축 폐지)

과거엔 이슈를 "Wave"라는 도메인 축(1 소셜 로그인·2 MVP 로직·3 외부 API 연동·4 리팩토링·성능·런칭후UX) 4단계로 나눴으나, 실제 이슈의 46%가 MVP 출시에 몰리고 출시 이후가 "기술부채"와 "런칭후 신규기능"을 억지로 묶고 있던 문제로 **폐지했다**(상세·변경 이력: [`release-milestones.md` §0](../../docs/product/release-milestones.md#0-무엇이-바뀌었나)).

지금은 서로 겹치지 않는 3개 축만 쓴다:

| 질문 | 담당 |
|---|---|
| MVP 범위인가? | `mvp.md` In/Out |
| 출시 전/후? | Milestone `MVP 출시` / `출시 이후` |
| 기능·버그(must) vs 성능·구조정리(could)? | 아래 `priority:` 절 |

**구 도메인 축 관련 용어(`wave:N` 라벨, Milestone 배치 결정 트리, "로그인 vs 외부연동 경계" 판단)는 전부 폐지됐다 — 새 이슈에 더 이상 적용하지 않는다.**

## `[미정]` 항목 처리 (전용 트래커 폐지 — 2026-08-19)

과거엔 기획·스펙·BR의 미확정 `[미정]` 항목을 이슈 `#2`(`[Chore] 기획·스펙 [미정] 사항 모음`)에 모아뒀으나, 사용자 결정으로 **이 트래커 관행은 폐지됐다.** `#2`는 이제 일반 기능 이슈(`feat/2-...`)로 재사용되며 더 이상 `[미정]` 모음 용도가 아니다.

- 새 `[미정]`이 생기면 지금은 **해당 문서(스펙·BR·ERD 등)에 `[미정]` 표기만** 남기고, 별도 중앙 트래커에 추가하지 않는다.
- 기획이 확정되면 문서를 직접 amend하고 필요 시 별도 Feat로 구현한다.
- 과거 `#2`를 링크하던 문서 내 서술은 **역사적 기록**(그 시점에 그 이슈에서 논의됐다는 사실)으로 남겨두되, 새로 `[미정]`을 추가할 때 그 링크를 재사용하지 않는다.
- 중앙 트래커가 다시 필요하다고 판단되면 그때 사용자에게 새 이슈 생성 여부를 확인한다 (`harness-workflow.md` "새 이슈·새 브랜치·새 PR 생성은 항상 먼저 확인" 절).

**금지:** `[미정]`을 묻지 않고 임의 확정해 구현·커밋 · 폐지된 `#2` 트래커에 새 `[미정]` 추가

## ⛔ priority: must / could · 용어 (단정 금지, 2026-08-26 기준 재정의)

**판단 기준 (Wave DoD 필수 여부가 아니라 작업 성격 기준):** 성능 개선·폴더/패키지 구조 정리·리팩터·최적화 = `priority: could`. 그 외 **기능 구현 자체·버그 수정은 전부** `priority: must`. 현재 could는 `#9`(S3 미러링, 이미지 서빙 안정성 개선)·`#52`·`#54`·`#86`(Calendar 동기화 신뢰성 개선)·`#100`(전부 구조정리·리팩터·인프라 안정성 성격) 5개뿐 — 나머지는 전부 must.

**⛔ 에이전트가 스스로 must/could를 판단해 라벨을 부여·변경하지 않는다 — 반드시 사용자에게 먼저 확인.** 2026-08-26 `#9`(S3 미러링)·`#86`(Calendar 동기화 방식 변경)을 에이전트가 "기능 구현/신뢰성 우선"으로 자체 판단해 must로 분류했다가, 사용자가 could로 정정한 사고 계기 — "새 기능([Feat]) 태그가 붙어 있다"는 표면적 신호만으로 must를 단정하지 말고, 실제 작업 성격(안정성·효율 개선인지)을 봐야 한다. 신규 이슈 생성 시에도 동일 — priority 필드는 애매하면 사용자 확인 후 채운다.

| 용어 | 의미 | SSOT |
|------|------|------|
| **이슈 `## 완료 조건`** (구 `## Must Have`) | **그 이슈**를 끝내는 체크리스트. `priority: must`와 이름이 겹쳐 헷갈려서 개명함 | 해당 Feature/Bug Issue |
| **Must** | 기능 구현 자체·버그 수정 | 이슈의 `priority: must` 라벨 |
| **Could** | 성능 개선·폴더/패키지 구조 정리·리팩터·최적화 (MoSCoW Could have) | 이슈의 `priority: could` 라벨 |
| **스펙 `MVP: In scope`** | 제품 범위에 들어감 — **`priority: must`와 별개 축** (범위 vs 작업 성격) | 혼동 금지 |

**금지**

- 근거(라벨) 없이 이슈를 Must/Could라고 말함
- **사용자 확인 없이 priority 라벨을 부여·변경**(신규 이슈 포함)
- Milestone·`priority:` 라벨 없이 이슈 방치
- 이미 구현·머지된 기능을 문서가 stale하다는 이유로 mvp.md Out으로 방치 (`#107` 사고 — 실제 의존관계가 생기면 In Scope·Must로 재승격하고 문서를 amend)

## 일정·기간 용어 (혼동 금지 — glossary SSOT)

| 용어 | 의미 | 비고 |
|------|------|------|
| **희망 기간** | `trip.startRange`~`endRange` | 추천·조율 탐색 범위 |
| **조회 윈도우** | 여행방 일정 calendar 조회 가능 구간 (#37) | 희망 기간과 **별 축** · C1=`today~+2y` · C2/C3=`startRange~endRange` |
| **C1 윈도우 (#37)** | 본인 `users/schedule/calendar` 조회·수정 가능 구간 | **today ~ max(today+2년−1, 참여 중 ONGOING 여행 endRange 최댓값)**(#53 R4) · 구 A1(730일 길이) 대체 |

상세: [`docs/product/release-milestones.md`](../../docs/product/release-milestones.md) · [`docs/product/glossary.md`](../../docs/product/glossary.md)
