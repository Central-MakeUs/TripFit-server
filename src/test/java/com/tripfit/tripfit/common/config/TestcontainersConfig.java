package com.tripfit.tripfit.common.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

// @SpringBootTest에 @Import로 붙이면 실제 MySQL 8 컨테이너를 띄우고 datasource를 자동 배선한다
// (@ServiceConnection). 컨테이너는 JVM당 1개만 뜨고 전체 테스트 스위트가 재사용한다(Ryuk이 종료 시 정리).
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

  @Bean
  @ServiceConnection
  MySQLContainer<?> mysqlContainer() {
    return new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));
  }
}
