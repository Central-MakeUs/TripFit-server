package com.tripfit.tripfit.auth.service.social;

import com.tripfit.tripfit.user.domain.SocialProvider;

public record OAuthProfile(
		SocialProvider provider,
		String providerUserId,
		String email,
		String nickname,
		String profileImageUrl
) {
}
