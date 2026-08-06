package com.tripfit.tripfit.user.googlecalendar.client;

import com.tripfit.tripfit.auth.oauth.OAuthProperties;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.googlecalendar.exception.GoogleCalendarAuthException;
import com.tripfit.tripfit.user.googlecalendar.exception.GoogleCalendarErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class GoogleCalendarOAuthClient {

  private static final Logger log = LoggerFactory.getLogger(GoogleCalendarOAuthClient.class);

  private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

  private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";

  private static final String FREE_BUSY_URL = "https://www.googleapis.com/calendar/v3/freeBusy";

  private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

  private static final String PRIMARY_CALENDAR_URL =
      "https://www.googleapis.com/calendar/v3/calendars/primary";

  private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  // Google freeBusy.query가 한 번에 너무 긴 range를 거부한다(문서화 안 된 제한, "timeRangeTooLong" 확인됨) —
  // C1 윈도우(최대 today+2년)를 이 단위로 잘라 여러 번 호출 후 병합한다
  private static final long FREE_BUSY_CHUNK_DAYS = 90;

  private final RestClient restClient;

  private final OAuthProperties oAuthProperties;

  public GoogleCalendarOAuthClient(RestClient restClient, OAuthProperties oAuthProperties) {
    this.restClient = restClient;
    this.oAuthProperties = oAuthProperties;
  }

  // authorization code → access·refresh token 교환 — Google 토큰 엔드포인트는 code를 발급받을 때 실제로 쓴
  // redirect_uri와 정확히 같은 값을 요구한다. 네이티브 앱(serverAuthCode)은 리다이렉트가 없어도 빈 문자열을 보내야
  // 하고, 브라우저 리다이렉트로 받은 code는 authorize 요청에 실제로 쓴 redirect_uri를 그대로 보내야 한다 — 로그인용
  // GoogleOAuthClient에서 동일 요건이 실계정 테스트로 확인됨
  public GoogleOAuthTokenResponse exchangeAuthorizationCode(
      String authorizationCode,
      String redirectUri) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("code", authorizationCode);
    form.add("client_id", oAuthProperties.getGoogleCalendarClientId());
    form.add("client_secret", oAuthProperties.getGoogleCalendarClientSecret());
    form.add("redirect_uri", redirectUri == null ? "" : redirectUri);
    form.add("grant_type", "authorization_code");
    try {
      JsonNode response = postTokenForm(form);
      return parseTokenResponse(response, true);
    } catch (GoogleCalendarAuthException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new TripFitException(GoogleCalendarErrorCode.GOOGLE_CALENDAR_CONNECT_FAILED);
    }
  }

  // refresh token → access token 갱신
  public GoogleOAuthTokenResponse refreshAccessToken(String refreshToken) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("refresh_token", refreshToken);
    form.add("client_id", oAuthProperties.getGoogleCalendarClientId());
    form.add("client_secret", oAuthProperties.getGoogleCalendarClientSecret());
    form.add("grant_type", "refresh_token");
    try {
      JsonNode response = postTokenForm(form);
      return parseTokenResponse(response, false);
    } catch (GoogleCalendarAuthException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new GoogleCalendarAuthException("refresh failed", exception);
    }
  }

  // primary 캘린더 freeBusy 조회 — FREE_BUSY_CHUNK_DAYS 단위로 여러 번 호출해 병합(단일 호출은 아래
  // queryFreeBusyChunk). 한 청크라도 실패하면 전체를 예외로 던짐(부분 sync 반영 안 함, 다음 스케줄에서 통째로 재시도)
  public List<GoogleFreeBusyInterval> queryFreeBusy(
      String accessToken,
      Instant timeMin,
      Instant timeMax) {
    List<GoogleFreeBusyInterval> merged = new ArrayList<>();
    Instant chunkStart = timeMin;
    while (chunkStart.isBefore(timeMax)) {
      Instant chunkEnd = chunkStart.plus(FREE_BUSY_CHUNK_DAYS, ChronoUnit.DAYS);
      if (chunkEnd.isAfter(timeMax)) {
        chunkEnd = timeMax;
      }
      merged.addAll(queryFreeBusyChunk(accessToken, chunkStart, chunkEnd));
      chunkStart = chunkEnd;
    }
    return merged;
  }

  // freeBusy 단일 청크 호출 — 401(access token 무효)만 인증 실패로 간주해 GoogleCalendarAuthException을
  // 던진다. 그 외 4xx/5xx·네트워크·파싱 오류는 일반 RuntimeException으로 던져 syncUserInternal의 일반 catch로
  // 흘러가게 한다 — 여기서도 GoogleCalendarAuthException을 쓰면 connect() 직후 1회 sync가 일시적 오류(429·5xx 등)
  // 만 만나도 방금 저장한 credential이 같은 트랜잭션에서 즉시 삭제돼버린다
  private List<GoogleFreeBusyInterval> queryFreeBusyChunk(
      String accessToken,
      Instant timeMin,
      Instant timeMax) {
    String body =
        """
            {
              "timeMin": "%s",
              "timeMax": "%s",
              "timeZone": "Asia/Seoul",
              "items": [{"id": "primary"}]
            }
            """
            .formatted(
                RFC3339.format(timeMin.atZone(java.time.ZoneId.of("Asia/Seoul"))),
                RFC3339.format(timeMax.atZone(java.time.ZoneId.of("Asia/Seoul"))));
    try {
      JsonNode response =
          restClient
              .post()
              .uri(FREE_BUSY_URL)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .onStatus(
                  HttpStatusCode::isError,
                  (request, clientResponse) -> {
                    if (clientResponse.getStatusCode().value() == 401) {
                      throw new GoogleCalendarAuthException("freeBusy unauthorized");
                    }
                    // TEMP(디버그): 400 원인 확정을 위해 Google 에러 응답 body까지 로그에 남긴다 — 원인
                    // 확정되면 body 캡처는 지우고 상태 코드만 남기는 원래 형태로 되돌릴 것
                    String errorBody =
                        StreamUtils.copyToString(
                            clientResponse.getBody(),
                            StandardCharsets.UTF_8);
                    throw new RuntimeException(
                        "freeBusy failed: "
                            + clientResponse.getStatusCode()
                            + " body="
                            + errorBody);
                  })
              .body(JsonNode.class);
      return parseFreeBusyIntervals(response);
    } catch (GoogleCalendarAuthException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new RuntimeException("freeBusy request failed", exception);
    }
  }

  // 연동 Google 계정 이메일 조회 — userinfo 우선, 실패 시 primary calendar id fallback (없으면 null)
  public String fetchGoogleAccountEmail(String accessToken) {
    String fromUserInfo = fetchEmailFromUserInfo(accessToken);
    if (fromUserInfo != null) {
      return fromUserInfo;
    }
    return fetchEmailFromPrimaryCalendar(accessToken);
  }

  // refresh token revoke (best-effort)
  public void revokeRefreshToken(String refreshToken) {
    try {
      restClient
          .post()
          .uri(REVOKE_URL + "?token=" + refreshToken)
          .retrieve()
          .toBodilessEntity();
    } catch (Exception exception) {
      // best-effort — disconnect·탈퇴는 로컬 정리가 SSOT라 예외를 삼키되, 실패 자체는 로그로 남김(토큰 값은 남기지 않음)
      log.warn("Google Calendar refresh token revoke failed", exception);
    }
  }

  private String fetchEmailFromUserInfo(String accessToken) {
    try {
      JsonNode response =
          restClient
              .get()
              .uri(USERINFO_URL)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
              .retrieve()
              .body(JsonNode.class);
      if (response != null && response.hasNonNull("email")) {
        String email = response.get("email").asText();
        if (email != null && !email.isBlank()) {
          return email.trim();
        }
      }
    } catch (Exception ignored) {
      // scope에 email 없을 수 있음 — primary calendar로 fallback
    }
    return null;
  }

  private String fetchEmailFromPrimaryCalendar(String accessToken) {
    try {
      JsonNode response =
          restClient
              .get()
              .uri(PRIMARY_CALENDAR_URL)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
              .retrieve()
              .body(JsonNode.class);
      if (response != null && response.hasNonNull("id")) {
        String id = response.get("id").asText();
        if (id != null && id.contains("@")) {
          return id.trim();
        }
      }
    } catch (Exception ignored) {
      // freeBusy-only scope 등 — 이메일 미저장 허용
    }
    return null;
  }

  private JsonNode postTokenForm(MultiValueMap<String, String> form) {
    JsonNode response =
        restClient
            .post()
            .uri(TOKEN_URL)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                (request, clientResponse) -> {
                  String body =
                      StreamUtils.copyToString(clientResponse.getBody(), StandardCharsets.UTF_8);
                  throw new GoogleCalendarAuthException(
                      "token endpoint error: "
                          + clientResponse.getStatusCode()
                          + " body="
                          + body);
                })
            .body(JsonNode.class);
    if (response == null) {
      throw new GoogleCalendarAuthException("empty token response");
    }
    if (response.has("error")) {
      String error = response.get("error").asText();
      if ("invalid_grant".equals(error)) {
        throw new GoogleCalendarAuthException("invalid_grant");
      }
      throw new GoogleCalendarAuthException("token error: " + error);
    }
    return response;
  }

  private GoogleOAuthTokenResponse parseTokenResponse(JsonNode response, boolean requireRefresh) {
    if (!response.has("access_token")) {
      throw new GoogleCalendarAuthException("missing access_token");
    }
    String accessToken = response.get("access_token").asText();
    String refreshToken =
        response.has("refresh_token") ? response.get("refresh_token").asText() : null;
    if (requireRefresh && (refreshToken == null || refreshToken.isBlank())) {
      throw new TripFitException(GoogleCalendarErrorCode.GOOGLE_CALENDAR_CONNECT_FAILED);
    }
    long expiresIn = response.has("expires_in") ? response.get("expires_in").asLong(3600) : 3600;
    Instant expiresAt = Instant.now().plusSeconds(Math.max(0, expiresIn - 60));
    return new GoogleOAuthTokenResponse(accessToken, refreshToken, expiresAt);
  }

  private List<GoogleFreeBusyInterval> parseFreeBusyIntervals(JsonNode response) {
    List<GoogleFreeBusyInterval> intervals = new ArrayList<>();
    if (response == null || !response.has("calendars")) {
      return intervals;
    }
    JsonNode primary = response.get("calendars").get("primary");
    if (primary == null || !primary.has("busy")) {
      return intervals;
    }
    for (JsonNode busy : primary.get("busy")) {
      if (!busy.has("start") || !busy.has("end")) {
        continue;
      }
      Instant start = Instant.parse(busy.get("start").asText());
      Instant end = Instant.parse(busy.get("end").asText());
      if (end.isAfter(start)) {
        intervals.add(new GoogleFreeBusyInterval(start, end));
      }
    }
    return intervals;
  }
}
