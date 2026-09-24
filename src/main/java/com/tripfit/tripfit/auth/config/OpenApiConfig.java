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

  @Bean

  public OperationCustomizer validationErrorResponseCustomizer() {
    return (operation, handlerMethod) -> {
      if (OpenApiResponseSupport.hasParameterAnnotation(handlerMethod, Valid.class)) {
        OpenApiResponseSupport.addResponseIfAbsent(operation, "400", "요청 값 검증 실패 (INVALID_INPUT)");
      }
      return operation;
    };
  }

  @Bean
  public OperationCustomizer apiErrorCodeCustomizer() {
    return (operation, handlerMethod) -> {
      OpenApiResponseSupport.addApiErrorCodes(operation, handlerMethod);
      return operation;
    };
  }
}
