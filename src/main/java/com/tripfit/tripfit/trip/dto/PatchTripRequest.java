package com.tripfit.tripfit.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "여행방 메타 수정 요청 (방장·ONGOING만)")
// @formatter:off
public record PatchTripRequest(
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

    @Schema(description = "예상 참여 인원", example = "6")
    @NotNull
    Integer targetMemberCount,

    @Schema(description = "여행지 (선택)", nullable = true, example = "제주")
    String destination
) {
}
// @formatter:on
