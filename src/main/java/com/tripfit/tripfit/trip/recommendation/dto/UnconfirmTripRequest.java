package com.tripfit.tripfit.trip.recommendation.dto;

import com.tripfit.tripfit.trip.recommendation.domain.UnconfirmReason;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    여행 일정 확정 취소 요청입니다. (POST /trips/{tripId}/unconfirm)
    - 방장만 호출 가능하며, 여행방이 CONFIRMED 상태일 때만 유효합니다.
    - reason 값이 누락되거나, OTHER이면서 reasonDetail 값이 없으면 에러(400 INVALID_UNCONFIRM_REASON)가 발생합니다.
    """)
public record UnconfirmTripRequest(
    @Schema(
        description = "일정 확정 취소 사유입니다.",
        requiredMode = Schema.RequiredMode.REQUIRED) UnconfirmReason reason,

    @Schema(
        description = "직접 입력한 상세 사유입니다. (reason=OTHER 일 때만 유효)",
        nullable = true) String reasonDetail
) {
}
