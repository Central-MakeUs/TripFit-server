package com.tripfit.tripfit.trip.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.tripfit.tripfit.common.config.TestcontainersConfig;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class TripFullLifecycleIntegrationTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private UserRepository userRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  private static String extract(String field, String body) {
    Matcher matcher = Pattern.compile("\"" + field + "\":\"?([^\",}]+)\"?").matcher(body);
    if (!matcher.find()) {
      throw new IllegalStateException("field not found: " + field + " in " + body);
    }
    return matcher.group(1);
  }

  @Test
  void fullLifecycle_createThroughConfirmLeaveUnconfirmReconfirmDelete_allTransitionsValid()
      throws Exception {
    User owner =
        new User(
            "lifecycle-owner-" + UUID.randomUUID(), SocialProvider.GOOGLE, "lo@example.com",
            "방장", null);
    owner.applyProfilePatch("길동", "홍", null);
    owner.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
    owner = userRepository.save(owner);
    String ownerToken = jwtService.createAccessToken(owner.getId());

    User member =
        new User(
            "lifecycle-member-" + UUID.randomUUID(), SocialProvider.GOOGLE, "lm@example.com",
            "참여자", null);
    member.applyProfilePatch("철수", "김", null);
    member.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
    member = userRepository.save(member);
    String memberToken = jwtService.createAccessToken(member.getId());

    User thirdUser =
        new User(
            "lifecycle-third-" + UUID.randomUUID(), SocialProvider.GOOGLE, "lt@example.com",
            "제3자", null);
    thirdUser.applyProfilePatch("영희", "박", null);
    thirdUser.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
    thirdUser = userRepository.save(thirdUser);
    String thirdToken = jwtService.createAccessToken(thirdUser.getId());

    LocalDate startRange = LocalDate.now().plusDays(30);
    LocalDate endRange = startRange.plusDays(6);

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/trips")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": \"전체 생명주기 테스트\", \"startRange\": \"" + startRange
                            + "\", \"endRange\": \"" + endRange
                            + "\", \"durationNights\": 3, \"durationDays\": 4,"
                            + " \"memberCount\": 6}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("ONGOING"))
            .andExpect(jsonPath("$.data.myMemberStatus").value("SCHEDULE_PENDING"))
            .andReturn();
    String tripId = extract("tripId", createResult.getResponse().getContentAsString());

    MvcResult activateResult =
        mockMvc
            .perform(
                post("/api/v1/trips/" + tripId + "/activate")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.myMemberStatus").value("ACTIVE"))
            .andReturn();
    String inviteCode = extract("inviteCode", activateResult.getResponse().getContentAsString());

    mockMvc
        .perform(
            post("/api/v1/trips/join")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteCode\": \"" + inviteCode + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.myMemberStatus").value("SCHEDULE_PENDING"))
        .andExpect(jsonPath("$.data.inviteCode").doesNotExist());

    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SCHEDULE_ACTIVATION_REQUIRED"));

    mockMvc
        .perform(
            post("/api/v1/trips/" + tripId + "/activate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.myMemberStatus").value("ACTIVE"));

    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId + "/members")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.activeMemberCount").value(2));

    mockMvc
        .perform(
            post("/api/v1/trips/" + tripId + "/recommendations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"BASIC\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(3))
        .andExpect(jsonPath("$.data.items[0].attendRate").value(100));

    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId + "/recommendations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("TRIP_FORBIDDEN"));

    mockMvc
        .perform(
            patch("/api/v1/trips/" + tripId + "/recommendations/1/feedback")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"HELPFUL\"}"))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/trips/" + tripId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recommendationRank\": 1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.data.confirmedAttendCount").value(2));

    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId + "/members/schedule-calendar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.readOnly").value(true));

    mockMvc
        .perform(
            patch("/api/v1/trips/" + tripId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"수정시도\", \"durationNights\": 3, \"durationDays\": 4,"
                        + " \"memberCount\": 6}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TRIP_NOT_ONGOING"));

    mockMvc
        .perform(
            post("/api/v1/trips/join")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + thirdToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteCode\": \"" + inviteCode + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TRIP_ALREADY_CONFIRMED"));

    mockMvc
        .perform(
            delete("/api/v1/trips/" + tripId + "/members/" + member.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TRIP_NOT_ONGOING"));

    mockMvc
        .perform(
            delete("/api/v1/trips/" + tripId + "/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/trips/" + tripId + "/unconfirm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"NEW_SCHEDULE_ADDED\"}"))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ONGOING"))
        .andExpect(jsonPath("$.data.confirmedStartDate").doesNotExist());

    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId + "/recommendations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(0));

    mockMvc
        .perform(
            post("/api/v1/trips/" + tripId + "/recommendations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"BASIC\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].attendRate").value(100));

    mockMvc
        .perform(
            patch("/api/v1/trips/" + tripId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"재조율 완료\", \"durationNights\": 3, \"durationDays\": 4,"
                        + " \"memberCount\": 6}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("재조율 완료"));

    LocalDate customStart = startRange.plusDays(1);
    LocalDate customEnd = customStart.plusDays(3);
    mockMvc
        .perform(
            post("/api/v1/trips/" + tripId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"startDate\": \"" + customStart + "\", \"endDate\": \"" + customEnd
                        + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.data.confirmedStartDate").value(customStart.toString()))
        .andExpect(jsonPath("$.data.confirmedEndDate").value(customEnd.toString()));

    mockMvc
        .perform(
            delete("/api/v1/trips/" + tripId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
  }
}
