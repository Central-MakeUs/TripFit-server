package com.tripfit.tripfit.notification.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import com.tripfit.tripfit.notification.dto.NotificationResponse;
import com.tripfit.tripfit.notification.service.NotificationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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

@Tag(name = "Notification", description = "알림센터 기능으로, 사용자의 알림 이력 조회 및 읽음 처리 기능을 제공합니다.")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final NotificationQueryService notificationQueryService;

  public NotificationController(NotificationQueryService notificationQueryService) {
    this.notificationQueryService = notificationQueryService;
  }

  /**
   * [알림센터 목록 조회] 본인이 받은 알림 이력을 최신순으로 조회합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 최신순으로 정렬된 알림 목록을 반환합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 최근 7일 이내에 발송된 알림만 쿼리하며, 8일 이전 데이터는 노출되지 않습니다.
   */
  @Operation(summary = "알림센터 목록 조회")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true),
  })
  @GetMapping
  ResponseEntity<SuccessResponse<List<NotificationResponse>>> list(@AuthorizedUser UUID userId) {
    return ResponseEntity.ok(SuccessResponse.of(notificationQueryService.listRecent(userId)));
  }

  /**
   * [알림 읽음 처리] 알림센터에서 특정 알림을 열람했음을 서버에 기록합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 읽음 처리 완료 시 204 No Content를 반환합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 해당 알림의 isRead 상태를 true로 업데이트합니다 (Idempotent).
   */
  @Operation(summary = "알림 읽음 처리")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "알림 읽음 처리가 성공적으로 완료되었습니다. (No Content)"),
      @ApiResponse(
          responseCode = "404",
          description = "요청한 알림이 존재하지 않거나, 본인의 알림이 아닙니다(NOTIFICATION_NOT_FOUND).",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
  })
  @PatchMapping("/{id}/read")
  ResponseEntity<Void> markRead(@AuthorizedUser UUID userId, @PathVariable UUID id) {
    notificationQueryService.markRead(userId, id);
    return ResponseEntity.noContent().build();
  }
}
