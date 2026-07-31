package com.tripfit.tripfit.auth.oauth;

import java.util.Arrays;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "tripfit.oauth")
public class OAuthProperties {

  private String googleClientId = "";

  private String googleClientSecret = "";

  private String googleClientIdIos = "";

  private String googleClientIdAndroid = "";

  private String googleCalendarClientId = "";

  private String googleCalendarClientSecret = "";

  private String appleBundleId = "";

  private String appleServiceId = "";

  private String appleTeamId = "";

  private String appleKeyId = "";

  private String applePrivateKey = "";

  private String kakaoAdminKey = "";

  public List<String> getGoogleClientIds() {
    return Arrays.stream(new String[] {googleClientId, googleClientIdIos, googleClientIdAndroid})
        .filter(id -> id != null && !id.isBlank())
        .toList();
  }

  // iOS 네이티브 앱(Bundle ID)·모바일 브라우저(Services ID) 두 경로 중 하나만 맞아도 통과시키기 위한 허용 audience 목록
  public List<String> getAppleAudiences() {
    return Arrays.stream(new String[] {appleBundleId, appleServiceId})
        .filter(id -> id != null && !id.isBlank())
        .toList();
  }
}
