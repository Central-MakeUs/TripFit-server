package com.tripfit.tripfit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

@Schema(description = "dev 전용 테스트 로그인 요청. POST /auth/dev-login")
public record DevLoginRequest(
    @Schema(
        description = "테스트 계정 식별자. 생략하면 chaeyeon 계정 사용. chaeyeon·soeun·giyeon은 각각 채연·소은·기연 팀원 전용 고정 계정이고, 그 외 값을 주면 해당 값 전용 계정이 새로 생성·재사용됨",
        example = "soeun",
        nullable = true) @Pattern(
            regexp = "^[a-zA-Z0-9_-]{0,20}$",
            message = "영문·숫자·-·_ 20자 이내로 입력하세요.") String testUserId
) {
}
