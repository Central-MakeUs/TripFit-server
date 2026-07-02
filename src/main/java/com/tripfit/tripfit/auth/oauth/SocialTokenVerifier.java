package com.tripfit.tripfit.auth.oauth;

import com.tripfit.tripfit.user.domain.SocialProvider;

public interface SocialTokenVerifier {

  SocialProvider getProvider();

  OAuthProfile verify(String token);
}
