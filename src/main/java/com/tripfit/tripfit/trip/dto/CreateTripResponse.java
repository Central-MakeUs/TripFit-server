package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "여행방 생성 응답")
public record CreateTripResponse(
    @Schema(description = "여행방 ID") UUID tripId,
    @Schema(description = "초대 코드 (6자)") String inviteCode,
    @Schema(description = "여행방 상태") TripStatus status
) {
}
