# Google Calendar 병합·연동 해제 — 구현 규칙

TripFit 프론트엔드 저장소에서 Google 캘린더 연동 관련 화면·로직을 구현·수정할 때 아래 규칙을 따르라. 여기 없는 세부 계약(전체 응답 필드, 에러 메시지 문구 등)은 추측해서 채우지 말고 사용자에게 확인하라.

모든 API 응답은 `{ "data": {...}, "message": "...", "code": "..." }` envelope로 온다. 아래 예시는 `data` 안쪽만 표기했으니, 실제 파싱 코드에는 한 단계 더 감싸야 한다.

## 규칙 1 — 병합된 슬롯 값을 그대로 써라. 프론트에서 재계산하지 마라

`GET /api/v1/users/schedule/calendar`, `GET /api/v1/trips/{tripId}/members/schedule-calendar` 두 API는 이미 Google busy와 수동 일정을 병합한 최종 `POSSIBLE`/`IMPOSSIBLE` 슬롯을 `days[]`로 내려준다. Google 연동 여부에 따라 프론트에서 별도 병합 로직을 짜지 마라 — 서버 응답을 그대로 렌더링하라.

디버깅·QA 목적으로 병합 규칙을 알아야 할 때만 아래를 참고하라. **2단계로 계산된다:**

**1단계 — 자동 계산(정기⊕Google, OR):** 슬롯별로 정기 일정과 Google busy 중 하나라도 불가능이면 그 슬롯은 `IMPOSSIBLE`이다.

| 정기 | Google busy | 자동 계산 결과 |
|---|---|---|
| POSSIBLE | busy 아님 | POSSIBLE |
| POSSIBLE | busy | IMPOSSIBLE |
| IMPOSSIBLE | busy 아님 | IMPOSSIBLE |
| 매칭 정기 없음 | busy | IMPOSSIBLE(응답에 처음 등장) |
| 매칭 정기 없음 | busy 아님 | 응답에 없음(생략) |

**2단계 — 개별(personal) 슬롯 오버라이드가 있으면 그 슬롯은 1단계 결과를 완전히 무시하고 최종값이 된다.** 즉 그날 Google이 `busy`라고 해도, 유저가 그 슬롯을 개별 일정으로 명시적으로 `POSSIBLE`로 오버라이드했다면 최종 응답은 `POSSIBLE`이다 — "Google busy가 항상 이긴다"고 가정하지 마라. 개별 오버라이드가 없는 슬롯만 위 1단계 표를 따른다.

- `uncertain` 플래그는 개별 일정(personal)에서만 온다고 가정하라 — Google busy가 이 값에 영향을 준다고 가정하지 마라.
- Google 종일(all-day) busy는 그 날짜 오전·오후·저녁 **전부**를 `IMPOSSIBLE`로 만든다고 가정하라.
- `days`에 날짜가 없으면 "미입력"으로 렌더링하라 — "가능(POSSIBLE)"으로 잘못 표시하지 마라. sparse 응답이다.
- 완료·만료된 여행방의 달력은 확정 시점 스냅샷으로 고정된다고 가정하라. 스냅샷 이후 Google 일정이 바뀌어도 그 방 안 과거 달력은 갱신되지 않는다 — "왜 최신 Google 일정이 반영 안 되지" 같은 버그로 오인해 재요청 로직을 넣지 마라.

## 규칙 2 — 연동 해제 시 UI를 다음과 같이 만들어라

`DELETE /api/v1/users/google-calendar` 호출 후:

- Google 관련 데이터(연동 정보·동기화된 busy 데이터)만 지워진다고 가정하라.
- 사용자가 직접 등록한 정기·개별 일정은 그대로 남는다고 가정하라 — 해제 확인 모달에 "일정도 같이 삭제됩니다" 같은 문구를 넣지 마라. 사실이 아니다.
- 응답의 `isGoogleCalendarConnected=false`를 보고 연동 버튼/CTA를 다시 노출하는 것으로 충분하다.

같은 결과(Google 데이터만 삭제, 수동 일정 유지)가 사용자의 명시적 해제 없이도 서버에서 자동으로 발생할 수 있다고 가정하고 UI를 짜라(권한 만료, refresh 실패 등). 즉 **연동 해제 전용 팝업/에러코드가 따로 없다는 전제로** 아래를 구현하라:

- 아무 화면에서든 최신 사용자 정보의 `isGoogleCalendarConnected`가 `false`로 바뀌어 있으면 "연동하기" 버튼을 다시 보여줘라.
- "연동이 만료되었습니다" 같은 별도 알림을 만들려면, 그 전용 API/에러코드가 아직 없다는 것을 먼저 사용자에게 확인하라 — 임의로 만들어 붙이지 마라.

## 규칙 3 — 관련 API는 아래 표만 사용하라

### 3.1 연동 관리

| Method | Path | 인증 | 요청/응답 |
|---|---|---|---|
| `POST` | `/api/v1/users/google-calendar` | JWT | body `{ "authorizationCode": string }` → 성공 시 `isGoogleCalendarConnected=true` 포함한 사용자 요약 반환 |
| `DELETE` | `/api/v1/users/google-calendar` | JWT | 성공 시 `isGoogleCalendarConnected=false` 포함한 사용자 요약 반환 |

`authorizationCode`를 서버로 넘기기 전의 OAuth redirect/복귀 URL 처리는 네가(프론트가) 구현할 몫이다 — 서버는 302 리다이렉트를 하지 않는다. Google에서 code를 받는 지점부터 REST로 넘겨라.

에러 코드를 처리할 때 아래 표에 없는 코드가 오면 임의로 의미를 추측하지 말고 사용자에게 물어라.

| HTTP | code | 상황 |
|---|---|---|
| 400 | `INVALID_INPUT` | `authorizationCode` 누락 |
| 502 | `GOOGLE_CALENDAR_CONNECT_FAILED` | code 교환·Google API 실패 — 재시도 유도 문구로 처리하라 |
| 409 | `GOOGLE_CALENDAR_NOT_CONNECTED` | 미연동 상태에서 `DELETE` 호출 |
| 401 | `AUTH_INVALID_TOKEN` / `AUTH_EXPIRED` | 토큰 없음·무효·만료 — 재로그인 플로우로 보내라 |

### 3.2 달력 조회 — 연동 여부와 무관하게 항상 이 API를 호출하라

| Method | Path | 인증 |
|---|---|---|
| `GET` | `/api/v1/users/schedule/calendar?startDate=&endDate=` | JWT (본인, 조회 구간: 오늘~오늘+2년-1, 단 참여 중인 ONGOING 여행 희망 기간 종료일이 그보다 뒤면 그 날짜까지 허용) |
| `GET` | `/api/v1/trips/{tripId}/members/schedule-calendar` | JWT + 여행방 멤버 (조회 구간: 여행 시작~종료일) |

응답 예시:

```json
{
  "data": {
    "startDate": "2026-08-01",
    "endDate": "2026-08-07",
    "days": [
      { "date": "2026-08-05", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE", "uncertain": false }
    ]
  }
}
```

### 3.3 수동 일정 CRUD (Google 연동과 무관, 별도 화면)

| Method | Path |
|---|---|
| `GET/POST/PATCH/DELETE` | `/api/v1/users/schedule/regular` |
| `PATCH` | `/api/v1/users/schedule/personal` (조회 없음 — upsert 응답만, 슬롯 3필드는 각각 선택/nullable — 상세는 [`schedule-calendar-merge.md`](../user-schedule/schedule-calendar-merge.md) 규칙 2) |

## 규칙 4 — 동기화 상태를 UI에 노출하려면 먼저 확인하라

- "마지막 동기화 시각"을 화면에 넣고 싶다면, 그 값이 현재 API 응답에 없다는 것을 먼저 인지하라 — 임의 필드명을 만들어 파싱하지 마라.
- 수동 즉시 동기화 버튼을 만들고 싶다면, 그 API가 아직 없다는 것을 사용자에게 먼저 알리고 구현하지 마라.
- 연동 상태 판단은 오직 `isGoogleCalendarConnected` 플래그 하나로 하라. 폴링 주기(30분)나 동기화 윈도우(오늘~오늘+2년-1, ONGOING 여행 종료일에 따라 뒤로 늘어날 수 있음)는 서버 내부 동작이므로 프론트 로직에서 이 값에 의존해 타이밍을 맞추려 하지 마라.
