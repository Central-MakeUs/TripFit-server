package com.tripfit.tripfit.user.domain;

import com.tripfit.tripfit.common.domain.BaseTimeEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalTime;

@Entity
@Table(name = "user_condition")
@Schema(description = "사용자 근무·연차 조건 (온보딩·내 일정 관리)")
public class UserCondition extends BaseTimeEntity {

  @Schema(description = "조건 레코드 ID", example = "1")
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

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

  protected UserCondition() {}

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

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getWorkDays() {
    return workDays;
  }

  public void setWorkDays(String workDays) {
    this.workDays = workDays;
  }

  public LocalTime getWorkStartTime() {
    return workStartTime;
  }

  public void setWorkStartTime(LocalTime workStartTime) {
    this.workStartTime = workStartTime;
  }

  public LocalTime getWorkEndTime() {
    return workEndTime;
  }

  public void setWorkEndTime(LocalTime workEndTime) {
    this.workEndTime = workEndTime;
  }

  public Integer getMaxVacationDays() {
    return maxVacationDays;
  }

  public void setMaxVacationDays(Integer maxVacationDays) {
    this.maxVacationDays = maxVacationDays;
  }

  public String getVacationApplyPeriod() {
    return vacationApplyPeriod;
  }

  public void setVacationApplyPeriod(String vacationApplyPeriod) {
    this.vacationApplyPeriod = vacationApplyPeriod;
  }

  public boolean isHalfVacationAvailable() {
    return halfVacationAvailable;
  }

  public void setHalfVacationAvailable(boolean halfVacationAvailable) {
    this.halfVacationAvailable = halfVacationAvailable;
  }

  public boolean isHolidayRest() {
    return holidayRest;
  }

  public void setHolidayRest(boolean holidayRest) {
    this.holidayRest = holidayRest;
  }
}
