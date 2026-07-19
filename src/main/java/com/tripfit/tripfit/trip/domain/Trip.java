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
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
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
@Table(name = "trip", uniqueConstraints = @UniqueConstraint(columnNames = "invite_code"))
@Schema(description = "여행방. 방장이 생성·초대·일정 확정")
public class Trip extends SoftDeleteEntity {

  @Schema(
      description = "여행방 ID (UUID v4)",
      example = "550e8400-e29b-41d4-a716-446655440000")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  private UUID id;

  @Schema(description = "방장(총대) 사용자")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  @Schema(description = "여행방 이름 (최대 15자)", example = "제주도 3박4일", maxLength = 15)
  @Column(nullable = false)
  private String name;

  @Schema(description = "여행지. UI 입력 선택 가능", nullable = true, example = "제주")
  @Column
  private String destination;

  @Schema(description = "희망 여행 기간 시작일", example = "2026-08-01")
  @Column(nullable = false)
  private LocalDate startRange;

  @Schema(description = "희망 여행 기간 종료일", example = "2026-08-10")
  @Column(nullable = false)
  private LocalDate endRange;

  @Schema(description = "희망 여행 일수 (m일)", example = "4")
  @Column(nullable = false)
  private Integer durationDays;

  @Schema(description = "참여 인원 (1~10)", example = "6", minimum = "1", maximum = "10")
  @Column(name = "member_count", nullable = false)
  private Integer memberCount;

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

  @Schema(description = "취소·삭제 VOC 사유. wave 4 구현", nullable = true)
  @Column(name = "cancel_reason")
  private String cancelReason;

  @Schema(
      description = "마지막 추천 모드. 추천 API 저장 시 갱신 (BR-TRIP-005)",
      nullable = true,
      example = "BASIC")
  @Enumerated(EnumType.STRING)
  @Column(name = "last_recommendation_mode")
  private RecommendationMode lastRecommendationMode;

  @Schema(description = "홈 정렬용 최근 활동 시각 (D5)", example = "2026-07-19T12:00:00")
  @Column(name = "last_activity_at", nullable = false)
  private LocalDateTime lastActivityAt;

  public Trip(
      User owner,
      String name,
      LocalDate startRange,
      LocalDate endRange,
      Integer durationDays,
      Integer memberCount,
      String inviteCode,
      TripStatus status) {
    this.owner = owner;
    this.name = name;
    this.startRange = startRange;
    this.endRange = endRange;
    this.durationDays = durationDays;
    this.memberCount = memberCount;
    this.inviteCode = inviteCode;
    this.status = status;
    this.lastActivityAt = LocalDateTime.now();
  }

  // {@link TripActivityAspect} — join · patch · submit · 추천 · 확정 (#26)
  public void touchLastActivity() {
    this.lastActivityAt = LocalDateTime.now();
  }
}
