package com.tripfit.tripfit.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, OAuthProperties.class})
public class AppConfig implements WebMvcConfigurer {

  private final AuthorizedUserArgumentResolver authorizedUserArgumentResolver;

  public AppConfig(AuthorizedUserArgumentResolver authorizedUserArgumentResolver) {
    this.authorizedUserArgumentResolver = authorizedUserArgumentResolver;
  }

  @Bean
  // 외부 OAuth 제공자 호출에 사용할 공용 RestClient를 생성함
  RestClient restClient() {
    return RestClient.create();
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(authorizedUserArgumentResolver);
  }
}
