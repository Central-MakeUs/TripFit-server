package com.tripfit.tripfit.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "여행방 Pin(상단 고정) 상태 변경 요청입니다. (PATCH /trips/{tripId}/pin)")
public record UpdateTripPinRequest(
    @Schema(
        description = "홈 상단 고정 여부입니다. (true: 고정, false: 해제)",
        example = "true") @NotNull Boolean pinned
) {
}
