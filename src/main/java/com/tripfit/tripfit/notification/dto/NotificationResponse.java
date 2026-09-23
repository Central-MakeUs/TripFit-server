package com.tripfit.tripfit.notification.dto;

import com.tripfit.tripfit.notification.domain.LandingType;
import com.tripfit.tripfit.notification.domain.NotificationHistory;
import com.tripfit.tripfit.notification.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(
    description = """
        알림 센터 내 개별 알림 항목 정보입니다. (GET /notifications)
        """)
public record NotificationResponse(
    @Schema(
        description = """
            알림 이력 고유 ID입니다.
            """) UUID id,

    @Schema(
        description = """
            알림의 종류입니다.
            """) NotificationType type,

    @Schema(
        description = """
            알림 제목입니다.
            """,
        example = "여행방 참여 알림") String title,

    @Schema(
        description = """
            알림 상세 본문입니다.
            """,
        example = "OO님이 여행방에 참여했어요! 참여 현황을 확인해보세요.") String body,

    @Schema(
        description = """
            해당 알림 탭(클릭) 시 이동할 대상 화면입니다.
            """) LandingType landingType,

    @Schema(
        description = """
            알림과 연관된 여행방 ID입니다.
            - 정기 리마인드 등 여행방과 무관한 알림은 null을 반환합니다.
            """,
        nullable = true) UUID tripId,

    @Schema(
        description = """
            알림과 연관된 여행방 이름입니다.
            - 여행방과 무관한 알림은 null을 반환합니다.
            """,
        nullable = true,
        example = "제주도 3박4일") String roomName,

    @Schema(
        description = """
            알림 읽음 여부입니다.
            """,
        example = "false") boolean isRead,

    @Schema(
        description = """
            알림이 발송된 시각입니다.
            """) LocalDateTime sentAt
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
