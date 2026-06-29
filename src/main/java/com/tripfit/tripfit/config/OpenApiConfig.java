package com.tripfit.tripfit.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI tripfitOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("TripFit API")
						.description("TripFit 백엔드 REST API 문서")
						.version("v0.0.1"));
	}
}
