package com.tripfit.tripfit.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "홈 여행방 목록 응답 (D5)")
public record TripListResponse(
    @Schema(description = "여행방 카드 목록 (scope별 정렬)") List<TripHomeCardResponse> trips
) {
}
