package com.tripfit.tripfit.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// GET /notifications의 OpenAPI 200 응답이 raw SuccessResponse로 뭉개지지 않고 NotificationResponse
// 필드·enum(NotificationType·LandingType)까지 실제로 노출되는지 검증한다 — useReturnTypeSchema 누락 회귀 방지
@SpringBootTest
@ActiveProfiles("test")
class NotificationSwaggerSchemaTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void apiDocs_notificationListResponse_exposesTypedSchemaNotRawSuccessResponse() throws Exception {
    String body =
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode root = new ObjectMapper().readTree(body);
    JsonNode listResponseSchemaRef =
        root.path("paths")
            .path("/api/v1/notifications")
            .path("get")
            .path("responses")
            .path("200")
            .path("content")
            .path("*/*")
            .path("schema")
            .path("$ref");

    assertThat(listResponseSchemaRef.asText()).isNotEqualTo("#/components/schemas/SuccessResponse");

    JsonNode notificationResponseSchema = root.path("components").path("schemas")
        .path("NotificationResponse")
        .path("properties");
    JsonNode notificationTypeEnum = notificationResponseSchema.path("type").path("enum");
    JsonNode landingTypeEnum = notificationResponseSchema.path("landingType").path("enum");

    assertThat(notificationTypeEnum.isMissingNode()).isFalse();
    assertThat(toValues(notificationTypeEnum)).containsExactlyInAnyOrder(
        "JOIN_COMPLETED",
        "ALL_MEMBERS_SUBMITTED",
        "TRIP_INFO_CHANGED",
        "TRIP_CONFIRMED",
        "TRIP_CONFIRM_CANCELED",
        "SCHEDULE_REMINDER");
    assertThat(toValues(landingTypeEnum)).containsExactlyInAnyOrder(
        "TRAVEL_ROOM_DETAIL",
        "SCHEDULE_MANAGEMENT");
  }

  private static java.util.List<String> toValues(JsonNode enumNode) {
    java.util.List<String> values = new java.util.ArrayList<>();
    enumNode.forEach(node -> values.add(node.asText()));
    return values;
  }
}
