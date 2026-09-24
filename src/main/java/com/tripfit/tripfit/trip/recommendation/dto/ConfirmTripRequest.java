package com.tripfit.tripfit.trip.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = """
    여행 일정 확정 요청입니다. (POST /trips/{tripId}/confirm)
    - 방장만 호출 가능하며, 여행방이 ONGOING 상태일 때만 유효합니다.
    - recommendationRank 또는 (startDate+endDate) 중 한 가지 방식만 입력해야 합니다.
    """)
public record ConfirmTripRequest(
    @Schema(
        description = "확정할 추천 후보의 순위(1~3)입니다. 직접 날짜를 입력하는 경우에는 null입니다.",
        nullable = true,
        example = "1") Integer recommendationRank,

    @Schema(
        description = "직접 날짜를 입력하여 확정할 경우의 여행 시작일입니다. 추천 후보를 선택한 경우에는 null입니다.",
        nullable = true,
        example = "2026-08-04") LocalDate startDate,

    @Schema(
        description = "직접 날짜를 입력하여 확정할 경우의 여행 종료일입니다. 추천 후보를 선택한 경우에는 null입니다.",
        nullable = true,
        example = "2026-08-07") LocalDate endDate
) {
}
