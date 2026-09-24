package com.tripfit.tripfit.common.config;

import com.tripfit.tripfit.common.api.ErrorResponse;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import org.springframework.web.method.HandlerMethod;

// 여러 도메인의 OperationCustomizer(auth/config, trip/config)가 공통으로 쓰는 헬퍼를 모은다.
// 어노테이션이 항상 같은 ErrorCode만 던지는 엔드포인트에 한해 그 상태 코드의 @ApiResponse를
// 자동으로 채우고, 컨트롤러가 이미 같은 상태 코드를 직접 선언해 둔 경우(도메인별 사유가 섞인
// 403·404 등)는 덮어쓰지 않는다.
public final class OpenApiResponseSupport {

  private OpenApiResponseSupport() {}

  public static boolean hasParameterAnnotation(
      HandlerMethod handlerMethod,
      Class<? extends Annotation> annotationType) {
    return Arrays.stream(handlerMethod.getMethodParameters())
        .anyMatch(param -> param.hasParameterAnnotation(annotationType));
  }

  public static void addResponseIfAbsent(
      Operation operation,
      String statusCode,
      String description) {
    if (operation.getResponses().containsKey(statusCode)) {
      return;
    }
    operation.getResponses().addApiResponse(
        statusCode,
        new ApiResponse()
            .description(description)
            .content(
                new Content().addMediaType(
                    "application/json",
                    new MediaType()
                        .schema(
                            new Schema<>().$ref(
                                "#/components/schemas/" + ErrorResponse.class.getSimpleName())))));
  }
}
