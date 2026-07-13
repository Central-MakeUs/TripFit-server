package com.tripfit.tripfit.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "초대 코드로 여행방 참여 요청. POST /trips/join")
public record JoinTripRequest(
    @Schema(
        description = "6자 Crockford Base32 초대 코드",
        example = "A2B3C4") @NotBlank String inviteCode
) {
}
