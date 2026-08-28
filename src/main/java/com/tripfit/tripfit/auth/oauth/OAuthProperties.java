package com.tripfit.tripfit.auth.oauth;

import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "tripfit.oauth")
public class OAuthProperties {

  private String googleClientId = "";

  @ToString.Exclude
  private String googleClientSecret = "";

  private String googleClientIdIos = "";

  private String googleClientIdAndroid = "";

  private String googleCalendarClientId = "";

  @ToString.Exclude
  private String googleCalendarClientSecret = "";

  private String appleBundleId = "";

  private String appleServiceId = "";

  private String appleTeamId = "";

  private String appleKeyId = "";

  @ToString.Exclude
  private String applePrivateKey = "";

  @ToString.Exclude
  private String kakaoAdminKey = "";

  public List<String> getGoogleClientIds() {
    return Arrays.stream(new String[] {googleClientId, googleClientIdIos, googleClientIdAndroid})
        .filter(id -> id != null && !id.isBlank())
        .toList();
  }

  public List<String> getAppleAudiences() {
    return Arrays.stream(new String[] {appleBundleId, appleServiceId})
        .filter(id -> id != null && !id.isBlank())
        .toList();
  }
}
