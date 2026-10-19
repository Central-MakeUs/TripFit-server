package com.tripfit.tripfit.loadtest;

import com.tripfit.tripfit.TripfitApplication;
import org.springframework.boot.SpringApplication;

// 부하 테스트 전용 실행 진입점이다. `./gradlew bootTestRun`이 이 클래스를 실행한다.
// 운영 애플리케이션에 LoadTestConfiguration(FCM·Google 검증 대역, 테스트 데이터 생성)만 더해 띄운다.
// 실행 방법은 loadtest/README.md에 있다.
public class LoadTestApplication {

  public static void main(String[] args) {
    SpringApplication.from(TripfitApplication::main)
        .with(LoadTestConfiguration.class)
        .withAdditionalProfiles("loadtest")
        .run(args);
  }
}
