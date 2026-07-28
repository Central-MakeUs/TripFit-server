# TripFit 개발 물결 (wave) — 요약

> **운영·판단·GitHub Backlog 절차 SSOT:** [`development-wave.md`](development-wave.md)  
> **MVP 범위:** [`mvp.md`](mvp.md)  
> P0/P1, Phase 1/2, Foundation 등 **다른 축은 사용하지 않음.**

## Wave 한 줄 (User Journey)

| wave | Milestone (GitHub) | 사용자가 할 수 있는 것 |
|------|-------------------|------------------------|
| **1** | Wave 1 — 준비 ** | 로그인하고 TripFit을 **쓸 준비** 완료 · **#22 Approved** |
| **2** | Wave 2 — 핵심 MVP ** | 여행방 → 일정 수집 → 추천 → **일정 확정** ([`mvp.md`](mvp.md) DoD) |
| **3** | Wave 3 — 출시 UX ** | 알림·카카오 공유·그룹 달력 — **베타처럼** 사용 |
| **4** | Wave 4 — 운영·확장 ** | Redis·RTR·S3·계정연결·운영 — **새 MVP 기능 아님** |

**판단:** Issue 만들기 **전** [`development-wave.md` §4](development-wave.md#4-새-기능--새-이슈--wave-배치-결정-트리) 결정 트리.

## Wave DoD (한 줄)

| wave | 완료 조건 |
|------|-----------|
| **1** | login → JWT → 온보딩 · **#22 스펙 Approved** · `./gradlew test` |
| **2** | **MVP 완료 기준** — 방장이 추천 TOP 3로 일정 확정 |
| **3** | 알림·공유·그룹 달력으로 내부/친구 베타 가능 |
| **4** | 팀 합의 운영·확장 체크리스트 (Wave 1~3 이후) |

## MVP 기능 → wave

| 기능 | wave | Must? |
|------|------|-------|
| 소셜 로그인·JWT·온보딩 | 1 | Must |
| 일정 참여·submit·sparse (#22) | 1 | **Must (게이트)** |
| 여행방·참여·홈 D5 | 2 | Must |
| 일정 CRUD · calendar resolve | 2 | Must |
| 여행방 일정 조회 윈도우 (+2년) (#37) | 2 | **Must** |
| EXPIRED 일정 snapshot (#38) | 2 | **Must** |
| 추천 4모드·확정 | 2 | Must |
| `last_activity_at` hook · EXPIRED 스케줄러 (#26, #27) | 2 | **Nice** |
| 참여자 내보내기 (#20) | 2 | **Nice** |
| 알림(BR-NOTI-005 정기 리마인드 포함) (#21) · 카카오·링크 공유 (#19) · 그룹 달력 | 3 | Must |
| RTR·Redis · S3 · 계정 연결 | 4 | — |
| Google Calendar OAuth (#44) | 4 | Must |
| join 정원 hold (#35) | 4 | — |
| 여행방 삭제 시 VOC 사유 (unconfirm 사유와 별개) | 4 | — |

상세 Must/Nice/Out: [`development-wave.md` §3](development-wave.md#3-wave-14-재정의).

## GitHub (요약)

| 객체 | 용도 |
|------|------|
| **Milestone** | Wave 컨테이너 |
| **Backlog Issue** | Wave당 1 — Must/Nice/Out SSOT ([`development-wave.md` §5](development-wave.md#5-github-운영-방식)) |
| **`wave:N`** | Milestone과 1:1 |
| **`kind:` / `area:`** | feature/bug/docs · api/domain/… |
| **`release: blocking`** | Release Gate 전용 — Wave 라벨과 별개, Milestone 없음 (위 절 참고) |

**Nice 구분:** 라벨 없음 — Backlog [#30](https://github.com/Central-MakeUs/TripFit-server/issues/30) Nice 섹션 + Issue **비고** `분류: Wave 2 Nice` (#20 · #26 · #27). **Out**은 Backlog Out만. `Nice / Out` 혼용 금지. Wave Must 전부 Closed 전 Nice 착수 금지(팀 예외 시 Backlog에 명시).

### Wave Backlog Issue (GitHub)

| wave | Issue | Milestone |
|------|-------|-----------|
| 1 | [#29](https://github.com/Central-MakeUs/TripFit-server/issues/29) | Wave 1 — 준비 |
| 2 | [#30](https://github.com/Central-MakeUs/TripFit-server/issues/30) | Wave 2 — 핵심 MVP |
| 3 | [#31](https://github.com/Central-MakeUs/TripFit-server/issues/31) | Wave 3 — 출시 UX |
| 4 | [#32](https://github.com/Central-MakeUs/TripFit-server/issues/32) | Wave 4 — 운영·확장 |

**활성 Wave (2026-07-24):** Wave 2 Must Open = **#13 · #50**(#13에서 계산 로직 분리) (Closed Must: #11 · #12 · #17 · #37 · #38). Nice: #20 · #26✓ · #27✓. Wave 3 Must: **#21** · **#19**. Wave 4: **#44** Google Calendar.

**Release Gate (2026-07-28, Wave와 무관):** #5 · #62 · #64 — 아래 절 참고.

> **용어:** 이슈 `## Must Have` ≠ Wave Must. `MVP: In scope` ≠ Wave Must. SSOT: [`development-wave.md`](development-wave.md) · harness `Wave Must / Nice / Out`.

## 스펙 메타

```markdown
> wave: N
> implements: BR-xxx
> deferred: BR-yyy → #이슈
```

스펙 `wave:`는 **Backlog에서 확정한 값**과 일치해야 함.

## 🚨 앱 배포·심사 체크리스트 (Release Gate, wave와 무관, 최우선)

**Wave 4("운영·확장", 출시 이후 개선)와 혼동 금지.** 이 항목들은 **스토어 제출·심사를 통과하기 위해 반드시 필요** — 없어도 되는 개선이 아니다. 상세·판단 기준: [`development-wave.md` §7](development-wave.md#7-앱-배포심사-release-gate--wave와-무관).

| 이슈 | 내용 |
|------|------|
| [#5](https://github.com/Central-MakeUs/TripFit-server/issues/5) | Apple S2S Notification webhook — Sign in with Apple 지원 시 Apple 요구사항 |
| [#62](https://github.com/Central-MakeUs/TripFit-server/issues/62) | 스토어 제출 전 OAuth 콘솔 설정값 (리다이렉션 URI·자바스크립트 원본·App Store ID) |
| [#64](https://github.com/Central-MakeUs/TripFit-server/issues/64) | 탈퇴 시 소셜 provider revoke 호출 (Google/Kakao/Apple) — Apple은 App Store Review Guideline 5.1.1(v) |

**메타 트래커(SSOT):** [#65](https://github.com/Central-MakeUs/TripFit-server/issues/65) — 새 항목 발견 시 여기 + 위 표 + `development-wave.md` §7에 동시 추가. 라벨 `release: blocking`, Milestone 없음(Wave 컨테이너 아님).

## 리뷰 등급 (wave와 무관)

N1(필수) ~ N5(사소) — [`.github/CONTRIBUTING.md`](../../.github/CONTRIBUTING.md)
