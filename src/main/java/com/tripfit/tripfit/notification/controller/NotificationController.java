package com.tripfit.tripfit.notification.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import com.tripfit.tripfit.notification.dto.NotificationResponse;
import com.tripfit.tripfit.notification.service.NotificationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림센터 — 알림 이력 조회·읽음 처리")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationQueryService notificationQueryService;

  public NotificationController(NotificationQueryService notificationQueryService) {
    this.notificationQueryService = notificationQueryService;
  }

  /** 본인이 받은 알림 이력을 최신순으로 반환한다. 최근 7일 이내 발송분만 포함되고, 8일 이전 알림은 제외된다. */
  @Operation(summary = "알림센터 목록 조회")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": [{"id": "550e8400-e29b-41d4-a716-446655440000", "type": "JOIN_COMPLETED", "title": "여행방 참여 알림", "body": "홍길동님이 여행방에 참여했어요! 참여 현황을 확인해보세요.", "landingType": "TRAVEL_ROOM_DETAIL", "tripId": "660e8400-e29b-41d4-a716-446655440000", "roomName": "제주도 3박4일", "isRead": false, "sentAt": "2026-08-01T09:00:00"}]}
                      """))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "AUTH_EXPIRED", "message": "액세스 토큰이 만료되었습니다."}
                  """)))
  })
  @GetMapping
  ResponseEntity<SuccessResponse<List<NotificationResponse>>> list(@AuthorizedUser UUID userId) {
    return ResponseEntity.ok(SuccessResponse.of(notificationQueryService.listRecent(userId)));
  }

  /** 알림센터에서 알림을 열람했음을 표시한다. 이미 읽음 상태면 변경 없이 동일하게 처리한다(idempotent). */
  @Operation(summary = "알림 읽음 처리")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "읽음 처리 성공(No Content)"),
      @ApiResponse(
          responseCode = "404",
          description = "NOTIFICATION_NOT_FOUND — 존재하지 않거나 본인 것이 아닌 알림",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "NOTIFICATION_NOT_FOUND", "message": "알림을 찾을 수 없습니다."}
                  """))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "AUTH_EXPIRED", "message": "액세스 토큰이 만료되었습니다."}
                  """)))
  })
  @PatchMapping("/{id}/read")
  ResponseEntity<Void> markRead(@AuthorizedUser UUID userId, @PathVariable UUID id) {
    notificationQueryService.markRead(userId, id);
    return ResponseEntity.noContent().build();
  }
}
