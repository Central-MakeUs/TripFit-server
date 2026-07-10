package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(
    description = """
        여행방 생성 응답. POST /trips.
        방장은 JOINED(입장 전). inviteCode 필드 없음 — confirm→RESPONDED 후 상세에서만 공유용 코드 제공.
        needsScheduleConfirm=true면 클라가 일정 confirm 플로우로 라우팅.
        """)
public record CreateTripResponse(
// @formatter:off
    @Schema(description = "생성된 여행방 ID") UUID tripId,

    @Schema(description = "여행방 진행 상태. create 직후 ONGOING") TripStatus status,

    @Schema(
        description =
            """
            방장 멤버십. create 직후 항상 JOINED(방장 전용).
            멤버는 이 응답을 받지 않음. confirm 후 myMemberStatus는 상세에서 RESPONDED.
            """)
    TripMemberStatus myMemberStatus,

    @Schema(
        description =
            """
            true=아직 schedule/confirm 필요(JOINED). 홈/딥링크 진입 시 상세 대신 일정 플로우로 보낼 것.
            confirm 성공 후 false(상세의 myMemberStatus=RESPONDED).
            """)
    boolean needsScheduleConfirm
    // @formatter:on
) {
}
