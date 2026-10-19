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

  // 아래 두 주소는 운영에서 바꾸지 않는다. 부하 테스트가 외부 지연을 재현하려고 WireMock 주소로 바꿀 때만 쓴다.
  private String kakaoUserMeUrl = "https://kapi.kakao.com/v2/user/me";

  private String googleTokenUrl = "https://oauth2.googleapis.com/token";

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
