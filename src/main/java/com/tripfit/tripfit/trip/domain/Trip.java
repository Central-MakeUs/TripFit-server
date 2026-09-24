package com.tripfit.tripfit.trip.domain;

import com.tripfit.tripfit.common.domain.SoftDeleteEntity;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationMode;
import com.tripfit.tripfit.trip.recommendation.domain.UnconfirmReason;
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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "trip", uniqueConstraints = @UniqueConstraint(columnNames = "invite_code"))
@Schema(description = "여행방입니다. 방장이 생성, 초대, 일정 확정을 수행할 수 있습니다.")
public class Trip extends SoftDeleteEntity {

  @Schema(
      description = "여행방의 식별자(ID)입니다. (UUID v4 형식)",
      example = "550e8400-e29b-41d4-a716-446655440000")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  @Setter
  private UUID id;

  @Schema(description = "여행방을 생성하고 관리하는 방장(총대) 사용자입니다.")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  @Schema(description = "여행방의 이름입니다. (최대 15자)", example = "제주도 3박4일", maxLength = 15)
  @Column(nullable = false)
  private String name;

  @Schema(description = "여행지의 이름입니다. UI에서 입력 또는 선택이 가능합니다.", nullable = true, example = "제주")
  @Column
  private String destination;

  @Schema(description = "희망하는 여행 기간의 시작일입니다.", example = "2026-08-01")
  @Column(nullable = false)
  private LocalDate startRange;

  @Schema(description = "희망하는 여행 기간의 종료일입니다.", example = "2026-08-10")
  @Column(nullable = false)
  private LocalDate endRange;

  @Schema(
      description = "희망하는 여행 일수(days)입니다. 정해지지 않은 경우 null이며, durationNights와 함께 쌍으로 관리됩니다.",
      nullable = true,
      example = "4")
  @Column
  private Integer durationDays;

  @Schema(
      description = "희망하는 여행 박수(nights)입니다. 정해지지 않은 경우 null이며, durationDays와 함께 쌍으로 관리됩니다.",
      nullable = true,
      example = "3")
  @Column
  private Integer durationNights;

  @Schema(description = "여행방의 전체 참여 정원입니다. (1~10명)", example = "6", minimum = "1", maximum = "10")
  @Column(name = "member_count", nullable = false)
  private Integer memberCount;

  @Schema(description = "여행방에 참여하기 위한 고유한 초대 코드입니다.", example = "ABC123")
  @Column(nullable = false)
  private String inviteCode;

  @Schema(description = "여행방의 현재 진행 상태를 나타냅니다.")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TripStatus status;

  @Schema(description = "최종 확정된 여행 시작일입니다. 상태(status)가 확정(CONFIRMED)일 때만 유효합니다.", nullable = true,
      example = "2026-08-03")
  @Column
  private LocalDate confirmedStartDate;

  @Schema(description = "최종 확정된 여행 종료일입니다. 상태(status)가 확정(CONFIRMED)일 때만 유효합니다.", nullable = true,
      example = "2026-08-06")
  @Column
  private LocalDate confirmedEndDate;

  @Schema(description = "일정 확정이 취소(unconfirm)된 사유입니다. 가장 최근의 사유만 덮어쓰기하여 저장됩니다.", nullable = true)
  @Enumerated(EnumType.STRING)
  @Column(name = "unconfirm_reason")
  private UnconfirmReason unconfirmReason;

  @Schema(description = "확정 취소 사유가 기타(OTHER)일 때 직접 입력한 상세 내용입니다.", nullable = true)
  @Column(name = "unconfirm_reason_detail", columnDefinition = "TEXT")
  private String unconfirmReasonDetail;

  @Schema(
      description = "일정을 확정한 시점의 총 참석 인원 수입니다. 확정(CONFIRMED) 상태일 때만 값이 존재하며, 취소 시 null이 됩니다.",
      nullable = true,
      example = "5")
  @Column(name = "confirmed_attend_count")
  private Integer confirmedAttendCount;

  @Schema(
      description = "일정을 확정한 시점 기준 연차가 필요한 인원 수입니다. 확정(CONFIRMED) 상태일 때만 값이 존재하며, 취소 시 null이 됩니다.",
      nullable = true,
      example = "1")
  @Column(name = "confirmed_vacation_member_count")
  private Integer confirmedVacationMemberCount;

  @Schema(
      description = "일정을 확정한 시점에 불확실한 일정을 가진 인원 수입니다. 확정(CONFIRMED) 상태일 때만 값이 존재하며, 취소 시 null이 됩니다.",
      nullable = true,
      example = "0")
  @Column(name = "confirmed_uncertain_count")
  private Integer confirmedUncertainCount;

  @Schema(
      description = "가장 마지막으로 사용된 일정 추천 모드입니다. 추천 생성 API 호출 시 갱신됩니다.",
      nullable = true,
      example = "BASIC")
  @Enumerated(EnumType.STRING)
  @Column(name = "last_recommendation_mode")
  private RecommendationMode lastRecommendationMode;

  @Schema(description = "홈 화면의 목록 정렬을 위해 사용되는 최근 활동 기준 시각입니다.", example = "2026-07-19T12:00:00")
  @Column(name = "last_activity_at", nullable = false)
  private LocalDateTime lastActivityAt;

  public Trip(
      User owner,
      String name,
      LocalDate startRange,
      LocalDate endRange,
      Integer durationNights,
      Integer durationDays,
      Integer memberCount,
      String inviteCode,
      TripStatus status) {
    this.owner = owner;
    this.name = name;
    this.startRange = startRange;
    this.endRange = endRange;
    this.durationNights = durationNights;
    this.durationDays = durationDays;
    this.memberCount = memberCount;
    this.inviteCode = inviteCode;
    this.status = status;
    this.lastActivityAt = LocalDateTime.now();
  }

  public void touchLastActivity() {
    this.lastActivityAt = LocalDateTime.now();
  }

  public void applyDestination(String destination) {
    this.destination = destination;
  }

  public void applyPatch(
      String name,
      String destination,
      Integer durationNights,
      Integer durationDays,
      Integer memberCount) {
    this.name = name;
    this.destination = destination;
    this.durationNights = durationNights;
    this.durationDays = durationDays;
    this.memberCount = memberCount;
  }

  public void confirm(
      LocalDate confirmedStartDate,
      LocalDate confirmedEndDate,
      Integer confirmedAttendCount,
      Integer confirmedVacationMemberCount,
      Integer confirmedUncertainCount) {
    this.status = TripStatus.CONFIRMED;
    this.confirmedStartDate = confirmedStartDate;
    this.confirmedEndDate = confirmedEndDate;
    this.confirmedAttendCount = confirmedAttendCount;
    this.confirmedVacationMemberCount = confirmedVacationMemberCount;
    this.confirmedUncertainCount = confirmedUncertainCount;
  }

  public void unconfirm(UnconfirmReason reason, String reasonDetail) {
    this.unconfirmReason = reason;
    this.unconfirmReasonDetail = reason == UnconfirmReason.OTHER ? reasonDetail : null;
    this.status = TripStatus.ONGOING;
    this.confirmedStartDate = null;
    this.confirmedEndDate = null;
    this.confirmedAttendCount = null;
    this.confirmedVacationMemberCount = null;
    this.confirmedUncertainCount = null;
  }

  public void expire() {
    this.status = TripStatus.EXPIRED;
  }

  public void applyLastRecommendationMode(RecommendationMode mode) {
    this.lastRecommendationMode = mode;
  }
}
