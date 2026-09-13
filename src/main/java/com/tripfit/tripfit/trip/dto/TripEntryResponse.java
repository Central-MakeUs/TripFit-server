package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(
    description = """
        방 진입 상태. POST /trips(생성) · POST /trips/join(참여) 공통 응답.
        방장·참여자 모두 SCHEDULE_PENDING으로 시작 — 일정 확인 플로우를 마치고 activate하면 ACTIVE가 된다.
        클라는 myMemberStatus 하나로 라우팅한다(SCHEDULE_PENDING=일정 플로우, ACTIVE=방 안).
        inviteCode 필드 없음 — 입장(ACTIVE) 후 상세에서만 공유용 코드를 제공한다.
        """)
public record TripEntryResponse(
// @formatter:off
    @Schema(description = "여행방 ID") UUID tripId,

    @Schema(description = "여행방 진행 상태. create·join 직후 ONGOING") TripStatus status,

    @Schema(
        description =
            """
            호출자의 이 방 멤버십 상태. create·신규 join 직후 항상 SCHEDULE_PENDING.
            이미 멤버인 사용자가 join을 다시 호출하면 그 시점의 실제 상태(SCHEDULE_PENDING 또는 ACTIVE)를 그대로 반환한다.
            """)
    TripMemberStatus myMemberStatus
    // @formatter:on
) {
}
