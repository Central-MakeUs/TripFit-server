package com.tripfit.tripfit.auth.config;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Arrays;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  public static final String BEARER_JWT = "bearer-jwt";

  @Bean
  // springdoc에 Bearer JWT 스키마를 전역 적용해 Swagger UI 자물쇠로 인증 필요 여부를 표시함
  public OpenAPI tripfitOpenAPI() {
    return new OpenAPI()
        .addServersItem(
            new Server().url("https://api.tripfit.online").description("Production API Server"))
        .info(
            new Info()
                .title("TripFit API")
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

  @Bean
  // @AuthorizedUser 파라미터가 없는 API는 전역 bearer-jwt 자물쇠를 해제함 — @Operation(security = {})만으로는
  // springdoc이 빈 배열을 응답 JSON에서 누락시켜(전역 상속으로 오인) 로그인류 API에 자물쇠가 잘못 표시되던 문제 수정
  public OperationCustomizer publicEndpointSecurityCustomizer() {
    return (operation, handlerMethod) -> {
      boolean requiresJwt = Arrays.stream(handlerMethod.getMethodParameters())
          .anyMatch(param -> param.hasParameterAnnotation(AuthorizedUser.class));
      if (!requiresJwt) {
        operation.setSecurity(List.of(new SecurityRequirement()));
      }
      return operation;
    };
  }
}
