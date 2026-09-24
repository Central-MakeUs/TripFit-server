package com.tripfit.tripfit.trip.recommendation.domain;

import com.tripfit.tripfit.trip.domain.Trip;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "recommendation")
@EntityListeners(AuditingEntityListener.class)
@Schema(description = "여행방의 추천 일정 후보 정보입니다. 순위, 추천 기간, 참여자 통계 등을 포함합니다.")
public class Recommendation {

  @Schema(
      description = "추천 레코드 ID (UUID v4)",
      example = "550e8400-e29b-41d4-a716-446655440000")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  private UUID id;

  @Schema(description = "일정을 추천받는 대상 여행방입니다.")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id", nullable = false)
  private Trip trip;

  @Schema(description = "추천 순위 (1=1순위)", example = "1")
  @Column(name = "recommendation_rank", nullable = false)
  private Integer rank;

  @Schema(description = "추천된 여행 시작일입니다.", example = "2026-08-03")
  @Column(nullable = false)
  private LocalDate startDate;

  @Schema(description = "추천된 여행 종료일입니다.", example = "2026-08-06")
  @Column(nullable = false)
  private LocalDate endDate;

  @Schema(description = "참석률(%)입니다. 전체참석 인원과 부분참석 인원을 합친 수를 일정에 응답한 전체 참여자 수로 나눈 값입니다.",
      example = "80")
  @Column(name = "attend_rate", nullable = false)
  private int attendRate;

  @Schema(description = "부분 참석이 가능한 인원 수입니다.", example = "1")
  @Column(name = "partial_attend_count", nullable = false)
  private int partialAttendCount;

  @Schema(description = "불확실한 일정을 가진 인원 수입니다.", example = "1")
  @Column(name = "uncertain_count", nullable = false)
  private int uncertainCount;

  @Schema(description = "총 연차 일수 (반차=0.5일 환산 합계)", example = "2.0")
  @Column(name = "total_vacation_days", nullable = false)
  private double totalVacationDays;

  @Schema(description = "추천 점수입니다. 점수가 높을수록 추천 순위가 높아집니다.", example = "91.5")
  @Column(nullable = false)
  private double score;

  @Schema(description = "추천 일정이 생성된 시각입니다.", example = "2026-07-07T12:00:00")
  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public Recommendation(
      Trip trip,
      Integer rank,
      LocalDate startDate,
      LocalDate endDate,
      int attendRate,
      int partialAttendCount,
      int uncertainCount,
      double totalVacationDays,
      double score) {
    this.trip = trip;
    this.rank = rank;
    this.startDate = startDate;
    this.endDate = endDate;
    this.attendRate = attendRate;
    this.partialAttendCount = partialAttendCount;
    this.uncertainCount = uncertainCount;
    this.totalVacationDays = totalVacationDays;
    this.score = score;
  }
}
