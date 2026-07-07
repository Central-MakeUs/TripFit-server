package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.auth.domain.RefreshToken;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.auth.dto.LoginResponse;
import com.tripfit.tripfit.auth.dto.RefreshResponse;
import com.tripfit.tripfit.auth.dto.UserSummaryResponse;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.auth.client.OAuthProfile;
import com.tripfit.tripfit.auth.client.SocialTokenVerifier;
import com.tripfit.tripfit.auth.client.SocialTokenVerifierRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final SocialTokenVerifierRegistry verifierRegistry;
	private final UserRepository userRepository;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;

	public AuthService(
			SocialTokenVerifierRegistry verifierRegistry,
			UserRepository userRepository,
			JwtService jwtService,
			RefreshTokenService refreshTokenService
	) {
		this.verifierRegistry = verifierRegistry;
		this.userRepository = userRepository;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
	}

	// 소셜 토큰을 검증하고 사용자 세션용 토큰 묶음을 발급함
	@Transactional
	public LoginResponse login(SocialProvider provider, String token) {
		// 1. 소셜 제공자별 검증기를 찾아 외부 토큰을 검증함
		SocialTokenVerifier verifier = verifierRegistry.getVerifier(provider);
		OAuthProfile profile = verifier.verify(token);

		// 2. 소셜 프로필 기준으로 사용자를 조회하거나 신규 저장함
		User user = upsertUser(profile);

		// 3. 로그인 세션에 필요한 액세스 토큰과 리프레시 토큰을 발급함
		String accessToken = jwtService.createAccessToken(user.getId());
		RefreshToken refreshToken = refreshTokenService.create(user.getId());
		return new LoginResponse(
				accessToken,
				refreshToken.getToken(),
				jwtService.getAccessExpirationSeconds(),
				toUserSummary(user)
		);
	}

	// 리프레시 토큰으로 새로운 액세스 토큰을 재발급함
	@Transactional
	public RefreshResponse refresh(String refreshTokenValue) {
		try {
			// 1. 리프레시 토큰의 존재 여부와 만료 여부를 검증함
			RefreshToken refreshToken = refreshTokenService.validate(refreshTokenValue);

			// 2. 검증된 사용자 ID로 새 액세스 토큰을 생성함
			String accessToken = jwtService.createAccessToken(refreshToken.getUserId());
			return new RefreshResponse(accessToken, jwtService.getAccessExpirationSeconds());
		} catch (TripFitException exception) {
			if (exception.getErrorCode() == AuthErrorCode.AUTH_INVALID_REFRESH) {
				// 만료된 리프레시 토큰으로 재시도한 경우 저장소에서 정리함
				refreshTokenService.deleteExpired(refreshTokenValue);
			}
			throw exception;
		}
	}

	// 로그아웃 요청에 해당하는 리프레시 토큰을 삭제함
	@Transactional
	public void logout(String refreshTokenValue) {
		refreshTokenService.delete(refreshTokenValue);
	}

	// 소셜 계정 기준으로 사용자를 조회하고 없으면 새로 생성함
	private User upsertUser(OAuthProfile profile) {
		return userRepository.findByProviderAndSocialId(profile.provider(), profile.providerUserId())
				.map(existing -> updateEmail(existing, profile))
				.orElseGet(() -> userRepository.save(new User(profile.providerUserId(), profile.provider(), profile.email())));
	}

	// 소셜 프로필에 이메일이 있으면 기존 사용자 이메일을 최신 값으로 갱신함
	private User updateEmail(User user, OAuthProfile profile) {
		if (profile.email() != null && !profile.email().isBlank()) {
			user.setEmail(profile.email());
		}
		return user;
	}

	// 인증 응답에 필요한 최소 사용자 정보를 DTO로 변환함
	private UserSummaryResponse toUserSummary(User user) {
		return new UserSummaryResponse(user.getId(), user.getEmail(), user.getProvider());
	}
}
