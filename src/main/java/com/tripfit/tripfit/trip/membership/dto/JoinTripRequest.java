package com.tripfit.tripfit.trip.membership.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "초대 코드를 이용한 여행방 참여 요청 정보입니다.")
public record JoinTripRequest(
    @Schema(
        description = "6자리의 영문 및 숫자 혼합 초대 코드입니다. (Crockford Base32 형식)",
        example = "A2B3C4") @NotBlank String inviteCode
) {
}
