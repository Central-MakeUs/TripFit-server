# user-schedule Architecture Audit — Round 2 (2026-08-05)

## 범위
- 패키지: com.tripfit.tripfit.user.schedule (+ 하위 패키지, 관련 호출부)
- 감사자: 서브에이전트 (Agent 툴, 읽기 전용) — 1차 반영(2026-08-05) 이후 재검사
- 기준: audit-checklist.md 1~15항목, harness-workflow.md STOP
- main 15개 파일 전수 재검토 + 관련 호출부(RecommendationEngine, TripServiceSupport, TripMemberQueryService, TripScheduleSnapshotService, UserSummaryService, UserWithdrawalPersistenceService) 교차 확인, git log(b5213fa)로 1차 반영분 diff 직접 검증

## ✅ A. 반드시 수정해야 하는 사항

### A-1. `upsertPersonal()`이 여전히 `ScheduleCalendarResolver.resolve()`를 `[minDate, maxDate]` **전체 구간**으로 호출 — 실제 필요한 것은 `items`에 담긴 날짜뿐
- Priority: High
- Category: Performance
- 문제/왜: 1차 A-1은 `personal_schedule` **DB 재조회**(N+1)만 제거했다. 그런데 `buildPersonalResponse()`(`ScheduleService.java:222-257`)는 여전히 `ScheduleCalendarResolver.resolve(regulars, personals, minDate, maxDate, googleBusy)`를 호출해 `minDate~maxDate` **전체 날짜를 하루씩 순회**(`ScheduleCalendarResolver.java:44-59`의 `for (LocalDate date = startDate; ...)`)하고, `googleCalendarService.findBusyDaysByUserId(userId, minDate, maxDate)`도 같은 폭의 범위로 조회한다. 정작 `upsertPersonal()`은 반영된 날짜(`dates`, 곧 `items` 개수)만 응답에 쓴다(`:242-255`에서 `resolvedByDate.get(date)`로 필요한 것만 추출). `items`는 날짜가 서로 멀리 떨어진 두 건만 있어도(예: 2026-01-01, 2026-12-31) 그 사이 365일 전체를 순회 계산하게 되어 `O(items)`이면 충분한 일을 `O(range)`로 하고 있다. 이 API는 `scheduleDate`에 날짜 범위 검증이 없어(C-1 참고) 이 폭이 이론상 매우 커질 수 있다는 점도 함께 확인했다.
- 개선 방법: `ScheduleCalendarResolver`에 연속 구간이 아니라 **요청받은 날짜 집합**(`Collection<LocalDate>`)을 순회하는 오버로드를 추가하고, `buildPersonalResponse()`는 `minDate~maxDate` 범위 대신 `dates`만 넘겨 계산하도록 바꾼다. `getCalendar()`(연속 구간이 실제로 필요한 API)의 기존 호출은 그대로 유지 — 시그니처 오버로드 추가이므로 기존 소비처(RecommendationEngine 등)도 영향 없음.
- API 영향: No Impact — 응답값은 동일 날짜 집합에 대해 동일 계산 결과. 내부 순회 방식만 변경.
- 예상 변경 파일: `user/schedule/service/ScheduleCalendarResolver.java`, `user/schedule/service/ScheduleService.java`
- 예상 변경 라인 수: ~30줄
- 위험도: Low — 순수 계산 로직, 입력 날짜 집합에 대한 출력은 동일해야 함(`ScheduleCalendarResolverTest`로 검증 가능).
- 테스트 영향도: `ScheduleServiceTest`의 `upsertPersonal_*` 테스트 결과는 동일해야 함. `ScheduleCalendarResolverTest`에 신규 오버로드 케이스 추가 권장.
- 예상 효과: 날짜가 넓게 퍼진 bulk upsert 요청에서 CPU/메모리 사용량이 `O(range)`→`O(items)`로 감소. 정상적인 사용 패턴(달력 UI에서 인접한 며칠)에서는 체감 차이가 작지만, 최악의 경우(멀리 떨어진 날짜 두 건)에 대한 방어가 된다.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `upsertPersonal()` — 항목별 값 검증(`validatePersonalItem`)이 구간 SELECT **이후**에 실행됨
- Priority: Low
- Category: Performance / Readability
- 문제/왜: `existingByDate` 맵을 만드는 SELECT(`ScheduleService.java:174-178`)가 먼저 실행되고, `slots`/`uncertain` 값 검증(`validatePersonalItem`, `:183`)은 그다음 루프 안에서 항목마다 실행된다. 즉 어떤 항목이 잘못된 슬롯 값(`ON_LEAVE` 등 허용 안 되는 값)을 담고 있어도 이미 DB 조회 한 번은 끝난 뒤에 400이 발생한다.
- 개선 방법: `items` 전체에 대해 `validatePersonalItem`을 먼저 한 번 순회해 검증을 끝낸 뒤, 그다음에 구간 SELECT와 upsert 루프를 실행한다. 검증 로직·에러코드는 그대로 유지.
- API 영향: No Impact — 실패하는 입력과 에러코드는 동일, 성공 경로 결과도 동일.
- 예상 변경 파일: `user/schedule/service/ScheduleService.java`
- 예상 변경 라인 수: ~10줄
- 위험도: Low
- 테스트 영향도: 없음 — 공개 동작 동일.
- 예상 효과: 잘못된 입력(400 경로)에서 불필요한 SELECT 1회 절약. 효과는 작지만 "실패 시 부작용 최소화" 원칙에 부합.

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- **`PATCH /users/schedule/personal`의 `scheduleDate`에 범위 검증이 전혀 없음** (`UpdatePersonalScheduleRequest.PersonalScheduleItem.scheduleDate`는 `@NotNull`만 있고 상/하한 없음, `validatePersonalItem()`도 값 조합만 검사) — `GET /calendar`는 `validateCalendarDateRange()`로 `[today, today+2년−1]`(또는 ONGOING 여행 종료일까지)로 명확히 제한되는데, 개별 일정 upsert는 임의 연도(예: 0001년, 9999년)도 그대로 저장된다. A-1을 적용하면 `resolve()` 자체의 순회 비용은 `O(items)`로 줄어 CPU/메모리 폭주 위험은 크게 낮아지지만, "말이 안 되는 날짜가 영구 저장된다"는 데이터 품질 문제 자체는 남는다. 다만 이를 지금 고치려면 지금까지 성공하던 요청(먼 과거/미래 날짜)에 새로운 400을 추가해야 해 **"API 계약 100% 동일 유지"라는 이번 라운드의 절대 원칙과 정면충돌**한다 — 별도 스펙(허용 날짜 윈도우 정책)·에러코드·product 승인이 선행돼야 하므로 이번 무손실 리팩터링 라운드 범위 밖으로 보류한다.
- **1차 audit.md의 C 항목 재검증 결과 — 여전히 유효, 변경 없음**: `CreateRegularScheduleRequest`/`UpdateRegularScheduleRequest` 구조 중복(record 상속 불가), `ScheduleCalendarResolver.resolve()` 4-인자 오버로드(테스트 전용, 실사용처 없음), `PersonalSchedule`/`RegularSchedule`의 전역 `@Setter` 컨벤션(저장소 전역 이슈) — 코드를 다시 읽어 확인했으며 1차 판단이 여전히 타당하다.

**참고(감사만, 수정 대상 아님) — 도메인 외부 소비처 관찰**: `trip/service/TripMemberQueryService.buildLive()`와 `trip/service/TripScheduleSnapshotService.freezeTrip()`는 멤버마다 `support.resolveMergedSchedule(...)`를 개별 호출해 멤버 수만큼 `RegularScheduleRepository`/`PersonalScheduleRepository` 쿼리를 반복한다(N+1). 반면 같은 패턴을 쓰는 `RecommendationEngine.loadContext()`는 `findByUserIdIn`/`findByUserIdInAndScheduleDateBetween`으로 이미 배치 조회한다. 이는 `trip` 도메인 코드이고 user-schedule 리포지토리를 그대로 소비만 하는 것이라 이번 라운드(user-schedule 도메인) 수정 대상은 아니다 — trip 도메인 자체 감사에서 다룰 사안으로 남겨둔다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **`ScheduleService.requireSlotStatus()`의 "항상 false인" 것처럼 보이는 조건** (`:343-347`) — `ScheduleStatus` enum이 현재 `POSSIBLE`/`IMPOSSIBLE` 2개뿐이라 `status != POSSIBLE && status != IMPOSSIBLE`는 지금 절대 참이 될 수 없는 사실상 no-op 분기다. 하지만 바로 위 주석(`:341-342`)이 "ON_LEAVE 등은 추후 wave, enum 값 제한은 Bean Validation으로 표현 불가"라고 명시적으로 밝히고 있어, 이는 방치된 죽은 코드가 아니라 **향후 enum 확장(연차 등 새 상태 추가) 시를 대비한 의도적 방어 코드**다. 지금 "죽은 코드니 제거"하면, 나중에 enum이 늘어날 때 이 검증을 다시 작성해야 하고 그 시점에 깜빡 잊으면 API로 임의 enum 값이 그대로 들어오는 회귀가 생길 수 있다 — 지금 제거해서 얻는 이득이 없고 되레 리스크만 키운다. 그대로 유지.
- **1차 audit.md의 D 항목 재검증 결과 — 여전히 유효**: `ScheduleErrorCode` 단일 상수 유지(`spring-boot-java.md` feature별 ErrorCode 컨벤션 그대로), `ScheduleCalendarResolver` 비-Bean 유지(순수 정적 유틸, DI 오버헤드 불필요), `ScheduleService` 미분리 유지(단일 응집 도메인), `requireOwnedRegularSchedule()` 미추출 유지(호출부 2곳뿐, 과도한 추상화) — 모두 재검토했으며 현재 구조가 여전히 더 낫다.

## 15. 백엔드 아키텍처 개선 제안

- **Security — Now**: 위 C의 `scheduleDate` 범위 미검증 건. `PATCH /personal`이 GET /calendar와 달리 날짜 상/하한이 없는 비대칭 상태다. A-1 적용으로 CPU/메모리 리스크는 완화되지만, 근본적으로는 "얼마나 먼 과거/미래까지 개별 일정을 허용할 것인가"를 스펙으로 확정하고 `INVALID_INPUT`(또는 신규 ErrorCode)로 막는 것이 바람직하다. 다만 이는 API 계약 변경(신규 400 케이스)이라 이번 라운드 승인 항목이 아니라 별도 스펙·이슈로 진행 권장.
- 그 외 카테고리(Redis/Event/Async/DB/Monitoring/Resilience/API)는 1차와 동일하게 해당 없음(YAGNI) — 이 도메인은 외부 I/O가 Google Calendar 조회(이미 `user/googlecalendar` 도메인에서 별도 처리)뿐이고, A-1 적용만으로 성능 리스크가 충분히 해소된다.

## 승인 대기

사용자 승인 후 A/B 항목만 우선순위 순으로 구현합니다. C/D는 이번 라운드에서 수정하지 않습니다.
