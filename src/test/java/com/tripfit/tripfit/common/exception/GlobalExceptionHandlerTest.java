package com.tripfit.tripfit.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.tripfit.tripfit.common.api.ErrorResponse;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  private Logger logger;

  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
  }

  @Test
  void handleTripFitException_usesErrorCodeStatusAndDefaultMessage() {
    TripFitException exception = new TripFitException(CommonErrorCode.INVALID_INPUT);

    ResponseEntity<ErrorResponse> response = handler.handleTripFitException(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("INVALID_INPUT");
    assertThat(response.getBody().message()).isEqualTo("입력값이 올바르지 않습니다.");
  }

  @Test
  void handleTripFitException_whenCustomMessage_overridesErrorCodeDefault() {
    TripFitException exception = new TripFitException(CommonErrorCode.INVALID_INPUT, "커스텀 메시지");

    ResponseEntity<ErrorResponse> response = handler.handleTripFitException(exception);

    assertThat(response.getBody().message()).isEqualTo("커스텀 메시지");
  }

  @Test
  void handleValidationException_returnsBadRequestWithFieldErrors() {
    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new Object(), "request");
    bindingResult.addError(new FieldError("request", "email", "이메일 형식이 올바르지 않습니다."));
    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(new MethodParameter(sampleMethod(), -1), bindingResult);

    ResponseEntity<ErrorResponse> response = handler.handleValidationException(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("INVALID_INPUT");
    List<com.tripfit.tripfit.common.api.FieldError> errors = response.getBody().errors();
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("email");
    assertThat(errors.get(0).message()).isEqualTo("이메일 형식이 올바르지 않습니다.");
  }

  @Test
  void handleMessageNotReadable_returnsCommonInvalidInput() {
    HttpMessageNotReadableException exception =
        new HttpMessageNotReadableException("malformed json", (HttpInputMessage) null);

    ResponseEntity<ErrorResponse> response = handler.handleMessageNotReadable(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("INVALID_INPUT");
  }

  @Test
  void handleTypeMismatch_returnsCommonInvalidInput() {
    MethodArgumentTypeMismatchException exception =
        new MethodArgumentTypeMismatchException(
            "abc",
            Integer.class,
            "rank",
            new MethodParameter(sampleMethod(), -1),
            new NumberFormatException("abc"));

    ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("INVALID_INPUT");
  }

  @Test
  void handleMissingParameter_returnsCommonInvalidInput() {
    MissingServletRequestParameterException exception =
        new MissingServletRequestParameterException("startDate", "LocalDate");

    ResponseEntity<ErrorResponse> response = handler.handleMissingParameter(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("INVALID_INPUT");
  }

  @Test
  void handleUnexpectedException_returnsInternalServerErrorWithCommonMessage() {
    RuntimeException exception = new RuntimeException("unexpected NPE-like failure");

    ResponseEntity<ErrorResponse> response = handler.handleUnexpectedException(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    assertThat(response.getBody().message()).isEqualTo("요청 처리 중 오류가 발생했습니다.");
  }

  @Test
  void handleUnexpectedException_masksEmailInLoggedThrowableMessage() {
    RuntimeException exception = new RuntimeException("failure for user@example.com");

    handler.handleUnexpectedException(exception);

    IThrowableProxy proxy = appender.list.get(0).getThrowableProxy();
    assertThat(proxy.getMessage()).contains("us***@example.com").doesNotContain(
        "user@example.com");
  }

  private static Method sampleMethod() {
    return GlobalExceptionHandlerTest.class.getDeclaredMethods()[0];
  }
}
