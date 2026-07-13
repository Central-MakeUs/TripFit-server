package com.tripfit.tripfit.user.domain;

import com.tripfit.tripfit.common.domain.BaseTimeEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
@Table(name = "user_condition")
@Schema(description = "사용자 근무·연차 조건 (온보딩·내 일정 관리)")
public class UserCondition extends BaseTimeEntity {

  @Schema(
      description = "조건 레코드 ID (UUID v4)",
      example = "550e8400-e29b-41d4-a716-446655440000")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  private UUID id;

  @Schema(description = "소유 사용자 (1:1)")
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Schema(description = "근무 요일. 콤마 구분", nullable = true, example = "MON,TUE,WED,THU,FRI")
  @Column
  private String workDays;

  @Schema(description = "출근 시각", nullable = true, example = "09:00:00")
  @Column
  private LocalTime workStartTime;

  @Schema(description = "퇴근 시각", nullable = true, example = "18:00:00")
  @Column
  private LocalTime workEndTime;

  @Schema(description = "여행당 사용 가능 최대 연차 일수", nullable = true, example = "5")
  @Column
  private Integer maxVacationDays;

  @Schema(description = "연차 신청 가능 시점 (예: 당일, 1주 전)", nullable = true, example = "1주 전")
  @Column
  private String vacationApplyPeriod;

  @Schema(description = "반차 사용 가능 여부", example = "true")
  @Column(name = "is_half_vacation_available", nullable = false)
  private boolean halfVacationAvailable;

  @Schema(description = "공휴일 휴무 여부", example = "true")
  @Column(name = "is_holiday_rest", nullable = false)
  private boolean holidayRest;

  public UserCondition(
      User user,
      String workDays,
      LocalTime workStartTime,
      LocalTime workEndTime,
      Integer maxVacationDays,
      String vacationApplyPeriod,
      boolean halfVacationAvailable,
      boolean holidayRest) {
    this.user = user;
    this.workDays = workDays;
    this.workStartTime = workStartTime;
    this.workEndTime = workEndTime;
    this.maxVacationDays = maxVacationDays;
    this.vacationApplyPeriod = vacationApplyPeriod;
    this.halfVacationAvailable = halfVacationAvailable;
    this.holidayRest = holidayRest;
  }
}
