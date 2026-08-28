package com.tripfit.tripfit.auth.security;

import com.tripfit.tripfit.auth.config.AuthCookieProperties;
import com.tripfit.tripfit.auth.jwt.AuthorizedUserArgumentResolver;
import com.tripfit.tripfit.auth.jwt.JwtProperties;
import com.tripfit.tripfit.auth.oauth.OAuthProperties;
import com.tripfit.tripfit.common.holiday.config.HolidayProperties;
import com.tripfit.tripfit.common.security.SocialTokenCryptoProperties;
import com.tripfit.tripfit.notification.config.FcmProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties({
    JwtProperties.class,
    AuthCookieProperties.class,
    OAuthProperties.class,
    SocialTokenCryptoProperties.class,
    FcmProperties.class,
    HolidayProperties.class
})
public class AppConfig implements WebMvcConfigurer {

  private final AuthorizedUserArgumentResolver authorizedUserArgumentResolver;

  public AppConfig(AuthorizedUserArgumentResolver authorizedUserArgumentResolver) {
    this.authorizedUserArgumentResolver = authorizedUserArgumentResolver;
  }

  @Bean

  RestClient restClient() {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(3));
    requestFactory.setReadTimeout(Duration.ofSeconds(5));
    return RestClient.builder().requestFactory(requestFactory).build();
  }

  @Override

  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(authorizedUserArgumentResolver);
  }
}
