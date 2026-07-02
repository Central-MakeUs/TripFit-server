package com.tripfit.tripfit.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class AuthErrorResponseWriter {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  // 인증 실패 시 API envelope 형식으로 401 응답을 작성함
  public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
    response.setStatus(errorCode.getHttpStatus().value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    OBJECT_MAPPER.writeValue(
        response.getOutputStream(),
        new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
  }
}
