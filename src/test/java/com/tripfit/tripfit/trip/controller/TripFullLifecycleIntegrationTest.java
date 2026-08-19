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

// 여행방 전체 생명주기(생성→activate→참여→추천→확정→나가기/내보내기/메타수정 시도→확정취소→재확정→삭제)를
// 실제 REST 엔드포인트 + MySQL(Testcontainers) DB로 처음부터 끝까지 밟아, #13/#50 추천·확정 구현이 기존 트립 라이프사이클
// 전이(ONGOING/CONFIRMED 게이트, join 차단, leave/remove 게이트)와 충돌하지 않는지 검증한다
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
    LocalDate endRange = startRange.plusDays(6); // 7일 범위, durationDays=4 → 후보 4개(TOP3만 저장)

    // 1. 생성 — 방장은 SCHEDULE_PENDING, 초대코드 없음
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

    // 2. 방장 activate — SCHEDULE_PENDING → ACTIVE (일정 건수는 보지 않음. 사전 일정 입력 완료는 필요)
    MvcResult activateResult =
        mockMvc
            .perform(
                post("/api/v1/trips/" + tripId + "/activate")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.myMemberStatus").value("ACTIVE"))
            .andReturn();
    String inviteCode = extract("inviteCode", activateResult.getResponse().getContentAsString());

    // 3. 멤버가 초대코드로 참여 — 링크를 연 시점이라 SCHEDULE_PENDING, 아직 방 안은 못 본다
    mockMvc
        .perform(
            post("/api/v1/trips/join")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteCode\": \"" + inviteCode + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.myMemberStatus").value("SCHEDULE_PENDING"))
        .andExpect(jsonPath("$.data.inviteCode").doesNotExist());

    // 3-1. 일정 확인 전에는 방 상세가 막힌다
    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SCHEDULE_ACTIVATION_REQUIRED"));

    // 3-2. 멤버도 방장과 같은 activate를 거쳐 ACTIVE가 된다
    mockMvc
        .perform(
            post("/api/v1/trips/" + tripId + "/activate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.myMemberStatus").value("ACTIVE"));

    // 4. 멤버 목록 — 2명 다 ACTIVE
    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId + "/members")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.activeMemberCount").value(2));

    // 5. 추천 생성 — 방장 전용, 둘 다 전부 free라 전체참석
    mockMvc
        .perform(
            post("/api/v1/trips/" + tripId + "/recommendations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"BASIC\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(3))
        .andExpect(jsonPath("$.data.items[0].attendRate").value(100));

    // 6. 참여자(멤버)는 추천 목록에 접근 불가 — 권한 매트릭스 재확인
    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId + "/recommendations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("TRIP_FORBIDDEN"));

    // 7. 피드백 저장 후 확정 — status CONFIRMED, 확정 통계 2명
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

    // 8. 멤버 달력 조회 — CONFIRMED면 스냅샷(readOnly=true)으로 전환
    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId + "/members/schedule-calendar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.readOnly").value(true));

    // 9. CONFIRMED 상태에서 메타 수정 시도 — TRIP_NOT_ONGOING
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

    // 10. CONFIRMED 상태에서 제3자 신규 참여 시도 — TRIP_ALREADY_CONFIRMED
    mockMvc
        .perform(
            post("/api/v1/trips/join")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + thirdToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteCode\": \"" + inviteCode + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TRIP_ALREADY_CONFIRMED"));

    // 11. CONFIRMED 상태에서 방장의 멤버 내보내기 시도 — TRIP_NOT_ONGOING (removeMember도 ONGOING 게이트)
    mockMvc
        .perform(
            delete("/api/v1/trips/" + tripId + "/members/" + member.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TRIP_NOT_ONGOING"));

    // 12. CONFIRMED여도 ACTIVE 멤버의 자진 나가기는 허용 — 방 상태 게이트는 없다(멤버 상태 게이트는 #122로 존재)
    mockMvc
        .perform(
            delete("/api/v1/trips/" + tripId + "/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
        .andExpect(status().isNoContent());

    // 13. 확정 취소 — ONGOING 복귀, 확정 통계 초기화, 추천 후보 hard delete
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

    // 14. 나갔던 멤버 자리는 비었으니 재추천 — 방장 혼자라 전체참석 100
    mockMvc
        .perform(
            post("/api/v1/trips/" + tripId + "/recommendations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"BASIC\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].attendRate").value(100));

    // 15. ONGOING 복귀 후에는 메타 수정도 다시 허용됨
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

    // 16. 직접 날짜 입력으로 재확정 — durationDays=4와 일치하는 3박4일 구간
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

    // 17. 삭제 — CONFIRMED여도 방장 삭제는 게이트 없이 허용(soft delete)
    mockMvc
        .perform(
            delete("/api/v1/trips/" + tripId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
        .andExpect(status().isNoContent());

    // 18. 삭제 후 조회 — TRIP_NOT_FOUND
    mockMvc
        .perform(
            get("/api/v1/trips/" + tripId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
  }
}
