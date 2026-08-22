# TripFit 개발 물결 (wave) — 요약

> **운영·판단·GitHub Backlog 절차 SSOT:** [`development-wave.md`](development-wave.md)  
> **MVP 범위:** [`mvp.md`](mvp.md)  
> P0/P1, Phase 1/2, Foundation 등 **다른 축은 사용하지 않음.**

## Wave 한 줄 (도메인 축)

| wave | Milestone (GitHub) | 범위 |
|------|-------------------|------------------------|
| **1** | Wave 1 — 소셜 로그인 | 카카오·구글·애플 로그인, JWT, 온보딩 |
| **2** | Wave 2 — MVP 로직 | trip·recommend·member 등 서버 도메인 로직 (여행방→일정→추천→확정) |
| **3** | Wave 3 — 외부 API 연동 | Google Calendar, Firebase(FCM), Kakao 등 3rd-party 연동 |
| **4** | Wave 4 — 리팩토링·성능·런칭 후 UX | 백엔드 성능·인프라 개선 + 이번 MVP엔 없는 런칭 이후 추가 기능 |

**판단:** Issue 만들기 **전** [`development-wave.md` §2](development-wave.md#2-새-기능--새-이슈--wave-배치-결정-트리) 결정 트리.

## Wave DoD (한 줄)

| wave | 완료 조건 |
|------|-----------|
| **1** | 소셜 로그인·JWT·온보딩 Backlog Must 전부 Closed · `./gradlew test` |
| **2** | **MVP 완료 기준** — 방장이 추천 TOP 3로 일정 확정 |
| **3** | Google Calendar·FCM 알림·카카오 공유 Backlog Must 전부 Closed |
| **4** | 팀 합의 체크리스트 (출시 게이트 아님) |

## 이슈 → wave

| Wave | Must | Nice |
|------|------|------|
| **1** | #1 소셜로그인 · #3 JWT · #10 온보딩 · #57 AuthErrorCode | — |
| **2** | #11·#12·#13·#17·#22·#24·#37·#38·#39·#47·#48·#50·#53·#54·#60·#67 | #20·#26·#27 |
| **3** | #19 카카오공유 · #21 FCM 알림 · #44 Google Calendar · #56 · #78 | — |
| **4** | #4 Redis/RTR · #6 계정연결 · #9 S3미러 · #35 join 정원 hold · #52 Dev 인증 스텁 | — |

상세 근거·"포함 안 됨" 목록: [`development-wave.md` §1](development-wave.md#1-wave-14-정의).

## GitHub (요약)

| 객체 | 용도 |
|------|------|
| **Milestone** | Wave 컨테이너 |
| **Backlog Issue** | Wave당 1 — Must/Nice/Out SSOT ([`development-wave.md` §3](development-wave.md#3-github-운영-방식)) |
| **`wave:N`** | Milestone과 1:1 |
| **`kind:` / `area:`** | feature/bug/chore/docs · api/domain/deploy/docs/infra |
| **`release: blocking`** | Release Gate 전용 — Wave 라벨과 별개, Milestone 없음 (아래 절 참고) |

**Nice 구분:** 라벨 없음 — Backlog [#30](https://github.com/Central-MakeUs/TripFit-server/issues/30) Nice 섹션 + Issue **비고** `분류: Wave 2 Nice` (#20 · #26 · #27). **Out**은 Backlog Out만. `Nice / Out` 혼용 금지.

### Wave Backlog Issue (GitHub)

| wave | Issue | Milestone |
|------|-------|-----------|
| 1 | [#29](https://github.com/Central-MakeUs/TripFit-server/issues/29) | Wave 1 — 소셜 로그인 |
| 2 | [#30](https://github.com/Central-MakeUs/TripFit-server/issues/30) | Wave 2 — MVP 로직 |
| 3 | [#31](https://github.com/Central-MakeUs/TripFit-server/issues/31) | Wave 3 — 외부 API 연동 |
| 4 | [#32](https://github.com/Central-MakeUs/TripFit-server/issues/32) | Wave 4 — 리팩토링·성능·런칭 후 UX |

**활성 Wave (2026-08-03):** Wave 2 Must Open = **#13 · #50**. Wave 3: #78 Open, 나머지 Must Closed. Wave 1·Release Gate: 전부 Closed.

**Release Gate (Wave와 무관):** 현재 열려 있는 항목 없음(#5·#64 전부 Closed, OAuth 콘솔 설정값도 완료 확인) — 아래 절 참고.

> **용어:** 이슈 `## Must Have` ≠ Wave Must. `MVP: In scope` ≠ Wave Must. SSOT: [`development-wave.md`](development-wave.md) · harness `Wave Must / Nice / Out`.

## 스펙 메타

```markdown
> wave: N
> implements: BR-xxx
> deferred: BR-yyy → #이슈
```

스펙 `wave:`는 **Backlog에서 확정한 값**과 일치해야 함.

## 🚨 앱 배포·심사 체크리스트 (Release Gate, wave와 무관, 최우선)

**Wave 4("리팩토링·성능·런칭 후 UX")와 혼동 금지.** 이 항목들은 **스토어 제출·심사를 통과하기 위해 반드시 필요** — 없어도 되는 개선이 아니다. 상세·판단 기준: [`development-wave.md` §5](development-wave.md#5-앱-배포심사-release-gate--wave와-무관).

**현재 상태(2026-08-03):** 열려 있는 Release Gate 항목 없음. 과거 항목([#5](https://github.com/Central-MakeUs/TripFit-server/issues/5) Apple S2S webhook · OAuth 콘솔 설정값 채우기(완료 확인 — 추적 이슈 번호는 이후 다른 용도로 재사용돼 고정 링크 없음) · [#64](https://github.com/Central-MakeUs/TripFit-server/issues/64) 탈퇴 시 provider revoke) 전부 완료. 과거 메타 트래커였던 `#65`는 관측성 개선 스펙([`social-integration-structured-logging.md`](../specs/cross-cutting/social-integration-structured-logging.md))으로 재사용됐다.

**새 항목 발견 시:** 새 이슈를 만들어 여기 + `development-wave.md` §5에 동시 추가. 라벨 `release: blocking`, Milestone 없음(Wave 컨테이너 아님).

## 리뷰 등급 (wave와 무관)

N1(필수) ~ N5(사소) — [`.github/CONTRIBUTING.md`](../../.github/CONTRIBUTING.md)
