package com.tripfit.tripfit.trip.membership.dto;

import com.tripfit.tripfit.trip.domain.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "초대코드로 여행방을 미리보기하며 10분 hold를 생성한 결과. POST /trips/join/hold")
// @formatter:off
public record TripJoinPreviewResponse(
    @Schema(description = "여행방 ID") UUID tripId,

    @Schema(description = "여행방 이름", maxLength = 15) String name,

    @Schema(description = "여행지. null=미정", nullable = true) String destination,

    @Schema(description = "희망 여행 기간 시작일") LocalDate startRange,

    @Schema(description = "희망 여행 기간 종료일") LocalDate endRange,

    @Schema(description = "희망 여행 일수 (m일). null=미정", nullable = true) Integer durationDays,

    @Schema(
        description = "희망 여행 박수 (n박). durationDays와 쌍으로 저장(nights+1 ≤ days ≤ nights+2). null=미정",
        nullable = true,
        example = "3")
    Integer durationNights,

    @Schema(description = "모집 정원 (1~10)", example = "6", minimum = "1", maximum = "10")
    Integer memberCount,

    @Schema(description = "일정 확인 완료(ACTIVE) 멤버 수") int activeMemberCount,

    @Schema(description = "여행방 진행 상태(effectiveStatus)") TripStatus status
) {}
// @formatter:on
