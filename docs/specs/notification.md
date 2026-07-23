# 알림 (Notification) — FCM 푸시 · 알림 설정 · 알림센터

> 상태: Draft
> MVP: In scope (Wave 3 Must — Wave Backlog `#31`)
> 관련 BR: BR-NOTI-001~005, 009 · BR-NOTI-008(카카오 공유, `#19` 소관) · BR-USER-005
> wave: 3
> implements: BR-NOTI-001, BR-NOTI-002, BR-NOTI-003, BR-NOTI-004, BR-NOTI-005, BR-NOTI-009, BR-USER-005
> deferred: BR-NOTI-008(카카오 공유) → `#19`(Approved)
> GitHub: **#21**
> 선행: `#12`(여행방 CRUD, Implemented) · `#13`(추천·확정·취소, **Draft** — BR-NOTI-004는 `confirmSchedule` 기존 구현에 훅 가능, **BR-NOTI-009는 `#13`의 취소 API 구현 후에만 실제 발송 가능**) · 참여 완료 정의(`#22`/`#39`, 확정됨)
> amend 대상(다른 스펙): [`user-my-page.md`](user-my-page.md) — `PATCH /users/my-page`에 `notificationEnabled` 추가 + partial update 전환 (D8, 별도 승인·changelog 완료)

## 목표

여행방 이벤트(참여·전원 제출·정보 변경·확정·확정 취소)와 정기 리마인드를 FCM 푸시로 알리고, 사용자가 알림 on/off를 설정하며, 앱 내 알림센터에서 최근 알림을 확인할 수 있게 한다.

## 배경

- 기획자 전달 알림 명세 표(2026-07-23)를 [`docs/product/business-rules/notification.md`](../product/business-rules/notification.md)에 반영 완료
- 이슈 `#21` Must Have: FCM 연동, 알림 이력 테이블, NOTI-001~005·009 트리거, 알림 설정, 알림센터
- [`client-platform.md`](../../.claude/rules/client-platform.md): "푸시(FCM/APNs)는 wave 3 — **스펙 없으면** 엔티티·발송 API 추가 금지" — 본 스펙이 그 게이트 역할

### 확정 사항 (2026-07-23, 사용자 승인) — 전부 임의 결정 아님

| # | 쟁점 | 결정 |
|---|------|------|
| **D1** | `BR-NOTI-005`(정기 리마인드) 포함 여부 | **포함.** wave 4 → wave 3 재분류 (`#21`/`#31`/`#32`·`development-wave.md`·`waves.md`·`mvp.md`·`flows/README.md` amend 완료) |
| **D2** | `BR-USER-005`(알림 on/off) 구현 여부 | **포함.** `users.notification_enabled`(default `true`) |
| **D3** | NOTI-003/004/009 발송 대상 방장 제외 여부 | **방장 제외** |
| **D4** | FCM 단일 채널 vs APNs 병행 | **FCM 단일 채널** |
| **D5** | 알림 이력 테이블 범위 | **알림센터 조회 API까지 포함** |
| **D6** | NOTI-005 발송 방식 — 토픽 vs DB조회 배치 | **DB 조회(`notification_enabled=true`) + 배치(500개) 멀티캐스트.** 토픽 방식은 클라이언트가 구독을 관리해야 해 서버가 게이트를 강제할 수 없어 기각 |
| **D7** | 동일 기기 재로그인 시 FCM 토큰 재등록 처리 | **`user_id` 재할당.** 토큰이 이미 있으면 소유자를 새 유저로 갱신 — 이전 계정에 오발송 방지 |
| **D8** | 알림 설정 API 위치 | **기존 `PATCH /users/my-page`에 `notificationEnabled` 필드 추가.** `/users/me/...` 새 경로 대신 기존 컨벤션 재사용 — 단, 한 필드만 보내는 호출을 지원해야 하므로 **`user-my-page.md`의 firstName/lastName도 optional로 전환**(partial update). 상세: [`user-my-page.md`](user-my-page.md) 변경 이력 |
| **D9** | 알림센터 목록 범위 | **최근 7일 윈도우** — 와이어프레임이 "오늘/어제/최근 7일" 3그룹으로 구성돼 있어, API도 `sent_at >= now-7d`만 반환(페이지네이션 불필요). DB 이력 자체는 그대로 보존 |
| **D10** | NOTI-001/002(수신자=방장)에도 게이트 적용 여부 | **적용.** 예외 없이 전체 이벤트가 `notification_enabled`를 따름 |
| **D11** | NOTI-002 "마지막 참여자" 판정 기준 | **여행방 정원(BR-TRIP-001, 1~10) 도달 순간.** 멤버는 join 즉시 RESPONDED라 "제출 대기" 상태가 따로 없어, 정원 도달만이 유의미한 판정 시점 |
| **D12** | no-op patchTrip도 NOTI-003 발송할지 | **미발송.** 실제 값이 하나 이상 바뀐 경우에만 발송 |

## 요구사항

### Must Have

- [ ] `UserDeviceToken` 엔티티 + Repository — 유저별 다중 기기 토큰(`ANDROID`/`IOS`/`WEB`), `token` UNIQUE
- [ ] 토큰 등록 시 **기존 토큰이 다른 `user_id` 소유면 재할당**(D7) — 단순 upsert 아님, 소유자 갱신 로직 필요
- [ ] `NotificationHistory` 엔티티 + Repository — 발송 이력 + 읽음 상태(D5), `sent_at` 기준 최근 7일 조회 메서드
- [ ] `FirebaseConfig` — 서비스 계정 키 env 로드, `FirebaseMessaging` Bean (D4)
- [ ] `FcmService` — 단일/멀티캐스트 발송 + 무효 토큰(`UNREGISTERED`/`INVALID_ARGUMENT`) 자동 삭제, 멀티캐스트 500건 배치 분할(D6)
- [ ] `POST /api/v1/notifications/device-tokens` — 디바이스 토큰 등록/갱신(JWT)
- [ ] `DELETE /api/v1/notifications/device-tokens` — 로그아웃 시 토큰 해제(JWT)
- [ ] `GET /api/v1/notifications` — 알림센터 목록(JWT, 최근 7일, 최신순) (D5·D9)
- [ ] `PATCH /api/v1/notifications/{id}/read` — 읽음 처리 (D5)
- [ ] **`user-my-page.md` amend 반영** — `PATCH /users/my-page`에 `notificationEnabled` 추가 + partial update 전환은 **이 스펙이 아니라 `user-my-page.md`의 Must Have**로 구현 (D8, 중복 정의 금지)
- [ ] 이벤트 발행 + `@Async` `@TransactionalEventListener(phase = AFTER_COMMIT)` 리스너로 트랜잭션 커밋 후 발송
- [ ] BR-NOTI-001 — `TripCommandService.joinTrip` 커밋 후 방장(`notification_enabled=true`)에게 발송
- [ ] BR-NOTI-002 — 같은 join 흐름에서 **정원 도달**(D11) 판정 후 방장(게이트 적용)에게 발송
- [ ] BR-NOTI-003 — `TripCommandService.patchTrip` 커밋 후, **실제 값이 바뀐 경우만**(D12) 참여자(방장 제외, 게이트 적용)에게 발송
- [ ] BR-NOTI-004 — `TripRecommendationService.confirmSchedule` 커밋 후 참여자(방장 제외, 게이트 적용)에게 발송
- [ ] BR-NOTI-009 — **`#13`의 취소 API 구현 이후** 커밋 시 참여자(방장 제외, 게이트 적용)에게 발송 (선행 미완료 시 이벤트 발행 지점만 준비)
- [ ] BR-NOTI-005 — `@Scheduled` cron으로 매월 1일·15일 09:00(KST), `notification_enabled=true` ∧ `deleted_at IS NULL` 사용자 조회 → 배치 멀티캐스트(D6)
- [ ] `./gradlew test`

### Out of Scope (이번 스펙)

- `BR-NOTI-008` 카카오 공유 문구 — `#19`/`kakao-invite-share.md` 소관
- 알림 실패 재시도·추가 "필수 알림" 예외 — `notification.md` `[미정]`
- `BR-TRIP-010`(추천 결과 초기화) 변경 시 NOTI-003 발송 여부·타이밍 — `notification.md` `[미정]`
- 7일보다 오래된 알림의 별도 아카이브·전체 이력 조회 UI

## API

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/api/v1/notifications/device-tokens` | JWT | 디바이스 토큰 등록/갱신 (기존 토큰이면 소유자·`deviceType`·`updatedAt` 갱신) |
| DELETE | `/api/v1/notifications/device-tokens` | JWT | 로그아웃 시 본인 토큰 해제 |
| GET | `/api/v1/notifications` | JWT | 본인 알림 이력, 최근 7일, 최신순 |
| PATCH | `/api/v1/notifications/{id}/read` | JWT | 알림 읽음 처리 |

> 알림 on/off는 별도 엔드포인트 없음 — [`user-my-page.md`](user-my-page.md)의 `PATCH /users/my-page` 참고 (D8).

### 에러

| 상황 | HTTP | code |
|------|------|------|
| 토큰 값 누락 | 400 | `NOTIFICATION_TOKEN_REQUIRED` (신규) |
| 삭제 대상 토큰이 없음/본인 것이 아님 | 404 | `NOTIFICATION_TOKEN_NOT_FOUND` (신규) |
| 존재하지 않거나 본인 것이 아닌 알림 읽음 처리 | 404 | `NOTIFICATION_NOT_FOUND` (신규) |

## 데이터 모델

```
users (컬럼 추가 — user-my-page.md에서 구현)
  notification_enabled  boolean default true   -- BR-USER-005 (D2)

user_device_token
  id            uuid PK
  user_id       uuid FK -> users.id      -- 재로그인 시 재할당 대상(D7)
  token         varchar(512) UK
  device_type   enum(ANDROID, IOS, WEB)
  created_at    datetime
  updated_at    datetime

notification_history
  id            uuid PK
  user_id       uuid FK -> users.id      -- 수신자
  trip_id       uuid FK -> trip.id null  -- 여행방 무관 알림(005)은 null
  type          enum(JOIN_COMPLETED, ALL_MEMBERS_SUBMITTED, TRIP_INFO_CHANGED,
                      TRIP_CONFIRMED, TRIP_CONFIRM_CANCELED, SCHEDULE_REMINDER)
  title         varchar
  body          varchar
  landing_type  enum(TRAVEL_ROOM_DETAIL, SCHEDULE_MANAGEMENT)
  is_read       boolean default false
  read_at       datetime null
  sent_at       datetime
  created_at    datetime
```

- 승인 후 `docs/architecture/erd.md` §2(Mermaid)·§5(Out of Scope 표에서 제거)·§8 동시 갱신
- PK/FK UUID v4 (`spring-boot-java.md` Entity Conventions)
- `GET /api/v1/notifications` 조회는 `sent_at >= now() - 7일`로 제한(D9) — 인덱스: `(user_id, sent_at)`

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-NOTI-001 | 참여자 join 완료 시 방장(게이트 적용)에게 발송 | `TripCommandService.joinTrip` → `TripJoinCompletedEvent` |
| BR-NOTI-002 | 여행방 정원 도달 시 방장(게이트 적용)에게 발송 | 위와 동일 지점, `joinedMemberCount == capacity` 판정 후 `AllMembersSubmittedEvent` |
| BR-NOTI-003 | 방장이 여행방을 수정해 실제 값이 바뀐 경우 참여자(방장 제외, 게이트 적용)에게 발송 | `TripCommandService.patchTrip` → diff 있을 때만 `TripInfoChangedEvent` |
| BR-NOTI-004 | 방장이 일정 확정 시 참여자(방장 제외, 게이트 적용)에게 발송 | `TripRecommendationService.confirmSchedule` → `TripConfirmedEvent` |
| BR-NOTI-009 | 방장이 확정 취소 시 참여자(방장 제외, 게이트 적용)에게 발송 | `#13` 취소 API(미구현) → `TripConfirmCanceledEvent` |
| BR-NOTI-005 | 매월 1·15일 09:00(KST) 게이트 통과 전체 사용자에게 발송 | `ScheduleReminderBatch`(`@Scheduled`) → DB 조회 + 배치 멀티캐스트 |
| BR-USER-005 | 마이페이지 알림 on/off, 전 이벤트에 예외 없이 적용 | `User.notificationEnabled` · `user-my-page.md`(`UserMyPageService`) |

## 패키지 구조 (적용 컨벤션 — `spring-boot-java.md` 그대로 적용)

```
com.tripfit.tripfit.notification
├── controller/     DeviceTokenController, NotificationController
├── dto/            DeviceTokenRegisterRequest, NotificationResponse 등
├── service/        DeviceTokenService, FcmService, NotificationQueryService, NotificationEventListener
├── event/          TripJoinCompletedEvent, AllMembersSubmittedEvent, TripInfoChangedEvent,
│                   TripConfirmedEvent, TripConfirmCanceledEvent, ScheduleReminderEvent
├── domain/         UserDeviceToken, NotificationHistory, DeviceType, NotificationType, LandingType
├── repository/     UserDeviceTokenRepository, NotificationHistoryRepository
├── scheduler/      ScheduleReminderBatch
├── config/         FirebaseConfig
└── exception/      NotificationErrorCode
```

- `User.notificationEnabled` 필드·API는 `user/` 도메인([`user-my-page.md`](user-my-page.md))에 둔다 — 알림 발송 로직과 소유 분리
- Java 21 / Spring Boot 4.1.0 (프로젝트 실제 스택)
- ErrorCode: `NotificationErrorCode implements ErrorCode`, 상수당 `@Schema` 필수
- Service/Listener/Scheduler/Config public 메서드: 역할 `//` 한 줄 필수 (harness Comments 절)

## 검증 시나리오

### 정상

- [ ] 참여자 join → 방장 기기(단일/다중)에 NOTI-001 발송, `notification_history` 1건 INSERT
- [ ] 정원 도달하는 join → NOTI-001 + NOTI-002 둘 다 발송
- [ ] 정원 미도달 join → NOTI-001만 발송
- [ ] 방장 patchTrip(값 변경 있음) → 참여자 전원(방장 제외, 게이트 통과자만)에게 멀티캐스트 NOTI-003
- [ ] 방장 patchTrip(값 변경 없음, no-op) → 미발송
- [ ] 방장 confirmSchedule → 참여자 전원(방장 제외)에게 멀티캐스트 NOTI-004
- [ ] 매월 1일·15일 09:00 → `notification_enabled=true`·`deleted_at IS NULL` 사용자에게만 NOTI-005, 500건 초과 시 배치 분할 확인
- [ ] `notificationEnabled=false` 설정 후 → NOTI-001~005·009 전부 미발송 (예외 없음)
- [ ] 동일 기기, 계정 A 로그아웃 후 계정 B 로그인 → 같은 토큰의 `user_id`가 B로 재할당, 이후 알림은 B에게만
- [ ] `GET /api/v1/notifications` → 최근 7일 내 알림만 최신순 반환, `PATCH .../read` 후 `is_read=true`

### 엣지 · 실패

- [ ] 무효 토큰(`UNREGISTERED`)으로 단일 발송 실패 → `user_device_token`에서 즉시 삭제
- [ ] 멀티캐스트 중 일부 토큰 무효 → 해당 토큰만 일괄 삭제, 나머지는 정상 발송
- [ ] 수신 대상에 등록된 토큰이 0개 → 발송 skip(에러 아님)
- [ ] 트랜잭션 롤백 시 알림 미발송 (`AFTER_COMMIT` 보장 확인)
- [ ] `#13` 취소 API 미구현 상태에서 NOTI-009는 이벤트 발행 코드만 존재, 실제 트리거 없음 확인
- [ ] 8일 전 알림은 `GET /api/v1/notifications` 결과에서 제외

## 완료 기준

- [ ] Must Have 전부 (본 스펙 + `user-my-page.md` amend)
- [ ] `./gradlew test` 통과
- [ ] OpenAPI 반영 (device-tokens·notifications API, `user-my-page.md`의 my-page PATCH 변경분)
- [ ] `docs/architecture/erd.md` §2·§5·§8 갱신
- [ ] `docs/specs/README.md` wave 3 표 갱신
- [ ] `.env.example`에 FCM 관련 키 추가

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| D1~D12 | 확정 | 위 "확정 사항" 표 — 2026-07-23 사용자 승인 |
| BR-NOTI-009 트리거 | 확정(선행 의존) | `#13`(Draft, 취소 API 미구현) 완료 전까지 이벤트 발행 지점만 준비 |
| 알림 실패 재시도·추가 "필수 알림" 예외 | `[미정]` | `notification.md` 동일 항목 |
| `BR-TRIP-010` 변경 시 NOTI-003 타이밍 | `[미정]` | `notification.md` 동일 항목 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-23 | D6~D12 추가 확정 (사용자 감수 확인 요청 후) — NOTI-005 배치 발송, 토큰 재할당, 알림 설정 API를 `user-my-page.md`로 이관, 알림센터 7일 윈도우, 001/002 게이트 적용, 정원 기준 판정, no-op 스킵 |
| 2026-07-23 | D1~D5 확정 (사용자 승인) — BR-NOTI-005 wave 3 편입, BR-USER-005 구현 포함, 방장 제외, FCM 단일, 알림센터 API 포함 |
| 2026-07-23 | 초안 — 기획자 알림 명세 표 + 사용자 제공 FCM 아키텍처 요구사항 기반 |
