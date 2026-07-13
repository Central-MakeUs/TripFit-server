package com.tripfit.tripfit.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "여행방 Pin 토글 요청. PATCH /trips/{tripId}/pin")
public record UpdateTripPinRequest(
    @Schema(description = "Pin 여부. true=홈 상단 고정, false=해제",
        example = "true") @NotNull Boolean pinned
) {
}
