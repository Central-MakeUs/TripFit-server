---
paths:
  - "**/domain/**"
  - "**/service/**"
  - "docs/specs/**"
---

# Figma Product Context

API 계약·CORS·인증·DTO는 **`client-platform.md`** 참고.

API·도메인 작업 전 **순서대로** 확인:

1. `docs/product/development-wave.md` — 활성 Wave·Must
2. `docs/product/waves.md` — wave 번호 요약
2. `docs/product/mvp.md` — MVP 범위인지
3. `docs/product/platform.md` — React 앱·스토어 목표, API/인증 전제
3. `docs/product/design/figma-wireframe-v1.md` — 화면 맵, 상태, 리소스 초안
4. `docs/product/glossary.md` — 용어 (여행방, 방장, 응답률 등)
5. `docs/product/business-rules/` — BR-* 규칙
6. Figma: [Wireframe ver1](https://www.figma.com/design/xEqgeZYrv8h4kQLr2Bkzh0/TripFit-%EA%B0%9C%EB%B0%9C-%EC%86%8C%EC%8A%A4%EC%9A%A9?node-id=0-1&t=5u84i1GhZruiId9v-1)

## 네이밍

| 계층 | 규칙 | 예 |
|------|------|-----|
| DB·JPA | snake_case, 단수 테이블 | `trip`, `trip_member` |
| Java | PascalCase / camelCase | `Trip`, `tripMember` |
| API path | kebab-case 또는 snake | `/api/v1/trip-rooms` |
| UI 한글 | glossary 기준 | "여행방" ≠ 임의 영문 |

## 상태 매핑 (여행방)

정책서 2-4 · 3-5 (2026-07-17):

| UI (정책서) | 코드 (`TripStatus`) | 비고 |
|-------------|---------------------|------|
| 조율 중 | `ONGOING` | join·수정·추천·일정 입력(역할별) |
| 일정 확정 | `CONFIRMED` | **신규 join 거부** |
| 종료 | `EXPIRED` | `end_range` 경과 (구 `TERMINATED`, #48 리네임). 기존 멤버 조회·삭제만 |

구 와이어프레임 “응답대기중”은 **UI 서브 상태**(응답률 미달 등) — 백엔드 enum 추가 없음.

`TripStatus` 변경 시 이 표 + glossary + 스펙 동시 업데이트.

## 구현 원칙

- 와이어프레임·PRD에 없는 정책 → `[미정]` 유지, 임의 비즈니스 로직 금지
- PRD vs mvp.md 충돌 → **PRD 우선**, mvp는 범위 컷
- BR-* 위반 코드 → 스펙에 예외 사유 없으면 구현하지 않음
