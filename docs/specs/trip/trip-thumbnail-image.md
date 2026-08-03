# 여행방 확정 일정 동적 썸네일 이미지 생성

> wave: TBD (Backlog #29~#32 미반영 — Wave 배치는 Backlog 확인 후 결정)
> deferred from: [`kakao-invite-share.md`](kakao-invite-share.md) (Approved, wave 3) — B(확정 후 공유) 모드 데이터 확장
> 상태: Draft
> MVP: TBD
> Issue: [#62](https://github.com/Central-MakeUs/TripFit-server/issues/62)

## 목표

여행방이 **확정(`CONFIRMED`)**되면, 사용자가 사진을 업로드하지 않아도 서버가 확정 기간(예: `6.12~6.15`)을 베이스 이미지 위에 합성한 썸네일을 자동 생성해, 카카오톡 공유 시 매번 다른 날짜가 보이는 이미지로 쓸 수 있게 한다.

## 배경

- 요청 계기: 카카오톡 공유 썸네일도 방마다 확정 날짜에 맞춰 동적으로 바뀌었으면 좋겠다는 요구 (2026-08-02)
- 참고 링크(요청자 제공): [메시지 템플릿 공통](https://developers.kakao.com/docs/latest/ko/message-template/common) · [커스텀 템플릿](https://developers.kakao.com/docs/latest/ko/message-template/custom)
- **기존 계약과의 관계 — 충돌 아님, 확장:** [`kakao-invite-share.md`](kakao-invite-share.md)(Approved)는 "카카오 SDK·템플릿 조립·발송은 프론트, 서버는 공유용 **데이터만**"이 원칙이고 Out of Scope에 "템플릿 DB·카카오 서버 대행"이 명시돼 있다. 이 스펙은 그 원칙을 깨지 않는다 — 서버가 카카오 API를 직접 호출해 발송하거나 템플릿을 저장하는 게 아니라, `confirmedStartDate`/`confirmedEndDate`처럼 **상세 API에 이미지 URL 필드 하나를 추가로 노출**하는 것뿐이다. 그 URL을 카카오 SDK 템플릿(`imageUrl` 등)에 넣는 조립은 여전히 프론트가 한다.
- 반대로 **인프라 관점에서는 이 저장소에 완전히 새로운 축**이다. 이미지 파일을 만들어 공개 URL로 서빙해야 하는데, 오브젝트 스토리지(S3 등) 연동이 현재 **전혀 없다** — 유일하게 근접한 문서인 [`user-profile-image-s3-mirror.md`](../user/user-profile-image-s3-mirror.md)도 Wave 4 **Draft**(미구현) 상태이고 `build.gradle`에 AWS SDK 의존성도 없다.

## 카카오 이미지 요구사항 (공식 문서 확인, 2026-08-02)

| 항목 | 값 |
|------|-----|
| 크기 | 최소 400×400px ~ 최대 800×800px (커스텀 템플릿 기준) |
| 종횡비 | 최소 2:1 ~ 최대 3:4 |
| 파일 용량 | 5MB 이하 |
| URL 요구사항 | 로컬 경로 불가 — 공개 접근 가능한 이미지 URL 필요 (HTTPS 명시는 문서에 없으나 프로덕션은 어차피 `api.tripfit.online` HTTPS만 사용) |
| 동적 이미지 | **가능** — 발송 시점마다 다른 이미지 URL을 넣을 수 있음 (커스텀 템플릿 "사용자 인자" 기준. 단, 지금 구조는 카카오 서버 발송 API가 아니라 프론트 카카오링크 SDK 공유이므로 이 필드는 "링크 SDK가 읽는 이미지 URL"로 대체 적용) |
| 카카오 이미지 캐싱/크롤링 정책 | 문서에 명시 없음 — URL이 바뀌면 캐시가 갱신되는지 확인 안 됨. **안전하게 URL에 트립 ID·버전을 포함해 캐시 무효화** 권장 |

## 기술적 실현 가능성 — 가능

Java 표준 라이브러리(`java.awt.Graphics2D` + `BufferedImage` + `ImageIO`)만으로 베이스 이미지 위에 텍스트를 합성해 PNG로 인코딩할 수 있다. 외부 이미지 처리 라이브러리는 불필요하다. 다만 실제로 서버에서 안정적으로 동작하려면 아래 세 가지를 새로 갖춰야 한다.

1. **한글 폰트 번들링** — 대부분의 slim JDK 컨테이너 이미지엔 한글 글리프가 없어 렌더링 시 텍스트가 네모(tofu)로 깨진다. `.ttf` 파일을 리소스로 포함해 `Font.createFont()`로 로드해야 함. (날짜 텍스트 자체는 숫자·`.`·`~`뿐이라 실제로는 한글 폰트가 필수는 아닐 수 있음 — 문구에 "6월 12일" 같은 한글이 들어가는지에 따라 갈림, 아래 `[미정]` 참고)
2. **베이스 템플릿 이미지** — 디자이너가 제공하는 정적 배경 이미지(코드 자산 아님). 여러 종류(테마별)를 지원할지도 `[미정]`.
3. **오브젝트 스토리지** — 생성된 PNG를 공개 URL로 서빙할 S3(또는 동급) 버킷·IAM·업로드 클라이언트가 **이번에 신규로 구축**돼야 함.

## 설계 초안

```
Trip.status → CONFIRMED 전이 시점 (trip-schedule-snapshot.md 확정 로직과 동일 훅)
        ↓
TripThumbnailService.generate(tripId, confirmedStartDate, confirmedEndDate)
        ↓
베이스 템플릿 PNG 로드 → Graphics2D로 날짜 텍스트 합성 → ImageIO.write(PNG)
        ↓
S3 putObject (key 예: trips/{tripId}/thumbnail-{version}.png)
        ↓
trip.thumbnail_image_url = 공개 URL
        ↓
GET /api/v1/trips/{tripId} 응답에 thumbnailImageUrl 필드로 노출 (B 모드 확정 공유에서 프론트가 사용)
```

## 요구사항

### Must Have

- [ ] 확정(`CONFIRMED`) 전이 시 썸네일 자동 생성 — 실패해도 확정 자체는 성공(비동기 또는 실패 시 null 유지, [`trip-schedule-snapshot.md`](trip-schedule-snapshot.md) 확정 흐름 블로킹 금지)
- [ ] `GET /trips/{tripId}` 응답에 `thumbnailImageUrl` 필드 추가 (없으면 null)
- [ ] S3(또는 합의된 오브젝트 스토리지) 연동 신규 구축 — bucket·IAM·env
- [ ] 카카오 이미지 규격(400~800px, 2:1~3:4, 5MB 이하) 준수 검증

### Nice to Have

- [ ] 확정 일정 변경 시(있다면) 재생성
- [ ] 테마별 베이스 이미지 다중 지원

### Out of Scope (이번 스펙에서 하지 않음)

- 카카오 서버 대행 발송 · 템플릿 DB 저장 (`kakao-invite-share.md` 원칙 유지)
- 사용자 직접 사진 업로드 썸네일
- A(확정 전 초대)·C(응답 재촉) 모드 썸네일 — 확정 기간이 없어 대상 아님

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| `GET` | `/api/v1/trips/{tripId}` | JWT | 기존 응답에 `thumbnailImageUrl`(nullable) 필드 추가만 — 신규 엔드포인트 없음 |

**Breaking-Change-Reason 대상 여부:** optional 필드 추가이지만 프론트가 카카오 템플릿 조립 로직에서 이 필드를 실제로 써야 하므로 `harness-workflow.md` STOP §5 기준 트레일러 대상 — 구현 커밋에 포함 필요.

## 데이터 모델

- ERD 참조: `docs/architecture/erd.md` — `trip` 테이블
- 신규 컬럼: `trip.thumbnail_image_url` (nullable, varchar) — `profile_image_url`과 동일하게 URL 문자열만 저장

## 공수 추정 (러프, 백엔드 단독 기준)

| 항목 | 추정 |
|------|------|
| S3 연동(버킷·IAM·env·업로드 클라이언트) — `user-profile-image-s3-mirror.md` 설계 재사용 가능 | 0.5~1일 |
| 이미지 합성 서비스(Graphics2D + 폰트 번들링 + 좌표) | 1~2일 (베이스 이미지 시안 확정 지연 시 좌표 튜닝 왕복 늘어남) |
| 확정 훅 연결 + API 필드 노출 + 실패 정책 | 0.5~1일 |
| 테스트 | 0.5일 |
| **합계** | **약 3~5일** — 디자인 시안(베이스 이미지)이 사전에 확정돼 있다는 전제 |

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 베이스 템플릿 이미지(디자인 자산) | [미정] | 디자이너 제공 필요 — 코드 작업 시작 전 선행 |
| 오브젝트 스토리지 선택(S3 vs 다른 서비스) | [미정] | `user-profile-image-s3-mirror.md`·decision 006과 별개로 이 스펙에서 먼저 확정할지, 그 스펙 Approve를 기다릴지 결정 필요 |
| 날짜 텍스트에 한글 포함 여부("6월 12일" vs "6.12") | [미정] | 한글 폰트 번들링 필요 여부에 영향 |
| 재생성 정책(확정 후 일정 변경 시) | [미정] | 확정 후 일정이 실제로 변경될 수 있는지 `trip-schedule-snapshot.md`와 대조 필요 |
| 카카오 클라이언트/크롤러의 이미지 캐싱 동작 | [미정] | 공식 문서에 명시 없음 — 실기기 테스트로 확인 필요 |
| Wave 배치 | [미정] | Backlog(#29~#32)에 없음 — 사용자 확인 필요 |

## 완료 기준

- [ ] `./gradlew test` 통과
- [ ] `./gradlew build` 성공
- [ ] 확정된 여행방 상세 조회 시 `thumbnailImageUrl`이 카카오 규격을 만족하는 이미지를 가리킴
- [ ] OpenAPI/Swagger 반영

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-02 | Draft — 요청 접수, 카카오 이미지 규격 확인, 공수 추정, `#62`로 이슈 신설(기존 #62 OAuth 콘솔 체크리스트는 `#86`으로 이관) |
