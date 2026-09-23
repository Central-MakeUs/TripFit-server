package com.tripfit.tripfit.trip.membership.dto;

import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "여행방 참여자 목록 응답입니다. (GET /trips/{tripId}/members)")
public record TripMembersResponse(
    @Schema(description = "방장이 설정한 전체 모집 정원입니다. (1~10)") int memberCount,

    @Schema(description = "일정 확인을 완료한(ACTIVE 상태) 멤버 수입니다.") int activeMemberCount,

    @Schema(
        description = """
            모집 충원율(응답률)입니다. (activeMemberCount ÷ memberCount)
            - 0.0 ~ 1.0 사이의 값이며, DB에 저장되지 않고 계산됩니다.
            """,
        example = "0.5") double memberFillRate,

    @Schema(description = "참여자 목록입니다.") List<TripMemberItemResponse> members
) {

  @Schema(description = "개별 참여자 정보입니다.")
  public record TripMemberItemResponse(
      @Schema(description = "사용자 ID입니다.") UUID userId,

      @Schema(description = "표시 이름입니다. (동명이인 시 접미사 추가)", example = "홍길동(2)") String displayName,

      @Schema(description = "여행방 내의 역할입니다. (OWNER: 방장, MEMBER: 일반 멤버)") TripMemberRole role,

      @Schema(
          description = """
              멤버십 상태입니다.
              - SCHEDULE_PENDING: 방장 생성 직후 (입장 불가)
              - ACTIVE: 방장 activate 완료 후 또는 일반 멤버 join 완료 후 (입장 가능)
              """) TripMemberStatus memberStatus,

      @Schema(description = "본인이 이 방을 홈 상단에 고정(Pin)했는지 여부입니다. (본인 데이터에만 유효)") boolean pinned
  ) {
  }
}
