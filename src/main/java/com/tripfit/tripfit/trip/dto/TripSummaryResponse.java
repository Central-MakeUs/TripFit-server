package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.RecommendationMode;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "여행방 요약 (목록·상세·참여 공통)")
// @formatter:off
public record TripSummaryResponse(
    @Schema(description = "여행방 ID") UUID tripId,
    @Schema(description = "여행방 이름", maxLength = 15) String name,
    @Schema(description = "여행지", nullable = true) String destination,
    @Schema(description = "희망 여행 기간 시작일") LocalDate startRange,
    @Schema(description = "희망 여행 기간 종료일") LocalDate endRange,
    @Schema(description = "희망 여행 일수") Integer durationDays,
    @Schema(description = "예상 참여 인원") Integer targetMemberCount,
    @Schema(description = "여행방 상태") TripStatus status,
    @Schema(description = "초대 코드") String inviteCode,
    @Schema(description = "확정 시작일", nullable = true) LocalDate confirmedStartDate,
    @Schema(description = "확정 종료일", nullable = true) LocalDate confirmedEndDate,
    @Schema(description = "마지막 추천 모드", nullable = true) RecommendationMode lastRecommendationMode,
    @Schema(description = "본인 Pin 여부") boolean pinned,
    @Schema(description = "본인 멤버 상태") TripMemberStatus myMemberStatus,
    @Schema(description = "일정 응답 완료 멤버 수") int respondedCount,
    @Schema(description = "현재 참여 멤버 수") int memberCount
) {
}
// @formatter:on
