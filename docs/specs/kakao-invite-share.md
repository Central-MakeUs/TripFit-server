# 카카오톡 · 링크 공유 (초대 / 확정 / 재촉)

> wave: 3  
> implements: BR-NOTI-008 (응답 재촉 수동 공유) · 공유 UX 공통  
> deferred: FCM/APNs → [#21](https://github.com/Central-MakeUs/TripFit-server/issues/21), 공유 스냅샷 단일 API (Nice · 미착수)  
> 상태: **Approved**  
> MVP: In scope (Wave 3)  
> Issue: [#19](https://github.com/Central-MakeUs/TripFit-server/issues/19)

## 목표

**방장**이 여행방 **링크를 카카오톡 공유·링크 복사**로 보내고,  
(1) 확정 전 초대 · (2) 확정 후 일정 공유 · (3) 응답 재촉 — 세 가지 맥락을 지원한다.

## 배경

- `#12` D3: `invite_code` 6자 · 공유 URL · `POST /trips/join`
- **카카오 SDK·템플릿·제목·설명·발송은 프론트.** 서버는 공유용 **데이터만** (문구 저장·카카오 대행 없음)
- 방 입장 게이트: **`RESPONDED` ∧ `canEnterRoom`** — JOINED(방장 confirm 전)는 상세·공유 불가

## 역할 분담 (확정)

| 계층 | 담당 | 하지 않음 |
|------|------|-----------|
| **프론트** | 제목·설명 · 템플릿 조립 · 카카오「공유하기」/「링크 복사」 · **공유 UI = 방장 ∧ RESPONDED** | JOINED 화면에서 공유 노출 |
| **백엔드** | `inviteCode`는 **RESPONDED 게이트된 상세**에서만 API 노출 · create(JOINED) 응답에 `inviteCode` **미포함** | 템플릿 DB 저장 · 카카오 대행 발송 · **공유 전용 신규 Must API 없음** |

### `TripMemberStatus` (현행)

| 상태 | 누가 | 의미 |
|------|------|------|
| **`JOINED`** | **방장만** (create 직후) | confirm 전 · **방 입장·공유 불가** |
| **`RESPONDED`** | 방장(confirm 후)·**멤버**(join 시 즉시) | 방 입장 가능 · **공유는 방장만** |

멤버는 중간 `JOINED` **없음**.

## 공통 UX (세 모드)

1. **S-1 + S-2:** 공유 UI = **방장** 이고 **`RESPONDED`(방 입장 후)** 만  
2. 제목·설명 입력 — 클라 로컬 · 서버 미저장  
3. 공유하기 / 링크 복사 — 클라 SDK  

## 공유 모드 · 서버 데이터 매핑 (확정 — 신규 API 없음)

### A. 확정 전 — 방 초대 (`ONGOING` · 방장 RESPONDED)

| FE 필요 | API | 필드 |
|---------|-----|------|
| 초대 코드·링크 | `GET /api/v1/trips/{tripId}` | `inviteCode` → `https://tripfit.online/room/{inviteCode}` |
| (선택) 방 이름 등 | 동일 | `name`, `startRange`, `endRange` … |

### B. 확정 후 — 확정 일정 공유 (`CONFIRMED`)

| FE 필요 | API | 필드 |
|---------|-----|------|
| 확정 기간 | `GET /api/v1/trips/{tripId}` | `confirmedStartDate`, `confirmedEndDate` |
| 링크 | 동일 | `inviteCode` (동일 D3 URL — **전용 path 없음**) |

### C. 응답 재촉 — **C-1 미join**

| 변수 | 의미 | API | 필드 |
|------|------|-----|------|
| `n` | 미join 인원 | `GET /trips/{id}` | `memberCount - joinedMemberCount` |
| `joinedNames` | 참여 멤버 표시명 | `GET /trips/{id}/members` | `members[].displayName` |
| `shareUrl` | 초대 링크 | `GET /trips/{id}` | `inviteCode` + D3 URL |

FE 참고용 템플릿 문구는 서버 미저장 · FE가 자유롭게 조립.

**호출 수:** A/B = 상세 1회. C = 상세 + members (최대 2회). 단일 스냅샷 API는 Nice(후속)·이번 Out.

## 요구사항

### Must Have

- [x] 모드 A/B/C · S-1 · S-2 · C-1 · FE 템플릿 **Approved**
- [x] create 응답에 `inviteCode` 없음 · 상세(RESPONDED)에만 있음
- [x] C = 상세 + members로 충분 · **신규 share API 없음**
- [x] D3 URL · 6자 `inviteCode` 유지
- [x] JOINED/RESPONDED·공유 계약 Swagger·glossary·trip-room-api 필독 절
- [x] `./gradlew test`

### Nice to Have (이번 Out)

- [ ] `GET .../share-preview` 등 단일 스냅샷 API (C 왕복 절감)
- [ ] OS 기본 공유 시트 fallback

### Out of Scope

- FCM → `#21`
- 템플릿 DB · 카카오 서버 대행
- JOINED 단계 공유
- join 전 미리보기 API

## 비즈니스 규칙

| ID | 내용 |
|----|------|
| D3 | 공유 URL `https://tripfit.online/room/{inviteCode}` · 코드 6자 Crockford |
| D8 | 인원·기간 cap 시 공유 UI 비노출 + join 409 |
| **C-1** | `n` = `memberCount - joinedMemberCount` · 이름 = `members[].displayName` |
| **S-1** | 공유 UI = **방장만** |
| **S-2** | 공유 = **RESPONDED 이후만** · create에 `inviteCode` 미노출 |
| **S-3** | 공유 데이터 = **기존 상세·members만** (Must 신규 API 없음) |

## `[미정]`

없음 (2026-07-22 종결).

| 과거 후보 | 결정 |
|-----------|------|
| URL 호스트 / 코드 형식 | **D3 유지** — `tripfit.online` + 6자 |
| B 전용 path · 확정 필드 | **전용 path 없음** · 상세 `confirmedStartDate`/`confirmedEndDate` |
| 카카오 템플릿 ID·OG | **FE / 카카오 콘솔** (서버 비관여) |

## 완료 기준

- [x] 스펙 **Approved**
- [x] 서버: create `inviteCode` 미노출 · 문서/Swagger 정합
- [x] `#19` · `#2` · README 동기화
- [x] `./gradlew test`

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-22 | **Approved** — S-3(기존 API만) · `[미정]` 종결 · A/B/C 필드 매핑 |
| 2026-07-22 | **S-2** · create에서 inviteCode 제거 |
| 2026-07-22 | **S-1** · C-1 · FE 템플릿 · JOINED=방장 전용 |
| 2026-07-22 | Draft — A/B/C |
