# TripFit 개발 물결 (wave) — 요약

> **운영·판단·GitHub Backlog 절차 SSOT:** [`development-wave.md`](development-wave.md)  
> **MVP 범위:** [`mvp.md`](mvp.md)  
> P0/P1, Phase 1/2, Foundation 등 **다른 축은 사용하지 않음.**
>
> 이 문서는 Wave 정의·DoD만 담는 **한 페이지 요약**이다. 이슈 번호·Open/Closed 상태·GitHub 운영 절차는 여기서 중복 서술하지 않는다 — [`development-wave.md`](development-wave.md)를 본다. 불일치 시 항상 그쪽이 우선.

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

## 이슈 번호·Open/Closed·GitHub 운영

**여기서 다루지 않음.** 항상 최신 상태인 [`development-wave.md` §1](development-wave.md#1-wave-14-정의)(Wave별 Must/Nice 정의·"포함 안 됨") · [§3.6](development-wave.md#36-현재-백로그-스냅샷-2026-08-03-wave-재정의-반영)(현재 스냅샷·Open/Closed) · [부록 B/C](development-wave.md#부록-b--마일스톤-이름-github)(Milestone·Backlog Issue 번호)를 본다 — 이 문서에 이슈 번호·상태를 다시 적지 않아 갱신 누락으로 두 문서가 어긋나는 걸 막는다.

> **용어:** 이슈 `## Must Have` ≠ Wave Must. `MVP: In scope` ≠ Wave Must. SSOT: [`development-wave.md`](development-wave.md) · harness `Wave Must / Nice / Out`.

## 스펙 메타

```markdown
> wave: N
> implements: BR-xxx
> deferred: BR-yyy → #이슈
```

스펙 `wave:`는 **Backlog에서 확정한 값**과 일치해야 함.

## 🚨 앱 배포·심사 체크리스트 (Release Gate, wave와 무관, 최우선)

**Wave 4("리팩토링·성능·런칭 후 UX")와 혼동 금지.** 이 항목들은 **스토어 제출·심사를 통과하기 위해 반드시 필요** — 없어도 되는 개선이 아니다. 판단 기준·현재 상태·이력은 [`development-wave.md` §5](development-wave.md#5-앱-배포심사-release-gate--wave와-무관)가 SSOT — 여기서 중복 서술하지 않는다.

## 리뷰 등급 (wave와 무관)

N1(필수) ~ N5(사소) — [`.github/CONTRIBUTING.md`](../../.github/CONTRIBUTING.md)
