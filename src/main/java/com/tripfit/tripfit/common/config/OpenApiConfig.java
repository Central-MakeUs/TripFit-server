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
                        Authorize에 access JWT를 넣으면 자물쇠 API에 Authorization: Bearer가 붙습니다.
                        각 API description은 목적 → 호출 시점 → 전제 → 결과 순으로 읽으면 됩니다.

                        ## 여행방 멤버십 상태 (헷갈리기 쉬운 계약 — 반드시 읽기)
                        - **JOINED**: **방장만**. `POST /trips` 직후 ~ `POST .../schedule/confirm` 전.
                          방 상세·멤버·달력·Pin·초대 공유 **불가**. create 응답에 **inviteCode 없음**.
                        - **RESPONDED**: 방장(confirm 후) **또는** 멤버(`POST /trips/join` 시 **바로**).
                          멤버는 중간에 JOINED를 **거치지 않음**.
                        - **방 입장(상세 등)**: RESPONDED **그리고** 입장 조건(일정≥1 또는 전부 free).
                        - **초대 공유(카카오/링크 복사)**: **방장**이고 **RESPONDED(입장 후)** 만.
                          inviteCode는 상세(`GET /trips/{id}` 등)에서만 옴.

                        홈 카드에 JOINED 방이 보여도 탭 시 상세가 아니라 **일정 confirm 플로우**로 보내야 합니다.
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
