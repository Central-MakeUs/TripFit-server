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
| TERMINATED 일정 snapshot (#38) | 2 | **Must** |
| 추천 4모드·확정 | 2 | Must |
| `last_activity_at` hook · TERMINATED 스케줄러 (#26, #27) | 2 | **Nice** |
| 참여자 내보내기 (#20) | 2 | **Nice** |
| join 전 미리보기 (이슈 미배정 · 구 #19) | 2 | **Out** |
| 알림 (#21) · 카카오 초대 공유 (#19) · 그룹 달력 | 3 | Must |
| RTR·Redis · Apple S2S · S3 · 계정 연결 | 4 | — |
| Google Calendar OAuth (#44) | 4 | Must |
| join 정원 hold (#35) | 4 | — |
| BR-NOTI-005 · cancel_reason | 4 | — |

상세 Must/Nice/Out: [`development-wave.md` §3](development-wave.md#3-wave-14-재정의).

## GitHub (요약)

| 객체 | 용도 |
|------|------|
| **Milestone** | Wave 컨테이너 |
| **Backlog Issue** | Wave당 1 — Must/Nice/Out SSOT ([`development-wave.md` §5](development-wave.md#5-github-운영-방식)) |
| **`wave:N`** | Milestone과 1:1 |
| **`kind:` / `area:`** | feature/bug/docs · api/domain/… |

**Nice 구분:** 라벨 없음 — Backlog [#30](https://github.com/Central-MakeUs/TripFit-server/issues/30) Nice 섹션 + Issue **비고** `분류: Wave 2 Nice` (#20 · #26 · #27). **Out**은 Backlog Out만 (join 전 미리보기 · 이슈 미배정). `Nice / Out` 혼용 금지. Wave Must 전부 Closed 전 Nice 착수 금지(팀 예외 시 Backlog에 명시).

### Wave Backlog Issue (GitHub)

| wave | Issue | Milestone |
|------|-------|-----------|
| 1 | [#29](https://github.com/Central-MakeUs/TripFit-server/issues/29) | Wave 1 — 준비 |
| 2 | [#30](https://github.com/Central-MakeUs/TripFit-server/issues/30) | Wave 2 — 핵심 MVP |
| 3 | [#31](https://github.com/Central-MakeUs/TripFit-server/issues/31) | Wave 3 — 출시 UX |
| 4 | [#32](https://github.com/Central-MakeUs/TripFit-server/issues/32) | Wave 4 — 운영·확장 |

**활성 Wave (2026-07-22):** Wave 2 Must Open = **#13** (Closed Must: #11 · #12 · #17 · #37 · #38). Nice: #20 · #26✓ · #27✓. Out: join 전 미리보기(이슈 미배정). Wave 3 Must: **#21** · **#19**. Wave 4: **#44** Google Calendar.

> **용어:** 이슈 `## Must Have` ≠ Wave Must. `MVP: In scope` ≠ Wave Must. SSOT: [`development-wave.md`](development-wave.md) · harness `Wave Must / Nice / Out`.

## 스펙 메타

```markdown
> wave: N
> implements: BR-xxx
> deferred: BR-yyy → #이슈
```

스펙 `wave:`는 **Backlog에서 확정한 값**과 일치해야 함.

## 리뷰 등급 (wave와 무관)

N1(필수) ~ N5(사소) — [`.github/CONTRIBUTING.md`](../../.github/CONTRIBUTING.md)
