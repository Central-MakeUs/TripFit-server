package com.tripfit.tripfit.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = "마이페이지 프로필(성·이름·알림 설정) 부분 수정 요청. PATCH /users/profile. 생략(null) 필드는 미변경, 최소 1개 필드 필요")

public record UpdateProfileRequest(
    @Schema(
        description = "이름. 생략 시 미변경, 필드로 포함됐지만 공백이면 400",
        example = "길동",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED) String firstName,

    @Schema(
        description = "성. 생략 시 미변경, 필드로 포함됐지만 공백이면 400",
        example = "홍",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED) String lastName,

    @Schema(
        description = "알림 수신 여부(BR-USER-005). 생략 시 미변경",
        example = "false",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean notificationEnabled
) {
}
