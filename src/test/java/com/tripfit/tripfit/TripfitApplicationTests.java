package com.tripfit.tripfit;

import org.junit.jupiter.api.Test;
import com.tripfit.tripfit.common.config.TestcontainersConfig;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class TripfitApplicationTests {

  @Test
  void contextLoads() {}
}
