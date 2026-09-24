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
@Schema(description = "발송된 FCM 알림 이력 정보입니다. 알림센터 조회 및 읽음 상태를 관리합니다.")
public class NotificationHistory extends BaseTimeEntity {

  @Schema(description = "알림 이력 ID (UUID v4)")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  private UUID id;

  @Schema(description = "알림을 수신하는 사용자입니다.")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Schema(description = "알림과 관련된 여행방 정보입니다. 여행방과 무관한 알림의 경우 null입니다.", nullable = true)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id")
  private Trip trip;

  @Schema(description = "알림의 종류를 나타냅니다.")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationType type;

  @Schema(description = "알림의 제목입니다.", example = "여행방 참여 알림")
  @Column(nullable = false)
  private String title;

  @Schema(description = "알림의 본문 내용입니다.", example = "OO님이 여행방에 참여했어요! 참여 현황을 확인해보세요.")
  @Column(nullable = false, length = 500)
  private String body;

  @Schema(description = "알림을 탭했을 때 이동할 화면 정보입니다.")
  @Enumerated(EnumType.STRING)
  @Column(name = "landing_type", nullable = false)
  private LandingType landingType;

  @Schema(description = "알림을 읽었는지 여부를 나타냅니다.", example = "false")
  @Column(name = "is_read", nullable = false)
  private boolean read = false;

  @Schema(description = "알림을 읽은 시각입니다. 아직 읽지 않은 경우 null입니다.", nullable = true)
  @Column(name = "read_at")
  private LocalDateTime readAt;

  @Schema(description = "FCM 발송 시각입니다.")
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
