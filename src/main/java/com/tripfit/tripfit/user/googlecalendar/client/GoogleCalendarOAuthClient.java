package com.tripfit.tripfit.user.googlecalendar.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripfit.tripfit.auth.oauth.OAuthProperties;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.googlecalendar.exception.GoogleCalendarAuthException;
import com.tripfit.tripfit.user.googlecalendar.exception.GoogleCalendarErrorCode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class GoogleCalendarOAuthClient {

  private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

  private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";

  private static final String FREE_BUSY_URL = "https://www.googleapis.com/calendar/v3/freeBusy";

  private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

  private static final String PRIMARY_CALENDAR_URL =
      "https://www.googleapis.com/calendar/v3/calendars/primary";

  private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  private final RestClient restClient;

  private final OAuthProperties oAuthProperties;

  public GoogleCalendarOAuthClient(RestClient restClient, OAuthProperties oAuthProperties) {
    this.restClient = restClient;
    this.oAuthProperties = oAuthProperties;
  }

  // authorization code → access·refresh token 교환
  public GoogleOAuthTokenResponse exchangeAuthorizationCode(String authorizationCode) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("code", authorizationCode);
    form.add("client_id", oAuthProperties.getGoogleClientId());
    form.add("client_secret", oAuthProperties.getGoogleClientId());
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
    form.add("client_id", oAuthProperties.getGoogleClientId());
    form.add("client_secret", oAuthProperties.getGoogleClientId());
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

  // primary 캘린더 freeBusy 조회
  public List<GoogleFreeBusyInterval> queryFreeBusy(
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
                    throw new GoogleCalendarAuthException(
                        "freeBusy failed: " + clientResponse.getStatusCode());
                  })
              .body(JsonNode.class);
      return parseFreeBusyIntervals(response);
    } catch (GoogleCalendarAuthException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new GoogleCalendarAuthException("freeBusy request failed", exception);
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
    } catch (Exception ignored) {
      // best-effort — disconnect는 로컬 정리가 SSOT
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
                  throw new GoogleCalendarAuthException(
                      "token endpoint error: " + clientResponse.getStatusCode());
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
