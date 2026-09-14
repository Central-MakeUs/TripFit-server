# 여행방 생성·입장 — 구현 규칙

TripFit 프론트엔드 저장소에서 방 생성/참여 관련 화면·라우팅을 구현·수정할 때 아래 규칙을 따르라. 여기 없는 세부 계약은 추측하지 말고 사용자에게 확인하라.

모든 API 응답은 `{ "data": {...}, "message": "...", "code": "..." }` envelope로 온다. 아래 예시는 `data` 안쪽만 표기했다.

## 규칙 1 — 방장과 멤버를 **같은 2단계**로 구현하라 (2026-08-19 변경)

방장과 멤버는 **똑같은 경로**로 `ACTIVE`(방 입장 가능 상태)에 도달한다.

```
방 진입(방장 POST /trips · 멤버 POST /trips/join) → SCHEDULE_PENDING
  → 일정 확인 플로우 → POST .../activate → ACTIVE
```

- **방장**: `POST /trips` 즉시 멤버 row가 `SCHEDULE_PENDING`으로 생기고, `POST .../activate`로 `ACTIVE`가 된다.
- **멤버**: **초대 링크를 연 직후**(일정 화면에 들어가기 **전**) `POST /trips/join`을 호출한다. 이때 멤버 row가 `SCHEDULE_PENDING`으로 생기고, 일정 확인을 마친 뒤 방장과 **같은** `POST .../activate`로 `ACTIVE`가 된다.

> **이전 구현과 달라진 점:** 예전에는 멤버가 일정을 다 입력한 **뒤** `join`을 한 번 호출해 곧바로 `ACTIVE`가 됐다. 이제 `join`은 플로우 **맨 앞**이고, `join`만으로는 방 안 API를 쓸 수 없다. 기존 순서를 그대로 둔 채 배포하면 참여가 깨진다.

라우팅은 역할이 아니라 **`myMemberStatus` 하나로** 분기하라 — 방장·멤버·재진입이 전부 같은 값으로 판단된다. `SCHEDULE_PENDING`이면 일정 확인 화면, `ACTIVE`면 방 상세다.

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

- 일정을 0건도 입력하지 않고(Skip) activate를 호출해도 에러로 처리하지 마라 — 서버는 일정 건수를 보지 않는다. Skip 버튼을 눌러도 activate 호출 자체는 항상 정상 진행되도록 만들어라.
- `SCHEDULE_ENTRY_REQUIRED`라는 에러 코드는 **더 이상 존재하지 않는다**(2026-08-18 삭제). 사용자 전역의 "일정을 등록했는가" 게이트가 사라지고, 방 입장 판정은 그 방의 `myMemberStatus` 하나가 답한다. 이 코드를 처리하는 분기가 남아 있으면 지워라. (401·403 `TRIP_ACCESS_DENIED` 정도만 일반 에러 처리로 잡아라.)
- activate를 이미 `ACTIVE`인 상태에서 다시 호출해도 에러 없이 같은 상세가 온다고 가정하라(idempotent) — 중복 호출 방지 로직을 서버 대신 프론트에서 구현할 필요 없다.

### 방장 이탈 후 재진입 처리 — 다음 두 가지를 반드시 구현하라

1. 홈 목록(`GET /trips`)에서 `myMemberStatus`가 `SCHEDULE_PENDING`인 카드를 발견하면, 탭 시 상세 페이지로 보내지 말고 STEP 2(정기 일정 입력)로 라우팅하라. 이 카드에는 `inviteCode`가 없으므로 공유 버튼을 노출하지 마라.
2. 위 라우팅을 실수로 빠뜨렸을 때를 대비해 방어 코드도 넣어라: `GET /trips/{tripId}` 호출이 `403 SCHEDULE_ACTIVATION_REQUIRED`를 반환하면, 무조건 정기 일정 입력 화면으로 리다이렉트하라.

## 규칙 3 — 멤버 플로우를 다음 순서·조건으로 구현하라

```
STEP 1. 초대 링크(…/room/{inviteCode}) 진입 → 로그인 + 이름 입력
  → 로그인·이름 완료 전에는 다음 단계로 넘어가지 못하게 막아라 (미완료 상태로 join 시도 시 403 PROFILE_NAME_REQUIRED)

STEP 2. POST /api/v1/trips/join { "inviteCode": "A2B3C4" }
  → 일정 화면에 들어가기 **전**에 호출하라. 멤버 row가 role=MEMBER, status=SCHEDULE_PENDING으로 생긴다
  → 정원이 가득 찼으면 여기서 409 TRIP_MEMBER_FULL이 온다 — 일정 입력 화면으로 넘어가지 말고 여기서 안내하라
  → 응답은 { tripId, status, myMemberStatus } 뿐이다. inviteCode·방 이름·기간은 오지 않는다
  → 이미 멤버인 사용자가 다시 호출해도 안전하다(멱등) — 새 자리를 쓰지 않고 현재 myMemberStatus만 돌아온다.
    "이미 참여한 방인지" 사전 체크 로직을 따로 만들지 마라

STEP 3. 정기 일정 입력 → 개별 일정 입력
  → 방장과 동일한 화면·API를 재사용하라

STEP 4. POST /api/v1/trips/{tripId}/activate
  → 방장이 쓰는 API와 **같다**. 여기서 비로소 SCHEDULE_PENDING → ACTIVE가 된다
  → 일정이 0건이어도 그대로 통과된다 — 프론트에서 별도로 값을 채워 보낼 필요 없다

STEP 5. 응답(TripDetailResponse)의 inviteCode로 상세 페이지를 렌더링하라
```

멤버 이탈 처리 시 다음을 지켜라:

- STEP 2까지 하고 STEP 4 전에 이탈하면 **멤버 row는 `SCHEDULE_PENDING`으로 남는다**(자리를 계속 차지한다). 재진입 시 초대 링크든 홈 카드든 `myMemberStatus=SCHEDULE_PENDING`을 보고 일정 확인 화면으로 보내라 — 예전처럼 "row가 없으니 처음부터"로 가정하지 마라.
- 자리를 자동으로 반환하는 장치는 **없다**(예전 10분 hold는 폐지됐다). 참여를 취소하려면 방 나가기 API를 써야 한다.
- 이미 `ACTIVE`인 멤버가 같은 초대 링크를 다시 열었을 때도 `join`을 그대로 호출해도 된다 — `myMemberStatus=ACTIVE`가 돌아오므로 그 값으로 상세로 보내라.

## 규칙 4 — 관련 API는 아래 표만 사용하라

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/v1/trips` | JWT | 방 생성. 방장을 `SCHEDULE_PENDING`로 즉시 등록 |
| `POST` | `/api/v1/trips/{tripId}/activate` | JWT + 해당 방 멤버 | **방장·멤버 공통** 일정 확인 완료. `SCHEDULE_PENDING` → `ACTIVE` |
| `POST` | `/api/v1/trips/join` | JWT | body `{ "inviteCode": string }` — 멤버 참여. `SCHEDULE_PENDING`으로 생성(멱등) |
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

`POST /trips/join` 응답도 **같은 축소 형태**다 — `myMemberStatus`는 신규 참여면 `SCHEDULE_PENDING`, 이미 멤버면 그 사람의 현재 상태다:

```json
{
  "data": {
    "tripId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "ONGOING",
    "myMemberStatus": "SCHEDULE_PENDING"
  }
}
```

`activate`/상세 조회 공통 응답 형태(`TripDetailResponse`) 예시:

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
| 403 | `SCHEDULE_ACTIVATION_REQUIRED` | 방장·멤버가 `SCHEDULE_PENDING` 상태로 상세·방 안 API 호출 → 일정 입력 화면으로 라우팅하라 |
| 404 | `INVITE_CODE_NOT_FOUND` | 잘못된 초대 코드 — 재입력 유도 |
| 409 | `TRIP_MEMBER_FULL` | 정원 초과 — `POST /trips/join`에서 온다. 일정 확인을 끝내지 않은(`SCHEDULE_PENDING`) 사람도 자리를 차지한다 |
| 409 | `TRIP_ALREADY_CONFIRMED` / `TRIP_EXPIRED` | 확정·종료된 방에 신규 참여 시도 (기존 멤버 재접속은 막지 마라) |

## 규칙 5 — 요약 비교표를 코드 리뷰 체크리스트로 사용하라

| | 방장 | 멤버 |
|---|---|---|
| 멤버 row 생성 시점 | 방 생성 즉시(`SCHEDULE_PENDING`) | **초대 링크 진입 직후 `join`**(`SCHEDULE_PENDING`) |
| 완료 API | `POST .../activate` | **`POST .../activate`** (동일) |
| 중간 이탈 시 라우팅 | `SCHEDULE_PENDING` 카드 → 일정 입력 화면으로 | **동일** — `SCHEDULE_PENDING`이면 일정 입력 화면으로 |
| 홈 카드 탭 분기 필요 여부 | **필요** — `myMemberStatus`로 분기 | **필요** — 멤버도 `SCHEDULE_PENDING`이 존재한다 |

PR을 리뷰하거나 스스로 구현을 마쳤을 때, 이 표에서 방장·멤버 열이 **같아졌다는 점**이 실제 코드에 반영돼 있는지 확인하라 — 역할별로 갈라진 라우팅 분기가 남아 있으면 그것이 회귀다.

## 규칙 6 — 일정 확인 플로우는 「최초 입력 / 갱신 입력」으로 분기하라. 일정 건수로 판단하지 마라

방장·멤버 공통으로, 방에 들어가기 전 일정 확인 플로우를 아래 그대로 구현하라. **이 플로우에는 「건너뛰기」 버튼이 없다.**

```
hasCompletedPreSchedule (login·GET /auth/me 응답)   ← 분기는 이 값 하나로만 판단하라
  │
  ├─ false = 최초 입력 → "정기 일정이 있나요?"
  │     ├─ 있어요 → [정기 일정] → [연차·휴일 정보] → [개별 일정] → activate
  │     └─ 없어요 → DELETE /users/schedule/regular (즉시) → [연차·휴일 정보] → [개별 일정] → activate
  │
  └─ true = 갱신 입력 → "일정 변경이 있나요?" (있어요/없어요 — 안내용, 분기 없음)
        → [정기 일정 수정] (기존 값 프리필) → [연차·휴일 정보] → [개별 일정] → activate
```

- **`hasCompletedPreSchedule`은 "연차·휴일 정보의 사전 신청일을 저장한 적이 있는가"다.** 일정 건수를 세지 않는다 — 정기 일정이 잔뜩 있어도 사전 신청일이 없으면 **최초 입력**이고, 일정이 하나도 없어도 사전 신청일이 있으면 **갱신 입력**이다. 일정 개수로 분기를 다시 만들지 마라(2026-08-19 이전 규칙이 그랬고, 그것이 아래 QA 이슈의 뿌리였다).
- **`일정 변경이 있나요?`는 분기용이 아니다.** 있어요/없어요 어느 쪽을 골라도 같은 경로로 간다 — 기존 사용자에게 "처음부터 다시 입력하는 게 아니라 확인·수정하는 과정"임을 알리는 안내 화면이다.
- **연차·휴일 정보는 두 갈래 모두 지난다.** `없어요` 경로에서도 띄워라(2026-08-19 변경 — 예전에는 띄우지 말라고 했다). **이 화면을 저장해야 사전 신청일이 채워지고, 그래야 다음번에 갱신 입력으로 들어온다.** 여기서 빠지면 그 사용자는 영원히 최초 입력에 머문다. 연차 값은 `GET`/`PATCH /api/v1/users/schedule/vacation-policy`로 읽고 쓰며, **4개 필드를 항상 함께 보내야 한다**(하나라도 빠지면 400 — `user-schedule/vacation-policy.md`).
- **`없어요`를 누르면 `DELETE /api/v1/users/schedule/regular`로 기존 정기 일정을 즉시 전부 지워라** (2026-08-19 변경 — 예전에는 지우지 말라고 했다). 최초 판정은 사전 신청일만 보므로, 마이페이지에서 정기만 넣고 나갔던 사용자가 이 화면을 만날 수 있다. 안 지우면 사용자는 "없다"고 답했는데 그 정기 일정이 추천 계산에 계속 반영된다. 0건이어도 204이니 존재 여부를 먼저 확인할 필요 없다.
- **`다음` 버튼:** 최초 입력은 필요한 정보를 넣기 전까지 비활성화, 갱신 입력은 기존 값이 있으므로 기본 활성화.
- **갱신 입력인데 정기 일정이 0건인 사용자도 정기 일정 수정 화면으로 보내라** (기획 명시). 이때 목록이 비어 있어도 `다음`을 **활성 상태로 유지**해야 한다 — 목록이 비면 CTA가 "추가하기"뿐이 되도록 만든 구 `RegularScheduleDetailStep`의 `hasEnteredListView` 초깃값 로직이 그대로 남아 있으면, QA 이슈 1과 **같은 막다른 길**이 이 경로에서 재현된다. 정기 일정이 0건인 갱신 사용자는 「연차만 저장하고 정기는 없는 사람」으로 정상적인 상태다.
- **`activate` 호출이 403 `PRE_SCHEDULE_REQUIRED`로 실패하면**, 사전 일정 입력(특히 연차·휴일 정보 저장)을 건너뛴 것이다. 서버가 같은 조건을 검사하므로 화면 규칙만으로 통과시키려 하지 마라.
- **개별 일정 화면은 사용자가 실제로 토글한 날짜만** `PATCH /users/schedule/personal`에 담아라. 화면에 뜬 구간을 통째로 되돌려보내면 정기 유래 계산값이 전부 개별 오버라이드로 굳고, **되돌릴 방법이 없다**(개별 일정은 삭제 경로 자체가 없음). 이 플로우는 방에 들어갈 때마다 매번 거치므로 피해가 누적된다. 상세는 `user-schedule/schedule-calendar-merge.md`를 따르라.

### 이미 확인된 위반 2건 (QA, 2026-08-17) — 아래 지점을 고쳐라

두 건 모두 **서버는 정상**이다. 서버는 정기 일정이 없어도 입장을 막지 않는다. 아래는 전부 클라이언트 분기 조건 문제다.

> **참고 (2026-08-19):** 아래 QA 기록에 나오는 `hasPreSchedule`·`hasRegularSchedule`·`isAllFree`는 **셋 다 서버 응답에서 삭제됐다.** 지금은 `hasCompletedPreSchedule` 하나만 있고, 위 규칙 6이 현행 계약이다. 아래는 "왜 조합식을 만들면 안 되는가"의 사례로만 읽어라.
>
> **참고 (2026-08-18):** 아래 코드에 나오는 `isAllFree`는 서버 응답에서 **삭제됐다.** 이 값이 "첫 입장 때 서버가 몰래 켜는" 성질을 가져 이슈 ①의 "방을 두 번 입장해야 재현된다"는 조건을 만든 장본인이었다. 조합식을 고치는 김에 이 필드 참조 자체를 전부 지워라.

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

걸리는 사용자: ⓐ "정기 없어요"로 저장하고 방을 **한 번 이상 입장한** 사람(첫 입장 때 서버가 `isAllFree=true`로 표시했었음 — 이 필드는 이제 삭제됨) ⓑ **개별 일정만** 등록한 사람. 회원가입 직후 첫 입장은 정상 동작하므로 재현하려면 방 입장을 두 번 해야 한다.

→ **수정 (2026-08-19 갱신):** 화면 선택 기준을 조합식이 아니라 **`hasCompletedPreSchedule`** 하나로 바꿔라. 일정 건수(`savedItems.length`·`regularSchedulesData?.length`)로 판단하던 코드도 함께 걷어내라 — 그 기준이 이 이슈의 뿌리다. 화면 이름 `'hasRegularSchedule'`(스텝 ID)과 서버 필드는 이제 이름조차 다르므로 혼동할 일도 없다.

```js
hasCompletedPreSchedule ? 'scheduleChanged' : 'hasRegularSchedule'
// false = 최초 입력 → "정기 일정이 있나요?" 스텝부터
// true  = 갱신 입력 → "일정 변경이 있나요?" 스텝부터
```

**② 기존 일정이 있는 참여자가 일정 확인 없이 바로 입장되는 문제 (P1)**

```js
// RoomDetailSection.tsx — 일정이 있으면 마법사를 건너뛰고 즉시 join
const needsScheduleEntry = ... || (needsJoin && !hasPreSchedule && !isAllFree) || ...;
const needsJoinOnly = needsJoin && !needsScheduleEntry;
useEffect(() => { if (!needsJoinOnly) return; handleJoinTrip()...; }, [needsJoinOnly]);
```

일정이 이미 있는 초대 참여자는 `needsScheduleEntry`가 거짓이 되어 **일정 확인 화면 없이 곧바로 `POST /trips/join`** 이 나가고 방 상세로 들어간다. 이는 위 규칙(프리패스 금지)과 정면으로 어긋난다.

→ **수정:** 초대 코드로 진입한 경우(`needsJoin`)는 **일정 보유 여부와 무관하게** 항상 일정 확인 플로우를 거친 뒤 `activate`를 호출하도록 조건에서 `!hasPreSchedule && !isAllFree`를 떼라.

→ **서버 강제 범위 (2026-08-19 갱신):** 방마다의 일정 확인은 여전히 프론트 책임이다 — `join`은 초대 코드만 맞으면 통과하고, `activate`도 "이 방에서 화면을 봤는지"까지는 알 수 없다. 다만 **사전 일정 입력을 한 번도 끝내지 않은 사용자**는 `activate`가 403 `PRE_SCHEDULE_REQUIRED`로 막는다. 즉 "아예 입력한 적 없는 사람"은 서버가 걸러주고, "입력은 했지만 이 방에서 확인 화면을 건너뛴 사람"은 프론트가 막아야 한다.
