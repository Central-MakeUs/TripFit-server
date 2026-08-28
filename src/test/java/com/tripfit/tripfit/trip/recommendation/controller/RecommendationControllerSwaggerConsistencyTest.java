package com.tripfit.tripfit.trip.recommendation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class RecommendationControllerSwaggerConsistencyTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TripRepository tripRepository;

  @Autowired
  private TripMemberRepository tripMemberRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  @Test
  void fullFlow_generateListDetailFeedbackConfirmUnconfirm_matchesDocumentedShape()
      throws Exception {
    User owner =
        new User(
            "recommendation-swagger-" + UUID.randomUUID(),
            SocialProvider.GOOGLE,
            "owner@example.com",
            "방장",
            null);
    owner.applyProfilePatch("길동", "홍", null);
    owner = userRepository.save(owner);
    String accessToken = jwtService.createAccessToken(owner.getId());

    LocalDate startRange = LocalDate.now().plusDays(30);
    LocalDate endRange = startRange.plusDays(4);
    Trip trip =
        tripRepository.save(
            new Trip(
                owner, "추천 스웨거 테스트", startRange, endRange, 2, 3, 6, "RECSWG",
                TripStatus.ONGOING));
    tripMemberRepository.save(
        new TripMember(
            trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE, LocalDateTime.now()));

    String tripId = trip.getId().toString();

    mockMvc
        .perform(
            post("/api/v1/trips/" + tripId + "/recommendations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"BASIC\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.mode").value("BASIC"))
        .andExpect(jsonPath("$.data.items.length()").value(3))
        .andExpect(jsonPath("$.data.items[0].rank").value(1))
        .andExpect(jsonPath("$.data.items[0].attendRate").value(100))
        .andExpect(jsonPath("$.data.items[0].partialAttendCount").value(0))
        .andExpect(jsonPath("$.data.items[0].uncertainCount").value(0))
        .andExpect(jsonPath("$.data.items[0].totalVacationDays").value(0.0));

    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId + "/recommendations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(3));

    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId + "/recommendations/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rank").value(1))
        .andExpect(jsonPath("$.data.members.length()").value(1))
        .andExpect(jsonPath("$.data.members[0].attendance").value("FULL_ATTEND"))
        .andExpect(jsonPath("$.data.members[0].uncertainDays").value(0))
        .andExpect(jsonPath("$.data.feedback").doesNotExist());

    mockMvc
        .perform(
            patch("/api/v1/trips/" + tripId + "/recommendations/1/feedback")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"HELPFUL\"}"))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId + "/recommendations/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.feedback.status").value("HELPFUL"));

    mockMvc
        .perform(
            post("/api/v1/trips/" + tripId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recommendationRank\": 1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.data.confirmedStartDate").value(startRange.toString()))
        .andExpect(jsonPath("$.data.confirmedAttendCount").value(1))
        .andExpect(jsonPath("$.data.confirmedVacationMemberCount").value(0))
        .andExpect(jsonPath("$.data.confirmedUncertainCount").value(0));

    mockMvc
        .perform(
            post("/api/v1/trips/" + tripId + "/unconfirm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"NEW_SCHEDULE_ADDED\"}"))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ONGOING"))
        .andExpect(jsonPath("$.data.confirmedStartDate").doesNotExist())
        .andExpect(jsonPath("$.data.confirmedAttendCount").doesNotExist());
  }

  @Test
  void participant_cannotAccessRecommendations_forbidden() throws Exception {
    User owner =
        new User(
            "recommendation-owner-" + UUID.randomUUID(), SocialProvider.GOOGLE, "o@example.com",
            "방장", null);
    owner.applyProfilePatch("길동", "홍", null);
    owner = userRepository.save(owner);
    User participant =
        new User(
            "recommendation-participant-" + UUID.randomUUID(), SocialProvider.GOOGLE,
            "p@example.com", "참여자", null);
    participant.applyProfilePatch("철수", "김", null);
    participant = userRepository.save(participant);
    String participantToken = jwtService.createAccessToken(participant.getId());

    LocalDate startRange = LocalDate.now().plusDays(30);
    Trip trip =
        tripRepository.save(
            new Trip(
                owner, "권한 테스트", startRange, startRange.plusDays(4), 2, 3, 6, "RECAUTH",
                TripStatus.ONGOING));
    tripMemberRepository.save(
        new TripMember(
            trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE, LocalDateTime.now()));
    tripMemberRepository.save(
        new TripMember(
            trip, participant, TripMemberRole.MEMBER, TripMemberStatus.ACTIVE,
            LocalDateTime.now()));

    mockMvc
        .perform(
            get("/api/v1/trips/" + trip.getId() + "/recommendations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + participantToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("TRIP_FORBIDDEN"));
  }

  @Test
  void participant_cannotAccessRecommendationDetailOrFeedback_forbidden() throws Exception {
    User owner =
        new User(
            "recommendation-owner-" + UUID.randomUUID(), SocialProvider.GOOGLE, "o5@example.com",
            "방장", null);
    owner.applyProfilePatch("길동", "홍", null);
    owner = userRepository.save(owner);
    User participant =
        new User(
            "recommendation-participant-" + UUID.randomUUID(), SocialProvider.GOOGLE,
            "p5@example.com", "참여자", null);
    participant.applyProfilePatch("철수", "김", null);
    participant = userRepository.save(participant);
    String ownerToken = jwtService.createAccessToken(owner.getId());
    String participantToken = jwtService.createAccessToken(participant.getId());

    LocalDate startRange = LocalDate.now().plusDays(30);
    Trip trip =
        tripRepository.save(
            new Trip(
                owner, "권한 테스트 detail", startRange, startRange.plusDays(4), 2, 3, 6,
                "RECAUTH3", TripStatus.ONGOING));
    tripMemberRepository.save(
        new TripMember(
            trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE, LocalDateTime.now()));
    tripMemberRepository.save(
        new TripMember(
            trip, participant, TripMemberRole.MEMBER, TripMemberStatus.ACTIVE,
            LocalDateTime.now()));

    String tripId = trip.getId().toString();

    mockMvc
        .perform(
            post("/api/v1/trips/" + tripId + "/recommendations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"BASIC\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId + "/recommendations/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + participantToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("TRIP_FORBIDDEN"));

    mockMvc
        .perform(
            patch("/api/v1/trips/" + tripId + "/recommendations/1/feedback")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + participantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"HELPFUL\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("TRIP_FORBIDDEN"));
  }

  @Test
  void participant_cannotConfirmOrUnconfirm_forbidden() throws Exception {
    User owner =
        new User(
            "recommendation-owner-" + UUID.randomUUID(), SocialProvider.GOOGLE, "o2@example.com",
            "방장", null);
    owner.applyProfilePatch("길동", "홍", null);
    owner = userRepository.save(owner);
    User participant =
        new User(
            "recommendation-participant-" + UUID.randomUUID(), SocialProvider.GOOGLE,
            "p2@example.com", "참여자", null);
    participant.applyProfilePatch("철수", "김", null);
    participant = userRepository.save(participant);
    String participantToken = jwtService.createAccessToken(participant.getId());

    LocalDate startRange = LocalDate.now().plusDays(30);
    Trip trip =
        tripRepository.save(
            new Trip(
                owner, "권한 테스트 confirm", startRange, startRange.plusDays(4), 2, 3, 6,
                "RECAUTH2", TripStatus.ONGOING));
    tripMemberRepository.save(
        new TripMember(
            trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE, LocalDateTime.now()));
    tripMemberRepository.save(
        new TripMember(
            trip, participant, TripMemberRole.MEMBER, TripMemberStatus.ACTIVE,
            LocalDateTime.now()));

    mockMvc
        .perform(
            post("/api/v1/trips/" + trip.getId() + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + participantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recommendationRank\": 1}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("TRIP_FORBIDDEN"));

    mockMvc
        .perform(
            post("/api/v1/trips/" + trip.getId() + "/unconfirm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + participantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"NEW_SCHEDULE_ADDED\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("TRIP_FORBIDDEN"));
  }

  @Test
  void nonexistentRank_getDetailAndFeedback_returnsRecommendationNotFound() throws Exception {
    User owner =
        new User(
            "recommendation-owner-" + UUID.randomUUID(), SocialProvider.GOOGLE, "o3@example.com",
            "방장", null);
    owner.applyProfilePatch("길동", "홍", null);
    owner = userRepository.save(owner);
    String accessToken = jwtService.createAccessToken(owner.getId());

    LocalDate startRange = LocalDate.now().plusDays(30);
    Trip trip =
        tripRepository.save(
            new Trip(
                owner, "존재하지 않는 rank 테스트", startRange, startRange.plusDays(4), 2, 3, 6,
                "RECNF01", TripStatus.ONGOING));
    tripMemberRepository.save(
        new TripMember(
            trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE, LocalDateTime.now()));

    String tripId = trip.getId().toString();

    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId + "/recommendations/99")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));

    mockMvc
        .perform(
            patch("/api/v1/trips/" + tripId + "/recommendations/99/feedback")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"HELPFUL\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));
  }

  @Test
  void patchTripDuration_hardDeletesRecommendations_thenListIsEmpty() throws Exception {
    User owner =
        new User(
            "recommendation-owner-" + UUID.randomUUID(), SocialProvider.GOOGLE, "o4@example.com",
            "방장", null);
    owner.applyProfilePatch("길동", "홍", null);
    owner = userRepository.save(owner);
    String accessToken = jwtService.createAccessToken(owner.getId());

    LocalDate startRange = LocalDate.now().plusDays(30);
    LocalDate endRange = startRange.plusDays(4);
    Trip trip =
        tripRepository.save(
            new Trip(
                owner, "기간 변경 hard delete 테스트", startRange, endRange, 2, 3, 6, "RECPCH1",
                TripStatus.ONGOING));
    tripMemberRepository.save(
        new TripMember(
            trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE, LocalDateTime.now()));

    String tripId = trip.getId().toString();

    mockMvc
        .perform(
            post("/api/v1/trips/" + tripId + "/recommendations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"BASIC\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(3));

    mockMvc
        .perform(
            patch("/api/v1/trips/" + tripId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"기간변경완료\", \"durationNights\": 3,"
                        + " \"durationDays\": 4, \"memberCount\": 6}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId + "/recommendations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(0));
  }
}
