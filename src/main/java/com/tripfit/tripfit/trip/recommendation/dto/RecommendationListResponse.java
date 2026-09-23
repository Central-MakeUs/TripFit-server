package com.tripfit.tripfit.trip.recommendation.dto;

import com.tripfit.tripfit.trip.recommendation.domain.RecommendationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = """
    저장된 상위 3개의 추천 목록 정보입니다. (GET/POST /trips/{tripId}/recommendations 공통 반환)
    - 방장 전용 기능입니다.
    """)
public record RecommendationListResponse(
    @Schema(
        description = "현재 적용된 추천 모드입니다. 추천을 생성한 적이 없으면 null을 반환합니다.",
        nullable = true) RecommendationMode mode,

    @Schema(description = "추천 후보 목록입니다. (순위 오름차순, 최대 3건)") List<RecommendationItemResponse> items
) {
}
