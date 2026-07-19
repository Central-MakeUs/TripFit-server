package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.RecommendationMode;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "여행방 상세·join·patch·pin 응답")
// @formatter:off
public record TripDetailResponse(
    @Schema(description = "여행방 ID") UUID tripId,

    @Schema(description = "여행방 이름", maxLength = 15) String name,

    @Schema(description = "여행지", nullable = true) String destination,

    @Schema(description = "희망 여행 기간 시작일") LocalDate startRange,

    @Schema(description = "희망 여행 기간 종료일") LocalDate endRange,

    @Schema(description = "희망 여행 일수") Integer durationDays,

    @Schema(description = "참여 인원 (1~10)", example = "6", minimum = "1", maximum = "10")
    Integer memberCount,

    @Schema(description = "여행방 상태 (effectiveStatus)") TripStatus status,

    @Schema(description = "초대 코드") String inviteCode,

    @Schema(description = "확정 시작일", nullable = true) LocalDate confirmedStartDate,

    @Schema(description = "확정 종료일", nullable = true) LocalDate confirmedEndDate,

    @Schema(description = "마지막 추천 모드", nullable = true) RecommendationMode lastRecommendationMode,

    @Schema(description = "최근 활동 시각") LocalDateTime lastActivityAt,

    @Schema(description = "본인 Pin 여부") boolean pinned,

    @Schema(description = "본인 역할") TripMemberRole myRole,

    @Schema(description = "본인 멤버 상태") TripMemberStatus myMemberStatus,

    @Schema(description = "일정 확인 완료(RESPONDED) 멤버 수") int respondedCount,

    @Schema(description = "현재 참여 멤버 수") int joinedMemberCount,

    @Schema(
        description = "모집 현황 joinedMemberCount/memberCount (0.0~1.0). 구 responseRate 대체",
        example = "0.67")
        double memberFillRate
) {}
// @formatter:on
