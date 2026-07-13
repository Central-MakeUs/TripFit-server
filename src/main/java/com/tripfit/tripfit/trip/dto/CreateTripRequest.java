package com.tripfit.tripfit.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "여행방 생성 요청. POST /trips")
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

    @Schema(
        description =
            "희망 여행 박수 (n박). durationDays와 둘 다 null=미정, 둘 다 값이면 nights==days-1."
                + " 0박 1일(당일치기) 허용",
        nullable = true,
        example = "3")
    Integer durationNights,

    @Schema(
        description = "희망 여행 일수 (m일). null=미정. 최소 1일(당일치기=1)",
        nullable = true,
        example = "4")
    Integer durationDays,

    @Schema(description = "모집 정원 (1~10)", example = "6", minimum = "1", maximum = "10")
    @NotNull
    @Min(1)
    @Max(10)
    Integer memberCount,

    @Schema(description = "여행지. null=미정", nullable = true, example = "제주")
    String destination
) {
}
// @formatter:on
