package com.tripfit.tripfit.trip.config;

import com.tripfit.tripfit.common.config.OpenApiResponseSupport;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TripOpenApiResponseConfig {

  // TripAuthorizationInterceptor는 @TripOwnerOnly가 붙은 메서드에서 항상 방 없음(TRIP_NOT_FOUND)
  // 다음에 방장 아님(TRIP_FORBIDDEN) 순서로만 막기 때문에, 이 403 텍스트는 어떤 컨트롤러든 완전히
  // 동일해 자동 생성으로 대체해도 정보 손실이 없다. 반면 @TripMemberOnly·@TripMembershipOnly의
  // 404는 컨트롤러마다 서비스 레이어가 추가로 던지는 사유(예: RECOMMENDATION_NOT_FOUND)가 섞여
  // 있어 자동 생성 문구로 덮어쓰면 그 정보가 사라진다. 그래서 addResponseIfAbsent로 이미 선언된
  // 응답은 절대 건드리지 않고, 컨트롤러가 더 구체적인 사유를 직접 적어둔 경우는 그대로 둔다.
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
