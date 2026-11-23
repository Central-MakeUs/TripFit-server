package com.tripfit.tripfit.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.common.config.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// 운영 배포에서 필요한 상태 확인과 나머지 Actuator 엔드포인트의 비공개 여부를 확인한다.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class ActuatorExposureIntegrationTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void readinessRemainsAvailable() throws Exception {
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  // 노출 목록에 없는 엔드포인트는 등록되지 않아야 한다. 등록되지 않은 경로는 공통 예외 처리기가
  // 404가 아닌 다른 상태로 바꿔 돌려줄 수 있어, 상태 코드 대신 "200이 아님"으로 확인한다.
  @Test
  void actuator_otherEndpointsStayHidden() throws Exception {
    for (String path : new String[] {"/actuator/env", "/actuator/heapdump", "/actuator/metrics",
        "/actuator/prometheus"}) {
      int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
      assertThat(status).as(path).isNotEqualTo(200);
    }
  }
}
