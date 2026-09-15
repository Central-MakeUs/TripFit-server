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

// 나가기도 "방 안 기능"이라 입장(ACTIVE) 후에만 허용된다(2026-08-19 기획 확정, #122). 이 게이트는 인터셉터가
// 담당하므로 서비스 단위 테스트로는 잡히지 않아, 실제 HTTP + MySQL(Testcontainers)로 확인한다.
// 함께 고정하는 것: 미입장자가 점유한 자리는 본인이 비울 수 없고 방장 내보내기로만 회수된다는 회수 경로.
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

  // 일정 확인을 끝내지 않은 멤버는 스스로 나갈 수 없다 — 403이고 자리(멤버 row)도 그대로 남는다
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

  // 입장(ACTIVE)까지 마친 멤버는 그대로 나갈 수 있다 — 게이트 추가로 기존 동작이 깨지지 않음을 고정
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

  // 미입장자가 잡은 자리를 비우는 유일한 경로 — 방장 내보내기. 정원 2인 방에서 자리가 실제로 회수되는지까지 확인
  @Test
  void ownerRemove_whenTargetSchedulePending_reclaimsSeat() throws Exception {
    Trip trip = createTripWithOwner(2);
    User owner = trip.getOwner();
    String ownerToken = jwtService.createAccessToken(owner.getId());
    User abandoner = createUser("abandoner");
    User latecomer = createUser("latecomer");
    String abandonerToken = jwtService.createAccessToken(abandoner.getId());
    String latecomerToken = jwtService.createAccessToken(latecomer.getId());

    // 1. 마지막 자리를 미입장자가 차지 — 뒤에 온 사람은 정원 초과로 막힌다
    joinAsSchedulePending(trip, abandonerToken);
    join(trip, latecomerToken)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TRIP_MEMBER_FULL"));

    // 2. 방장이 미입장자를 내보낸다 — 대상 상태를 보지 않는다
    mockMvc
        .perform(
            delete("/api/v1/trips/" + trip.getId() + "/members/" + abandoner.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.activeMemberCount").value(1));

    // 3. 비워진 자리로 새 참여자가 들어온다
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

  // 이름은 방 참여 전제(BR-USER-001), 연차·휴일 정보는 activate 전제(#112) — 둘 다 채워 게이트 대상을 나가기 하나로 좁힌다
  private User createUser(String prefix) {
    String subject = prefix + "-" + UUID.randomUUID();
    User user =
        new User(subject, SocialProvider.GOOGLE, subject + "@example.com", "닉", null);
    user.applyProfilePatch("철수", "김", null);
    user.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
    return userRepository.save(user);
  }
}
