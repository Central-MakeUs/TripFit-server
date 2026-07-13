package com.tripfit.tripfit.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "홈 여행방 목록 응답. GET /trips")
public record TripListResponse(
    @Schema(
        description = "여행방 카드 목록 (scope·statusFilter에 따라 정렬·필터됨)") List<TripHomeCardResponse> trips
) {
}
