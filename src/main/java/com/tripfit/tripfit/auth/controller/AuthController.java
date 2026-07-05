package com.tripfit.tripfit.auth.controller;

import com.tripfit.tripfit.auth.controller.dto.LoginRequest;
import com.tripfit.tripfit.auth.controller.dto.LoginResponse;
import com.tripfit.tripfit.auth.controller.dto.LogoutRequest;
import com.tripfit.tripfit.auth.controller.dto.RefreshRequest;
import com.tripfit.tripfit.auth.controller.dto.RefreshResponse;
import com.tripfit.tripfit.auth.service.AuthService;
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

	@Operation(summary = "소셜 로그인")
	@PostMapping("/login")
	ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		LoginResponse response = authService.login(request.provider(), request.token());
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "액세스 토큰 재발급")
	@PostMapping("/refresh")
	ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		RefreshResponse response = authService.refresh(request.refreshToken());
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "로그아웃")
	@PostMapping("/logout")
	ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request.refreshToken());
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
