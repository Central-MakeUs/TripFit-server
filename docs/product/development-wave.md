# TripFit Development Wave — 운영 가이드

> **이 문서가 Wave 운영의 SSOT입니다.**
> Wave 정의·판단·GitHub 운영·백로그 절차는 여기를 따릅니다.
> 짧은 요약표: [`waves.md`](waves.md) · MVP 범위: [`mvp.md`](mvp.md)

---

## 0. Wave란 무엇인가

**Wave = 백엔드 작업을 도메인/기술 축으로 끊어 놓은 릴리즈 단위.**

| Wave | 축 |
|------|-----|
| **1** | 소셜 로그인 |
| **2** | MVP 로직 — trip · recommend · member 등 서버 도메인 로직 |
| **3** | 외부 API 연동 — Google Calendar · Firebase(FCM) · Kakao 등 |
| **4** | 리팩토링·백엔드 성능 개선 + 이번 MVP엔 없으나 **런칭 이후 UX**로 추가될 기능 |

**원칙:** Wave가 GitHub Issue를 소유한다. Issue를 만들고 나서 사후에 "이거 몇 Wave 같네" 라벨을 붙이는 건 금지 — §2 결정 트리로 먼저 Wave를 정하고 Issue를 만든다.

**tooling 이슈는 wave 라벨 생략 가능:** CI·로깅·모니터링처럼 3개 도메인(로그인/MVP 로직/외부연동) 어디에도 속하지 않고 Wave 4의 "리팩토링·성능"과도 딱 맞지 않는 순수 개발 도구성 이슈(`#63` API breaking-change CI, `#65` 구조화 로깅, `#75`·`#77` 등)는 `wave:N` 라벨 없이 `area: infra`만 붙여도 된다 — 억지로 4개 Wave 중 하나에 끼워 맞추지 않는다. `docs/specs/README.md`의 "도구 (Wave 무관)" 섹션과 동일한 취급.

```
❌ Issue 생성 → 개발 → "Wave2인 것 같네?" → 문서 수정
✅ §2 결정 트리로 Wave 판단 → Backlog에 추가 → Issue 생성 → 개발
```

### 변경 이력 (축 자체가 바뀐 이력)

| 시점 | 축 | 비고 |
|------|-----|------|
| 초기 | 기술 스택 도입 단계 (Redis, RTR, Docker…) | "API 몇 개 만들었는지"로 흐르는 문제 — 사용자 가치가 안 보임 |
| 2026-07 | User Journey ("로그인→여행방→추천→확정→베타→운영") | Wave가 결과 기록이 되는 문제 — 도메인 이슈가 저니 단계에 걸쳐 흩어짐(#22가 로그인 Wave인데 trip 로직인 식) |
| **2026-08 (현재)** | **도메인/기술 축** (로그인 / MVP 서버로직 / 외부연동 / 리팩토링·성능·런칭후UX) | 이슈 하나가 어느 도메인인지로 바로 Wave가 정해짐 — 판단 기준이 가장 단순 |

---

## 1. Wave 1~4 정의

### Wave 1 — 소셜 로그인 (`Wave 1 — 소셜 로그인`)

**범위:** 카카오/구글/애플 로그인, JWT 발급·검증·refresh·logout, 온보딩(이름·프로필), 로그인 관련 에러 처리. **로그인 자격증명 자체**를 다루는 것만 — 특정 서비스(Calendar 등)와의 연동은 Wave 3.

**DoD:** Wave 1 Backlog Must 전부 Closed · `./gradlew test`

| 분류 | 이슈 |
|------|------|
| **Must** | #1(소셜 로그인 API) · #3(JWT 필터) · #10(온보딩·프로필) · #57(AuthErrorCode 세분화) |
| **포함 안 됨** | 여행방·추천·일정(#12, #13 등 → Wave 2) · Google Calendar·FCM·카카오(→ Wave 3) · RTR/Redis(#4 → Wave 4) |

---

### Wave 2 — MVP 로직 (`Wave 2 — MVP 로직`)

**범위:** trip · recommend · member 등 **서비스 핵심 서버 도메인 로직**. 여행방 생성·참여·일정 수집·추천·확정, 관련 CRUD·정책·인터셉터·스케줄러.

**DoD:** [`mvp.md`](mvp.md) MVP 완료 기준(방장이 추천 TOP 3로 최종 날짜 확정) 충족 + Wave 2 Backlog Must 전부 Closed · `./gradlew test`

| 분류 | 이슈 |
|------|------|
| **Must** | #11(일정 API) · #12(여행방 API) · #13(추천 API 껍데기) · #17(calendar resolve) · #22(참여·submit·sparse 재설계) · #24(권한 가드) · #37(조회 윈도우 +2년) · #38(EXPIRED snapshot) · #39(JOINED→confirm→RESPONDED) · #47(나가기·내보내기·삭제 정합성) · #48(TripStatus 정리) · #50(추천 계산 로직) · #53(마이페이지 달력 C1 상한) · #54(TripMember 상태 파생) · #60(TripDetailResponse 필드) · #67(슬롯 오버라이드) |
| **Nice** (DoD 불필요) | #20(참여자 내보내기) · #26(`last_activity_at` hook) · #27(EXPIRED·Pin 스케줄러) |
| **포함 안 됨** | Google Calendar·FCM·카카오(→ Wave 3) · Redis·RTR·S3·계정연결(→ Wave 4) |

> **용어:** 이슈 본문 `## Must Have` = **그 이슈** 완료 체크리스트. **Wave Must** = 이 표 · Backlog Issue Must 섹션. `MVP: In scope` ≠ Wave Must. **Nice와 Out 혼용 금지.**

> **⚠️ 미분류 (2026-08-16 발견):** Backlog `#30`은 2026-08-11에 DoD 완료로 이미 Closed됐는데, `#105`(연차/반차 자동 반영 — Implemented, PR #108)·`#107`(공휴일 반영, Open)이 그 이후 8/15에 새로 생겨 위 Must/Nice 어디에도 없다. `harness-wave.md`상 에이전트가 스스로 Must/Nice/Out을 단정할 수 없어 분류는 비워둠 — 이미 Closed된 Backlog에 소급 추가할지, 별도 트래킹으로 둘지 **사용자 확인 필요**.

---

### Wave 3 — 외부 API 연동 (`Wave 3 — 외부 API 연동`)

**범위:** TripFit 자체 로직이 아니라 **3rd-party 서비스 API/SDK 연동** — Google Calendar OAuth·이벤트 동기화, Firebase(FCM) 푸시, Kakao 공유 SDK.

**DoD:** Wave 3 Backlog Must 전부 Closed · `./gradlew test`

| 분류 | 이슈 |
|------|------|
| **Must** | #19(카카오·링크 공유) · #21(FCM 알림, BR-NOTI) · #44(Google Calendar OAuth — Implemented) · #56(Calendar env 버그 — Implemented) · #78(Calendar OAuth Client ID 분리 — Implemented) |
| **포함 안 됨** | trip/recommend/member 신규 도메인 로직(→ Wave 2) · 로그인 자격증명 자체(→ Wave 1) |

---

### Wave 4 — 리팩토링·성능 · 런칭 이후 UX (`Wave 4 — 리팩토링·성능·런칭 후 UX`)

**목적:** (a) 리팩토링·백엔드 성능·인프라 개선, (b) 이번 MVP In Scope엔 없지만 **런칭 이후** 붙일 UX/기능. 새 MVP 기능이 아님 — **스토어 제출 전 필수 항목은 여기 아니라 §5 Release Gate.**

**DoD:** 팀이 합의한 체크리스트 (출시 게이트 아님, 항목별 완료로 판단)

| 분류 | 이슈 |
|------|------|
| **포함** | #4(RTR+Redis) · #6(소셜 계정 연결·해제) · #9(프로필 S3 미러링) · #35(join 정원 hold) |
| **포함 안 됨** | MVP In Scope 신규 기능 — 넣으려면 **Wave 1~3 Backlog 개정 + `mvp.md` amend** 필요 · 스토어 심사 필수 항목(→ §5 Release Gate) |

---

## 2. 새 기능 · 새 이슈 — Wave 배치 결정 트리

```
┌─ Q1. 로그인/인증 자체(카카오·구글·애플 로그인, JWT 발급·검증·refresh)를 다루는가?
│     Yes → Wave 1
│     No  ↓
├─ Q2. 3rd-party 서비스 API/SDK 연동인가? (Google Calendar, Firebase/FCM, Kakao SDK 등 —
│     "우리 로그인 자격증명"이 아니라 "다른 서비스와 주고받는" 연동)
│     Yes → Wave 3
│     No  ↓
├─ Q3. trip·recommend·member 등 서비스 핵심 서버 도메인 로직이고 mvp.md In Scope인가?
│     Yes → Wave 2 (Must/Nice는 §1 표 기준 — DoD 필수면 Must, 아니면 Nice)
│     No  ↓
├─ Q4. 리팩토링·성능·인프라 개선이거나, MVP엔 없지만 런칭 이후 추가할 UX/기능인가?
│     Yes → Wave 4
│     No  ↓
└─ Q5. 스토어 제출·심사를 통과하기 위해 반드시 필요한가?
      Yes → Wave 아님 — §5 Release Gate
      No  → 팀 논의 (15분) → development-wave.md amend
```

**주의 — 사용자 확인 우선:** 어느 질문 단계에서 답이 나오든, Wave를 **에이전트가 스스로 확정하지 않는다.** 특히 Q1/Q2 경계(로그인 자격증명 자체 vs 로그인이 매개하는 외부 서비스 연동)는 헷갈리기 쉬우므로 결론을 사용자에게 한 줄로 확인받고 Backlog에 반영한다. (2026-08 Google Calendar가 Wave 축 개편 중 Wave 2/3/4를 오가며 재분류된 사고 재발 방지.)

### 2.1 논쟁이 나면

1. 이 기능이 **어느 도메인**인지 한 문장으로 적기 (로그인 / trip·recommend·member / 외부연동 / 인프라·UX)
2. Must vs Nice 결정 (Wave 2만 해당 — DoD 필수 여부)
3. 15분 안에 안 되면 **Wave 4 또는 보류** — MVP 속도 우선

---

## 3. GitHub 운영 방식

### 3.1 역할 분담

| GitHub 객체 | 역할 |
|-------------|------|
| **Milestone** `Wave N — {한글}` | Wave **컨테이너**. 해당 Wave에 **속하는 Issue만** 연결 |
| **Backlog Issue** (Wave당 1개) | Wave **계획 SSOT** — Must / Nice / Out 목록 · DoD 체크리스트 |
| **Issue** | Backlog에서 **파생**된 실행 단위. `#n` = 브랜치·PR·스펙 |
| **`wave:N` 라벨** | Milestone과 **1:1** (필터용). Issue 생성 **전** Backlog에서 확정 |
| **`kind:` / `area:`** | feature/bug/docs/chore · api/domain/deploy/docs/infra |

**Notion (선택):** PRD·와이어프레임·회의록. **실행·DoD·Issue 번호는 GitHub만 SSOT.**

### 3.2 Wave 계획 → Issue 생성 (표준 절차)

**① Wave Backlog Issue 유지** (각 Milestone에 pinned 1개, 제목 예: `[Wave 2 Backlog] MVP 로직 — Must / Nice / Out`)

```markdown
## DoD (Wave N)
- [ ] ...

## Must (Issue 없으면 DoD 불가)
- [ ] #13 추천·확정

## Nice (Must 다음)
- [ ] #20 참여자 내보내기

## Out (이 Wave에서 안 함)
- Google Calendar → Wave 3 #44

## 보류 (Wave 미정)
- ...
```

**② 새 기능 발생:** §2 결정 트리 실행 → Backlog에 한 줄 추가 → Must/Nice 확정 후 Feature Issue 생성(Milestone·`wave:N` 동시 지정) → DB·인증·3파일+는 `docs/specs/`(Approved 후 구현)

**③ 금지 패턴:** Backlog에 없는데 "일단 Issue만" · merge 후 `wave:` 라벨만 붙이기 · Must/Nice 구분 없이 polish·Out을 Wave Must에 섞기

### 3.3 브랜치 · PR · 스펙

- 브랜치: `{type}/{issue-number}-{description}` — [`.github/CONTRIBUTING.md`](../../.github/CONTRIBUTING.md)
- PR: `Closes #n` · Spec 링크 · `./gradlew test`
- 스펙 헤더: `> wave: N` — **Backlog와 동일**해야 함

### 3.4 주기적 점검 (15분 · 주 1회)

| 체크 | 조치 |
|------|------|
| Open Issue 중 Backlog Must에 없는 것 | Backlog 추가 or Wave 이동 or close |
| Must 미완인데 Nice 착수 | Nice Issue `meta:blocked` 또는 담당 재배정 |
| Wave N DoD 전부 체크 | Milestone close · 다음 Wave Backlog kickoff |
| `waves.md` / 본 문서와 GitHub 불일치 | 문서 amend (본 문서 우선) |

### 3.5 스크립트

- `./scripts/github-sync-issues.sh` — **라벨·마일스톤 정렬** (Backlog 결정 **후** 실행)
- Wave 배치 **판단은 스크립트가 하지 않음** — 반드시 §2 + Backlog

### 3.6 현재 백로그 스냅샷 (2026-08-03, Wave 재정의 반영)

| Wave | Backlog Issue | Must (DoD) | Nice | Out / other Wave |
|------|---------------|------------|------|------------------|
| **1** | **#29** | #1·#3·#10·#57 (전부 Closed) | — | trip·추천 → Wave 2, 외부연동 → Wave 3 |
| **2** | **#30** | #13·#50 Open (#11·#12·#17·#22·#24·#37·#38·#39·#47·#48·#53·#54·#60·#67 Closed) | #20✓·#26✓·#27✓ | 외부연동(#19·#21·#44) → Wave 3 |
| **3** | **#31** | #44✓·#56✓·#78✓ (Closed) · #19✓·#21 Open | — | trip·recommend → Wave 2 |
| **4** | **#32** | — (팀 합의 체크리스트) | — | #4·#6·#9·#35 |
| **Release Gate** | — (Wave 무관, §5) | #5✓·#64✓ (전부 Closed) · OAuth 콘솔 설정값✓(완료, 이슈 번호 재사용됨) | — | — |

---

## 4. 문서 · 스펙 · Agent

| 문서 | 역할 |
|------|------|
| **본 문서** | Wave 운영·판단·GitHub 절차 SSOT |
| [`waves.md`](waves.md) | Wave 1~4 **한 페이지 요약** |
| [`mvp.md`](mvp.md) | MVP In/Out · Wave 2 DoD 원문 |
| [`docs/specs/`](../specs/) | 기능 계약 — `> wave: N`은 Backlog와 일치 |

Agent·개발자 **시작 체크:**

1. 현재 **활성 Wave**는? (Must 미완인 가장 낮은 N)
2. 내 Issue가 그 Wave Backlog **Must**에 있는가?
3. #22 관련 후속 변경 — submit·ACTIVE·Hidden API 임의 구현 금지(SSOT는 `schedule-participation-onboarding.md`)
4. 열려있는 **Release Gate** 항목이 있는가? (§5 — 스토어 제출 전 필수, Wave와 무관)

---

## 5. 앱 배포·심사 (Release Gate) — Wave와 무관

Wave는 "백엔드 작업이 어느 도메인인가"를 끊는 축이고, Release Gate는 **"스토어 심사를 통과할 수 있는가"**를 끊는 축이다. 두 축은 독립적 — Wave 4(리팩토링·성능·런칭후UX)로 잘못 분류된 항목이 있으면 안 된다.

**계기:** `#5`(Apple S2S Notification webhook)가 "App Store 심사 요건"이라고 이슈 본문에 이미 적혀 있었음에도 한때 Wave 4 Milestone·라벨로 분류돼 있었다.

### 판단 기준

> 이 항목이 없으면 **스토어 제출·심사를 통과할 수 없는가?**
> - Yes → Release Gate (`release: blocking` 라벨, Milestone 없음 — Wave와 독립)
> - No, 그냥 나중에 하면 좋은 개선 → Wave 4

### 현재 상태 (2026-08-03)

**열려 있는 Release Gate 항목 없음.** 과거 항목 3개([#5](https://github.com/Central-MakeUs/TripFit-server/issues/5) Apple S2S webhook · OAuth 콘솔 설정값 채우기(완료 확인 — 추적 이슈 번호는 이후 다른 용도로 재사용돼 고정 링크 없음) · [#64](https://github.com/Central-MakeUs/TripFit-server/issues/64) 탈퇴 시 provider revoke) 전부 완료. 메타 트래커였던 `#65`는 관측성 개선 스펙([`social-integration-structured-logging.md`](../specs/cross-cutting/social-integration-structured-logging.md))으로 재사용됐다 — 더 이상 Release Gate 트래커가 아니다.

**주의 — 이슈 번호 재사용 관행:** 이 프로젝트는 Closed 이슈 번호를 완전히 무관한 새 작업으로 재사용하는 경우가 있다(`#65`, `#86` 사례). 다른 문서·이슈 본문에서 과거 이슈 번호를 인용할 때는 링크를 걸기 전에 **현재 제목·상태를 다시 확인**한다.

**규칙:**

- 새 Release Gate 항목 발견 시 **새 이슈를 만들어** 이 절에 추가 — `#2`([미정] 트래커)와 동일한 패턴, 단 트래커 이슈 번호는 그때 새로 발급. `waves.md`·`harness-wave.md`는 이 절을 링크만 하므로 별도 갱신 불필요.
- `release: blocking` 라벨만 부여, **Milestone은 지정하지 않음**(Wave 컨테이너가 아니므로).
- Wave 4 후보 이슈를 만들기 **전** 위 판단 기준으로 먼저 걸러본다.

---

## 부록 A — `waves.md`와의 관계

- **운영·판단·Backlog·이슈 번호·Open/Closed 상태:** 본 문서
- **Wave 정의·DoD 한 줄 요약만:** `waves.md` (이슈 번호·GitHub 상태는 담지 않음 — 본 문서와 **불일치 시 본 문서 우선**, `waves.md` 갱신)

## 부록 B — 마일스톤 이름 (GitHub)

| wave | Milestone (GitHub) | 한글 |
|------|-------------------|------|
| 1 | Wave 1 — 소셜 로그인 | 소셜 로그인 |
| 2 | Wave 2 — MVP 로직 | trip·recommend·member 서버 로직 |
| 3 | Wave 3 — 외부 API 연동 | Google Calendar·Firebase·Kakao |
| 4 | Wave 4 — 리팩토링·성능·런칭 후 UX | 리팩토링·백엔드 성능 + 런칭 이후 추가 기능 |

## 부록 C — Wave Backlog Issue (GitHub SSOT)

| Wave | Issue | 제목 |
|------|-------|------|
| 1 | **#29** | `[Wave 1 Backlog]` |
| 2 | **#30** | `[Wave 2 Backlog]` |
| 3 | **#31** | `[Wave 3 Backlog]` |
| 4 | **#32** | `[Wave 4 Backlog]` |

재생성 시 본문 템플릿: `docs/product/templates/wave-backlog-body.md` · §1 DoD/Must/Nice/Out 반영.

Wave Backlog Issue는 **코드 구현 Issue가 아님** — `kind: chore` + `area: docs` 권장.

**Pin (GitHub 한도 3개):** Wave 1~3 Backlog **#29 · #30 · #31** pinned. Wave 4 **#32**는 Milestone·본 문서 표로 SSOT.

**Nice 구분:** GitHub 라벨 없음 — Backlog **Nice** 목록 + Issue **비고** `분류: Wave N Nice` (#20 · #26 · #27). **Out**은 Backlog Out. `Nice / Out` 혼용 금지.

---

*최종 갱신: 2026-08-03 — Wave 축을 User Journey → 도메인(로그인/MVP 로직/외부연동/리팩토링·성능·런칭후UX)으로 재정의(#88). 이전: 2026-07-28 (§5 Release Gate 신설) · TripFit 백엔드 2~3명 · Spring Boot 단일 모듈*
