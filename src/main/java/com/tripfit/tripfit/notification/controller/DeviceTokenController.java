package com.tripfit.tripfit.notification.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.notification.dto.DeviceTokenRegisterRequest;
import com.tripfit.tripfit.notification.service.DeviceTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification Device Token", description = "FCM 디바이스 토큰 등록·해제")
@RestController
@RequestMapping("/api/v1/notifications/device-tokens")
public class DeviceTokenController {
  private final DeviceTokenService deviceTokenService;

  public DeviceTokenController(DeviceTokenService deviceTokenService) {
    this.deviceTokenService = deviceTokenService;
  }

  /**
   * [디바이스 토큰 등록·갱신]
   * 본인 기기의 FCM 등록 토큰을 서버에 저장합니다.
   *
   * <p>■ FE 유의사항
   * <br>- 앱 실행 시 혹은 토큰 갱신 이벤트 발생 시 주기적으로 호출해야 합니다.
   *
   * <p>■ BE 처리
   * <br>- 동일 토큰이 이미 등록되어 있다면 현재 사용자의 소유로 재할당(Upsert) 및 갱신합니다.
   */
  @Operation(summary = "디바이스 토큰 등록·갱신")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "등록·갱신 성공(No Content)"),
      @ApiResponse(
          responseCode = "400",
          description = "NOTIFICATION_TOKEN_REQUIRED (토큰 값 누락)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "400",
          description = "INVALID_INPUT (deviceType 누락(필수))·정의되지 않은 값",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping
  ResponseEntity<Void> register(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody DeviceTokenRegisterRequest request) {
    deviceTokenService.registerToken(userId, request);
    return ResponseEntity.noContent().build();
  }

  /**
   * [디바이스 토큰 해제]
   * 특정 기기의 FCM 토큰을 서버에서 삭제합니다.
   *
   * <p>■ FE 유의사항
   * <br>- 로그아웃 직전 등에 호출하여 더 이상 푸시 알림이 오지 않도록 합니다.
   *
   * <p>■ BE 처리
   * <br>- DB에서 해당 FCM 토큰을 찾아 삭제합니다.
   */
  @Operation(summary = "디바이스 토큰 해제")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "해제 성공(No Content)"),
      @ApiResponse(
          responseCode = "404",
          description = "NOTIFICATION_TOKEN_NOT_FOUND (토큰이 없거나 본인 것이 아님)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping
  ResponseEntity<Void> unregister(
      @AuthorizedUser UUID userId,
      @Parameter(description = "해제할 FCM 등록 토큰 값") @RequestParam String token) {
    deviceTokenService.unregisterToken(userId, token);
    return ResponseEntity.noContent().build();
  }
}
