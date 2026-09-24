package com.tripfit.tripfit.auth.config;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.config.OpenApiResponseSupport;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  public static final String BEARER_JWT = "bearer-jwt";

  @Bean

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

  // JWT가 필요 없는 엔드포인트(@AuthorizedUser 파라미터 없음)는 전역 security를 비워 Swagger
  // 자물쇠를 해제하고, JWT가 필요한 엔드포인트에는 401 응답을 자동으로 채운다. 컨트롤러가 이미
  // 같은 상태 코드를 직접 선언해 둔 경우는 덮어쓰지 않는다.
  @Bean

  public OperationCustomizer publicEndpointSecurityCustomizer() {
    return (operation, handlerMethod) -> {
      boolean requiresJwt =
          OpenApiResponseSupport.hasParameterAnnotation(handlerMethod, AuthorizedUser.class);
      if (!requiresJwt) {
        operation.setSecurity(List.of(new SecurityRequirement()));
      } else {
        OpenApiResponseSupport.addResponseIfAbsent(
            operation,
            "401",
            "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)");
      }
      return operation;
    };
  }

  // @Valid로 검증하는 요청 파라미터가 있으면 검증 실패 시 항상 같은 INVALID_INPUT 코드로 응답하는
  // GlobalExceptionHandler와 짝을 이뤄, 그 400 응답을 컨트롤러마다 반복해 적지 않아도 되게 한다.
  // 이미 도메인별 사유가 섞인 400을 선언해 둔 컨트롤러는 건드리지 않는다.
  @Bean

  public OperationCustomizer validationErrorResponseCustomizer() {
    return (operation, handlerMethod) -> {
      if (OpenApiResponseSupport.hasParameterAnnotation(handlerMethod, Valid.class)) {
        OpenApiResponseSupport.addResponseIfAbsent(operation, "400", "요청 값 검증 실패 (INVALID_INPUT)");
      }
      return operation;
    };
  }
}
