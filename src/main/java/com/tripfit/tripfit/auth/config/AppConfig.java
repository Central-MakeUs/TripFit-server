package com.tripfit.tripfit.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, OAuthProperties.class})
public class AppConfig {

	@Bean
	// 외부 OAuth 제공자 호출에 사용할 공용 RestClient를 생성함
	RestClient restClient() {
		return RestClient.create();
	}
}
