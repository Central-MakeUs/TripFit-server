package com.tripfit.tripfit.trip.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "추천 후보 목록용 요약 카드(1건)입니다.")
public record RecommendationItemResponse(
    @Schema(description = "추천 순위입니다. (1이 1순위)", example = "1") int rank,

    @Schema(description = "추천된 여행 시작일입니다.", example = "2026-08-03") LocalDate startDate,

    @Schema(description = "추천된 여행 종료일입니다.", example = "2026-08-06") LocalDate endDate,

    @Schema(description = "참석률(%)입니다. ((전체참석 + 부분참석 인원) / 응답 참여자 수)",
        example = "80") int attendRate,

    @Schema(description = "부분 참석 인원 수입니다.", example = "1") int partialAttendCount,

    @Schema(description = "불확실한 일정이 있는 인원 수입니다.", example = "1") int uncertainCount,

    @Schema(description = "필요한 총 연차 일수 합계입니다. (반차는 0.5일 환산)",
        example = "2.0") double totalVacationDays
) {
}
