package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.TripMemberRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "홈 카드 참여자 미리보기 1명. GET /trips 카드 membersPreview")
public record MemberPreviewResponse(
    @Schema(description = "사용자 ID") UUID userId,

    @Schema(description = "프로필 이미지 URL. 미제공 시 null", nullable = true) String profileImageUrl,

    @Schema(description = "방 내 역할 (방장 OWNER / 일반 MEMBER)") TripMemberRole role
) {
}
