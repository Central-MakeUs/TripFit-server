package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "여행방 생성 응답. POST /trips")
public record CreateTripResponse(
// @formatter:off
    @Schema(description = "생성된 여행방 ID") UUID tripId,

    @Schema(description = "초대 코드 (6자)") String inviteCode,

    @Schema(description = "여행방 진행 상태. create 직후 ONGOING") TripStatus status,

    @Schema(
        description =
            "방장 멤버십 상태. create 직후 JOINED(일정 확인 전). POST /trips/{tripId}/schedule/confirm 필요")
    TripMemberStatus myMemberStatus,

    @Schema(
        description =
            "일정 확인(confirm) 호출 필요 여부. myMemberStatus=JOINED이면 true. confirm 후 false")
    boolean needsScheduleConfirm
    // @formatter:on
) {
}
