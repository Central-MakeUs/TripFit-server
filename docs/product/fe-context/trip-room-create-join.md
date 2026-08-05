# 여행방 생성·입장 — 구현 규칙

TripFit 프론트엔드 저장소에서 방 생성/참여 관련 화면·라우팅을 구현·수정할 때 아래 규칙을 따르라. 여기 없는 세부 계약은 추측하지 말고 사용자에게 확인하라.

모든 API 응답은 `{ "data": {...}, "message": "...", "code": "..." }` envelope로 온다. 아래 예시는 `data` 안쪽만 표기했다.

## 규칙 1 — 방장과 멤버를 같은 라우팅 로직으로 다루지 마라

방장과 멤버는 서로 다른 경로로 `ACTIVE`(방 입장 가능 상태)에 도달한다고 가정하고 구현하라.

- **방장**: `POST /trips` 호출 즉시 멤버 row가 생기지만 상태는 `SCHEDULE_PENDING`다. 별도로 `POST .../activate`를 호출해야 `ACTIVE`가 된다. **2단계**로 구현하라.
- **멤버**: 일정을 다 입력한 뒤 `POST /trips/join`을 호출하는 순간 바로 `ACTIVE`로 등록된다. 멤버에게는 `SCHEDULE_PENDING`라는 중간 상태가 존재한다고 가정하지 마라. **1단계**로 구현하라.

이 차이 때문에 홈 화면 방 카드를 탭하는 핸들러는 반드시 `myMemberStatus`를 먼저 확인하고 분기하도록 짜라 — 방장이든 멤버든 똑같이 상세 페이지로 보내는 코드를 작성하지 마라.

## 규칙 2 — 방장 플로우를 다음 순서·조건으로 구현하라

```
STEP 1. POST /api/v1/trips
  → trip 생성(status=ONGOING) + 방장 멤버 row 즉시 INSERT (role=OWNER, status=SCHEDULE_PENDING)
  → invite_code는 DB에 발급되지만 이 응답에는 포함되지 않는다고 가정하라 (파싱하려 하지 마라)
  → 응답의 myMemberStatus=SCHEDULE_PENDING을 보고 다음 화면(정기 일정)으로 강제 이동시켜라

STEP 2. 정기 일정 입력 → 개별 일정 입력
  → User 전역 데이터(regular/personal) CRUD를 호출하라
  → 사용자가 이미 다른 방에서 일정을 입력해뒀어도 이 확인 플로우는 항상 다시 보여줘라 — 프리패스 조건을 넣지 마라

STEP 3. POST /api/v1/trips/{tripId}/activate
  → 이 호출로 SCHEDULE_PENDING → ACTIVE 전이가 일어난다고 가정하라
  → 상세·초대공유·방 안 화면은 이 호출 성공 이후에만 진입 가능하게 라우팅을 막아라

STEP 4. GET /api/v1/trips/{tripId} 로 여행방 상세 페이지 렌더링
  → 이 응답에는 inviteCode가 포함된다
```

STEP 3 구현 시 아래를 지켜라:

- 일정을 0건도 입력하지 않고(Skip) activate를 호출해도 에러로 처리하지 마라 — 서버가 자동으로 `isAllFree=true`로 채운 뒤 통과시킨다. Skip 버튼을 눌러도 activate 호출 자체는 항상 정상 진행되도록 만들어라.
- 이 API는 `SCHEDULE_ENTRY_REQUIRED`(입장 조건 미충족) 에러를 반환하지 않는다 — 호출 자체가 서버 쪽에서 그 조건을 항상 채운 뒤 진행되므로, 이 엔드포인트에 한해서는 그 에러 UI를 만들 필요가 없다. (401·403 `TRIP_ACCESS_DENIED` 정도만 일반 에러 처리로 잡아라.)
- activate를 이미 `ACTIVE`인 상태에서 다시 호출해도 에러 없이 같은 상세가 온다고 가정하라(idempotent) — 중복 호출 방지 로직을 서버 대신 프론트에서 구현할 필요 없다.

### 방장 이탈 후 재진입 처리 — 다음 두 가지를 반드시 구현하라

1. 홈 목록(`GET /trips`)에서 `myMemberStatus`가 `SCHEDULE_PENDING`인 카드를 발견하면, 탭 시 상세 페이지로 보내지 말고 STEP 2(정기 일정 입력)로 라우팅하라. 이 카드에는 `inviteCode`가 없으므로 공유 버튼을 노출하지 마라.
2. 위 라우팅을 실수로 빠뜨렸을 때를 대비해 방어 코드도 넣어라: `GET /trips/{tripId}` 호출이 `403 SCHEDULE_ACTIVATION_REQUIRED`를 반환하면, 무조건 정기 일정 입력 화면으로 리다이렉트하라.

## 규칙 3 — 멤버 플로우를 다음 순서·조건으로 구현하라

```
STEP 1. 초대 링크(…/room/{inviteCode}) 진입 → 로그인 + 이름 입력
  → 로그인·이름 완료 전에는 다음 단계로 넘어가지 못하게 막아라 (미완료 상태로 join 시도 시 403 PROFILE_NAME_REQUIRED)

STEP 2. 정기 일정 입력 → 개별 일정 입력
  → 방장과 동일한 화면·API를 재사용하라

STEP 3. POST /api/v1/trips/join { "inviteCode": "A2B3C4" }
  → 이 한 번의 호출로 멤버 row가 role=MEMBER, status=ACTIVE로 바로 생성된다고 가정하라
  → 일정이 0건이면 서버가 isAllFree=true로 자동 처리하므로, 프론트에서 별도로 값을 채워 보낼 필요 없다

STEP 4. 응답(TripDetailResponse)에 포함된 inviteCode로 곧바로 상세 페이지를 렌더링하라
```

멤버 이탈 처리 시 다음을 지켜라:

- STEP 2까지만 하고 STEP 3(`POST /trips/join`) 전에 이탈한 경우, DB에 멤버 row가 없다고 가정하라 — "이어하기" 상태를 복구하려는 로직을 만들지 마라. 재진입 시 무조건 STEP 1(초대 링크)부터 다시 시작하도록 라우팅하라.
- 이미 `ACTIVE`인 멤버가 같은 초대 링크를 다시 열었을 때는 `join`을 그대로 호출해도 된다 — 에러 없이 상세가 오므로(idempotent), "이미 참여한 방인지" 사전 체크 로직을 따로 만들지 마라.

## 규칙 4 — 관련 API는 아래 표만 사용하라

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/v1/trips` | JWT | 방 생성. 방장을 `SCHEDULE_PENDING`로 즉시 등록 |
| `POST` | `/api/v1/trips/{tripId}/activate` | JWT + 해당 방 멤버 | 방장의 일정 확인 완료. `SCHEDULE_PENDING` → `ACTIVE` |
| `POST` | `/api/v1/trips/join` | JWT | body `{ "inviteCode": string }` — 멤버 참여. 즉시 `ACTIVE` |
| `GET` | `/api/v1/trips/{tripId}` | JWT + 멤버 **ACTIVE** | 방 상세. `inviteCode` 포함 |
| `GET` | `/api/v1/trips` | JWT | 홈 목록 카드. `myMemberStatus`로 `SCHEDULE_PENDING`/`ACTIVE` 분기하라. `inviteCode` 없음 |
| `GET/POST/PATCH/DELETE` | `/api/v1/users/schedule/regular`, `/api/v1/users/schedule/personal` | JWT | STEP 2 일정 입력에 이 CRUD를 써라 (User 전역, 방과 무관) |

`POST /trips` 응답 예시(방장, 입장 전) — `inviteCode` 필드 자체가 없다는 것을 파싱 코드에 반영하라:

```json
{
  "data": {
    "tripId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "ONGOING",
    "myMemberStatus": "SCHEDULE_PENDING"
  }
}
```

`activate`/`join`/상세 조회 공통 응답 형태(`TripDetailResponse`) 예시:

```json
{
  "data": {
    "tripId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "ONGOING",
    "inviteCode": "A2B3C4",
    "myRole": "OWNER",
    "myMemberStatus": "ACTIVE"
  }
}
```

(실제 응답에는 이름·기간·인원 등 필드가 더 있다 — 전체 스키마가 필요하면 백엔드 팀에 OpenAPI 문서를 요청하라. 이 문서만 보고 필드를 추측해 타입을 만들지 마라.)

에러 코드를 처리할 때 아래 표를 그대로 매핑하라. 없는 코드가 오면 임의로 의미를 추측하지 말고 사용자에게 물어라.

| HTTP | code | 상황 |
|---|---|---|
| 400 | `INVALID_INPUT` | 이름 15자 초과, 인원 1~10 범위 밖, `inviteCode` 누락 등 |
| 403 | `PROFILE_NAME_REQUIRED` | 로그인은 했지만 이름 미완료 상태로 생성/참여 시도 → 이름 입력 화면으로 보내라 |
| 403 | `SCHEDULE_ACTIVATION_REQUIRED` | 방장이 `SCHEDULE_PENDING` 상태로 상세·방 안 API 호출 → 일정 입력 화면으로 라우팅하라 |
| 403 | `SCHEDULE_ENTRY_REQUIRED` | 방 입장 조건 미충족 — **`activate`/`join` 자체에서는 나오지 않는다**(서버가 호출 시점에 조건을 항상 채움). 상세·멤버 등 "방 안" API에서만 이론상 가능한 방어용 케이스 |
| 404 | `INVITE_CODE_NOT_FOUND` | 잘못된 초대 코드 — 재입력 유도 |
| 409 | `TRIP_MEMBER_FULL` | 정원 초과 — 참여 불가 안내 |
| 409 | `TRIP_ALREADY_CONFIRMED` / `TRIP_EXPIRED` | 확정·종료된 방에 신규 참여 시도 (기존 멤버 재접속은 막지 마라) |

## 규칙 5 — 요약 비교표를 코드 리뷰 체크리스트로 사용하라

| | 방장 | 멤버 |
|---|---|---|
| 멤버 row 생성 시점 | 방 생성 즉시(`SCHEDULE_PENDING`) | `join` 호출 시점(`ACTIVE`로 바로) |
| 완료 API | `POST .../activate` | `POST /trips/join` |
| 중간 이탈 시 라우팅 | `SCHEDULE_PENDING` 카드 → 일정 입력 화면으로 | row 없음 → 초대 링크부터 |
| 홈 카드 탭 분기 필요 여부 | **필요** — `myMemberStatus=SCHEDULE_PENDING`면 상세 대신 일정 입력으로 | 불필요 — 멤버는 항상 `ACTIVE`로만 존재 |

PR을 리뷰하거나 스스로 구현을 마쳤을 때, 이 표의 두 행("중간 이탈 시 라우팅", "홈 카드 탭 분기")이 실제 코드에 반영돼 있는지 반드시 확인하라.
