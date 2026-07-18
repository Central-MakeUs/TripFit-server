package com.tripfit.tripfit.trip.domain;

import com.tripfit.tripfit.common.domain.BaseTimeEntity;
import com.tripfit.tripfit.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
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

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "trip_member_schedule_snapshot",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_trip_member_schedule_snapshot",
        columnNames = {"trip_id", "user_id", "schedule_date"}))
@Schema(description = "확정·종료된 여행방의 멤버 effective 일정 스냅샷. 희망 기간만, 빈 날은 저장하지 않음")
public class TripMemberScheduleSnapshot extends BaseTimeEntity {

  @Schema(description = "snapshot 행 ID (UUID v4)")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  private UUID id;

  @Schema(description = "소속 여행방")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id", nullable = false)
  private Trip trip;

  @Schema(description = "멤버 사용자")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Schema(description = "일정 날짜", example = "2026-08-03")
  @Column(name = "schedule_date", nullable = false)
  private LocalDate scheduleDate;

  @Embedded
  private SlotStatuses slotStatuses = SlotStatuses.empty();

  @Schema(description = "날짜 단위 불확실", example = "false")
  @Column(name = "is_uncertain", nullable = false)
  private boolean uncertain;

  @Schema(description = "freeze 시각", example = "2026-07-21T00:05:00")
  @Column(name = "frozen_at", nullable = false)
  private LocalDateTime frozenAt;

  public static TripMemberScheduleSnapshot create(
      Trip trip,
      User user,
      LocalDate scheduleDate,
      ScheduleStatus morningStatus,
      ScheduleStatus afternoonStatus,
      ScheduleStatus eveningStatus,
      boolean uncertain,
      LocalDateTime frozenAt) {
    TripMemberScheduleSnapshot row = new TripMemberScheduleSnapshot();
    row.trip = trip;
    row.user = user;
    row.scheduleDate = scheduleDate;
    row.slotStatuses = new SlotStatuses(morningStatus, afternoonStatus, eveningStatus);
    row.uncertain = uncertain;
    row.frozenAt = frozenAt;
    return row;
  }
}
