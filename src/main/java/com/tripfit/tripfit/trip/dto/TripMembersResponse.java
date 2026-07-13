package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "여행방 참여자 목록. GET /trips/{tripId}/members")
public record TripMembersResponse(
    @Schema(description = "방장이 설정한 모집 정원 (1~10)") int memberCount,
    @Schema(description = "현재 참여 멤버 수 (trip_member row 수)") int joinedMemberCount,
    @Schema(description = "일정 확인 완료(RESPONDED) 멤버 수") int respondedCount,
    @Schema(
        description = """
            모집 충원율 joinedMemberCount ÷ memberCount (0.0~1.0, DB 저장 없음).
            respondedCount와 무관 — 참여 인원 기준.
            join·remove·정원 변경 시 갱신 — GET /trips/{tripId}/members 재호출.
            """,
        example = "0.67") double memberFillRate,
    @Schema(description = "참여자 목록") List<TripMemberItemResponse> members
) {

  @Schema(description = "참여자 1명")
  public record TripMemberItemResponse(
      @Schema(description = "사용자 ID") UUID userId,
      @Schema(description = "표시 이름 (동명이인 시 접미사)", example = "홍길동(2)") String displayName,
      @Schema(description = "방 내 역할 (방장 OWNER / 일반 MEMBER)") TripMemberRole role,
      @Schema(
          description = "멤버십 상태. JOINED=일정 확인 전, RESPONDED=일정 확인 완료") TripMemberStatus status,
      @Schema(description = "본인이 이 방을 홈 상단에 Pin했는지 (본인 row만 의미)") boolean pinned
  ) {
  }
}
