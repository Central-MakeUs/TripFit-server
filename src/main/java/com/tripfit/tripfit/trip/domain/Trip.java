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
  @Setter
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

  @Schema(
      description = "희망 여행 일수 (m일). null=아직 못정했어요. durationNights와 쌍으로 저장",
      nullable = true,
      example = "4")
  @Column
  private Integer durationDays;

  @Schema(
      description = "희망 여행 박수 (n박). null=아직 못정했어요. durationDays와 쌍으로 저장(nights+1 ≤ days ≤ nights+2)",
      nullable = true,
      example = "3")
  @Column
  private Integer durationNights;

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

  @Schema(description = "확정 취소(unconfirm) 사유. 최신값만 저장(덮어쓰기, 이력 아님)", nullable = true)
  @Enumerated(EnumType.STRING)
  @Column(name = "unconfirm_reason")
  private UnconfirmReason unconfirmReason;

  @Schema(description = "unconfirmReason=OTHER일 때 직접 입력 텍스트", nullable = true)
  @Column(name = "unconfirm_reason_detail", columnDefinition = "TEXT")
  private String unconfirmReasonDetail;

  @Schema(
      description = "확정 시점 참석 인원 수(전체+부분참석). status=CONFIRMED일 때만 값 있음, unconfirm 시 null",
      nullable = true,
      example = "5")
  @Column(name = "confirmed_attend_count")
  private Integer confirmedAttendCount;

  @Schema(
      description = "확정 시점 연차가 필요한 인원 수. status=CONFIRMED일 때만 값 있음, unconfirm 시 null",
      nullable = true,
      example = "1")
  @Column(name = "confirmed_vacation_member_count")
  private Integer confirmedVacationMemberCount;

  @Schema(
      description = "확정 시점 불확실 일정이 있던 인원 수. status=CONFIRMED일 때만 값 있음, unconfirm 시 null",
      nullable = true,
      example = "0")
  @Column(name = "confirmed_uncertain_count")
  private Integer confirmedUncertainCount;

  @Schema(
      description = "마지막으로 사용한 추천 모드. 추천 생성 API 저장 시 갱신",
      nullable = true,
      example = "BASIC")
  @Enumerated(EnumType.STRING)
  @Column(name = "last_recommendation_mode")
  private RecommendationMode lastRecommendationMode;

  @Schema(description = "홈 목록 정렬용 최근 활동 시각", example = "2026-07-19T12:00:00")
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

  // 최근 활동 시각 갱신 — join·patch·confirm·추천·확정 등에서 Aspect가 호출
  public void touchLastActivity() {
    this.lastActivityAt = LocalDateTime.now();
  }

  // 여행지 설정 — 생성자에 destination 파라미터가 없어 생성 직후 별도로 채움
  public void applyDestination(String destination) {
    this.destination = destination;
  }

  // 방장 메타 수정(PATCH) — 값 변경 여부 판단은 호출부 책임, 5개 필드 항상 덮어씀
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

  // 여행 날짜 확정 — 확정 시점 참석·연차·불확실 인원 통계도 함께 저장
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

  // 확정 취소 — ONGOING으로 되돌리고 확정 통계 전부 초기화. reasonDetail은 reason=OTHER일 때만 보존
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

  // end_range 경과로 방을 종료 처리
  public void expire() {
    this.status = TripStatus.EXPIRED;
  }

  // 추천 생성 API 저장 시 마지막 사용 모드 갱신
  public void applyLastRecommendationMode(RecommendationMode mode) {
    this.lastRecommendationMode = mode;
  }
}
