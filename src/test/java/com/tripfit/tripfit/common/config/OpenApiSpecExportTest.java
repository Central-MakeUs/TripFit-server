package com.tripfit.tripfit.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class OpenApiSpecExportTest {

  private static final Path OUTPUT_PATH = Path.of("build/openapi/openapi.json");

  @Autowired
  private WebApplicationContext webApplicationContext;

  // CI의 api-contract-check가 breaking change 비교에 쓸 OpenAPI 스펙을 실서버 기동 없이 생성한다
  @Test
  void exportsOpenApiSpecToBuildDirectory() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

    MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();

    Files.createDirectories(OUTPUT_PATH.getParent());
    Files.write(OUTPUT_PATH, result.getResponse().getContentAsByteArray());
  }
}
