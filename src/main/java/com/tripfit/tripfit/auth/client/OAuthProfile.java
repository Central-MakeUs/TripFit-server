package com.tripfit.tripfit.auth.client;

import com.tripfit.tripfit.user.domain.SocialProvider;

public record OAuthProfile(
		SocialProvider provider,
		String providerUserId,
		String email
) {
}
