package com.tripfit.tripfit.trip.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TripWebConfig implements WebMvcConfigurer {

  private final TripAuthorizationInterceptor tripAuthorizationInterceptor;

  public TripWebConfig(TripAuthorizationInterceptor tripAuthorizationInterceptor) {
    this.tripAuthorizationInterceptor = tripAuthorizationInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(tripAuthorizationInterceptor)
        // /api/v1/trips/** 만 대상 — join 등 tripId 없는 경로는 @Trip*Only 미적용
        .addPathPatterns("/api/v1/trips/**");
  }
}
