# TripFit — 릴리즈 고유 사실

이 저장소에만 해당하는 **릴리즈·배포·도메인 용어** 사실을 모았다. 스토어 심사 요건(Release Gate)·Milestone·여행 일정 용어를 다룰 때 연다. 하네스를 다른 프로젝트로 옮길 때 **가져가지 않는 파일**이다.

일반 원칙(우선순위 라벨·`[미정]` 처리)은 `core-scope.md`가 담당한다.

## 🚨 Release Gate — 앱 배포·심사 필수 체크리스트

**Milestone `출시 이후`(런칭 후 개선)와 혼동 금지.** 스토어 제출·심사를 **통과하기 위해 반드시 필요**한 항목 — 없어도 되는 개선이 아니다. 2026-07-28 도입 계기: `#5`(Apple S2S webhook)가 심사 요건이었는데도 한때 잘못 분류돼 있었음. 상세: [`release-milestones.md` §4](../../docs/product/release-milestones.md#4-앱-배포심사-release-gate--milestonepriority와-무관).

**판단 기준:** "이게 없으면 스토어 심사를 통과 못 하는가?" → Yes면 Release Gate(`release: blocking` 라벨, Milestone은 다른 이슈와 동일하게 지정), No면 `priority:`만으로 충분.

**현재 상태·과거 항목 이력·이슈 번호 재사용 관행:** [`release-milestones.md` §4](../../docs/product/release-milestones.md#4-앱-배포심사-release-gate--milestonepriority와-무관)가 SSOT — 여기서 중복 서술하지 않는다. **새 Release Gate 항목 발견 시 새 이슈를 만들어**(생성 전 `core-workflow.md` "새 이슈·새 브랜치·새 PR 생성은 항상 먼저 확인" 절 적용) 그 문서 §4에 등록.

**에이전트 행동:** 이 파일은 always-load이므로 매 세션 로드된다. 인증·소셜로그인·배포·탈퇴 관련 파일을 다루거나 릴리즈 상태를 논의할 때, `release-milestones.md` §4에 열린 Release Gate 항목이 있으면 **먼저 묻지 않아도 짧게 리마인드**한다.

**금지:** Release Gate 항목을 `출시 이후`로 분류

## 릴리즈 축 — 겹치지 않는 3개 질문

이슈를 분류할 때 아래 3개만 묻는다. 축이 바뀐 경위는 [`release-milestones.md`](../../docs/product/release-milestones.md)에 있다.

| 질문 | 담당 |
|---|---|
| MVP 범위인가? | `mvp.md` In/Out |
| 출시 전/후? | Milestone `MVP 출시` / `출시 이후` |
| 기능·버그(must) vs 성능·구조정리(could)? | `core-scope.md` ⛔ (단정 금지) |

## 일정·기간 용어 — TripFit 도메인 (혼동 금지 — glossary SSOT)

아래는 소프트웨어 릴리즈가 아니라 **여행 일정** 용어다. 파일명의 "release"와 무관하며, 이 저장소 고유 사실이라는 이유로 여기 함께 뒀다.

| 용어 | 의미 | 비고 |
|------|------|------|
| **희망 기간** | `trip.startRange`~`endRange` | 추천·조율 탐색 범위 |
| **조회 윈도우** | 여행방 일정 calendar 조회 가능 구간 (#37) | 희망 기간과 **별 축** · C1=`today~+2y` · C2/C3=`startRange~endRange` |
| **C1 윈도우 (#37)** | 본인 `users/schedule/calendar` 조회·수정 가능 구간 | **today ~ max(today+2년−1, 참여 중 ONGOING 여행 endRange 최댓값)**(#53 R4) · 구 A1(730일 길이) 대체 |

상세: [`docs/product/release-milestones.md`](../../docs/product/release-milestones.md) · [`docs/product/glossary.md`](../../docs/product/glossary.md)

## 도메인·배포 (확정 — 재질문 금지)

| 도메인 | 호스팅 | 이 repo |
|--------|--------|---------|
| `tripfit.online` | Vercel (프론트) | **없음** — `FRONTEND_IMAGE`·frontend 컨테이너 금지 |
| `api.tripfit.online` | EC2 Nginx + Spring Boot | `deploy/app/`, `deploy/nginx/` |

API: `https://api.tripfit.online` · SSOT: `docs/decisions/002-domain-split-vercel-api.md`, `deploy/README.md`
