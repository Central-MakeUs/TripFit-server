package com.tripfit.tripfit.user.schedule.domain;

import com.tripfit.tripfit.common.domain.BaseTimeEntity;
import com.tripfit.tripfit.trip.domain.SlotStatuses;
import com.tripfit.tripfit.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
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
@Table(name = "regular_schedule")
@Schema(description = "User 정기 일정 (출근·수업·회의 등). trip FK 없음. user당 N행 (BR-TRIP-006)")
public class RegularSchedule extends BaseTimeEntity {

  public static final int DEFAULT_MAX_VACATION_DAYS = 2;

  public static final int MAX_VACATION_DAYS_LIMIT = 10;

  @Schema(
      description = "정기 일정 ID (UUID v4)",
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

  @Schema(description = "표시명 (출근·수업·회의 등)", example = "출근")
  @Column(nullable = false)
  private String title;

  @Schema(
      description = "반복 요일. Weekday 콤마 구분(MON~SUN)",
      nullable = true,
      example = "MON,TUE,WED,THU,FRI")
  @Column(name = "days_of_week")
  private String daysOfWeek;

  @Schema(description = "시작 시각", nullable = true, example = "09:00:00")
  @Column(name = "start_time")
  private LocalTime startTime;

  @Schema(description = "종료 시각", nullable = true, example = "18:00:00")
  @Column(name = "end_time")
  private LocalTime endTime;

  @Embedded
  private SlotStatuses slotStatuses = SlotStatuses.empty();

  @Schema(description = "여행당 사용 가능 최대 연차 일수. default 2, 최대 10", example = "2")
  @Column(name = "max_vacation_days", nullable = false)
  private int maxVacationDays = DEFAULT_MAX_VACATION_DAYS;

  @Schema(description = "연차 신청 가능 시점. null = 미설정", nullable = true)
  @Enumerated(EnumType.STRING)
  @Column(name = "vacation_apply_period")
  private VacationApplyPeriod vacationApplyPeriod;

  @Schema(description = "반차 사용 가능 여부. default false(N)", example = "false")
  @Column(name = "is_half_vacation_available", nullable = false)
  private boolean halfVacationAvailable;

  @Schema(description = "공휴일 휴무 여부. default true(Y)", example = "true")
  @Column(name = "is_holiday_rest", nullable = false)
  private boolean holidayRest = true;

  public static RegularSchedule create(
      User user,
      String title,
      String daysOfWeek,
      LocalTime startTime,
      LocalTime endTime,
      Integer maxVacationDays,
      VacationApplyPeriod vacationApplyPeriod,
      Boolean halfVacationAvailable,
      Boolean holidayRest) {
    RegularSchedule schedule = new RegularSchedule();
    schedule.user = user;
    schedule.title = title;
    schedule.daysOfWeek = daysOfWeek;
    schedule.startTime = startTime;
    schedule.endTime = endTime;
    schedule.maxVacationDays =
        maxVacationDays != null ? maxVacationDays : DEFAULT_MAX_VACATION_DAYS;
    schedule.vacationApplyPeriod = vacationApplyPeriod;
    schedule.halfVacationAvailable = halfVacationAvailable != null && halfVacationAvailable;
    schedule.holidayRest = holidayRest == null || holidayRest;
    schedule.slotStatuses = SlotStatuses.fromTimeRange(startTime, endTime);
    return schedule;
  }

  public void applyUpdate(
      String title,
      String daysOfWeek,
      LocalTime startTime,
      LocalTime endTime,
      Integer maxVacationDays,
      VacationApplyPeriod vacationApplyPeriod,
      Boolean halfVacationAvailable,
      Boolean holidayRest) {
    this.title = title;
    this.daysOfWeek = daysOfWeek;
    this.startTime = startTime;
    this.endTime = endTime;
    this.maxVacationDays =
        maxVacationDays != null ? maxVacationDays : DEFAULT_MAX_VACATION_DAYS;
    this.vacationApplyPeriod = vacationApplyPeriod;
    this.halfVacationAvailable = halfVacationAvailable != null && halfVacationAvailable;
    this.holidayRest = holidayRest == null || holidayRest;
    this.slotStatuses = SlotStatuses.fromTimeRange(startTime, endTime);
  }
}
