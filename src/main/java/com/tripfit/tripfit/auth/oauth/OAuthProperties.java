package com.tripfit.tripfit.auth.oauth;

import java.util.Arrays;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
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
}
