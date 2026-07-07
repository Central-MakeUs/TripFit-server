package com.tripfit.tripfit.auth.controller;

import com.tripfit.tripfit.auth.dto.LoginRequest;
import com.tripfit.tripfit.auth.dto.LoginResponse;
import com.tripfit.tripfit.auth.dto.LogoutRequest;
import com.tripfit.tripfit.auth.dto.RefreshRequest;
import com.tripfit.tripfit.auth.dto.RefreshResponse;
import com.tripfit.tripfit.auth.service.AuthService;
import com.tripfit.tripfit.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	// 소셜 로그인 요청을 받아 액세스 토큰과 리프레시 토큰을 발급함
	@Operation(summary = "소셜 로그인")
	@PostMapping("/login")
	ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		LoginResponse response = authService.login(request.provider(), request.token());
		return ResponseEntity.ok(ApiResponse.of(response));
	}

	// 리프레시 토큰을 검증해 새로운 액세스 토큰을 재발급함
	@Operation(summary = "액세스 토큰 재발급")
	@PostMapping("/refresh")
	ResponseEntity<ApiResponse<RefreshResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
		RefreshResponse response = authService.refresh(request.refreshToken());
		return ResponseEntity.ok(ApiResponse.of(response));
	}

	// 전달받은 리프레시 토큰을 삭제해 현재 세션을 로그아웃 처리함
	@Operation(summary = "로그아웃")
	@PostMapping("/logout")
	ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request.refreshToken());
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
