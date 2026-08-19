package com.tripfit.tripfit.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

// tripfit.auth.cookie.* — refreshToken 쿠키 속성. domain은 배포 서브도메인(api.tripfit.online)이
// 프론트 도메인(tripfit.online)과도 쿠키를 공유하도록 상위 도메인으로 설정한다. secure는 로컬 개발
// 환경(HTTPS 없음)에서만 false로 오버라이드한다(application-local.yml)
@Data
@ConfigurationProperties(prefix = "tripfit.auth.cookie")
public class AuthCookieProperties {

  private String domain = "localhost";

  private boolean secure = true;
}
