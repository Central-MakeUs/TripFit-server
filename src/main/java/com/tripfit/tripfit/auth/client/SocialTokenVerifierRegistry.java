package com.tripfit.tripfit.auth.client;

import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SocialTokenVerifierRegistry {

  private final Map<SocialProvider, SocialTokenVerifier> verifiers;

  public SocialTokenVerifierRegistry(List<SocialTokenVerifier> verifierList) {
    this.verifiers = new EnumMap<>(SocialProvider.class);
    for (SocialTokenVerifier verifier : verifierList) {
      this.verifiers.put(verifier.getProvider(), verifier);
    }
  }

  // 소셜 제공자에 대응하는 토큰 검증기를 조회함
  public SocialTokenVerifier getVerifier(SocialProvider provider) {
    SocialTokenVerifier verifier = verifiers.get(provider);
    if (verifier == null) {
      throw new TripFitException(AuthErrorCode.AUTH_INVALID_REQUEST);
    }
    return verifier;
  }
}
