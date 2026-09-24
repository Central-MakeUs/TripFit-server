package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = """
    여행방 진입 상태 응답입니다. (생성 및 참여 시 공통 반환)
    - 방장 및 참여자 모두 초기 상태는 SCHEDULE_PENDING입니다. 일정 확인 플로우를 완료(activate)하면 ACTIVE가 됩니다.
    - 클라이언트는 myMemberStatus를 기준으로 화면을 라우팅합니다. (SCHEDULE_PENDING: 일정 플로우, ACTIVE: 여행방 내부)
    - 이 응답에는 초대 코드(inviteCode)가 포함되지 않습니다. (입장 후 상세 API에서만 제공)
    """)
public record TripEntryResponse(

    @Schema(description = "여행방의 고유 식별자(ID)입니다.") UUID tripId,

    @Schema(description = "여행방의 진행 상태입니다. 방 생성 또는 참여 직후에는 ONGOING 상태가 됩니다.") TripStatus status,

    @Schema(
        description = """
            호출자의 현재 방 멤버십 상태입니다.
            - 생성 및 신규 참여 직후에는 항상 SCHEDULE_PENDING입니다.
            - 이미 멤버인 사용자가 다시 참여(join)를 호출하면, 현재의 실제 상태(SCHEDULE_PENDING 또는 ACTIVE)를 반환합니다.
            """) TripMemberStatus myMemberStatus

) {
}
