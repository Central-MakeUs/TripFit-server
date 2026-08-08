package com.tripfit.tripfit.trip.membership.domain;

import com.tripfit.tripfit.common.domain.SoftDeleteEntity;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
// active UNIQUE(trip_id,user_id)는 app 강제 — soft-deleted row 재가입 허용 (MySQL partial unique 미지원)
@Table(name = "trip_member")
@Schema(description = "여행방 참여자. trip–user 매핑 및 응답 상태")
public class TripMember extends SoftDeleteEntity {

  @Schema(
      description = "참여자 레코드 ID (UUID v4)",
      example = "550e8400-e29b-41d4-a716-446655440000")
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

  @Schema(description = "참여 사용자 (소셜 로그인으로 생성된 User)",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Schema(description = "방 내 역할 (방장/멤버)")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TripMemberRole role;

  @Schema(description = "방 참여 시각 (멤버 row 생성)", example = "2026-07-07T12:00:00")
  @Column(nullable = false)
  private LocalDateTime joinedAt;

  @Schema(
      description = "일정 확인·가입 완료 시각. null이면 SCHEDULE_PENDING(미확인), 값이 있으면 ACTIVE(확인 완료) — confirm/join 시 set."
          + " 일정 응답 진행 상태(SCHEDULE_PENDING|ACTIVE)의 SSOT이며 별도 status 컬럼은 없음",
      nullable = true,
      example = "2026-07-07T12:05:00")
  @Column(name = "activated_at")
  private LocalDateTime activatedAt;

  @Schema(description = "홈 화면 고정 여부 (참여자별 · 진행 중 캐러셀)", example = "false")
  @Column(name = "is_pinned", nullable = false)
  private boolean pinned;

  @Schema(description = "Pin을 켠 시각. Pin OFF면 null", nullable = true,
      example = "2026-07-19T14:00:00")
  @Column(name = "pinned_at")
  private LocalDateTime pinnedAt;

  public TripMember(
      Trip trip, User user, TripMemberRole role, TripMemberStatus status, LocalDateTime joinedAt) {
    this.trip = trip;
    this.user = user;
    this.role = role;
    this.joinedAt = joinedAt;
    // join 경로: INSERT 즉시 ACTIVE → activated_at = joined_at
    if (status == TripMemberStatus.ACTIVE) {
      this.activatedAt = joinedAt;
    }
  }

  // 일정 응답 진행 상태 — activatedAt null 여부로 파생 계산(저장 컬럼 없음)
  @Schema(description = "일정 응답 진행 상태")
  public TripMemberStatus getStatus() {
    return activatedAt == null ? TripMemberStatus.SCHEDULE_PENDING : TripMemberStatus.ACTIVE;
  }

  // Pin on/off — on이면 pinnedAt=now, off이면 null
  public void applyPin(boolean pinned) {
    this.pinned = pinned;
    this.pinnedAt = pinned ? LocalDateTime.now() : null;
  }

  // 일정 확인 완료 — activatedAt 세팅만으로 SCHEDULE_PENDING→ACTIVE 파생 전환
  public void activate() {
    this.activatedAt = LocalDateTime.now();
  }

  // end_range 경과 시 Pin 자동 해제
  public void clearPin() {
    this.pinned = false;
    this.pinnedAt = null;
  }
}
