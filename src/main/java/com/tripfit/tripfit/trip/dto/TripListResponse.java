package com.tripfit.tripfit.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "홈 화면의 여행방 목록 응답입니다. (GET /trips)")
public record TripListResponse(
    @Schema(
        description = """
            여행방 카드 목록입니다.
            - 쿼리의 `scope` 및 `statusFilter`에 맞게 정렬 및 필터링된 결과가 반환됩니다.
            """) List<TripHomeCardResponse> trips
) {
}
