package com.tripfit.tripfit.trip.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "추천 후보 카드 한 건 (목록용 요약 통계)")
// @formatter:off
public record RecommendationItemResponse(
    @Schema(description = "추천 순위 (1=1순위)", example = "1") int rank,

    @Schema(description = "추천 여행 시작일", example = "2026-08-03") LocalDate startDate,

    @Schema(description = "추천 여행 종료일", example = "2026-08-06") LocalDate endDate,

    @Schema(description = "참석률(%) — (전체참석+부분참석 인원)/응답 참여자 수", example = "80") int attendRate,

    @Schema(description = "부분 참석 인원 수", example = "1") int partialAttendCount,

    @Schema(description = "불확실 일정이 있는 인원 수", example = "1") int uncertainCount,

    @Schema(description = "총 연차 일수 (반차=0.5일 환산 합계)", example = "2.0") double totalVacationDays
) {
}
// @formatter:on
