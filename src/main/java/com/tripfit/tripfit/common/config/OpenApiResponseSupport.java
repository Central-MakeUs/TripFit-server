package com.tripfit.tripfit.common.config;

import com.tripfit.tripfit.common.api.ErrorResponse;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import com.tripfit.tripfit.common.exception.ErrorCode;
import io.swagger.v3.oas.models.examples.Example;
import org.springframework.web.method.HandlerMethod;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.beans.factory.config.BeanDefinition;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  private static final Map<String, ErrorCode> ERROR_CODE_MAP = new HashMap<>();

  static {
    try {
      ClassPathScanningCandidateComponentProvider provider =
          new ClassPathScanningCandidateComponentProvider(false);
      provider.addIncludeFilter(new AssignableTypeFilter(ErrorCode.class));
      for (BeanDefinition component : provider.findCandidateComponents("com.tripfit.tripfit")) {
        Class<?> cls = Class.forName(component.getBeanClassName());
        if (cls.isEnum()) {
          for (Object enumConstant : cls.getEnumConstants()) {
            ErrorCode code = (ErrorCode) enumConstant;
            ERROR_CODE_MAP.put(((Enum<?>) enumConstant).name(), code);
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to scan ErrorCode enums for Swagger", e);
    }
  }

  public static void addApiErrorCodes(Operation operation, HandlerMethod handlerMethod) {
    if (operation.getResponses() == null) {
      return;
    }

    Pattern pattern = Pattern.compile("\\(([A-Z0-9_]+)\\)");

    for (Map.Entry<String, ApiResponse> entry : operation.getResponses().entrySet()) {
      ApiResponse response = entry.getValue();
      if (response.getDescription() == null) {
        continue;
      }

      Matcher matcher = pattern.matcher(response.getDescription());
      List<ErrorCode> foundCodes = new ArrayList<>();
      while (matcher.find()) {
        ErrorCode code = ERROR_CODE_MAP.get(matcher.group(1));
        if (code != null) {
          foundCodes.add(code);
        }
      }

      if (!foundCodes.isEmpty()) {
        MediaType mediaType = new MediaType().schema(
            new Schema<>().$ref("#/components/schemas/" + ErrorResponse.class.getSimpleName()));

        for (ErrorCode errorCode : foundCodes) {
          Example example = new Example();
          example.description(errorCode.getMessage());
          example.value(new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
          mediaType.addExamples(errorCode.getCode(), example);
        }

        if (response.getContent() == null) {
          response.setContent(new Content());
        }
        if (response.getContent().get("application/json") == null) {
          response.getContent().addMediaType("application/json", mediaType);
        } else {
          MediaType existingMediaType = response.getContent().get("application/json");
          for (ErrorCode errorCode : foundCodes) {
            Example example = new Example();
            example.description(errorCode.getMessage());
            example.value(new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
            existingMediaType.addExamples(errorCode.getCode(), example);
          }
        }
      }
    }
  }
}
