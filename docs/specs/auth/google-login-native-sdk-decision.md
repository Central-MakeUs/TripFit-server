# Google 로그인 — 네이티브 SDK vs 시스템 브라우저 탈출 결정

> 상태: **Resolved (2026-07-31) — 결정 불필요, 이미 구현됨**
> MVP: 해당 없음 — 결정 전용 문서
> 관련 BR: 해당 없음
> Issue: [#77](https://github.com/Central-MakeUs/TripFit-server/issues/77)
> deferred from: [`google-login-revoke.md`](google-login-revoke.md) Out of Scope

## 정정 (2026-07-31)

이 문서는 최초 작성 시 "네이티브 SDK vs 시스템 브라우저 탈출 중 어느 쪽을 쓸지 FE가 아직 결정하지 않았다"는 전제로 작성됐다. **그 전제가 틀렸다.** FE 코드(`apps/app/utils/socialLogin.ts`, `apps/app/apis/nativeBridge.ts`) 확인 결과:

- Google/Kakao/Apple **세 provider 모두 네이티브 SDK가 이미 완전히 구현돼 있음** — `@react-native-google-signin/google-signin`, `@react-native-seoul/kakao-login`, `expo-apple-authentication`
- `nativeBridge.ts`의 `requestSocialToken(provider, redirectToAuthorize)`가 `isReactNativeWebView()`로 이미 환경 분기 중: 네이티브 앱 안에서는 `requestNativeSocialLogin`(네이티브 SDK)으로, 일반 브라우저에서는 `redirectToAuthorize`(웹 리다이렉트)로 위임
- 즉 아래 "옵션 (b)"는 이미 선택·구현이 끝난 상태였고, 이 문서가 걱정한 "네이티브 SDK를 새로 만들어야 하나" 자체가 해당 없는 질문이었음

**남은 실제 작업**은 결정이 아니라, `google-login-revoke.md`의 FE 변경 요건 절에 정리된 대로 — 이미 있는 `GoogleSignin.configure()`에 `offlineAccess: true`를 추가하고 `serverAuthCode`를 `authorizationCode`로 실어 보내는 것뿐이다. 이 문서가 우려했던 "PKCE·client_id 이원화 별도 스펙 필요"도 근거가 없었다 — `serverAuthCode`는 플랫폼 무관하게 항상 webClientId로 교환되도록 라이브러리가 설계돼 있어, Apple과 달리 client_id가 갈리지 않는다.

이 문서는 같은 실수(전제 확인 없이 "결정 필요"로 단정)를 남기지 않기 위한 기록으로 아래 원본 내용을 보존한다.

---

## (원본, 2026-07-31 초안 — 아래는 틀린 전제로 작성된 내용)

## 목표

TripFit 앱(WebView 패키징 확정, 2026-07-31)에서 Google 로그인을 열 때 **네이티브 Google Sign-In SDK**를 쓸지, **시스템 브라우저로 탈출**해 지금의 웹 리다이렉트를 재사용할지 결정한다.

## 배경

- 앱 패키징은 WebView로 확정됐다(React 화면을 네이티브 셸의 WebView에 띄움).
- 그런데 Google은 보안상 WebView 내부에서 자신의 OAuth 로그인 화면을 여는 것을 차단한다(`403 disallowed_useragent`) — [`platform.md`](../../product/platform.md) "Google 로그인 · Calendar — WebView / 인앱 차단" 절.
- 즉 패키징이 WebView로 정해졌다고 해서 "Google 로그인도 WebView 안에서 그냥 열면 된다"가 성립하지 않는다. Google 로그인 순간만큼은 WebView를 벗어나야 한다.
- `google-login-revoke.md`(#64 후속)는 이 결정과 무관하게 **지금의 웹 리다이렉트 방식**(단일 Web Client ID)을 전제로 구현 가능해 별도로 진행하지만, 이 문서의 결정에 따라 **추가 스펙(client_id 이원화·PKCE)** 이 필요해질 수 있다.

## 옵션

### (a) 시스템 브라우저 탈출

- WebView 밖으로 나가 iOS `ASWebAuthenticationSession` / Android Custom Tabs 같은, OS가 제공하는 보안 브라우저 세션을 띄운다.
- Google 계정 선택은 시스템 브라우저 UI에서 이뤄지고, 완료 후 커스텀 스킴/Universal Link로 앱에 복귀한다.
- **지금 쓰는 Web Client ID + 리다이렉트 코드를 그대로 재사용** — `google-login-revoke.md`가 이미 이 경로를 전제로 설계돼 있어 백엔드 추가 작업 없음.
- iOS에서는 일반 `SFSafariViewController`나 URL scheme 오픈이 아니라 **`ASWebAuthenticationSession`을 쓰는 것이 Apple 권장·심사상 안전**(서드파티 인증 UI에 일반 WebView·비표준 브라우저 호출을 쓰면 리젝 사유가 될 수 있음).

### (b) 네이티브 Google Sign-In SDK — **실제로 이미 이 옵션이 구현돼 있었음**

- iOS `GoogleSignIn` SDK / Android Credential Manager 등으로 OS 레벨 계정 선택 UI를 앱 안에서 직접 띄움(컨텍스트 전환 없음, UX가 더 매끄러움).
- ~~iOS/Android 전용 OAuth Client ID(client_secret 없는 "퍼블릭 클라이언트")가 신규로 필요~~ — **틀림.** `@react-native-google-signin/google-signin`은 `webClientId` 설정만으로 동작하며, 별도 퍼블릭 클라이언트 등록이 강제되지 않는다.
- ~~코드 교환 방식이 client_secret이 아니라 PKCE로 바뀜~~ — **틀림.** `serverAuthCode`(offlineAccess 옵션)는 기존 Web Client ID + client_secret 교환 방식 그대로 통한다.
- ~~Apple 로그인에서 겪은 Bundle ID/Services ID 이원화와 유사한 client_id 다중화 문제가 재발할 가능성~~ — **틀림.** serverAuthCode는 플랫폼 무관 단일 webClientId로만 교환됨.

## 비교 (참고용 — 실제로는 (b)가 이미 선택·구현됨)

| | (a) 시스템 브라우저 탈출 | (b) 네이티브 SDK (실제 채택) |
|---|---|---|
| 백엔드 추가 작업 | 없음 | 없음(당초 우려와 달리 추가 작업 불필요로 확인됨) |
| FE 추가 작업 | 딥링크 복귀 처리 | 이미 구현됨 — `offlineAccess` 옵션 추가만 남음 |
| 로그인 UX | 앱 밖으로 잠깐 나갔다 돌아옴 | 앱 안에서 끊김 없음 |
| 리스크 | 상대적으로 낮음 | 당초 우려(Apple 이원화 반복)와 달리 리스크 없음 |

## 완료 기준

- [x] ~~FE/네이티브 셸 담당과 (a)/(b) 중 결정~~ → 이미 (b)로 구현돼 있음을 확인, 별도 결정 불필요
- [x] ~~(b) 선택 시 후속 스펙 작성 착수~~ → 후속 스펙 불필요, `google-login-revoke.md`의 FE 변경 요건 절에 필요한 내용(offlineAccess 추가) 전부 반영 완료

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-31 | 초안 — `google-login-revoke.md` Out of Scope에서 분리 (틀린 전제: "결정 필요") |
| 2026-07-31 | **Resolved** — FE 코드 확인 결과 네이티브 SDK 이미 구현·배포 구조 완료. 결정·후속 스펙 불필요 |
