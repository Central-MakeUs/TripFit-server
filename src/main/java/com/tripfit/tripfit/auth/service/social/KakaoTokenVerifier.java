package com.tripfit.tripfit.auth.service.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.common.exception.ErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KakaoTokenVerifier implements SocialTokenVerifier {

	private static final String KAKAO_USER_ME_URL = "https://kapi.kakao.com/v2/user/me";

	private final RestClient restClient;

	public KakaoTokenVerifier(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public SocialProvider getProvider() {
		return SocialProvider.KAKAO;
	}

	@Override
	public OAuthProfile verify(String token) {
		try {
			JsonNode response = restClient.get()
					.uri(KAKAO_USER_ME_URL)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
					.retrieve()
					.onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
						throw new TripFitException(ErrorCode.AUTH_INVALID_TOKEN);
					})
					.body(JsonNode.class);
			if (response == null || !response.has("id")) {
				throw new TripFitException(ErrorCode.AUTH_INVALID_TOKEN);
			}
			String providerUserId = response.get("id").asText();
			String nickname = null;
			String profileImageUrl = null;
			JsonNode kakaoAccount = response.get("kakao_account");
			if (kakaoAccount != null && kakaoAccount.has("profile")) {
				JsonNode profile = kakaoAccount.get("profile");
				if (profile.has("nickname")) {
					nickname = profile.get("nickname").asText();
				}
				if (profile.has("profile_image_url")) {
					profileImageUrl = profile.get("profile_image_url").asText();
				}
			}
			return new OAuthProfile(SocialProvider.KAKAO, providerUserId, null, nickname, profileImageUrl);
		} catch (TripFitException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new TripFitException(ErrorCode.AUTH_INVALID_TOKEN);
		}
	}
}
