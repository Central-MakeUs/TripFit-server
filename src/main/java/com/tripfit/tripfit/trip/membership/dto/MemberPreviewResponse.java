package com.tripfit.tripfit.trip.membership.dto;

import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "홈 화면 카드의 참여자 미리보기(1명) 정보입니다.")
public record MemberPreviewResponse(
    @Schema(description = "사용자 ID입니다.") UUID userId,

    @Schema(
        description = "미리보기용 표시 이름입니다. (성 없이 이름만 표시, 닉네임 사용 시 그대로, 동명이인 시 접미사 추가)",
        example = "길동(2)") String displayName,

    @Schema(description = "프로필 이미지 URL입니다. 없을 경우 null을 반환합니다.",
        nullable = true) String profileImageUrl,

    @Schema(description = "여행방 내의 역할입니다. (OWNER: 방장, MEMBER: 일반 멤버)") TripMemberRole role
) {
}
