# TripFit Development Wave — 운영 가이드

> **이 문서가 Wave 운영의 SSOT입니다.**  
> Wave 정의·판단·GitHub 운영·백로그 절차는 여기를 따릅니다.  
> 짧은 요약표: [`waves.md`](waves.md) · MVP 범위: [`mvp.md`](mvp.md)

---

## 0. Wave란 무엇인가

**Wave = 사용자가 서비스를 어디까지 사용할 수 있는지(User Journey)를 끊어 놓은 릴리즈 단위.**

| Wave는 **아니다** | Wave **이다** |
|------------------|---------------|
| 기술 스택 도입 단계 (Redis, RTR, Docker 개선…) | 사용자 가치·MVP 목표 기준의 출시 물결 |
| “API 몇 개 만들었는지” | “데모·베타·출시에서 **무엇을 보여줄 수 있는지**” |
| 이슈에 사후로 붙이는 라벨 | **이슈를 소유하는** 계획 단위 |

**원칙:** Wave가 GitHub Issue를 소유한다. Issue가 Wave를 결정하면 안 된다.

```
❌ Issue 생성 → 개발 → “Wave2인 것 같네?” → 문서 수정
✅ Wave 정의·백로그 → Issue 생성 → 개발 → Wave DoD 검증
```

---

## 1. 현재 Wave 정의의 문제 (비판적 분석)

### 1.1 기술·도메인 혼재

기존 `waves.md`는 Wave 1을 “인증, JWT, API 규약, 배포…”처럼 **기술 레이어**로 설명했습니다.

그 결과:

- **#22(일정 참여·submit 재설계)** 같은 **제품·정책** 작업이 Wave 1에 들어가도 납득이 어렵고,
- **#24(권한 가드)** 같은 **구현 편의** 작업도 Wave 1·2 어디에 둘지 매번 논쟁이 납니다.
- “Redis는 Wave 4”처럼 **기술 이름**이 Wave 번호를 설명하는 경우가 생깁니다.

→ Wave 번호만으로 **사용자에게 무엇이 가능해지는지**가 드러나지 않습니다.

### 1.2 Wave가 결과 기록이 됨

실제 운영 순서:

1. 기능 필요성 발생
2. Issue 생성 (`kind: feature`, `area: api`)
3. 개발·merge
4. 나중에 `wave:2` 라벨·마일스톤 부착

이 순서에서는 Wave가 **계획**이 아니라 **분류 사후 기록**입니다.  
`#12(여행방)`이 Wave 2인데 `#22(일정 재설계)`가 Wave 1인 채 **동시에 진행**되는 것도, Wave가 “지금 뭘 끝내야 하는가”를 말해 주지 못해서 생긴 혼선입니다.

### 1.3 Must / Nice / Out 구분 부재

Wave 2 라벨이 붙은 이슈만 해도:

| 이슈 | Wave 2 **필수**? |
|------|------------------|
| #11 · #12 · #13 | **예** — MVP DoD |
| #26 · #27 | 아니오 — D5 polish defer |
| #19 | 아니오 — Wave 2 **Out** |
| #20 | 아니오 — Wave 2 **Nice** (DoD 불필요) |

같은 `wave:2`라도 **Wave 2 완료에 필수인지**가 문서·GitHub에서 한눈에 안 보입니다.

### 1.4 판단 기준 공유 불가

“이 기능 Wave 몇?” 질문에 사람마다 다른 답:

- API 도메인 기준 → trip이면 Wave 2
- 기술 난이도 기준 → Redis면 Wave 4
- 급한 것 먼저 → 현재 sprint

→ **누구나 같은 결론**을 내릴 **결정 트리**가 없었습니다.

---

## 2. Wave를 나누는 기준 (제안)

### 2.1 1순위: User Journey

각 Wave는 **한 문장으로 설명 가능한 사용자 경험**이어야 합니다.

| Wave | 한 문장 |
|------|---------|
| 1 | 로그인하고, TripFit을 쓸 **준비**가 끝난다. |
| 2 | 친구들과 여행방을 만들고, 일정을 모아, **추천을 받아 확정**한다. |
| 3 | 알림·공유·달력으로 **실제 서비스처럼** 쓴다. |
| 4 | 성능·운영·확장을 **개선**한다. (새 MVP 기능 아님) |

### 2.2 2순위: MVP 목표·릴리즈 가능 여부

[`mvp.md`](mvp.md) **MVP 완료 기준**을 Wave 2 DoD에 직접 연결합니다.

> 방장이 여행방을 만들고, 참여자 일정이 수집되고, 추천 TOP 3로 **최종 날짜가 확정**되는 시점.

Wave 3 = MVP **이후** “쓸 만한” UX. Wave 4 = MVP **범위 밖** 기술·운영 debt.

### 2.3 3순위: 기술 작업은 “Wave 4로 미룸”

다음은 **중요하지만 Wave 번호를 올리지 않습니다.** 기본 **Wave 4 (또는 현재 Wave Must 완료 후)**:

- Redis, RTR, 캐싱, 성능 프로파일링
- S3 미러, Apple S2S, 계정 연결
- 모니터링·알람·Docker/Nginx 고도화
- “더 깔끔한” 리팩터링 (동작 변경 없음)

**예외:** Wave N DoD를 **막는** 최소 인프라(예: Wave 1에서 EC2에 API 한 번 배포)만 해당 Wave Must에 포함.

### 2.4 우리 팀 규모에 맞는 현실

- 백엔드 2~3명 · Spring Boot 단일 모듈 · ~110 클래스
- **Notion** = 여정·기획 메모 (선택) · **GitHub Milestone + Backlog Issue** = 실행 SSOT
- Wave당 **Must 3~7개 이슈**가 상한 — 넘으면 다음 Wave로 쪼갬
- 스프린트/칸반은 쓰지 않아도 됨. **“지금 Wave Must만”**이 우선순위

---

## 3. Wave 1~4 재정의

### Wave 1 — 준비 (`Wave 1 — 준비`)

**목적:** 사용자(및 참여자)가 **로그인하고 TripFit을 쓸 준비**를 마친다. 여행방·추천은 아직 핵심 데모 범위가 아님.

**User Journey (데모 시나리오):**

1. 카카오/구글/애플로 로그인
2. 이름·온보딩 완료
3. (정책 확정 후) “내 일정을 trip에 어떻게 제출하는지” 규칙이 **문서·API 계약**으로 확정됨

**Definition of Done (DoD):**

- [ ] 소셜 login / refresh / logout 동작 (#1)
- [ ] JWT로 보호 API 호출 가능 (#3)
- [ ] 프로필·온보딩 PATCH (#10)
- [ ] **`#22` 스펙 Approved** — submit·sparse·온보딩 skip·RESPONDED SSOT 확정
- [ ] `./gradlew test` · dev 배포에서 위 여정 **E2E 1회** (프론트 또는 curl 시나리오)
- [ ] Wave 1 Backlog의 **Must** 이슈 전부 Closed

**포함 (Must):**

| 항목 | GitHub | 비고 |
|------|--------|------|
| 소셜 로그인·JWT·온보딩 | #1, #3, #10 | Closed |
| 일정 참여·submit·sparse 재설계 | **#22** | **Wave 1 게이트** |
| 최소 API 규약·예외 envelope | 스펙·코드 | Wave 1 Must |
| dev 배포 가능 | deploy | Wave 1 Must |

**포함 (Nice — Wave 1 DoD **불필요**):**

| 항목 | GitHub | 처리 |
|------|--------|------|
| 여행방 권한 가드 | #24 | Closed — Wave 2 전 코드 품질 |

**포함하면 안 됨 (Wave 1):**

- 여행방 생성·참여·추천·확정 (#12, #13)
- FCM·카카오 알림 (#21)
- Redis·RTR (#4)
- join 전 미리보기 (#19) → Wave 2 **Out**
- 참여자 내보내기 (#20) → Wave 2 **Nice** (DoD 불필요)

---

### Wave 2 — 핵심 MVP (`Wave 2 — 핵심 MVP`)

**목적:** [`mvp.md`](mvp.md) **MVP 완료 기준**을 달성한다 — **일정 확정**까지.

**User Journey (데모 시나리오):**

1. 방장이 여행방 생성 → 초대 링크 공유
2. 친구가 로그인 후 참여 → 일정 입력·제출
3. 방장이 추천 4모드 중 하나로 TOP 3 확인 → **일정 확정**

**Definition of Done:**

- [ ] Wave 1 DoD **전부** 충족 (특히 #22 Approved + Hidden API 해제/제거 결정 반영)
- [ ] 정기·개인 일정 API (#11) · 여행방 API (#12) · 추천·확정 (#13) Must 완료
- [ ] 여행방 일정 조회 윈도우 (#37) · TERMINATED snapshot (#38) Must 완료
- [ ] `./gradlew test` · OpenAPI · **MVP 시나리오 E2E 1회**
- [ ] Wave 2 Backlog **Must** 전부 Closed

**포함 (Must):**

| 항목 | GitHub |
|------|--------|
| User 일정 CRUD | #11 |
| 여행방·참여·홈 D5 (submit 제외분) | #12 |
| 추천 4모드·TOP 3·확정·취소 | #13 |
| calendar effective resolve | #17 |
| 여행방 일정 조회 윈도우 (+2년) | **#37** |
| TERMINATED 일정 snapshot | **#38** |

**포함 (Nice — `wave:2`이지만 Wave DoD Must **아님**):**

| 항목 | GitHub | Wave 2 DoD |
|------|--------|------------|
| `last_activity_at` hook 전체 | #26 | 불필요 |
| TERMINATED·Pin 스케줄러 | #27 | 불필요 (Implemented · Closed) |
| 참여자 내보내기 | #20 | 불필요 |

Nice 이슈는 **Wave Backlog Nice 섹션**과 Issue **비고**(`분류: Wave N Nice`)로만 표시하고, **Wave Must 완료 전 착수 금지**(팀 예외는 Backlog에 명시).

> **용어:** 이슈 본문 `## Must Have` = **그 이슈** 완료 체크리스트. **Wave Must** = Backlog Must 섹션 · Wave DoD. **`MVP: In scope` ≠ Wave Must** — Must는 Backlog에 명시. **Nice와 Out 혼용 금지.** harness `Wave Must / Nice / Out` 절.

**포함하면 안 됨 (Wave 2 Out / other Wave):**

| 항목 | GitHub | 처리 |
|------|--------|------|
| join 전 미리보기 | **#19** | Wave 2 **Out** |
| 푸시 알림·FCM | #21 | Wave 3 |
| Redis·RTR·S3 미러 | #4, #9 | Wave 4 |
| 그룹 달력 **시각화 UX** (프론트) | — | Wave 3 (백엔드 API는 Wave 2와 겹칠 수 있음 → Backlog에서 구분) |

---

### Wave 3 — 출시 UX (`Wave 3 — 출시 UX`)

**목적:** MVP **이후** “실제 서비스처럼” 쓸 수 있게 한다. **내부·친구 베타**에 내보낼 UX.

**User Journey:**

1. 참여·확정 시 **알림**을 받는다
2. 카카오 등으로 **초대 링크를 공유**한다
3. **그룹 달력**에서 누가 응답했는지 본다

**Definition of Done:**

- [ ] Wave 2 DoD 충족
- [ ] 알림 스펙 Approved + BR-NOTI Must 이벤트 구현 (#21)
- [ ] 카카오(또는 팀 합의) 링크 공유 연동
- [ ] 그룹 달력 API·프론트 연동 가능
- [ ] `./gradlew test` · **베타 시나리오** 1회

**포함:** #21, 그룹 달력, 리마인드(단 **BR-NOTI-005 정기 스케줄러는 Wave 4**)

**포함하면 안 됨:**

- RTR·Redis (#4)
- 소셜 계정 다중 연결 (#6)
- cancel_reason VOC (wave 4)

---

### Wave 4 — 운영·확장 (`Wave 4 — 운영·확장`)

**목적:** **새 사용자 여정 없음.** 성능·보안·운영·확장·기술 debt.

**User Journey:** (없음 — 기존 기능의 품질·안정성 향상)

**Definition of Done:** Wave별로 **팀이 합의한 체크리스트** (출시 게이트). 예:

- [ ] RTR + Redis (#4)
- [ ] Apple S2S (#5)
- [ ] 프로필 S3 미러 (#9)
- [ ] 소셜 계정 연결 (#6)
- [ ] (선택) 모니터링·부하 테스트

**포함:** #4, #5, #6, #9, Google Calendar OAuth, cancel_reason, BR-NOTI-005 스케줄러

**포함하면 안 됨:**

- MVP In Scope **신규** 기능 — 넣으려면 **Wave 1~3 Backlog 개정** + `mvp.md` amend 필요

---

## 4. 새 기능 · 새 이슈 — Wave 배치 결정 트리

아래를 **위에서부터 순서대로** 질문합니다. **한 번 Yes면 그 Wave (또는 더 낮은 Wave)에 넣고 종료.**

```
┌─ Q1. 이 기능 없이 현재 진행 중인 Wave의 DoD를 달성할 수 있는가?
│     No  → 그 Wave Must (Backlog Must에 추가 후 Issue 생성)
│     Yes ↓
├─ Q2. MVP 완료 기준(mvp.md) — "일정 확정까지" — 을 막는가?
│     Yes → Wave 2 Must
│     No  ↓
├─ Q3. Wave 1 DoD — "로그인·준비·#22 Approved" — 을 막는가?
│     Yes → Wave 1 Must
│     No  ↓
├─ Q4. 없어도 MVP 핵심 데모(Wave 2 시나리오)는 가능한가?
│     No  → Wave 2 Nice (Must 아님 — Backlog Nice)
│     Yes ↓
├─ Q5. 없어도 친구 베타(Wave 3 — 알림·공유·달력)는 가능한가?
│     No  → Wave 3
│     Yes ↓
├─ Q6. Redis/RTR/캐시/리팩터/모니터링/S3/계정연결 계열인가?
│     Yes → Wave 4
│     No  ↓
└─ Q7. mvp.md Out of Scope인가?
      Yes → Wave 4 또는 **하지 않음** (Backlog 보류)
      No  → 팀 15분 리뷰 — development-wave.md amend
```

### 4.1 빠른 참조 표

| 질문 | Yes면 |
|------|--------|
| 현재 Wave DoD 불가? | **현재 Wave Must** |
| MVP 일정 확정 불가? | **Wave 2 Must** |
| 로그인·#22 확정 불가? | **Wave 1 Must** |
| MVP 데모는 되지만 있으면 좋음? | **해당 Wave Nice** |
| MVP·베타 데모 다 되는 기술 작업? | **Wave 4** |

### 4.2 논쟁이 나면

1. **User Journey 한 문장**으로 Wave 후보 적기
2. **Must vs Nice** 먼저 결정 (Must면 DoD 문구에 직접 연결)
3. 15분 안에 안 되면 **Wave 4 또는 보류** — MVP 속도 우선

---

## 5. GitHub 운영 방식

### 5.1 역할 분담

| GitHub 객체 | 역할 |
|-------------|------|
| **Milestone** `Wave N — {한글}` | Wave **컨테이너**. 해당 Wave에 **속하는 Issue만** 연결 |
| **Backlog Issue** (Wave당 1개) | Wave **계획 SSOT** — Must / Nice / Out 목록 · DoD 체크리스트 |
| **Issue** | Backlog에서 **파생**된 실행 단위. `#n` = 브랜치·PR·스펙 |
| **`wave:N` 라벨** | Milestone과 **1:1** (필터용). Issue 생성 **전** Backlog에서 확정 |
| **`kind:` / `area:`** | feature/bug/docs · api/domain/… |

**Notion (선택):** PRD·와이어프레임·회의록. **실행·DoD·Issue 번호는 GitHub만 SSOT.**

### 5.2 Wave 계획 → Issue 생성 (표준 절차)

**① Wave Backlog Issue 유지** (각 Milestone에 pinned 1개)

제목 예: `[Wave 2 Backlog] 핵심 MVP — Must / Nice / Out`

본문 템플릿:

```markdown
## DoD (Wave N)
- [ ] ...

## Must (Issue 없으면 DoD 불가)
- [ ] #13 추천·확정
- [ ] ...

## Nice (Must 다음)
- [ ] #26 last_activity_at
- [ ] ...

## Out (이 Wave에서 안 함)
- 알림 → Wave 3 #21

## 보류 (Wave 미정)
- ...
```

**② 새 기능 발생**

1. 결정 트리(§4) 실행 → **Wave + Must/Nice** 확정
2. **Backlog Issue에 한 줄 추가** (PR 또는 편집)
3. Must/Nice 확정 후 **Feature Issue 생성** — Milestone·`wave:N` **동시 지정**
4. DB·인증·3파일+ → `docs/specs/` (Approved 후 구현)

**③ Issue 생성 금지 패턴**

- Backlog에 없는데 “일단 Issue만”
- merge 후 `wave:` 라벨만 붙이기
- Must/Nice 구분 없이 Wave 2에 polish·Out 넣기

### 5.3 브랜치 · PR · 스펙 (기존 유지)

- 브랜치: `{type}/{issue-number}-{description}` — [`.github/CONTRIBUTING.md`](../../.github/CONTRIBUTING.md)
- PR: `Closes #n` · Spec 링크 · `./gradlew test`
- 스펙 헤더: `> wave: N` — **Backlog와 동일**해야 함

### 5.4 주기적 점검 (15분 · 주 1회)

| 체크 | 조치 |
|------|------|
| Open Issue 중 Backlog Must에 없는 것 | Backlog 추가 or Wave 이동 or close |
| Must 미완인데 Nice 착수 | Nice Issue `meta:blocked` 또는 담당 재배정 |
| Wave N DoD 전부 체크 | Milestone close · 다음 Wave Backlog kickoff |
| `waves.md` / 본 문서와 GitHub 불일치 | 문서 amend (본 문서 우선) |

### 5.5 스크립트

- `./scripts/github-sync-issues.sh` — **라벨·마일스톤 정렬** (Backlog 결정 **후** 실행)
- Wave 배치 **판단은 스크립트가 하지 않음** — 반드시 §4 + Backlog

### 5.6 현재 백로그 스냅샷 (2026-07-21)

| Wave | Backlog Issue | Must (DoD) | Nice | Out / other Wave |
|------|---------------|------------|------|------------------|
| **1** | **#29** | #22 | #24 ✓ | trip·추천 → Wave 2 |
| **2** | **#30** | #13 · **#37** · **#38** (#11·#12·#17 ✓) | #20, #26, #27✓ | **#19** Out · #21 → Wave 3 |
| **3** | **#31** | #21 | — | NOTI-005 → Wave 4 |
| **4** | **#32** | (팀 합의 시) | — | #4, #5, #6, #9 |

---

## 6. 문서 · 스펙 · Agent

| 문서 | 역할 |
|------|------|
| **본 문서** | Wave 운영·판단·GitHub 절차 SSOT |
| [`waves.md`](waves.md) | Wave 1~4 **한 페이지 요약** |
| [`mvp.md`](mvp.md) | MVP In/Out · Wave 2 DoD 원문 |
| [`docs/specs/`](../specs/) | 기능 계약 — `> wave: N`은 Backlog와 일치 |

Agent·개발자 **시작 체크:**

1. 현재 **활성 Wave**는? (Must 미완인 가장 낮은 N)
2. 내 Issue가 그 Wave Backlog **Must**에 있는가?
3. #22 Approved 전 — submit·RESPONDED·Hidden API 임의 구현 금지

---

## 부록 A — `waves.md`와의 관계

- **운영·판단·Backlog:** 본 문서
- **표·한 줄 정의:** `waves.md` (본 문서와 **불일치 시 본 문서 우선**, `waves.md` 갱신)

## 부록 B — 마일스톤 이름 (GitHub)

| wave | Milestone (GitHub) | 한글 |
|------|-------------------|------|
| 1 | Wave 1 — 준비 | 준비 |
| 2 | Wave 2 — 핵심 MVP | 핵심 MVP |
| 3 | Wave 3 — 출시 UX | 출시 UX |
| 4 | Wave 4 — 운영·확장 | 운영·확장 |

> GitHub Milestone 제목은 위 표와 동기화됨 (2026-07-20). wave 번호(`wave:N`)는 SSOT.

## 부록 C — Wave Backlog Issue (GitHub SSOT)

| Wave | Issue | 제목 |
|------|-------|------|
| 1 | **#29** | `[Wave 1 Backlog]` |
| 2 | **#30** | `[Wave 2 Backlog]` |
| 3 | **#31** | `[Wave 3 Backlog]` |
| 4 | **#32** | `[Wave 4 Backlog]` |

재생성 시 본문 템플릿: `docs/product/templates/wave-backlog-body.md` · §3 DoD/Must/Nice/Out 반영.

Wave Backlog Issue는 **코드 구현 Issue가 아님** — `kind: chore` + `area: docs` 권장.

**Pin (GitHub 한도 3개):** Wave 1~3 Backlog **#29 · #30 · #31** pinned. Wave 4 **#32**는 Milestone·본 문서 표로 SSOT.

**Nice 구분:** GitHub 라벨 없음 — Backlog **Nice** 목록 + Issue **비고** `분류: Wave N Nice` (#20 · #26 · #27). **Out**은 Backlog Out (#19). `Nice / Out` 혼용 금지.

---

*최종 갱신: 2026-07-20 · TripFit 백엔드 2~3명 · Spring Boot 단일 모듈*
