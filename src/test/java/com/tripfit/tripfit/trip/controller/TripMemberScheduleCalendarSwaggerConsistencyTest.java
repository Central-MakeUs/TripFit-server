package com.tripfit.tripfit.trip.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// GET /trips/{tripId}/members/schedule-calendar의 Swagger(@ApiResponse 200/403/404 예시)가
// 실제 컨트롤러 동작과 어긋나지 않는지 검증한다
@SpringBootTest
@ActiveProfiles("test")
class TripMemberScheduleCalendarSwaggerConsistencyTest {

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

  @Autowired
  private RegularScheduleRepository regularScheduleRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  private User seedUser(String tag) {
    return userRepository.save(
        new User("member-cal-swagger-" + tag + "-" + UUID.randomUUID(), SocialProvider.GOOGLE,
            tag + "@example.com", tag, null));
  }

  // 200 @ApiResponse 예시가 선언한 필드(startDate/endDate/readOnly/members[].userId/displayName/
  // role/memberStatus/days[]...)가 실제 응답에도 그대로 있는지
  @Test
  void okExample_fieldShape_matchesActualResponse() throws Exception {
    User owner = seedUser("owner1");
    String accessToken = jwtService.createAccessToken(owner.getId());
    LocalDate startRange = LocalDate.now().plusDays(45);
    LocalDate endRange = startRange.plusDays(3);
    regularScheduleRepository.save(
        RegularSchedule.create(
            owner,
            "출근",
            "MON,TUE,WED,THU,FRI,SAT,SUN",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            2,
            VacationApplyPeriod.ANY,
            false,
            true));
    Trip trip =
        tripRepository.save(
            new Trip(
                owner, "swagger 멤버 달력", startRange, endRange, 2, 3, 4, "SWMBR1",
                TripStatus.ONGOING));
    tripMemberRepository.save(
        new TripMember(trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE,
            LocalDateTime.now()));

    mockMvc
        .perform(
            get("/api/v1/trips/" + trip.getId() + "/members/schedule-calendar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.startDate").value(startRange.toString()))
        .andExpect(jsonPath("$.data.endDate").value(endRange.toString()))
        .andExpect(jsonPath("$.data.readOnly").value(false))
        .andExpect(jsonPath("$.data.members[0].userId").value(owner.getId().toString()))
        .andExpect(jsonPath("$.data.members[0].displayName").exists())
        .andExpect(jsonPath("$.data.members[0].role").value("OWNER"))
        .andExpect(jsonPath("$.data.members[0].memberStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.data.members[0].days[0].morningStatus").value("IMPOSSIBLE"));
  }

  // 403 @ApiResponse 예시(TRIP_ACCESS_DENIED)가 실제로 비멤버 접근 시 나오는 바디와 일치하는지
  @Test
  void forbiddenExample_nonMember_matchesActualErrorBody() throws Exception {
    User owner = seedUser("owner2");
    User outsider = seedUser("outsider");
    String outsiderToken = jwtService.createAccessToken(outsider.getId());
    LocalDate startRange = LocalDate.now().plusDays(50);
    Trip trip =
        tripRepository.save(
            new Trip(
                owner,
                "swagger 403 테스트",
                startRange,
                startRange.plusDays(2),
                1,
                2,
                4,
                "SWMBR2",
                TripStatus.ONGOING));
    tripMemberRepository.save(
        new TripMember(trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE,
            LocalDateTime.now()));

    mockMvc
        .perform(
            get("/api/v1/trips/" + trip.getId() + "/members/schedule-calendar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("TRIP_ACCESS_DENIED"))
        .andExpect(jsonPath("$.message").value("여행방 참여 권한이 없습니다."));
  }

  // 404 @ApiResponse 예시(TRIP_NOT_FOUND)가 실제로 존재하지 않는 tripId 조회 시 나오는 바디와 일치하는지
  @Test
  void notFoundExample_unknownTripId_matchesActualErrorBody() throws Exception {
    User user = seedUser("lonely");
    String accessToken = jwtService.createAccessToken(user.getId());

    mockMvc
        .perform(
            get("/api/v1/trips/" + UUID.randomUUID() + "/members/schedule-calendar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("여행방을 찾을 수 없습니다."));
  }
}
