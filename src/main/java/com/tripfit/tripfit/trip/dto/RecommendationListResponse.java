package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.RecommendationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "저장된 추천 TOP 3 목록. GET/POST /trips/{tripId}/recommendations 공용(방장 전용)")
public record RecommendationListResponse(
    @Schema(description = "현재 저장된 추천의 모드. 아직 추천 전이면 null", nullable = true) RecommendationMode mode,

    @Schema(description = "추천 후보 목록 (rank 오름차순, 최대 3건)") List<RecommendationItemResponse> items
) {
}
