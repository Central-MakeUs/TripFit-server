package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalTime;

// 정기·개인 일정이 공유하는 오전/오후/저녁 슬롯 상태
@Embeddable
@Schema(description = "TimeSlot별 가능/불가 상태 (정기·개인 일정 공통)")
public class SlotStatuses {

  @Schema(description = "오전 슬롯 상태", example = "IMPOSSIBLE", nullable = true)
  @Enumerated(EnumType.STRING)
  @Column(name = "morning_status")
  private ScheduleStatus morningStatus;

  @Schema(description = "오후 슬롯 상태", example = "POSSIBLE", nullable = true)
  @Enumerated(EnumType.STRING)
  @Column(name = "afternoon_status")
  private ScheduleStatus afternoonStatus;

  @Schema(description = "저녁 슬롯 상태", example = "POSSIBLE", nullable = true)
  @Enumerated(EnumType.STRING)
  @Column(name = "evening_status")
  private ScheduleStatus eveningStatus;

  protected SlotStatuses() {}

  public SlotStatuses(
      ScheduleStatus morningStatus, ScheduleStatus afternoonStatus, ScheduleStatus eveningStatus) {
    this.morningStatus = morningStatus;
    this.afternoonStatus = afternoonStatus;
    this.eveningStatus = eveningStatus;
  }

  public static SlotStatuses empty() {
    return new SlotStatuses(null, null, null);
  }

  // start~end와 각 TimeSlot 경계를 겹쳐 상태 산출
  public static SlotStatuses fromTimeRange(LocalTime startTime, LocalTime endTime) {
    if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
      return empty();
    }
    return new SlotStatuses(
        TimeSlot.MORNING.statusForRange(startTime, endTime),
        TimeSlot.AFTERNOON.statusForRange(startTime, endTime),
        TimeSlot.EVENING.statusForRange(startTime, endTime));
  }

  public ScheduleStatus get(TimeSlot slot) {
    return switch (slot) {
      case MORNING -> morningStatus;
      case AFTERNOON -> afternoonStatus;
      case EVENING -> eveningStatus;
    };
  }

  public void set(TimeSlot slot, ScheduleStatus status) {
    switch (slot) {
      case MORNING -> morningStatus = status;
      case AFTERNOON -> afternoonStatus = status;
      case EVENING -> eveningStatus = status;
    }
  }

  public ScheduleStatus getMorningStatus() {
    return morningStatus;
  }

  public ScheduleStatus getAfternoonStatus() {
    return afternoonStatus;
  }

  public ScheduleStatus getEveningStatus() {
    return eveningStatus;
  }

  public void setMorningStatus(ScheduleStatus morningStatus) {
    this.morningStatus = morningStatus;
  }

  public void setAfternoonStatus(ScheduleStatus afternoonStatus) {
    this.afternoonStatus = afternoonStatus;
  }

  public void setEveningStatus(ScheduleStatus eveningStatus) {
    this.eveningStatus = eveningStatus;
  }
}
