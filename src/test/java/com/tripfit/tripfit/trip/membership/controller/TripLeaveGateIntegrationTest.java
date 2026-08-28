package com.tripfit.tripfit.trip.membership.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.common.config.TestcontainersConfig;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
class TripLeaveGateIntegrationTest {

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
  void leave_whenSchedulePending_returns403AndKeepsSeat() throws Exception {
    Trip trip = createTripWithOwner(3);
    User member = createUser("pending-leaver");
    String memberToken = jwtService.createAccessToken(member.getId());

    joinAsSchedulePending(trip, memberToken);

    mockMvc
        .perform(
            delete("/api/v1/trips/" + trip.getId() + "/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SCHEDULE_ACTIVATION_REQUIRED"));

    TripMember membership =
        tripMemberRepository
            .findByTripIdAndUserIdAndDeletedAtIsNull(trip.getId(), member.getId())
            .orElseThrow();
    assertThat(membership.getStatus()).isEqualTo(TripMemberStatus.SCHEDULE_PENDING);
    assertThat(membership.getDeletedAt()).isNull();
    assertThat(tripMemberRepository.countByTripIdAndDeletedAtIsNull(trip.getId())).isEqualTo(2);
  }

  @Test
  void leave_whenActive_returns204() throws Exception {
    Trip trip = createTripWithOwner(3);
    User member = createUser("active-leaver");
    String memberToken = jwtService.createAccessToken(member.getId());

    joinAsSchedulePending(trip, memberToken);
    activate(trip, memberToken);

    mockMvc
        .perform(
            delete("/api/v1/trips/" + trip.getId() + "/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
        .andExpect(status().isNoContent());

    assertThat(
        tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(
            trip.getId(),
            member.getId()))
        .isEmpty();
    assertThat(tripMemberRepository.countByTripIdAndDeletedAtIsNull(trip.getId())).isEqualTo(1);
  }

  @Test
  void ownerRemove_whenTargetSchedulePending_reclaimsSeat() throws Exception {
    Trip trip = createTripWithOwner(2);
    User owner = trip.getOwner();
    String ownerToken = jwtService.createAccessToken(owner.getId());
    User abandoner = createUser("abandoner");
    User latecomer = createUser("latecomer");
    String abandonerToken = jwtService.createAccessToken(abandoner.getId());
    String latecomerToken = jwtService.createAccessToken(latecomer.getId());

    joinAsSchedulePending(trip, abandonerToken);
    join(trip, latecomerToken)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TRIP_MEMBER_FULL"));

    mockMvc
        .perform(
            delete("/api/v1/trips/" + trip.getId() + "/members/" + abandoner.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.activeMemberCount").value(1));

    join(trip, latecomerToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.myMemberStatus").value("SCHEDULE_PENDING"));

    assertThat(tripMemberRepository.countByTripIdAndDeletedAtIsNull(trip.getId())).isEqualTo(2);
  }

  private org.springframework.test.web.servlet.ResultActions join(Trip trip, String token)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/trips/join")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"inviteCode\": \"" + trip.getInviteCode() + "\"}"));
  }

  private void joinAsSchedulePending(Trip trip, String token) throws Exception {
    join(trip, token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.myMemberStatus").value("SCHEDULE_PENDING"));
  }

  private void activate(Trip trip, String token) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/trips/" + trip.getId() + "/activate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.myMemberStatus").value("ACTIVE"));
  }

  private Trip createTripWithOwner(int memberCount) {
    User owner = createUser("leave-gate-owner");
    Trip trip =
        new Trip(
            owner,
            "나가기 게이트 테스트",
            LocalDate.now().plusDays(7),
            LocalDate.now().plusDays(30),
            3,
            4,
            memberCount,
            "LG" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(),
            TripStatus.ONGOING);
    tripRepository.save(trip);
    tripMemberRepository.save(
        new TripMember(
            trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE, LocalDateTime.now()));
    return trip;
  }

  private User createUser(String prefix) {
    String subject = prefix + "-" + UUID.randomUUID();
    User user =
        new User(subject, SocialProvider.GOOGLE, subject + "@example.com", "닉", null);
    user.applyProfilePatch("철수", "김", null);
    user.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
    return userRepository.save(user);
  }
}
