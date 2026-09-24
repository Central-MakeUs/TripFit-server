package com.tripfit.tripfit.trip.recommendation.domain;

import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.common.domain.BaseTimeEntity;
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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "recommendation_feedback",
    uniqueConstraints = @UniqueConstraint(columnNames = "recommendation_id"))
@Schema(
    description = "방장이 추천 근거 화면에서 남기는 '도움이 되었나요' 피드백입니다. 후보(recommendation)당 최대 1건만 등록되며 방장 전용 기능입니다.")
public class RecommendationFeedback extends BaseTimeEntity {

  @Schema(
      description = "피드백 레코드 ID (UUID v4)",
      example = "550e8400-e29b-41d4-a716-446655440000")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  private UUID id;

  @Schema(description = "피드백 대상 여행방입니다.")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id", nullable = false)
  private Trip trip;

  @Schema(
      description = "대상 추천 결과(recommendation)의 ID입니다. 외래 키(FK) 제약을 걸지 않는 참조값입니다."
          + " recommendation은 재추천 시 hard DELETE되지만 이 피드백은 분석용으로 계속 남아야 하기 때문")
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "recommendation_id", length = 36, nullable = false, updatable = false)
  private UUID recommendationId;

  @Schema(description = "피드백을 남긴 시점의 추천 모드 스냅샷입니다.")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private RecommendationMode mode;

  @Schema(description = "피드백을 남긴 시점의 추천 순위 스냅샷입니다. (1~3위)", example = "1")
  @Column(name = "recommendation_rank", nullable = false, updatable = false)
  private int recommendationRank;

  @Schema(description = "피드백을 남긴 시점의 추천 시작일 스냅샷입니다.", example = "2026-08-03")
  @Column(name = "start_date", nullable = false, updatable = false)
  private LocalDate startDate;

  @Schema(description = "피드백을 남긴 시점의 추천 종료일 스냅샷입니다.", example = "2026-08-06")
  @Column(name = "end_date", nullable = false, updatable = false)
  private LocalDate endDate;

  @Schema(description = "추천 결과가 도움이 되었는지 여부를 나타냅니다.")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RecommendationFeedbackStatus status;

  @Schema(description = "도움이 되지 않은 이유입니다. 상태가 HELPFUL인 경우 null이 됩니다.", nullable = true)
  @Enumerated(EnumType.STRING)
  @Column
  private RecommendationFeedbackReason reason;

  @Schema(description = "피드백 사유가 기타(OTHER)일 때 직접 입력한 상세 텍스트입니다.", nullable = true)
  @Column(name = "reason_detail", columnDefinition = "TEXT")
  private String reasonDetail;

  public RecommendationFeedback(
      Trip trip,
      UUID recommendationId,
      RecommendationMode mode,
      int recommendationRank,
      LocalDate startDate,
      LocalDate endDate,
      RecommendationFeedbackStatus status,
      RecommendationFeedbackReason reason,
      String reasonDetail) {
    this.trip = trip;
    this.recommendationId = recommendationId;
    this.mode = mode;
    this.recommendationRank = recommendationRank;
    this.startDate = startDate;
    this.endDate = endDate;
    this.status = status;
    this.reason = reason;
    this.reasonDetail = reasonDetail;
  }

  public void applyFeedback(
      RecommendationFeedbackStatus status,
      RecommendationFeedbackReason reason,
      String reasonDetail) {
    this.status = status;
    this.reason = reason;
    this.reasonDetail = reasonDetail;
  }
}
