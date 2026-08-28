package com.tripfit.tripfit.trip.recommendation.dto;

import com.tripfit.tripfit.trip.recommendation.domain.UnconfirmReason;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = "확정 취소 요청. POST /trips/{tripId}/unconfirm (방장·CONFIRMED만)."
        + " reason 누락 또는 OTHER인데 reasonDetail 없으면 400 INVALID_UNCONFIRM_REASON"
        + ". 도메인 에러 코드를 정확히 내려주기 위해 Bean Validation(@NotNull) 대신 서비스에서 직접 검증한다")

public record UnconfirmTripRequest(
    @Schema(description = "확정 취소 사유",
        requiredMode = Schema.RequiredMode.REQUIRED) UnconfirmReason reason,

    @Schema(description = "reason=OTHER일 때 직접 입력 텍스트", nullable = true) String reasonDetail
) {
}
