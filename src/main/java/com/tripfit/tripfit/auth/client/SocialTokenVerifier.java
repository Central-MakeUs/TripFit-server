package com.tripfit.tripfit.auth.client;

import com.tripfit.tripfit.user.domain.SocialProvider;

public interface SocialTokenVerifier {

  SocialProvider getProvider();

  OAuthProfile verify(String token);
}
