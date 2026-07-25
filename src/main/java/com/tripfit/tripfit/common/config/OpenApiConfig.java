package com.tripfit.tripfit.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  public static final String BEARER_JWT = "bearer-jwt";

  @Bean
  // springdoc에 Bearer JWT 스키마를 전역 적용해 Swagger UI 자물쇠로 인증 필요 여부를 표시함
  public OpenAPI tripfitOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("TripFit API")
                .description(
                    """
                        TripFit 백엔드 REST API (프론트·신규 서버 개발자용).

                        ## 인증
                        Authorize에 access JWT를 입력하면 잠긴 API에 Authorization: Bearer가 자동으로 붙습니다.

                        ## API 설명 읽는 법
                        각 엔드포인트 설명은 목적 → 호출 시점 → 전제 → 결과 → 주의 → 주요 에러 순서로 구성됩니다.

                        ## 필독 — 여행방 멤버십 상태
                        JOINED/RESPONDED 상태 전이는 헷갈리기 쉽습니다. Trip·Trip Members API의 각 엔드포인트 설명에서 전제 조건을 확인하세요.
                        """)
                .version("v0.0.1"))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_JWT,
                    new SecurityScheme()
                        .name(BEARER_JWT)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Authorization: Bearer {accessToken}")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
  }
}
