package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "여행방 참여자 목록")
public record TripMembersResponse(
    @Schema(description = "현재 참여 멤버 수") int memberCount,
    @Schema(description = "일정 응답 완료 멤버 수") int respondedCount,
    @Schema(description = "응답률 (0.0~1.0)") double responseRate,
    @Schema(description = "참여자 목록") List<TripMemberItemResponse> members
) {

  @Schema(description = "참여자 1명")
  public record TripMemberItemResponse(
      @Schema(description = "사용자 ID") UUID userId,
      @Schema(description = "표시 이름 (동명이인 시 접미사)", example = "홍길동(2)") String displayName,
      @Schema(description = "방 내 역할") TripMemberRole role,
      @Schema(description = "일정 응답 상태") TripMemberStatus status,
      @Schema(description = "홈 Pin 여부") boolean pinned
  ) {
  }
}
