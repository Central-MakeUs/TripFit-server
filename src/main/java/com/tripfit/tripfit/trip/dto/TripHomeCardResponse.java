package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.membership.dto.MemberPreviewResponse;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = """
    홈 화면의 여행방 카드 정보입니다. (GET /trips)
    - 초대 코드(inviteCode)는 포함되지 않습니다.
    - myMemberStatus가 SCHEDULE_PENDING인 경우, 카드 선택 시 상세 화면이 아닌 일정 확인(activate) 플로우로 이동해야 합니다.
    """)

public record TripHomeCardResponse(
    @Schema(description = "여행방 ID") UUID tripId,

    @Schema(description = "여행방 이름", maxLength = 15) String name,

    @Schema(description = "여행지. null=미정", nullable = true) String destination,

    @Schema(description = "희망 여행 기간 시작일") LocalDate startRange,

    @Schema(description = "희망 여행 기간 종료일") LocalDate endRange,

    @Schema(description = "희망 여행 일수 (m일). null=미정", nullable = true) Integer durationDays,

    @Schema(
        description = """
            희망 여행 박수(n박)입니다.
            - 미정일 경우 null을 반환합니다.
            - 값이 있을 경우 durationDays와 함께 nights+1 ≤ days ≤ nights+2 범위를 가집니다.
            """,
        nullable = true,
        example = "3") Integer durationNights,

    @Schema(description = "모집 정원 (1~10)", example = "6", minimum = "1",
        maximum = "10") Integer memberCount,

    @Schema(
        description = "여행방 진행 상태(effectiveStatus). end_range 경과·방장 취소 등 반영된 화면 표시용") TripStatus status,

    @Schema(description = "여행방 최근 활동 시각") LocalDateTime lastActivityAt,

    @Schema(description = "본인이 이 방을 홈 상단에 Pin했는지") boolean pinned,

    @Schema(description = "본인 역할 (방장 OWNER / 일반 MEMBER)") TripMemberRole myRole,

    @Schema(
        description = """
            호출자 본인의 멤버십 상태입니다.
            - SCHEDULE_PENDING: 방장 생성 직후 (입장/공유 불가)
            - ACTIVE: 방장 activate 완료 후 또는 일반 멤버 join 완료 후 (입장 가능)
            """) TripMemberStatus myMemberStatus,

    @Schema(description = "일정 확인 완료(ACTIVE) 멤버 수") int activeMemberCount,

    @Schema(
        description = """
            모집 충원율(응답률)입니다. (activeMemberCount ÷ memberCount)
            - 0.0 ~ 1.0 사이의 값이며, DB에 저장되지 않고 계산됩니다.
            """,
        example = "0.5") double memberFillRate,

    @Schema(
        description = "참여자 미리보기 (방장 우선 · joinedAt DESC · 최대 4명)") List<MemberPreviewResponse> membersPreview,

    @Schema(
        description = "미리보기 초과 인원 (참여 인원 - 4, 최소 0). +N 배지 표시용") int membersPreviewOverflow
) {
}
