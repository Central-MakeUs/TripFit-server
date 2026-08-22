# API 계약 Drift 자동 감지 CI 파이프라인

> 상태: Approved
> MVP: N/A — 제품 기능이 아닌 개발 인프라/도구 (Wave 분류 대상 아님)
> 관련 BR: 해당 없음

## 목표

백엔드 DTO/컨트롤러 변경이 breaking change일 때, 별도 저장소인 프론트가 놓치지 않도록 CI에서 자동으로 감지하고 프론트가 실제로 보는 채널(Discord `#frontend`)까지 push로 전달한다.

## 배경

- 프론트는 이 저장소와 완전히 분리된 별도 저장소(Vercel, `tripfit.online`)다 — [`docs/decisions/002-domain-split-vercel-api.md`](../decisions/002-domain-split-vercel-api.md). 모바일도 별도 코드가 아니라 같은 React 화면을 WebView로 감싸는 방식([`platform.md`](../product/platform.md))이라 프론트 동기화 대상은 사실상 1곳.
- 개발자가 DTO 변경 시 프론트에 알려야 하는데, "바이브 코딩" 특성상 변경 사항을 손으로 다 기억해서 전달하기 어렵다는 문제 제기(대화 2026-07-28)에서 출발.
- `springdoc-openapi-starter-webmvc-ui:3.0.3`가 이미 의존성에 있어(`build.gradle:29`) `/v3/api-docs`에서 OpenAPI 스펙이 별도 설정 없이 자동 생성되는 중이지만, 이 스펙을 PR 리뷰·프론트 codegen에 실제로 활용하는 장치는 없음.
- `docs/specs/swagger-openapi-docs.md`(Implemented)는 Swagger UI **가독성**(설명·예시·태그 문구) 개선이 목적이라 이 스펙과 범위가 다름 — 이 스펙은 스펙 **diff 자동 감지**(CI)가 목적.
- `client-platform.md` "프론트 협업" 절이 이미 "API 변경은 OpenAPI(springdoc) 반영 권장"이라고 명시하고 있어, 이 스펙은 그 권장을 CI 강제로 한 단계 끌어올리는 것.
- **설계 변경 이력(2026-07-28 대화, 순서대로):**
  1. 최초안 "PR 인라인 코멘트 + fail"만으로는 프론트가 이 repo PR을 능동적으로 열어봐야 아는 **pull 방식**이라 놓칠 수 있다는 지적 → Discord `#frontend` **push 알림**을 Must Have로 승격, 수동 "PR 체크리스트" 항목 제거
  2. 사용자가 실제 리포·CI 구조 분석 후 계획을 요구 → `oasdiff-action`(마켓플레이스) 대신 **oasdiff CLI 직접 사용**으로 전환(Discord 메시지에 필요한 endpoint별 breaking 상세를 직접 파싱하기 위해 JSON 출력을 온전히 제어할 필요), `docs/api/openapi.json` 생성 방식도 "앱 부팅+curl" 대신 **JUnit 테스트로 MockMvc 호출**로 전환(새 인프라 없이 기존 H2 테스트 프로필 재사용)
  3. "왜 변경했는가"를 하드코딩하지 않고 커밋마다 동적으로 담기 위해 PR Template/PR Label/PR Body Parsing/Commit 트레일러 4가지를 비교 → **Commit 트레일러** 채택(아래 "왜 변경했는가 — 전달 방식 결정" 절)
  4. breaking change 감지 시 `deploy` job도 막을지 확인 → **막지 않음**, Discord 알림만(아래 "실패 처리 범위" 절)

## 왜 변경했는가 — 전달 방식 결정

| 방식 | `push`(main 병합 후) 커버 | 비고 | 채택 여부 |
|------|------|------|-----------|
| PR Template 파싱 | ❌ 병합 후엔 PR body를 얻으려면 `commits/{sha}/pulls` GitHub API 호출 추가 필요 | | 미채택 |
| PR Label | 자유 텍스트를 담을 수 없음(라벨은 분류용) | | 미채택 |
| PR Body Parsing(마커) | PR Template과 동일한 한계 | | 미채택 |
| **Commit 트레일러** | ✅ `push`는 `before..after`, PR은 `base.sha..head.sha` — 동일한 `git log` 한 줄로 양쪽 다 커버 | GitHub API 불필요, 소스가 git 히스토리 하나뿐이라 가장 유지보수 낮음 | **채택** |

**컨벤션**: 커밋 메시지 본문에 `Breaking-Change-Reason: <한 줄 사유>` 트레일러를 추가. 첫 줄은 기존 `{Type}: {한글 설명}` 형식(`.github/CONTRIBUTING.md`) 그대로 유지.

```
Fix: 마이페이지 응답 필드명 정리

Breaking-Change-Reason: 프론트 요청으로 name → nickname 통일 (디자인 시스템 용어 정합)
```

트레일러가 없는 커밋만 있으면 하드코딩 문구 대신 "⚠️ 사유 미기재 — 커밋 메시지에 `Breaking-Change-Reason:` 추가 필요"라는 **안내문**만 동적으로 채움(값을 지어내지 않음).

## 실패 처리 범위

```
test 통과 ──────────────▶ deploy (배포 진행)
api-contract-check ─────▶ Discord 알림만 (job은 항상 통과, 배포도 막지 않음)
```

- `api-contract-check`(신규 job)는 `test`/`deploy`와 **독립적으로 병렬 실행** — `deploy`의 `needs: test`는 변경하지 않음
- breaking change가 있어도 `api-contract-check`는 **항상 통과(exit 0)** 한다 — Discord `#frontend` 알림만으로 충분하다는 판단(2026-07-29 amend, 아래 변경 이력). PR 목록·Actions 탭을 빨갛게 만들어 눈에 띄게 하는 대신, 알림 확인만으로 흐름을 끊지 않는다
- job이 항상 통과하므로 GitHub 저장소 Required status checks에 `api-contract-check`를 추가해도 merge를 막는 효과가 없다 — merge 차단이 필요해지면 별도 메커니즘(리뷰 체크리스트 등)을 새로 설계해야 함

## 요구사항

### Must Have

- [ ] **OpenAPI 스펙 export**: `src/test/java/.../OpenApiSpecExportTest.java` 신규 — `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`로 `/v3/api-docs`를 MockMvc 호출해 `build/openapi/openapi.json`에 기록. 새 Gradle 플러그인·실서버 기동·MySQL 불필요(H2 테스트 프로필 재사용, `SecurityConfig.java:69` `/v3/api-docs/**`는 이미 `permitAll`)
- [ ] **base 스펙 조회**: `curl -fsSL https://raw.githubusercontent.com/${{ github.repository }}/main/docs/api/openapi.json` — public repo라 인증 불필요. 404(최초 실행, 파일 없음)면 breaking 비교를 스킵하고 안내 로그만 남김
- [ ] **oasdiff CLI**: `oasdiff breaking base.json build/openapi/openapi.json --format json` 실행, exit code 1 = breaking 존재(공식 문서 확인). 정확한 JSON 필드 구조(경로·method·설명 텍스트 키 이름)는 구현 착수 시 실제 실행 결과로 확정 — 추측으로 파서 작성 안 함
- [ ] **`.github/workflows/ci-cd.yml`에 신규 job `api-contract-check` 추가**: workflow-level 기존 트리거(`push: main`, `pull_request: main`) 그대로 재사용, `test`/`deploy`와 병렬(간섭 없음)
- [ ] breaking change 감지 시:
  1. 커밋별로 순회하며 `Breaking-Change-Reason:` 트레일러 추출 — 값이 다음 줄로 wrap돼도(빈 줄·다른 `Key: value` 트레일러 전까지) 한 사유로 접어 합치고 짧은 SHA를 붙여 나열(없으면 안내문). NUL 구분 다중 레코드 방식은 BSD awk가 NUL 바이트를 못 다뤄 커밋 1개씩 순회하는 방식으로 구현(2026-07-29 amend)
  2. Discord embed 조립 후 `curl -X POST`로 `${{ secrets.DISCORD_WEBHOOK_URL }}`에 전송(이미 등록된 secret, 새 secret 불필요)
  3. job은 항상 통과(`exit 0`) — Discord 알림만으로 충분하다는 판단(2026-07-29 amend, "실패 처리 범위" 절)
- [ ] **Discord 메시지 구성**(embed): 제목 `🚨 API Breaking Change` · Repository/Branch/Commit/PR(있을 때만) · 발견된 변경(endpoint·method별 breaking 설명 목록) · 왜 변경했는가(트레일러, 없으면 안내문) · 프론트 작업 체크리스트(고정 텍스트: orval 재생성 / 타입 오류 확인 / 영향받는 API 수정) · GitHub 링크(PR 또는 커밋 + Actions 실행 링크)
- [ ] non-breaking 변경만 있으면 Discord 알림 없이 조용히 통과
- [ ] `push`(main)에서만: 위 단계 통과 후 `docs/api/openapi.json`을 최신 스펙으로 갱신·커밋(`git commit -m "Chore: OpenAPI 스펙 스냅샷 갱신 [skip ci]"`) — `[skip ci]`로 재트리거 방지
- [ ] `docs/api/openapi.json` 최초 시딩 커밋 (구현 브랜치 첫 커밋)
- [ ] `docs/api/README.md`(신규) 또는 이 스펙에 사용법 문서화: 트레일러 컨벤션, 로컬 재현 방법(`./gradlew test --tests "*OpenApiSpecExportTest"` → `oasdiff breaking` 로컬 실행)
- [ ] repo가 public이므로 프론트 저장소가 `raw.githubusercontent.com/.../main/docs/api/openapi.json`을 별도 인증 없이 orval 등 codegen 소스로 쓸 수 있음 — 이 사실만 문서에 남기고, 실제 orval 설정은 프론트 저장소 소관(Out of Scope)

### Nice to Have

- (없음 — "PR 체크리스트 수동 표기"는 Discord 자동 알림이 대체)

### Out of Scope (이번 스펙에서 하지 않음)

- 프론트 저장소의 orval 설정·codegen 스크립트 — 별도 저장소 소관, 이 스펙은 "무엇을 참고하면 되는지"만 안내
- tRPC·모노레포 전환 — `decisions/002`로 이미 확정 배제 (재질문 금지 대상)
- Pact 등 본격 contract test — 2인 규모 프로젝트에 과함(오버엔지니어링 판단, 2026-07-28 대화에서 배제 합의)
- 프론트 저장소에 자동으로 이슈를 생성하는 `repository_dispatch` 연동 — 현재 Discord 알림으로 충분(2026-07-28 대화)
- GitHub 저장소 Settings → Branches의 Required status check 등록 — job이 항상 통과하므로 이 스펙 범위에서는 의미가 없음(위 "실패 처리 범위" 참고 절)
- `oasdiff/oasdiff-action`(마켓플레이스) 사용 — CLI 직접 사용으로 대체(위 "설계 변경 이력" 2번)

## API / 인터페이스

API 없음 — CI 인프라 변경.

## 데이터 모델

- 신규 파일 `docs/api/openapi.json` — springdoc이 생성하는 OpenAPI 3.0 스펙의 스냅샷. **손으로 편집하지 않음**, `main` push 시 CI가 자동 갱신.
- 엔티티·DB 변경 없음.

## 비즈니스 규칙

해당 없음.

## 검증 시나리오

### 정상

- [ ] DTO에 필드 추가 등 non-breaking 변경 → `api-contract-check` 통과, Discord 알림 없음
- [ ] PR이 `main`에 merge된 뒤 `docs/api/openapi.json`이 병합된 스펙으로 자동 갱신됨을 확인, 갱신 커밋이 워크플로를 재트리거하지 않음(`[skip ci]`)

### 엣지 · 실패

- [ ] 필수 필드 삭제·타입 변경 등 breaking 변경 → `api-contract-check`는 통과(⛔ 아님) + Discord `#frontend` 채널에 알림 도착, `deploy`(push 케이스)는 그대로 진행됨을 확인
- [ ] `Breaking-Change-Reason:` 트레일러가 없는 커밋만 있는 breaking PR → Discord 메시지의 "왜 변경했는가"가 안내문으로 채워짐(빈 값/하드코딩 아님)
- [ ] `docs/api/openapi.json`이 아직 없는 최초 실행 → breaking 비교 스킵, 안내 로그만

### 수동 / 통합 (해당 시)

- [ ] 로컬에서 `./gradlew test --tests "*OpenApiSpecExportTest"` 실행 후 `build/openapi/openapi.json` 생성 확인
- [ ] 로컬에서 `oasdiff breaking` 직접 실행해 실제 JSON 출력 필드 구조 확인(파서 구현 전 1회 필수)
- [ ] 실제 breaking/non-breaking PR 각 1건씩 만들어 CI 결과(그린/레드)·Discord 알림 눈으로 확인

## 완료 기준

- [ ] `./gradlew test` 통과 (기존 test job 영향 없음 확인)
- [ ] `./gradlew build` 성공
- [ ] `.github/workflows/ci-cd.yml`의 `api-contract-check`가 실제 PR·push에서 breaking/non-breaking 두 케이스 모두 의도대로 동작(Discord 알림 여부 포함), job은 항상 통과하고 `deploy`도 breaking 여부와 무관하게 정상 진행됨을 확인
- [ ] `docs/api/openapi.json` 최초 시딩 커밋 완료
- [ ] 사용법 문서(`docs/api/README.md` 등) 작성
- [ ] OpenAPI/Swagger 반영 — 해당 없음(이 스펙 자체가 OpenAPI 활용 인프라)

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| `oasdiff breaking --format json`의 정확한 필드명 | [미정] | 공식 문서에 예시가 없음 — 구현 착수 시 실제 실행 결과로 확정(검증 시나리오 "수동/통합" 항목) |
| oasdiff의 "필드명 변경(rename)" 표현 가능 여부 | [미정] | OpenAPI 스키마 diff는 구조적으로 rename을 감지하지 못하고 delete+add 두 변경으로 보고하는 게 일반적 — `name → nickname`처럼 명시적 화살표 표기가 항상 가능한지는 미검증, 불가능하면 "속성 A 삭제 + 속성 B 추가" 형태로 메시지 포맷 조정 |
| GitHub 이슈 번호 | [미정] | 스펙 Approved 후 `gh issue create`로 생성, 브랜치명에 사용 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-03 | **Release Gate #65 콜아웃 제거** — `#5`·`#64`·`#86`(구 `#62`) 전부 Closed로 열려 있는 Release Gate 항목이 없어졌고, 메타 트래커 `#65`는 관측성 개선 스펙(`social-integration-structured-logging.md`)으로 재사용됨. `notify-api-breaking-change.sh`의 `is_gate_critical`·`gate_callout_field`·관련 embed 필드 전부 제거, `docs/api/README.md` 콜아웃 절도 삭제 |
| 2026-07-29 | **Amend #2** — 실제 알림에서 발견된 2가지 버그 수정: (1) breaking 문구 한글 템플릿 매핑을 전부 제거하고 oasdiff 원문(영어)만 노출 — 매핑 안 된 id가 많아 한 필드 안에서 한글·영어가 뒤섞이던 문제. (2) `Breaking-Change-Reason:` 트레일러가 여러 줄로 wrap된 커밋에서 사유가 중간에 잘려 다른 커밋 사유와 뒤섞이던 버그 수정 — 커밋별로 순회하며 wrap된 값을 접어 합치고 짧은 SHA를 붙여 나열하도록 재구현 |
| 2026-07-29 | **Amend** — breaking change 감지 시 `api-contract-check` job을 실패(`exit 1`) 처리하던 로직 제거, 항상 통과(`exit 0`)로 변경. Discord `#frontend` 알림만으로 충분하다는 판단(job 실패로 인한 CI 빨간불이 실질적 이득 없이 혼란만 유발). `notify-api-breaking-change.sh`·`docs/api/README.md` 동기화 |
| 2026-07-28 | 초안 |
| 2026-07-28 | Discord `#frontend` webhook 알림을 Must Have로 승격, "PR 체크리스트" 수동 Nice to Have 제거·대체 |
| 2026-07-28 | 사용자 요구사항(정확한 Discord 메시지 포맷·oasdiff CLI·"왜 변경했는가" 동적 전달)에 맞춰 전면 갱신 — oasdiff-action→CLI 전환, OpenAPI export를 JUnit 테스트 방식으로 전환, Commit 트레일러 채택, deploy 비차단 확정. **상태 Approved로 전환** |
| 2026-07-28 | Release Gate #65(로그인·탈퇴 API) 관련 엔드포인트 콜아웃을 Must Have로 추가 — 앱 심사 영향 API는 일반 breaking/추가 알림보다 눈에 띄게 표시하고 provider(GOOGLE/KAKAO/APPLE)를 함께 표시. 알림 봇 아바타를 TripFit 앱 아이콘(`docs/api/tripfit_app_icon.png`)으로 교체 |
