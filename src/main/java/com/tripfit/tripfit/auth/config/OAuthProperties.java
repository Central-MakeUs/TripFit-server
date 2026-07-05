package com.tripfit.tripfit.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "tripfit.oauth")
public class OAuthProperties {

	private String googleClientId = "";
	private String googleClientIdIos = "";
	private String googleClientIdAndroid = "";
	private String appleClientId = "";

	public List<String> getGoogleClientIds() {
		return Arrays.stream(new String[] {googleClientId, googleClientIdIos, googleClientIdAndroid})
				.filter(id -> id != null && !id.isBlank())
				.toList();
	}

	public String getGoogleClientId() {
		return googleClientId;
	}

	public void setGoogleClientId(String googleClientId) {
		this.googleClientId = googleClientId;
	}

	public String getGoogleClientIdIos() {
		return googleClientIdIos;
	}

	public void setGoogleClientIdIos(String googleClientIdIos) {
		this.googleClientIdIos = googleClientIdIos;
	}

	public String getGoogleClientIdAndroid() {
		return googleClientIdAndroid;
	}

	public void setGoogleClientIdAndroid(String googleClientIdAndroid) {
		this.googleClientIdAndroid = googleClientIdAndroid;
	}

	public String getAppleClientId() {
		return appleClientId;
	}

	public void setAppleClientId(String appleClientId) {
		this.appleClientId = appleClientId;
	}
}
