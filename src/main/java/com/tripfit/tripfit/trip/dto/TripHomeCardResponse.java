package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "홈 여행방 카드 (GET /trips 목록)")
// @formatter:off
public record TripHomeCardResponse(
    @Schema(description = "여행방 ID") UUID tripId,

    @Schema(description = "여행방 이름", maxLength = 15) String name,

    @Schema(description = "여행지", nullable = true) String destination,

    @Schema(description = "희망 여행 기간 시작일") LocalDate startRange,

    @Schema(description = "희망 여행 기간 종료일") LocalDate endRange,

    @Schema(description = "희망 여행 일수") Integer durationDays,

    @Schema(description = "여행방 상태 (effectiveStatus)") TripStatus status,

    @Schema(description = "최근 활동 시각") LocalDateTime lastActivityAt,

    @Schema(description = "본인 Pin 여부") boolean pinned,

    @Schema(description = "본인 역할") TripMemberRole myRole,

    @Schema(description = "본인 멤버 상태") TripMemberStatus myMemberStatus,

    @Schema(description = "일정 응답 완료 멤버 수") int respondedCount,

    @Schema(description = "현재 참여 멤버 수") int memberCount,

    @Schema(description = "참여자 미리보기 (최대 4)") List<MemberPreviewResponse> membersPreview,

    @Schema(description = "미리보기 초과 인원 (memberCount - 4, 최소 0)") int membersPreviewOverflow
) {}
// @formatter:on
