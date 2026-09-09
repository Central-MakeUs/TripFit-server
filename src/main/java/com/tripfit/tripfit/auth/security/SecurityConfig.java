package com.tripfit.tripfit.auth.security;

import com.tripfit.tripfit.auth.jwt.JwtAuthenticationEntryPoint;
import com.tripfit.tripfit.auth.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(
        List.of(
            "https://tripfit.online",
            "https://www.tripfit.online",
            "https://api.tripfit.online",
            "http://localhost:3000"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  // login/refresh/logout/apple-notifications/error·actuator·swagger·scalar는 공개. logout은
  // 만료·폐기 토큰도 body로 처리하기 위해 permitAll.
  // apple/notifications는 Apple 서버가 직접 호출 — signed JWT 자체 검증으로 보호
  // login/refresh/logout/apple-notifications 경로 목록은 JwtAuthenticationFilter.PUBLIC_AUTH_POST_PATHS가
  // SSOT — 그 필터가 같은 경로를 파싱 자체에서 건너뛰므로 여기서 임의로 목록이 갈리면 안 됨
  // /error: authenticated 상태였던 요청도 예외 처리 중 SecurityContext가 비워진 채 내부 forward되므로, permitAll이 아니면
  // 원래 500이어야 할 응답이 401 AUTH_INVALID_TOKEN으로 오인 마스킹됨
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
        .authorizeHttpRequests(
            auth -> {
              JwtAuthenticationFilter.PUBLIC_AUTH_POST_PATHS.forEach(
                  path -> auth.requestMatchers(HttpMethod.POST, path).permitAll());
              auth.requestMatchers("/error").permitAll();
              auth.requestMatchers("/actuator/**").permitAll();
              auth.requestMatchers(
                  "/swagger-ui/**",
                  "/swagger-ui.html",
                  "/v3/api-docs/**",
                  "/scalar",
                  "/scalar/**")
                  .permitAll();
              auth.anyRequest().authenticated();
            })
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
