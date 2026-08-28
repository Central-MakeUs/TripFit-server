package com.tripfit.tripfit.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "tripfit.auth.cookie")
public class AuthCookieProperties {

  private String domain = "localhost";

  private boolean secure = true;
}
