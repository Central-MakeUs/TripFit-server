package com.tripfit.tripfit.notification.domain;

import com.tripfit.tripfit.common.domain.BaseTimeEntity;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "notification_history",
    indexes = @Index(name = "idx_notification_history_user_sent_at",
        columnList = "user_id, sent_at"))
@Schema(description = "발송된 FCM 알림 이력. 알림센터 조회·읽음 상태를 포함한다")
public class NotificationHistory extends BaseTimeEntity {

  @Schema(description = "알림 이력 ID (UUID v4)")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  private UUID id;

  @Schema(description = "알림 수신자")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Schema(description = "관련 여행방. 여행방과 무관한 알림(정기 리마인드)은 null", nullable = true)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id")
  private Trip trip;

  @Schema(description = "알림 종류")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationType type;

  @Schema(description = "알림 제목", example = "여행방 참여 알림")
  @Column(nullable = false)
  private String title;

  @Schema(description = "알림 본문", example = "OO님이 여행방에 참여했어요! 참여 현황을 확인해보세요.")
  @Column(nullable = false, length = 500)
  private String body;

  @Schema(description = "탭 시 이동할 화면")
  @Enumerated(EnumType.STRING)
  @Column(name = "landing_type", nullable = false)
  private LandingType landingType;

  @Schema(description = "읽음 여부", example = "false")
  @Column(name = "is_read", nullable = false)
  private boolean read = false;

  @Schema(description = "읽은 시각. 미읽음이면 null", nullable = true)
  @Column(name = "read_at")
  private LocalDateTime readAt;

  @Schema(description = "FCM 발송 시각")
  @Column(name = "sent_at", nullable = false)
  private LocalDateTime sentAt;

  public NotificationHistory(
      User user,
      Trip trip,
      NotificationType type,
      String title,
      String body,
      LandingType landingType,
      LocalDateTime sentAt) {
    this.user = user;
    this.trip = trip;
    this.type = type;
    this.title = title;
    this.body = body;
    this.landingType = landingType;
    this.sentAt = sentAt;
  }

  public void markRead(LocalDateTime now) {
    if (this.read) {
      return;
    }
    this.read = true;
    this.readAt = now;
  }
}
