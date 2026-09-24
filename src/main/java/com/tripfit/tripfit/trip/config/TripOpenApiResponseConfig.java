package com.tripfit.tripfit.trip.config;

import com.tripfit.tripfit.common.config.OpenApiResponseSupport;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TripOpenApiResponseConfig {

  @Bean
  public OperationCustomizer tripAuthorizationResponseCustomizer() {
    return (operation, handlerMethod) -> {
      if (handlerMethod.getMethodAnnotation(TripOwnerOnly.class) != null) {
        OpenApiResponseSupport.addResponseIfAbsent(
            operation,
            "404",
            "TRIP_NOT_FOUND (여행방 없음)·soft deleted");
        OpenApiResponseSupport.addResponseIfAbsent(operation, "403", "TRIP_FORBIDDEN (방장 아님)");
      } else if (handlerMethod.getMethodAnnotation(TripMemberOnly.class) != null) {
        OpenApiResponseSupport.addResponseIfAbsent(
            operation,
            "404",
            "TRIP_NOT_FOUND (여행방 없음)·soft deleted");
        OpenApiResponseSupport.addResponseIfAbsent(
            operation,
            "403",
            "TRIP_ACCESS_DENIED (비참여자)·SCHEDULE_ACTIVATION_REQUIRED (이 방 일정 확인 미완료)");
      } else if (handlerMethod.getMethodAnnotation(TripMembershipOnly.class) != null) {
        OpenApiResponseSupport.addResponseIfAbsent(
            operation,
            "404",
            "TRIP_NOT_FOUND (여행방 없음)·soft deleted");
        OpenApiResponseSupport.addResponseIfAbsent(operation, "403", "TRIP_ACCESS_DENIED (비참여자)");
      }
      return operation;
    };
  }
}
