package com.tripfit.tripfit.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = """
    여행방 메타 정보 수정 요청입니다. (PATCH /trips/{tripId})
    - 방장만 수정할 수 있으며, 여행방 상태가 ONGOING인 경우에만 가능합니다.
    - 희망 여행 기간(startRange, endRange)은 수정할 수 없습니다.
    """)
public record PatchTripRequest(
    @Schema(
        description = "수정할 여행방 이름입니다. (최대 15자)",
        example = "제주 3박4일",
        maxLength = 15) @NotBlank String name,

    @Schema(
        description = """
            수정할 희망 여행 박수(n박)입니다.
            - 미정일 경우 null로 설정합니다.
            - 값을 입력할 경우 `durationDays`와 함께 nights+1 ≤ days ≤ nights+2 규칙을 만족해야 합니다.
            - 0박(당일치기)의 경우에도 동일한 규칙이 적용됩니다.
            """,
        nullable = true,
        example = "3") Integer durationNights,

    @Schema(
        description = """
            수정할 희망 여행 일수(m일)입니다.
            - 미정일 경우 null로 설정합니다.
            - 값을 입력할 경우 `durationNights`에 대해 nights+1 ≤ days ≤ nights+2 범위만 허용됩니다.
            """,
        nullable = true,
        example = "4") Integer durationDays,

    @Schema(
        description = "수정할 모집 정원입니다. (1~10명)",
        example = "6",
        minimum = "1",
        maximum = "10") @NotNull @Min(1) @Max(10) Integer memberCount,

    @Schema(
        description = "수정할 목적지입니다. 미정일 경우 null로 설정합니다.",
        nullable = true,
        example = "제주") String destination
) {
}
