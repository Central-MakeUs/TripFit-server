package com.tripfit.tripfit.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "내 여행방 목록 응답")
public record TripListResponse(
    @Schema(description = "여행방 목록 (pin → updatedAt 정렬)") List<TripSummaryResponse> trips
) {
}
