# TripFit MVP

> NotebookLM 기획 자료 정리본.

## MVP 목표

- **검증 가설:** 그룹 달력 시각화와 추천 로직이 사용자의 여행 일정 확정 시간을 단축하고 의사결정 부담을 줄일 것이다
- **성공 지표:** 평균 일정 확정 시간 단축, 추천 조회 후 확정 전환율, 여행 일정 확정률

## In Scope (MVP 포함)

- [ ] 소셜 로그인 및 회원가입 (참여자 포함 — 비회원 없음)
- [ ] 여행방 생성 (이름, 기간, 일수, 인원, **여행지**)
- [ ] 초대 링크 생성 및 카카오톡 공유
- [ ] 참여자 일정 입력 (오전/오후/저녁 단위, 미정 상태 포함)
- [ ] 개인별 근무 정보 및 연차 조건 설정
- [ ] 그룹 달력 내 응답 현황 시각화
- [ ] 홈 여행방 — 진행 중/전체 2뷰 · Pin (`trip_member.is_pinned` + `pinned_at`) · `trip.last_activity_at` (D5)
- [ ] 추천 모드 4종 및 가중치 기반 TOP 3 후보 산출 (기본 / 모두 참석 / 휴가 아끼기 / 확실하게 가기 — wave 2)
- [ ] 방장의 최종 일정 확정 및 취소 기능

## Out of Scope (v2 이후)

- 여행지 가격 요소 반영 추천
- 공동 방장 및 참여자 권한 세부 관리
- 사진 인식을 통한 일정 자동 입력
- 지난 데이터를 활용한 일정 자동 채우기
- 숙소/교통 제휴 예약 서비스
- 비회원·게스트 참여
- 여행방 취소·삭제 VOC 사유 저장 (`cancel_reason` — wave 4)

## 개발 물결 (wave)

기능 우선순위·출시 단계는 [`waves.md`](waves.md) **wave 1~4** 만 사용한다. 기능→wave 매핑 표는 [`waves.md`](waves.md)가 SSOT — 여기서 중복 정의하지 않는다.  
**운영·판단·Backlog:** [`development-wave.md`](development-wave.md) · 요약표: [`waves.md`](waves.md)

## MVP 완료 기준

방장이 생성한 여행방에서 참여자들의 일정이 모두 수집되고, 방장이 추천 모드 중 하나를 선택해 시스템이 산출한 TOP 3 후보를 바탕으로 최종 날짜가 확정되는 시점.

## 의존성 / 선행 조건

- **#22 확정 완료 (2026-07-21, #39 amend까지 반영)** — 일정 참여·submit·sparse·온보딩 skip SSOT 확정됨. wave 2 일정 응답·추천(#13)은 이 SSOT 위에서 진행
- 외부 캘린더(구글 등) API 연동 — Google Calendar OAuth(#44)로 wave 4 진행 중, 상세: [`google-calendar-oauth.md`](../specs/google-calendar-oauth.md)
- 가중치 기반 추천 엔진(4모드) 페널티 점수 산출 로직 검증 — wave 2, [`trip-recommendation.md`](../specs/trip-recommendation.md) (Draft)

## 기획 메모 (NotebookLM)

- **근거 약한 In Scope:** 외부 캘린더 연동 — Pain Point 해결용 `[제안]`
- **확정 (2026-07-08):** 참여자 소셜 로그인 필수, 추천 4모드 wave 2, `trip.destination` MVP In, ~~BR-NOTI-005 wave 4~~ (2026-07-23 번복)
- **확정 (2026-07-23):** BR-NOTI-005 wave 4 → **wave 3** 재분류, 발송 주기 매월 1·15일 09:00, 채널 FCM 단일 (`docs/specs/notification.md` D1·D4)
