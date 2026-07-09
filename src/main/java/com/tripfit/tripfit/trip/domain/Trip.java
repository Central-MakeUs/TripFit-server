package com.tripfit.tripfit.trip.domain;

import com.tripfit.tripfit.common.domain.SoftDeleteEntity;
import com.tripfit.tripfit.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

@Entity
@Table(
    name = "trip",
    uniqueConstraints = @UniqueConstraint(columnNames = "invite_code"))
@Schema(description = "여행방. 방장이 생성·초대·일정 확정")
public class Trip extends SoftDeleteEntity {

  @Schema(description = "여행방 ID", example = "1")
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Schema(description = "방장(총대) 사용자")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  @Schema(description = "여행방 이름", example = "제주도 3박4일")
  @Column(nullable = false)
  private String name;

  @Schema(description = "희망 여행 기간 시작일", example = "2026-08-01")
  @Column(nullable = false)
  private LocalDate startRange;

  @Schema(description = "희망 여행 기간 종료일", example = "2026-08-10")
  @Column(nullable = false)
  private LocalDate endRange;

  @Schema(description = "희망 여행 일수 (m일)", example = "4")
  @Column(nullable = false)
  private Integer durationDays;

  @Schema(description = "예상 참여 인원", example = "6")
  @Column(nullable = false)
  private Integer targetMemberCount;

  @Schema(description = "초대 코드. UNIQUE", example = "ABC123")
  @Column(nullable = false)
  private String inviteCode;

  @Schema(description = "여행방 진행 상태")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TripStatus status;

  @Schema(description = "확정된 여행 시작일. status=CONFIRMED 시", nullable = true, example = "2026-08-03")
  @Column
  private LocalDate confirmedStartDate;

  @Schema(description = "확정된 여행 종료일. status=CONFIRMED 시", nullable = true, example = "2026-08-06")
  @Column
  private LocalDate confirmedEndDate;

  protected Trip() {}

  public Trip(
      User owner,
      String name,
      LocalDate startRange,
      LocalDate endRange,
      Integer durationDays,
      Integer targetMemberCount,
      String inviteCode,
      TripStatus status) {
    this.owner = owner;
    this.name = name;
    this.startRange = startRange;
    this.endRange = endRange;
    this.durationDays = durationDays;
    this.targetMemberCount = targetMemberCount;
    this.inviteCode = inviteCode;
    this.status = status;
  }

  public Long getId() {
    return id;
  }

  public User getOwner() {
    return owner;
  }

  public void setOwner(User owner) {
    this.owner = owner;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public LocalDate getStartRange() {
    return startRange;
  }

  public void setStartRange(LocalDate startRange) {
    this.startRange = startRange;
  }

  public LocalDate getEndRange() {
    return endRange;
  }

  public void setEndRange(LocalDate endRange) {
    this.endRange = endRange;
  }

  public Integer getDurationDays() {
    return durationDays;
  }

  public void setDurationDays(Integer durationDays) {
    this.durationDays = durationDays;
  }

  public Integer getTargetMemberCount() {
    return targetMemberCount;
  }

  public void setTargetMemberCount(Integer targetMemberCount) {
    this.targetMemberCount = targetMemberCount;
  }

  public String getInviteCode() {
    return inviteCode;
  }

  public void setInviteCode(String inviteCode) {
    this.inviteCode = inviteCode;
  }

  public TripStatus getStatus() {
    return status;
  }

  public void setStatus(TripStatus status) {
    this.status = status;
  }

  public LocalDate getConfirmedStartDate() {
    return confirmedStartDate;
  }

  public void setConfirmedStartDate(LocalDate confirmedStartDate) {
    this.confirmedStartDate = confirmedStartDate;
  }

  public LocalDate getConfirmedEndDate() {
    return confirmedEndDate;
  }

  public void setConfirmedEndDate(LocalDate confirmedEndDate) {
    this.confirmedEndDate = confirmedEndDate;
  }
}
