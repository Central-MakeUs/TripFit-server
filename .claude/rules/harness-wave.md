# Harness — Wave · 용어 · [미정]

코어 STOP·코딩 흐름: `harness-workflow.md`

## 🚨 Release Gate — 앱 배포·심사 필수 체크리스트 — [#65](https://github.com/Central-MakeUs/TripFit-server/issues/65)

**Wave 4("운영·확장", 출시 이후 개선)와 혼동 금지.** 스토어 제출·심사를 **통과하기 위해 반드시 필요**한 항목 — 없어도 되는 개선이 아니다. 2026-07-28 도입 계기: `#5`(Apple S2S webhook)가 심사 요건이었는데도 Wave 4로 잘못 분류돼 있었음. 상세: [`development-wave.md` §7](../../docs/product/development-wave.md#7-앱-배포심사-release-gate--wave와-무관).

| 이슈 | 내용 |
|------|------|
| [#5](https://github.com/Central-MakeUs/TripFit-server/issues/5) | Apple S2S Notification webhook |
| [#62](https://github.com/Central-MakeUs/TripFit-server/issues/62) | 스토어 제출 전 OAuth 콘솔 설정값(리다이렉션 URI·자바스크립트 원본·App Store ID) |
| [#64](https://github.com/Central-MakeUs/TripFit-server/issues/64) | 탈퇴 시 소셜 provider revoke 호출 (Google/Kakao/Apple — Apple은 App Store Review Guideline 5.1.1(v)) |

**판단 기준:** "이게 없으면 스토어 심사를 통과 못 하는가?" → Yes면 Release Gate(`release: blocking` 라벨, Milestone 없음), No면 Wave 4.

**에이전트 행동:** 이 파일은 always-load이므로 매 세션 로드된다. 인증·소셜로그인·배포·탈퇴 관련 파일을 다루거나 Wave/출시 상태를 논의할 때, 위 표에 열린 항목이 있으면 **먼저 묻지 않아도 짧게 리마인드**한다(예: "참고로 Release Gate #64 아직 안 끝났음"). 새 Release Gate 항목 발견 시 `#65` + 이 표 + `waves.md`/`development-wave.md` §7에 **동시** 추가 — `[미정]` 트래커와 동일한 패턴.

**금지:** Release Gate 항목을 Wave 4로 분류 · `#65`에만 적고 이 표를 빼먹기

## `[미정]` chore 트래커 — [#2](https://github.com/Central-MakeUs/TripFit-server/issues/2)

기획·스펙·BR에서 **아직 확정하지 않은 `[미정]`** 은 기능 Feat로 쪼개지 말고 **[#2](https://github.com/Central-MakeUs/TripFit-server/issues/2)** (`[Chore] 기획·스펙 [미정] 사항 모음`)에 모은다.

| 할 일 | 규칙 |
|-------|------|
| 새 `[미정]` 생김 | 문서(스펙·BR·ERD 등)에 `[미정]` 표기 **+** `#2` 본문 Must 체크리스트에 항목 추가 (`gh issue edit`) |
| 기획 확정 | 문서 amend → `#2` 해당 항목 `[x]`(+결정·날짜) → 필요 시 **별도 Feat**로 구현 |
| 브랜치 | `#2`를 `feat/2-...` 구현 타깃으로 **쓰지 않음** (메타 트래커) |

**금지:** `[미정]`을 묻지 않고 임의 확정해 구현·커밋 · `#2`에만 적고 문서 `[미정]`을 빼먹기

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
| **C1 윈도우 (#37)** | 본인 `users/schedule/calendar` 조회·수정 가능 구간 | **today ~ today+2년−1** · 구 A1(730일 길이) 대체 |

상세: [`docs/product/development-wave.md`](../../docs/product/development-wave.md) · [`docs/product/glossary.md`](../../docs/product/glossary.md)
