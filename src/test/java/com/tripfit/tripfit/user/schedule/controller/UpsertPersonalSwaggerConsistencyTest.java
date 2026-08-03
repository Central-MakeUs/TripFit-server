package com.tripfit.tripfit.user.schedule.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
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

// UserScheduleController.upsertPersonal의 Swagger(@RequestBody 예시·200/400 @ApiResponse 예시·
// PersonalScheduleItem/SlotUpdate @Schema requiredMode)가 실제 컨트롤러 동작과 어긋나지 않는지
// 검증한다 — "Swagger를 보고 프론트가 그대로 호출하면 문서와 같은 응답이 온다"를 실제 MySQL(Testcontainers) DB로 확인
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class UpsertPersonalSwaggerConsistencyTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private UserRepository userRepository;

  private MockMvc mockMvc;

  private String accessToken;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    User user =
        new User(
            "swagger-check-sub-" + java.util.UUID.randomUUID(),
            SocialProvider.GOOGLE,
            "swagger@example.com",
            "닉",
            null);
    user = userRepository.save(user);
    accessToken = jwtService.createAccessToken(user.getId());
  }

  // @RequestBody 예시 "슬롯만 변경" — Swagger에 적힌 그대로 보내면 200과 동일한 필드 구성이 와야 한다
  @Test
  void requestBodyExample_slotsOnly_matchesDocumentedResponseShape() throws Exception {
    var date = java.time.LocalDate.now().plusDays(10);
    String body =
        """
            {"items": [{"scheduleDate": "%s", "slots": {"morningStatus": "IMPOSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE"}}]}
            """
            .formatted(date);

    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        // 200 @ApiResponse 예시가 선언한 필드 6개(id, scheduleDate, morningStatus, afternoonStatus,
        // eveningStatus, uncertain)가 실제 응답에도 정확히 그 이름으로 존재하는지
        .andExpect(jsonPath("$.data.items[0].id").exists())
        .andExpect(jsonPath("$.data.items[0].scheduleDate").value(date.toString()))
        .andExpect(jsonPath("$.data.items[0].morningStatus").value("IMPOSSIBLE"))
        .andExpect(jsonPath("$.data.items[0].afternoonStatus").value("POSSIBLE"))
        .andExpect(jsonPath("$.data.items[0].eveningStatus").value("POSSIBLE"))
        .andExpect(jsonPath("$.data.items[0].uncertain").value(false));
  }

  // @RequestBody 예시 "불확실 여부만 변경" — slots 생략이 Swagger 설명대로 실제로 허용되는지
  @Test
  void requestBodyExample_uncertainOnly_isAccepted() throws Exception {
    var date = java.time.LocalDate.now().plusDays(11);
    String body = """
        {"items": [{"scheduleDate": "%s", "uncertain": true}]}
        """.formatted(date);

    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].uncertain").value(true));
  }

  // @RequestBody 예시 "슬롯·불확실 동시 변경"
  @Test
  void requestBodyExample_slotsAndUncertainTogether_isAccepted() throws Exception {
    var date = java.time.LocalDate.now().plusDays(12);
    String body =
        """
            {"items": [{"scheduleDate": "%s", "slots": {"morningStatus": "IMPOSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE"}, "uncertain": false}]}
            """
            .formatted(date);

    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].afternoonStatus").value("POSSIBLE"))
        .andExpect(jsonPath("$.data.items[0].uncertain").value(false));
  }

  // 400 @ApiResponse 예시 문구({"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다."})가
  // 실제 400 응답 바디와 글자 그대로 일치하는지
  @Test
  void badRequestExample_matchesActualErrorBody() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items": []}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));
  }

  // /v3/api-docs가 실제로 뿜는 스키마의 required 목록이 @Schema(requiredMode)로 선언한 것과 같은지 —
  // PersonalScheduleItem은 scheduleDate만 필수(slots/uncertain은 선택), SlotUpdate는 3필드 전부 필수
  @Test
  void openApiSchema_requiredFields_matchAnnotatedConstraints() throws Exception {
    var result =
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    JsonNode root = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray());
    JsonNode schemas = root.path("components").path("schemas");

    JsonNode item = schemas.path("PersonalScheduleItem");
    assertThat(item.isMissingNode()).as("PersonalScheduleItem 스키마가 존재해야 함").isFalse();
    assertThat(requiredFieldNames(item)).containsExactly("scheduleDate");

    JsonNode slotUpdate = schemas.path("SlotUpdate");
    assertThat(slotUpdate.isMissingNode()).as("SlotUpdate 스키마가 존재해야 함").isFalse();
    assertThat(requiredFieldNames(slotUpdate))
        .containsExactlyInAnyOrder("morningStatus", "afternoonStatus", "eveningStatus");
  }

  private static Set<String> requiredFieldNames(JsonNode schemaNode) {
    JsonNode required = schemaNode.path("required");
    Iterator<JsonNode> it = required.elements();
    return StreamSupport.stream(
        java.util.Spliterators.spliteratorUnknownSize(it, 0),
        false)
        .map(JsonNode::asText)
        .collect(Collectors.toSet());
  }
}
