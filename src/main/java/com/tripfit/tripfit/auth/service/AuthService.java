package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.auth.repository.RefreshToken;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.auth.controller.dto.LoginResponse;
import com.tripfit.tripfit.auth.controller.dto.RefreshResponse;
import com.tripfit.tripfit.auth.controller.dto.UserSummaryResponse;
import com.tripfit.tripfit.common.exception.ErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.auth.service.social.OAuthProfile;
import com.tripfit.tripfit.auth.service.social.SocialTokenVerifier;
import com.tripfit.tripfit.auth.service.social.SocialTokenVerifierRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private static final String DEFAULT_NICKNAME = "여행자";

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

	@Transactional
	public LoginResponse login(SocialProvider provider, String token) {
		SocialTokenVerifier verifier = verifierRegistry.getVerifier(provider);
		OAuthProfile profile = verifier.verify(token);
		User user = upsertUser(profile);
		String accessToken = jwtService.createAccessToken(user.getId());
		RefreshToken refreshToken = refreshTokenService.create(user.getId());
		return new LoginResponse(
				accessToken,
				refreshToken.getToken(),
				jwtService.getAccessExpirationSeconds(),
				toUserSummary(user)
		);
	}

	@Transactional
	public RefreshResponse refresh(String refreshTokenValue) {
		try {
			RefreshToken refreshToken = refreshTokenService.validate(refreshTokenValue);
			String accessToken = jwtService.createAccessToken(refreshToken.getUserId());
			return new RefreshResponse(accessToken, jwtService.getAccessExpirationSeconds());
		} catch (TripFitException exception) {
			if (exception.getErrorCode() == ErrorCode.AUTH_INVALID_REFRESH) {
				refreshTokenService.deleteExpired(refreshTokenValue);
			}
			throw exception;
		}
	}

	@Transactional
	public void logout(String refreshTokenValue) {
		refreshTokenService.delete(refreshTokenValue);
	}

	private User upsertUser(OAuthProfile profile) {
		return userRepository.findByProviderAndSocialId(profile.provider(), profile.providerUserId())
				.map(existing -> updateProfile(existing, profile))
				.orElseGet(() -> createUser(profile));
	}

	private User createUser(OAuthProfile profile) {
		String nickname = resolveNickname(profile);
		User user = new User(
				profile.providerUserId(),
				profile.provider(),
				nickname,
				profile.profileImageUrl()
		);
		return userRepository.save(user);
	}

	private User updateProfile(User user, OAuthProfile profile) {
		if (profile.nickname() != null && !profile.nickname().isBlank()) {
			user.setNickname(profile.nickname());
		}
		if (profile.profileImageUrl() != null && !profile.profileImageUrl().isBlank()) {
			user.setProfileImageUrl(profile.profileImageUrl());
		}
		return user;
	}

	private String resolveNickname(OAuthProfile profile) {
		if (profile.nickname() != null && !profile.nickname().isBlank()) {
			return profile.nickname();
		}
		return DEFAULT_NICKNAME;
	}

	private UserSummaryResponse toUserSummary(User user) {
		return new UserSummaryResponse(
				user.getId(),
				user.getNickname(),
				user.getProfileImageUrl(),
				user.getProvider()
		);
	}
}
