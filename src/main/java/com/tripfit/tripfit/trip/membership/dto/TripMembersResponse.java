package com.tripfit.tripfit.trip.membership.dto;

import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "여행방 참여자 목록 응답 정보입니다.")
public record TripMembersResponse(
    @Schema(description = "방장이 설정한 여행방의 전체 모집 정원입니다. (1~10명)") int memberCount,

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

      @Schema(description = "사용자의 표시 이름입니다. 동명이인이 있을 경우 접미사가 추가될 수 있습니다.",
          example = "홍길동(2)") String displayName,

      @Schema(description = "여행방 내에서의 역할입니다. (OWNER: 방장, MEMBER: 일반 멤버)") TripMemberRole role,

      @Schema(
          description = """
              멤버십 상태를 나타냅니다.
              - SCHEDULE_PENDING: 방장이 방을 생성한 직후 상태입니다. (입장 불가)
              - ACTIVE: 방장이 일정을 활성화(activate)했거나, 일반 멤버가 참여(join)를 완료한 상태입니다. (입장 가능)
              """) TripMemberStatus memberStatus,

      @Schema(
          description = "사용자 본인이 이 여행방을 홈 화면 상단에 고정(Pin)했는지 여부를 나타냅니다. 본인의 데이터에 대해서만 유효한 값을 가집니다.") boolean pinned
  ) {
  }
}
