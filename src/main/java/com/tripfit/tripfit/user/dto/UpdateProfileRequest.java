package com.tripfit.tripfit.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    마이페이지 프로필 부분 수정 요청입니다. (PATCH /users/profile)
    - 수정할 필드만 보내며, 생략(null)된 필드는 기존 값을 유지합니다.
    - 최소 1개 이상의 필드가 포함되어야 합니다.
    """)
public record UpdateProfileRequest(
    @Schema(
        description = """
            수정할 이름입니다.
            - 값을 보내지 않으면 변경되지 않습니다.
            - 키를 포함했지만 공백 문자열인 경우 에러(400)가 발생합니다.
            """,
        example = "길동",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED) String firstName,

    @Schema(
        description = """
            수정할 성입니다.
            - 값을 보내지 않으면 변경되지 않습니다.
            - 키를 포함했지만 공백 문자열인 경우 에러(400)가 발생합니다.
            """,
        example = "홍",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED) String lastName,

    @Schema(
        description = "앱 알림 수신 여부입니다. 생략 시 기존 값을 유지합니다.",
        example = "false",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean notificationEnabled
) {
}
