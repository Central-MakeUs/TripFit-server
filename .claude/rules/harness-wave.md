# Harness — Wave · 용어 · [미정]

코어 STOP·코딩 흐름: `harness-workflow.md`

## 🚨 Release Gate — 앱 배포·심사 필수 체크리스트

**Wave 4("리팩토링·성능·런칭 후 UX", 출시 이후 개선)와 혼동 금지.** 스토어 제출·심사를 **통과하기 위해 반드시 필요**한 항목 — 없어도 되는 개선이 아니다. 2026-07-28 도입 계기: `#5`(Apple S2S webhook)가 심사 요건이었는데도 Wave 4로 잘못 분류돼 있었음. 상세: [`development-wave.md` §5](../../docs/product/development-wave.md#5-앱-배포심사-release-gate--wave와-무관).

**판단 기준:** "이게 없으면 스토어 심사를 통과 못 하는가?" → Yes면 Release Gate(`release: blocking` 라벨, Milestone 없음), No면 Wave 4.

**현재 상태·과거 항목 이력·이슈 번호 재사용 관행:** [`development-wave.md` §5](../../docs/product/development-wave.md#5-앱-배포심사-release-gate--wave와-무관)가 SSOT — 여기서 중복 서술하지 않는다. **새 Release Gate 항목 발견 시 새 이슈를 만들어**(생성 전 `harness-workflow.md` "새 이슈·새 브랜치·새 PR 생성은 항상 먼저 확인" 절 적용) 그 문서 §5에 등록.

**에이전트 행동:** 이 파일은 always-load이므로 매 세션 로드된다. 인증·소셜로그인·배포·탈퇴 관련 파일을 다루거나 Wave/출시 상태를 논의할 때, `development-wave.md` §5에 열린 Release Gate 항목이 있으면 **먼저 묻지 않아도 짧게 리마인드**한다.

**금지:** Release Gate 항목을 Wave 4로 분류

## Wave 축 (2026-08 개편 — 도메인 축)

Wave = **도메인/기술 축**: 1 소셜 로그인 · 2 MVP 로직(trip·recommend·member) · 3 외부 API 연동(Google Calendar·Firebase·Kakao) · 4 리팩토링·성능·런칭 후 UX. 상세 정의·이슈 매핑: [`development-wave.md` §1](../../docs/product/development-wave.md#1-wave-14-정의).

**로그인(Wave 1) vs 외부 연동(Wave 3) 경계 주의:** "로그인 자격증명 자체"(카카오·구글·애플 로그인, JWT)는 Wave 1, "로그인이 매개하는 외부 서비스 연동"(Google Calendar, FCM)은 Wave 3 — 헷갈리기 쉬우므로 새 이슈를 이 경계로 분류할 땐 **에이전트가 스스로 확정하지 않고 사용자에게 한 줄로 확인**받는다 (2026-08 Google Calendar가 Wave 축 개편 중 재분류되며 얻은 교훈).

## `[미정]` 항목 처리 (전용 트래커 폐지 — 2026-08-19)

과거엔 기획·스펙·BR의 미확정 `[미정]` 항목을 이슈 `#2`(`[Chore] 기획·스펙 [미정] 사항 모음`)에 모아뒀으나, 사용자 결정으로 **이 트래커 관행은 폐지됐다.** `#2`는 이제 일반 기능 이슈(`feat/2-...`)로 재사용되며 더 이상 `[미정]` 모음 용도가 아니다.

- 새 `[미정]`이 생기면 지금은 **해당 문서(스펙·BR·ERD 등)에 `[미정]` 표기만** 남기고, 별도 중앙 트래커에 추가하지 않는다.
- 기획이 확정되면 문서를 직접 amend하고 필요 시 별도 Feat로 구현한다.
- 과거 `#2`를 링크하던 문서 내 서술은 **역사적 기록**(그 시점에 그 이슈에서 논의됐다는 사실)으로 남겨두되, 새로 `[미정]`을 추가할 때 그 링크를 재사용하지 않는다.
- 중앙 트래커가 다시 필요하다고 판단되면 그때 사용자에게 새 이슈 생성 여부를 확인한다 (`harness-workflow.md` "새 이슈·새 브랜치·새 PR 생성은 항상 먼저 확인" 절).

**금지:** `[미정]`을 묻지 않고 임의 확정해 구현·커밋 · 폐지된 `#2` 트래커에 새 `[미정]` 추가

## ⛔ Wave Must / Nice / Out · 용어 (단정 금지)

에이전트가 “Must다 / Nice다 / Out이다”를 **Backlog 없이** 단정하지 않는다. 분류 SSOT는 Wave Backlog Issue(`#29`~`#32`).

| 용어 | 의미 | SSOT |
|------|------|------|
| **이슈 `## Must Have`** | **그 이슈**를 끝내는 체크리스트 | 해당 Feature Issue |
| **Wave Must** | 해당 Wave **DoD에 필수** | Backlog **Must** 섹션 |
| **스펙 `MVP: In scope`** | 제품 범위에 들어감 — **Wave Must 자동 아님** | Must로 쓰려면 Backlog Must에 **명시** |
| **Nice** | `wave:N`이지만 DoD **불필요** | Backlog **Nice** + 이슈 비고 `분류: Wave N Nice` |
| **Out** | **이 Wave에서 안 함** (보류·다음 Wave·안 함) | Backlog **Out** — Nice와 **혼용 표기 금지** (`Nice / Out` 금지) |

**금지**

- Backlog에 없는 이슈를 Wave Must/Nice라고 말함
- `MVP: In scope`만 보고 Wave Must로 취급
- Nice와 Out을 한 칸에 섞어 씀 (`#19`/`#20` 사고)

## 일정·기간 용어 (혼동 금지 — glossary SSOT)

| 용어 | 의미 | 비고 |
|------|------|------|
| **희망 기간** | `trip.startRange`~`endRange` | 추천·조율 탐색 범위 |
| **조회 윈도우** | 여행방 일정 calendar 조회 가능 구간 (#37) | 희망 기간과 **별 축** · C1=`today~+2y` · C2/C3=`startRange~endRange` |
| **C1 윈도우 (#37)** | 본인 `users/schedule/calendar` 조회·수정 가능 구간 | **today ~ max(today+2년−1, 참여 중 ONGOING 여행 endRange 최댓값)**(#53 R4) · 구 A1(730일 길이) 대체 |

상세: [`docs/product/development-wave.md`](../../docs/product/development-wave.md) · [`docs/product/glossary.md`](../../docs/product/glossary.md)
