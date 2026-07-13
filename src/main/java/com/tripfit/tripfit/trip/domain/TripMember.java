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
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
    name = "trip_member",
    uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "user_id"}))
@Schema(description = "여행방 참여자. trip–user 매핑 및 응답 상태")
public class TripMember extends BaseTimeEntity {

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

  @Schema(description = "참여 사용자. 소셜 로그인 필수 (BR-USER-002)",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
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

  @Schema(description = "홈 화면 고정 여부 (참여자별)", example = "false")
  @Column(name = "is_pinned", nullable = false)
  private boolean pinned;

  public TripMember(
      Trip trip, User user, TripMemberRole role, TripMemberStatus status, LocalDateTime joinedAt) {
    this.trip = trip;
    this.user = user;
    this.role = role;
    this.status = status;
    this.joinedAt = joinedAt;
  }
}
