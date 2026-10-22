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

// Prometheus 감시 규칙이 읽는 수치가 실제로 노출되는지 확인한다.
// registry 의존성과 노출 설정 중 하나만 빠져도 엔드포인트가 404가 되므로 둘을 함께 지킨다.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class PrometheusEndpointIntegrationTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void prometheusEndpoint_exposesMetricsUsedByWatchRules() throws Exception {
    String body =
        mockMvc
            .perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // http_server_requests_seconds는 실제 요청이 서블릿 필터를 거쳐야 생기는데, 이 MockMvc는
    // 필터 없이 만들어서 확인 대상에서 뺀다. 운영에서는 첫 요청부터 노출된다.
    assertThat(body)
        .contains("process_cpu_usage")
        .contains("jvm_memory_used_bytes")
        .contains("jvm_memory_max_bytes")
        .contains("hikaricp_connections_pending");
  }

  // 노출 목록에 없는 엔드포인트는 등록되지 않아야 한다. 등록되지 않은 경로는 공통 예외 처리기가
  // 404가 아닌 다른 상태로 바꿔 돌려줄 수 있어, 상태 코드 대신 "200이 아님"으로 확인한다.
  @Test
  void actuator_otherEndpointsStayHidden() throws Exception {
    for (String path : new String[] {"/actuator/env", "/actuator/heapdump", "/actuator/metrics"}) {
      int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
      assertThat(status).as(path).isNotEqualTo(200);
    }
  }
}
