package com.tripfit.tripfit.notification.dto;

import com.tripfit.tripfit.notification.domain.LandingType;
import com.tripfit.tripfit.notification.domain.NotificationHistory;
import com.tripfit.tripfit.notification.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "알림센터 알림 항목. GET /notifications 응답")

public record NotificationResponse(
    @Schema(description = "알림 이력 ID") UUID id,

    @Schema(description = "알림 종류") NotificationType type,

    @Schema(description = "알림 제목", example = "여행방 참여 알림") String title,

    @Schema(description = "알림 본문", example = "OO님이 여행방에 참여했어요! 참여 현황을 확인해보세요.") String body,

    @Schema(description = "탭 시 이동할 화면") LandingType landingType,

    @Schema(
        description = "관련 여행방 ID. 여행방과 무관한 알림(정기 리마인드)은 null",
        nullable = true) UUID tripId,

    @Schema(
        description = "관련 여행방 이름. 여행방과 무관한 알림(정기 리마인드)은 null",
        nullable = true,
        example = "제주도 3박4일") String roomName,

    @Schema(description = "읽음 여부", example = "false") boolean isRead,

    @Schema(description = "발송 시각") LocalDateTime sentAt
) {

  public static NotificationResponse from(NotificationHistory history) {
    return new NotificationResponse(
        history.getId(),
        history.getType(),
        history.getTitle(),
        history.getBody(),
        history.getLandingType(),
        history.getTrip() != null ? history.getTrip().getId() : null,
        history.getTrip() != null ? history.getTrip().getName() : null,
        history.isRead(),
        history.getSentAt());
  }
}
