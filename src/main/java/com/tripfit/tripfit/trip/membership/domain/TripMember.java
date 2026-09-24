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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity

@Table(name = "trip_member")
@Schema(description = "여행방 참여자 정보입니다. 여행방과 사용자의 매핑 관계 및 응답 상태를 관리합니다.")
public class TripMember extends SoftDeleteEntity {

  @Schema(
      description = "참여자 레코드의 식별자(ID)입니다. (UUID v4 형식)",
      example = "550e8400-e29b-41d4-a716-446655440000")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  @Setter
  private UUID id;

  @Schema(description = "참여자가 소속된 여행방입니다.")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id", nullable = false)
  private Trip trip;

  @Schema(description = "참여한 사용자 정보입니다. (소셜 로그인을 통해 생성된 사용자)",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Schema(description = "여행방 내에서의 역할입니다. (방장 또는 일반 멤버)")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TripMemberRole role;

  @Schema(description = "여행방에 참여한 시각입니다.", example = "2026-07-07T12:00:00")
  @Column(nullable = false)
  private LocalDateTime joinedAt;

  @Schema(
      description = "일정 확인 완료 시각. null이면 SCHEDULE_PENDING(미확인), 값이 있으면 ACTIVE(확인 완료). activate 시 set."
          + " 일정 응답 진행 상태(SCHEDULE_PENDING|ACTIVE)의 SSOT이며 별도 status 컬럼은 없음",
      nullable = true,
      example = "2026-07-07T12:05:00")
  @Column(name = "activated_at")
  private LocalDateTime activatedAt;

  @Schema(description = "홈 화면 고정(Pin) 여부입니다. (참여자별로 다르게 설정되며, 진행 중인 캐러셀 영역에 표시됩니다)",
      example = "false")
  @Column(name = "is_pinned", nullable = false)
  private boolean pinned;

  @Schema(description = "홈 화면 고정(Pin)을 설정한 시각입니다. 고정되어 있지 않은 경우 null입니다.", nullable = true,
      example = "2026-07-19T14:00:00")
  @Column(name = "pinned_at")
  private LocalDateTime pinnedAt;

  public TripMember(
      Trip trip, User user, TripMemberRole role, TripMemberStatus status, LocalDateTime joinedAt) {
    this.trip = trip;
    this.user = user;
    this.role = role;
    this.joinedAt = joinedAt;

    if (status == TripMemberStatus.ACTIVE) {
      this.activatedAt = joinedAt;
    }
  }

  @Schema(description = "참여자의 일정 응답 진행 상태를 나타냅니다.")
  public TripMemberStatus getStatus() {
    return activatedAt == null ? TripMemberStatus.SCHEDULE_PENDING : TripMemberStatus.ACTIVE;
  }

  public void applyPin(boolean pinned) {
    this.pinned = pinned;
    this.pinnedAt = pinned ? LocalDateTime.now() : null;
  }

  public void activate() {
    this.activatedAt = LocalDateTime.now();
  }

  public void clearPin() {
    this.pinned = false;
    this.pinnedAt = null;
  }
}
