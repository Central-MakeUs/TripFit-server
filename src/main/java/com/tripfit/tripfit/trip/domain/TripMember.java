package com.tripfit.tripfit.trip.domain;

import com.tripfit.tripfit.common.domain.BaseTimeEntity;
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

import java.time.LocalDateTime;

@Entity
@Table(
    name = "trip_member",
    uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "user_id"}))
@Schema(description = "여행방 참여자. trip–user 매핑 및 응답 상태")
public class TripMember extends BaseTimeEntity {

  @Schema(description = "참여자 레코드 ID", example = "1")
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Schema(description = "소속 여행방")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id", nullable = false)
  private Trip trip;

  // TODO(BR-USER-002): 비회원 링크 참여 시 nullable vs 로그인 강제 정책 확정 필요
  @Schema(description = "참여 사용자. 비회원 참여 정책 미정 시 null 가능", nullable = true)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Schema(description = "방 내 역할 (방장/멤버)")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TripMemberRole role;

  @Schema(description = "일정 응답 진행 상태")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TripMemberStatus status;

  @Schema(description = "방 참여 시각", example = "2026-07-07T12:00:00")
  @Column(nullable = false)
  private LocalDateTime joinedAt;

  protected TripMember() {}

  public TripMember(Trip trip, User user, TripMemberRole role, TripMemberStatus status,
      LocalDateTime joinedAt) {
    this.trip = trip;
    this.user = user;
    this.role = role;
    this.status = status;
    this.joinedAt = joinedAt;
  }

  public Long getId() {
    return id;
  }

  public Trip getTrip() {
    return trip;
  }

  public void setTrip(Trip trip) {
    this.trip = trip;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public TripMemberRole getRole() {
    return role;
  }

  public void setRole(TripMemberRole role) {
    this.role = role;
  }

  public TripMemberStatus getStatus() {
    return status;
  }

  public void setStatus(TripMemberStatus status) {
    this.status = status;
  }

  public LocalDateTime getJoinedAt() {
    return joinedAt;
  }

  public void setJoinedAt(LocalDateTime joinedAt) {
    this.joinedAt = joinedAt;
  }
}
