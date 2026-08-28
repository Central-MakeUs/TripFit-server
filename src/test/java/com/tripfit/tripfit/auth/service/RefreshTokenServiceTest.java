package com.tripfit.tripfit.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.auth.jwt.JwtProperties;
import com.tripfit.tripfit.common.exception.TripFitException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class RefreshTokenServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  private static GenericContainer<?> redisContainer;

  private static LettuceConnectionFactory connectionFactory;

  private static RefreshTokenService service;

  @BeforeAll
  static void startContainer() {
    redisContainer =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    redisContainer.start();

    RedisStandaloneConfiguration config =
        new RedisStandaloneConfiguration(
            redisContainer.getHost(), redisContainer.getMappedPort(6379));
    connectionFactory = new LettuceConnectionFactory(config);
    connectionFactory.afterPropertiesSet();

    StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();

    JwtProperties jwtProperties = new JwtProperties();
    jwtProperties.setRefreshExpirationDays(30);

    service = new RefreshTokenService(redisTemplate, jwtProperties);
  }

  @AfterAll
  static void stopContainer() {
    connectionFactory.destroy();
    redisContainer.stop();
  }

  @Test
  void create_issuesTokenWithNewFamily() {
    IssuedRefreshToken first = service.create(USER_ID);
    IssuedRefreshToken second = service.create(USER_ID);

    assertThat(first.token()).isNotEqualTo(second.token());
    assertThat(first.familyId()).isNotEqualTo(second.familyId());
    assertThat(first.userId()).isEqualTo(USER_ID);
  }

  @Test
  void rotate_validToken_revokesOldAndIssuesNewInSameFamily() {
    IssuedRefreshToken issued = service.create(USER_ID);

    IssuedRefreshToken rotated = service.rotate(issued.token());

    assertThat(rotated.token()).isNotEqualTo(issued.token());
    assertThat(rotated.familyId()).isEqualTo(issued.familyId());
    assertThat(rotated.userId()).isEqualTo(USER_ID);
  }

  @Test
  void rotate_unknownToken_throwsInvalidRefresh() {
    assertThatThrownBy(() -> service.rotate(UUID.randomUUID().toString()))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_INVALID_REFRESH);
  }

  @Test
  void rotate_alreadyRevokedToken_revokesWholeFamilyAndThrowsReuse() {
    IssuedRefreshToken issued = service.create(USER_ID);
    IssuedRefreshToken rotatedOnce = service.rotate(issued.token());

    assertThatThrownBy(() -> service.rotate(issued.token()))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_REFRESH_REUSE);

    assertThatThrownBy(() -> service.rotate(rotatedOnce.token()))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_INVALID_REFRESH);
  }

  @Test
  void delete_removesActiveToken() {
    IssuedRefreshToken issued = service.create(USER_ID);

    service.delete(issued.token());

    assertThatThrownBy(() -> service.rotate(issued.token()))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_INVALID_REFRESH);
  }

  @Test
  void delete_unknownToken_doesNotThrow() {
    service.delete(UUID.randomUUID().toString());
  }

  @Test
  void revokeAllForUser_revokesEveryFamily() {
    UUID userId = UUID.randomUUID();
    IssuedRefreshToken first = service.create(userId);
    IssuedRefreshToken second = service.create(userId);

    service.revokeAllForUser(userId);

    assertThatThrownBy(() -> service.rotate(first.token()))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_INVALID_REFRESH);
    assertThatThrownBy(() -> service.rotate(second.token()))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_INVALID_REFRESH);
  }
}
