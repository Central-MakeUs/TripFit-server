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

STEP 2. POST /api/v1/trips/join/hold { "inviteCode": "A2B3C4" }
  → 방 미리보기 정보를 받으면서 동시에 10분짜리 "정원 hold"를 선점한다고 가정하라
  → 정원이 이미 가득 찼으면 이 시점에 409 TRIP_MEMBER_FULL이 온다 — 일정 입력 화면으로 넘어가지 말고 여기서 바로 안내하라
  → 같은 사용자가 이 API를 재호출해도(예: 앱 재시작) hold TTL만 갱신되고 자리를 중복 소비하지 않는다고 가정하라

STEP 3. 정기 일정 입력 → 개별 일정 입력
  → 방장과 동일한 화면·API를 재사용하라
  → 이 플로우의 **첫 화면**(정기 일정 입력)에서 뒤로가기로 플로우 자체를 이탈하면 DELETE /api/v1/trips/{tripId}/join/hold를 호출해 hold를 즉시 반환하라 — 정기→개별 화면 사이를 오가는 뒤로가기는 호출하지 마라(hold 유지)
  → 이 호출을 놓쳐도 10분 뒤 서버가 자동으로 hold를 반환하므로 앱 강제종료 등에서는 치명적이지 않지만, 정상 종료 경로에서는 반드시 구현하라(다른 사용자가 더 빨리 자리를 받게 하기 위함)

STEP 4. POST /api/v1/trips/join { "inviteCode": "A2B3C4" }
  → 이 한 번의 호출로 멤버 row가 role=MEMBER, status=ACTIVE로 바로 생성된다고 가정하라
  → STEP 2의 hold를 아직 갖고 있으면 정원 재확인 없이 확정된다 — hold가 만료됐거나 STEP 2를 건너뛰었어도 이 API 자체는 그대로 동작한다(그 시점 정원을 다시 확인할 뿐)
  → 일정이 0건이면 서버가 isAllFree=true로 자동 처리하므로, 프론트에서 별도로 값을 채워 보낼 필요 없다

STEP 5. 응답(TripDetailResponse)에 포함된 inviteCode로 곧바로 상세 페이지를 렌더링하라
```

멤버 이탈 처리 시 다음을 지켜라:

- STEP 3까지만 하고 STEP 4(`POST /trips/join`) 전에 이탈한 경우, DB에 멤버 row가 없다고 가정하라 — "이어하기" 상태를 복구하려는 로직을 만들지 마라. 재진입 시 무조건 STEP 1(초대 링크)부터 다시 시작하도록 라우팅하라. hold는 최대 10분 후 자동으로 풀리므로 별도 정리가 필요 없다.
- 이미 `ACTIVE`인 멤버가 같은 초대 링크를 다시 열었을 때는 `join`을 그대로 호출해도 된다 — 에러 없이 상세가 오므로(idempotent), "이미 참여한 방인지" 사전 체크 로직을 따로 만들지 마라. `POST .../join/hold`도 이미 `ACTIVE`인 멤버가 호출하면 hold 없이 동일 미리보기 정보만 반환한다.

## 규칙 4 — 관련 API는 아래 표만 사용하라

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/v1/trips` | JWT | 방 생성. 방장을 `SCHEDULE_PENDING`로 즉시 등록 |
| `POST` | `/api/v1/trips/{tripId}/activate` | JWT + 해당 방 멤버 | 방장의 일정 확인 완료. `SCHEDULE_PENDING` → `ACTIVE` |
| `POST` | `/api/v1/trips/join/hold` | JWT | body `{ "inviteCode": string }` — 방 미리보기 + 10분 정원 hold 생성 |
| `DELETE` | `/api/v1/trips/{tripId}/join/hold` | JWT | 플로우 첫 화면 이탈 시 hold 즉시 반환. 존재하지 않는 hold를 호출해도 204(안전) |
| `POST` | `/api/v1/trips/join` | JWT | body `{ "inviteCode": string }` — 멤버 참여. 즉시 `ACTIVE`. STEP 2 hold를 갖고 있으면 그대로 소비 |
| `GET` | `/api/v1/trips/{tripId}` | JWT + 멤버 **ACTIVE** | 방 상세. `inviteCode` 포함 |
| `GET` | `/api/v1/trips` | JWT | 홈 목록 카드. `myMemberStatus`로 `SCHEDULE_PENDING`/`ACTIVE` 분기하라. `inviteCode` 없음 |
| `GET/POST/PATCH/DELETE` | `/api/v1/users/schedule/regular` | JWT | 정기 일정 CRUD (User 전역, 방과 무관) |
| `PATCH` | `/api/v1/users/schedule/personal` | JWT | 개별 일정은 **`PATCH`만** 존재 — 전용 GET·DELETE 없음(조회는 `GET /calendar`, 삭제 경로 자체가 없음 — O1.4). STEP 2 일정 입력에 사용 |

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
| 409 | `TRIP_MEMBER_FULL` | 정원 초과 — `join/hold`(선점 시점) 또는 `join`(hold 없이·만료 후 직접 호출 시) 둘 다에서 올 수 있다. 참여 불가 안내 |
| 409 | `TRIP_ALREADY_CONFIRMED` / `TRIP_EXPIRED` | 확정·종료된 방에 신규 참여 시도 (기존 멤버 재접속은 막지 마라) |

## 규칙 5 — 요약 비교표를 코드 리뷰 체크리스트로 사용하라

| | 방장 | 멤버 |
|---|---|---|
| 멤버 row 생성 시점 | 방 생성 즉시(`SCHEDULE_PENDING`) | `join` 호출 시점(`ACTIVE`로 바로) |
| 완료 API | `POST .../activate` | `POST /trips/join` |
| 중간 이탈 시 라우팅 | `SCHEDULE_PENDING` 카드 → 일정 입력 화면으로 | row 없음 → 초대 링크부터 |
| 홈 카드 탭 분기 필요 여부 | **필요** — `myMemberStatus=SCHEDULE_PENDING`면 상세 대신 일정 입력으로 | 불필요 — 멤버는 항상 `ACTIVE`로만 존재 |

PR을 리뷰하거나 스스로 구현을 마쳤을 때, 이 표의 두 행("중간 이탈 시 라우팅", "홈 카드 탭 분기")이 실제 코드에 반영돼 있는지 반드시 확인하라.

## 규칙 6 — 일정 확인 플로우는 「정기 일정 보유 여부」로 분기하라. `hasPreSchedule`을 쓰지 마라

방장·멤버 공통으로, 방에 들어가기 전 일정 확인 플로우를 아래 그대로 구현하라. **이 플로우에는 「건너뛰기」 버튼이 없다** — 회원가입 온보딩(건너뛰기 가능)과 반대이니 화면을 재사용할 때 버튼 노출 분기를 반드시 넣어라.

```
hasRegularSchedule (login·GET /auth/me 응답)   ← 분기는 이 값으로 판단하라
  또는 GET /api/v1/users/schedule/regular 응답 배열 길이
  │
  ├─ 0건 → "사전 일정 입력이 필요해요" 모달 → 확인
  │        → "정기 일정이 있나요?"
  │            ├─ 예     → [정기 일정 + 연차] → [개별 일정] → activate/join
  │            └─ 없어요 → [개별 일정] → activate/join      ← 연차 화면을 띄우지 마라
  │
  └─ 1건 이상 → "입력하신 일정을 확인해주세요" 모달 → 확인
           → [정기 일정 + 연차] (기존 값 프리필, 수정 가능)
           → [개별 일정] → activate/join
```

- **`hasPreSchedule`로 이 분기를 하지 마라.** 이 값은 "정기 **또는** 개별 일정이 하나라도 있으면 `true`"라서, 개별 일정만 등록한 사용자(정기 0건)에게 "입력하신 일정을 확인해주세요"를 띄우고 정기 화면이 텅 빈 상태를 만든다. 이름이 비슷한 **`hasRegularSchedule`**(정기만 반영)이 이 분기를 위해 있는 값이니 그쪽을 써라 — 둘의 차이는 개별 일정만 등록한 사용자에게서만 드러나므로, 잘못 골라도 일반 계정 테스트에서는 안 걸린다.
- **1건 이상 분기에서는 어차피 `GET /users/schedule/regular`를 호출해야 한다**(기존 값 프리필). 이미 요약 응답을 들고 있으면 `hasRegularSchedule`로 먼저 갈라 0건 경로에서 이 호출을 생략하고, 요약이 없거나 일정 CRUD 직후처럼 값이 낡았을 수 있으면 목록 길이로 판단하라 — 두 값은 같은 테이블을 본다.
- **연차 3문항은 정기 일정 화면과 한 덩어리다.** 정기를 입력·수정하는 경로에서만 노출하고, "없어요" 경로에서는 띄우지 마라. 연차 값은 별도 주소로 읽고 쓴다 — `GET`/`PATCH /api/v1/users/schedule/vacation-policy`. 정기 일정 목록과 **나란히** 조회하라(`GET /users/schedule/regular` 응답에는 연차 필드가 없다).
- **"없어요"를 눌러도 저장된 정기 일정을 삭제하지 마라.** 애초에 정기 0건인 사용자에게만 나오는 질문이라 지울 대상이 없다. `DELETE /users/schedule/regular/{id}`를 이 분기에 엮지 마라.
- **개별 일정 화면은 사용자가 실제로 토글한 날짜만** `PATCH /users/schedule/personal`에 담아라. 화면에 뜬 구간을 통째로 되돌려보내면 정기 유래 계산값이 전부 개별 오버라이드로 굳고, **되돌릴 방법이 없다**(개별 일정은 삭제 경로 자체가 없음). 이 플로우는 방에 들어갈 때마다 매번 거치므로 피해가 누적된다. 상세는 `user-schedule/schedule-calendar-merge.md`를 따르라.

### 이미 확인된 위반 2건 (QA, 2026-08-17) — 아래 지점을 고쳐라

두 건 모두 **서버는 정상**이다. 서버는 정기 일정 없이도 입장을 막지 않고, 일정이 0건이면 `activate`/`join` 시점에 자동으로 `isAllFree=true`로 채운다. 아래는 전부 클라이언트 분기 조건 문제다.

**① "정기 일정 없음" 사용자에게 정기 입력이 강제되는 문제**

```js
// RoomDetailSection.tsx — 화면 선택을 hasSavedSchedule로 하고 있다
const hasSavedSchedule = hasPreSchedule || isAllFree;
setBasicInfoInitialScreen(
  hasSavedSchedule ? 'regularScheduleDetail' : 'hasRegularSchedule',
);
// RoomCreateForm.tsx(방장)도 동일 — hasSavedSchedule ? 'confirmSchedule' : 'preSchedule'
```

`hasPreSchedule`은 정기 **또는** 개별이므로, **정기 0건인데도 참이 되는 사용자**가 정기 입력 화면으로 직행한다. 그 화면은 목록이 비면 빠져나갈 수 없다 — `RegularScheduleDetailStep`이 `hasEnteredListView`(초깃값 = 목록이 비어 있지 않은지)로 CTA를 정해서 목록이 비면 버튼이 "추가하기"뿐이고, 방 입장 경로는 `allowSkip={false}`라 건너뛰기도 없다. **막다른 길이 된다.**

걸리는 사용자: ⓐ "정기 없어요"로 저장하고 방을 **한 번 이상 입장한** 사람(첫 입장 때 서버가 `isAllFree=true`로 표시) ⓑ **개별 일정만** 등록한 사람. 회원가입 직후 첫 입장은 정상 동작하므로 재현하려면 방 입장을 두 번 해야 한다.

→ **수정:** 화면 선택 기준을 `hasPreSchedule || isAllFree`가 아니라 **정기 일정 유무**로 바꿔라. 바로 아랫줄 `initialValue`에서 이미 `hasRegularSchedule: savedItems.length > 0`으로 같은 판단을 하고 있으니 기준만 맞추면 된다. 서버 요약 응답에도 같은 이름의 `hasRegularSchedule` 필드가 생겼으니(2026-08-17, 정기 EXISTS만 반영) 목록을 아직 안 불렀으면 그 값을 그대로 써도 된다 — 화면 이름 `'hasRegularSchedule'`과 헷갈리지 않게 주의하라.

```js
(regularSchedulesData?.length ?? 0) > 0 ? 'regularScheduleDetail' : 'hasRegularSchedule'
```

**② 기존 일정이 있는 참여자가 일정 확인 없이 바로 입장되는 문제 (P1)**

```js
// RoomDetailSection.tsx — 일정이 있으면 마법사를 건너뛰고 즉시 join
const needsScheduleEntry = ... || (needsJoin && !hasPreSchedule && !isAllFree) || ...;
const needsJoinOnly = needsJoin && !needsScheduleEntry;
useEffect(() => { if (!needsJoinOnly) return; handleJoinTrip()...; }, [needsJoinOnly]);
```

일정이 이미 있는 초대 참여자는 `needsScheduleEntry`가 거짓이 되어 **일정 확인 화면 없이 곧바로 `POST /trips/join`** 이 나가고 방 상세로 들어간다. 이는 위 규칙(프리패스 금지)과 정면으로 어긋난다.

→ **수정:** 초대 코드로 진입한 경우(`needsJoin`)는 **일정 보유 여부와 무관하게** 항상 일정 확인 플로우를 거친 뒤 `join`을 호출하도록 조건에서 `!hasPreSchedule && !isAllFree`를 떼라.

→ **서버로는 막을 수 없다.** 구 `POST .../schedule/submit`을 폐기하면서 "일정 확인을 마쳤다"는 신호가 서버에 남지 않게 됐다. `POST /trips/join`은 초대 코드만 맞으면 언제든 참여시키므로, 이 강제는 전적으로 프론트 책임이다 — 뒤로가기·재진입·딥링크 등 우회 경로도 프론트에서 함께 막아야 한다.
