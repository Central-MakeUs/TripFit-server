package com.tripfit.tripfit.trip.domain;

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
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "recommendation")
@EntityListeners(AuditingEntityListener.class)
@Schema(description = "여행방 추천 일정 후보 (순위·기간·사유)")
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

  @Schema(description = "대상 여행방")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id", nullable = false)
  private Trip trip;

  @Schema(description = "추천 순위 (1=1순위)", example = "1")
  @Column(name = "recommendation_rank", nullable = false)
  private Integer rank;

  @Schema(description = "추천 여행 시작일", example = "2026-08-03")
  @Column(nullable = false)
  private LocalDate startDate;

  @Schema(description = "추천 여행 종료일", example = "2026-08-06")
  @Column(nullable = false)
  private LocalDate endDate;

  @Schema(description = "추천 사유 설명", nullable = true)
  @Column(columnDefinition = "TEXT")
  private String reason;

  @Schema(description = "리스크·주의 사항", nullable = true)
  @Column(columnDefinition = "TEXT")
  private String riskNote;

  // #13 추천 순위·동점 비교용 (trip-recommendation.md)
  @Schema(description = "추천 점수 (#13 순위·동점 비교)", nullable = true, example = "0.92")
  @Column
  private Double score;

  @Schema(description = "추천 생성 시각", example = "2026-07-07T12:00:00")
  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public Recommendation(
      Trip trip,
      Integer rank,
      LocalDate startDate,
      LocalDate endDate,
      String reason,
      String riskNote,
      Double score) {
    this.trip = trip;
    this.rank = rank;
    this.startDate = startDate;
    this.endDate = endDate;
    this.reason = reason;
    this.riskNote = riskNote;
    this.score = score;
  }
}
