package com.tripfit.tripfit.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "여행방 생성 요청")
// @formatter:off
public record CreateTripRequest(
    @Schema(description = "여행방 이름 (최대 15자)", example = "제주 3박4일", maxLength = 15)
    @NotBlank
    String name,

    @Schema(description = "희망 여행 기간 시작일", example = "2026-08-01")
    @NotNull
    LocalDate startRange,

    @Schema(description = "희망 여행 기간 종료일", example = "2026-08-10")
    @NotNull
    LocalDate endRange,

    @Schema(description = "희망 여행 일수 (m일)", example = "4")
    @NotNull
    Integer durationDays,

    @Schema(description = "참여 인원 (1~10)", example = "6", minimum = "1", maximum = "10")
    @NotNull
    @Min(1)
    @Max(10)
    Integer memberCount,

    @Schema(description = "여행지 (선택)", nullable = true, example = "제주")
    String destination
) {
}
// @formatter:on
