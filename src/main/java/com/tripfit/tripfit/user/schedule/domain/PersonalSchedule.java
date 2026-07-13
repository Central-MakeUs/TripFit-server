package com.tripfit.tripfit.user.schedule.domain;

import com.tripfit.tripfit.common.domain.BaseTimeEntity;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.domain.SlotStatuses;
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
    name = "personal_schedule",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_personal_schedule_user_date",
        columnNames = {"user_id", "schedule_date"}))
@Schema(description = "User 개인(개별) 일정. 특정 날짜의 오전/오후/저녁 + 날짜 단위 불확실. trip FK 없음")
public class PersonalSchedule extends BaseTimeEntity {

  @Schema(
      description = "개인 일정 ID (UUID v4)",
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

  @Schema(description = "일정 날짜", example = "2026-08-03")
  @Column(name = "schedule_date", nullable = false)
  private LocalDate scheduleDate;

  @Embedded
  private SlotStatuses slotStatuses = SlotStatuses.empty();

  @Schema(description = "해당 날짜 전체 불확실 여부 (슬롯별 아님)", example = "false")
  @Column(name = "is_uncertain", nullable = false)
  private boolean uncertain;

  public static PersonalSchedule create(
      User user,
      LocalDate scheduleDate,
      ScheduleStatus morningStatus,
      ScheduleStatus afternoonStatus,
      ScheduleStatus eveningStatus,
      boolean uncertain) {
    PersonalSchedule schedule = new PersonalSchedule();
    schedule.user = user;
    schedule.scheduleDate = scheduleDate;
    schedule.slotStatuses = new SlotStatuses(morningStatus, afternoonStatus, eveningStatus);
    schedule.uncertain = uncertain;
    return schedule;
  }

  public void apply(
      ScheduleStatus morningStatus,
      ScheduleStatus afternoonStatus,
      ScheduleStatus eveningStatus,
      boolean uncertain) {
    this.slotStatuses = new SlotStatuses(morningStatus, afternoonStatus, eveningStatus);
    this.uncertain = uncertain;
  }
}
