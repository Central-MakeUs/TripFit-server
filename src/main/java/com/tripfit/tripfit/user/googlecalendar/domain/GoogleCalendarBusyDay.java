package com.tripfit.tripfit.user.googlecalendar.domain;

import com.tripfit.tripfit.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "google_calendar_busy_day",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_google_calendar_busy_day_user_date",
        columnNames = {"user_id", "schedule_date"}))
@Schema(description = "Google Calendar freeBusy → 날짜×슬롯 busy 캐시 (sparse, busy 슬롯 있는 날만)")
public class GoogleCalendarBusyDay {

  @Schema(
      description = "busy day ID (UUID v4)",
      example = "550e8400-e29b-41d4-a716-446655440000")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  private UUID id;

  @Schema(description = "소유 사용자")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Schema(description = "일정 날짜 (Asia/Seoul)", example = "2026-08-03")
  @Column(name = "schedule_date", nullable = false)
  private LocalDate scheduleDate;

  @Schema(description = "오전 슬롯 busy 여부", example = "true")
  @Column(name = "morning_busy", nullable = false)
  private boolean morningBusy;

  @Schema(description = "오후 슬롯 busy 여부", example = "false")
  @Column(name = "afternoon_busy", nullable = false)
  private boolean afternoonBusy;

  @Schema(description = "저녁 슬롯 busy 여부", example = "false")
  @Column(name = "evening_busy", nullable = false)
  private boolean eveningBusy;

  @Schema(description = "마지막 sync 반영 시각")
  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public static GoogleCalendarBusyDay create(
      User user,
      LocalDate scheduleDate,
      boolean morningBusy,
      boolean afternoonBusy,
      boolean eveningBusy) {
    GoogleCalendarBusyDay day = new GoogleCalendarBusyDay();
    day.user = user;
    day.scheduleDate = scheduleDate;
    day.morningBusy = morningBusy;
    day.afternoonBusy = afternoonBusy;
    day.eveningBusy = eveningBusy;
    return day;
  }

  public void apply(boolean morningBusy, boolean afternoonBusy, boolean eveningBusy) {
    this.morningBusy = morningBusy;
    this.afternoonBusy = afternoonBusy;
    this.eveningBusy = eveningBusy;
  }

  public boolean hasAnyBusy() {
    return morningBusy || afternoonBusy || eveningBusy;
  }
}
