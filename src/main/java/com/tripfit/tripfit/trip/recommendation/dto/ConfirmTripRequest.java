package com.tripfit.tripfit.trip.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(
    description = "일정 확정 요청. POST /trips/{tripId}/confirm (방장·ONGOING만)."
        + " recommendationRank 또는 (startDate+endDate) 중 정확히 하나만 입력")

public record ConfirmTripRequest(
    @Schema(description = "확정할 추천 후보의 순위 (1~3). 직접 입력 시 null", nullable = true,
        example = "1") Integer recommendationRank,

    @Schema(description = "직접 입력 시작일. 후보 선택 시 null", nullable = true,
        example = "2026-08-04") LocalDate startDate,

    @Schema(description = "직접 입력 종료일. 후보 선택 시 null", nullable = true,
        example = "2026-08-07") LocalDate endDate
) {
}
