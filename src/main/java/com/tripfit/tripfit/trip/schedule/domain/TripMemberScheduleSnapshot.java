package com.tripfit.tripfit.trip.schedule.domain;

import com.tripfit.tripfit.trip.domain.Trip;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "trip_member_schedule_snapshot",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_trip_member_schedule_snapshot",
        columnNames = {"trip_id", "user_id", "schedule_date"}))
@Schema(
    description = "확정되거나 종료된 여행방 멤버의 정기 및 개별 일정을 합친 스냅샷입니다. 희망 기간 내의 일정만 저장하며 비어있는 날은 저장하지 않습니다.")
public class TripMemberScheduleSnapshot extends BaseTimeEntity {

  @Schema(description = "스냅샷 행의 식별자(ID)입니다. (UUID v4 형식)")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  private UUID id;

  @Schema(description = "스냅샷이 속한 여행방입니다.")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id", nullable = false)
  private Trip trip;

  @Schema(description = "스냅샷의 대상이 되는 멤버 사용자입니다.")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Schema(description = "일정이 해당하는 날짜입니다.", example = "2026-08-03")
  @Column(name = "schedule_date", nullable = false)
  private LocalDate scheduleDate;

  @Embedded
  private SlotStatuses slotStatuses = SlotStatuses.empty();

  @Schema(description = "해당 날짜 전체의 일정이 불확실한지 여부를 나타냅니다.", example = "false")
  @Column(name = "is_uncertain", nullable = false)
  private boolean uncertain;

  @Schema(description = "일정 스냅샷이 고정(freeze)된 시각입니다.", example = "2026-07-21T00:05:00")
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
