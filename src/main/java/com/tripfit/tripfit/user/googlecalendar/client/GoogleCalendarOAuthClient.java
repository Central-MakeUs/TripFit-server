package com.tripfit.tripfit.user.googlecalendar.client;

import com.tripfit.tripfit.auth.oauth.OAuthProperties;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.googlecalendar.exception.GoogleCalendarAuthException;
import com.tripfit.tripfit.user.googlecalendar.exception.GoogleCalendarErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static final long FREE_BUSY_CHUNK_DAYS = 90;

  private static final Pattern ERROR_REASON_PATTERN =
      Pattern.compile("\"reason\"\\s*:\\s*\"([^\"]+)\"");

  private static final Set<String> PERMANENT_PERMISSION_FAILURE_REASONS =
      Set.of("insufficientPermissions", "ACCESS_TOKEN_SCOPE_INSUFFICIENT");

  private final RestClient restClient;

  private final OAuthProperties oAuthProperties;

  public GoogleCalendarOAuthClient(RestClient restClient, OAuthProperties oAuthProperties) {
    this.restClient = restClient;
    this.oAuthProperties = oAuthProperties;
  }

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

  public List<GoogleFreeBusyInterval> queryFreeBusy(
      UUID userId,
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
      merged.addAll(queryFreeBusyChunk(userId, accessToken, chunkStart, chunkEnd));
      chunkStart = chunkEnd;
    }
    return merged;
  }

  private List<GoogleFreeBusyInterval> queryFreeBusyChunk(
      UUID userId,
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
                RFC3339.format(timeMin.atZone(SEOUL)),
                RFC3339.format(timeMax.atZone(SEOUL)));
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

                    String errorBody =
                        StreamUtils.copyToString(
                            clientResponse.getBody(),
                            StandardCharsets.UTF_8);
                    String reason = extractReason(errorBody);
                    if (PERMANENT_PERMISSION_FAILURE_REASONS.contains(reason)) {
                      throw new GoogleCalendarAuthException(
                          "freeBusy forbidden. permanent scope failure (reason=" + reason + ")");
                    }
                    throw new FreeBusyHttpException(clientResponse.getStatusCode().value(),
                        errorBody);
                  })
              .body(JsonNode.class);
      return parseFreeBusyIntervals(response);
    } catch (GoogleCalendarAuthException exception) {
      throw exception;
    } catch (FreeBusyHttpException exception) {

      SocialIntegrationLog.warn(
          log,
          SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.CALENDAR_SYNC)
              .withUserId(userId)
              .withHttpStatus(exception.httpStatus)
              .withProviderError(extractReason(exception.body), exception.body),
          "Google Calendar freeBusy request failed");
      throw new RuntimeException(
          "freeBusy failed: " + exception.httpStatus + " body=" + exception.body);
    } catch (Exception exception) {

      SocialIntegrationLog.warn(
          log,
          SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.CALENDAR_SYNC)
              .withUserId(userId),
          "Google Calendar freeBusy request failed unexpectedly",
          exception);
      throw new RuntimeException("freeBusy request failed", exception);
    }
  }

  private static final class FreeBusyHttpException extends RuntimeException {

    private final int httpStatus;

    private final String body;

    private FreeBusyHttpException(int httpStatus, String body) {
      super("freeBusy failed: " + httpStatus + " body=" + body);
      this.httpStatus = httpStatus;
      this.body = body;
    }
  }

  private String extractReason(String errorBody) {
    if (errorBody == null) {
      return null;
    }
    Matcher matcher = ERROR_REASON_PATTERN.matcher(errorBody);
    String last = null;
    while (matcher.find()) {
      last = matcher.group(1);
    }
    return last;
  }

  public String fetchGoogleAccountEmail(String accessToken) {
    String fromUserInfo = fetchEmailFromUserInfo(accessToken);
    if (fromUserInfo != null) {
      return fromUserInfo;
    }
    return fetchEmailFromPrimaryCalendar(accessToken);
  }

  public void revokeRefreshToken(UUID userId, String refreshToken) {
    try {
      restClient
          .post()
          .uri(REVOKE_URL + "?token=" + refreshToken)
          .retrieve()
          .toBodilessEntity();
    } catch (Exception exception) {

      SocialIntegrationLog.warn(
          log,
          SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.CALENDAR_TOKEN_REVOKE)
              .withUserId(userId),
          "Google Calendar refresh token revoke failed",
          exception);
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
    String scope = response.hasNonNull("scope") ? response.get("scope").asText() : null;
    return new GoogleOAuthTokenResponse(accessToken, refreshToken, expiresAt, scope);
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
