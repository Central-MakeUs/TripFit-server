package com.tripfit.tripfit.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.domain.RefreshToken;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.auth.jwt.JwtProperties;
import com.tripfit.tripfit.auth.repository.RefreshTokenRepository;
import com.tripfit.tripfit.common.exception.TripFitException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private RefreshTokenRepository refreshTokenRepository;

  private RefreshTokenService refreshTokenService;

  @BeforeEach
  void setUp() {
    JwtProperties jwtProperties = new JwtProperties();
    jwtProperties.setRefreshExpirationDays(30);
    refreshTokenService = new RefreshTokenService(refreshTokenRepository, jwtProperties);
  }

  private static RefreshToken tokenOf(String token, String familyId, LocalDateTime expiresAt) {
    return new RefreshToken(USER_ID, token, familyId, expiresAt);
  }

  @Test
  void create_issuesTokenWithNewFamily() {
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    RefreshToken created = refreshTokenService.create(USER_ID);

    assertThat(created.getUserId()).isEqualTo(USER_ID);
    assertThat(created.getFamilyId()).isNotBlank();
    assertThat(created.isRevoked()).isFalse();
  }

  @Test
  void rotate_validToken_revokesOldAndIssuesNewInSameFamily() {
    String familyId = UUID.randomUUID().toString();
    RefreshToken current = tokenOf("old-token", familyId, LocalDateTime.now().plusDays(10));
    when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(current));
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    RefreshToken rotated = refreshTokenService.rotate("old-token");

    assertThat(current.isRevoked()).isTrue();
    assertThat(rotated.getFamilyId()).isEqualTo(familyId);
    assertThat(rotated.getToken()).isNotEqualTo("old-token");
    assertThat(rotated.isRevoked()).isFalse();
  }

  @Test
  void rotate_unknownToken_throwsInvalidRefresh() {
    when(refreshTokenRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> refreshTokenService.rotate("missing"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_INVALID_REFRESH);
  }

  @Test
  void rotate_expiredToken_deletesAndThrowsInvalidRefresh() {
    RefreshToken expired =
        tokenOf("expired-token", UUID.randomUUID().toString(), LocalDateTime.now().minusDays(1));
    when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> refreshTokenService.rotate("expired-token"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_INVALID_REFRESH);

    verify(refreshTokenRepository).delete(expired);
  }

  // 재사용 탐지 — 이미 rotate로 폐기된(revokedAt != null) 토큰이 재제출되면 family 전체를 폐기하고 REUSE로 던짐
  @Test
  void rotate_alreadyRevokedToken_revokesWholeFamilyAndThrowsReuse() {
    String familyId = UUID.randomUUID().toString();
    RefreshToken reusedToken = tokenOf("rotated-away", familyId, LocalDateTime.now().plusDays(10));
    reusedToken.setRevokedAt(LocalDateTime.now().minusMinutes(5));
    when(refreshTokenRepository.findByToken("rotated-away")).thenReturn(Optional.of(reusedToken));

    RefreshToken siblingInFamily =
        tokenOf("sibling-token", familyId, LocalDateTime.now().plusDays(10));
    when(refreshTokenRepository.findAllByFamilyIdAndRevokedAtIsNull(familyId))
        .thenReturn(List.of(siblingInFamily));

    assertThatThrownBy(() -> refreshTokenService.rotate("rotated-away"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_REFRESH_REUSE);

    assertThat(siblingInFamily.isRevoked()).isTrue();
    verify(refreshTokenRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void delete_delegatesToRepository() {
    refreshTokenService.delete("token-value");
    verify(refreshTokenRepository).deleteByToken("token-value");
  }

  @Test
  void revokeAllForUser_delegatesToRepository() {
    refreshTokenService.revokeAllForUser(USER_ID);
    verify(refreshTokenRepository).deleteAllByUserId(USER_ID);
  }

  @Test
  void create_savesTokenWithConfiguredExpiration() {
    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    when(refreshTokenRepository.save(captor.capture()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    refreshTokenService.create(USER_ID);

    LocalDateTime expiresAt = captor.getValue().getExpiresAt();
    assertThat(expiresAt).isAfter(LocalDateTime.now().plusDays(29));
    assertThat(expiresAt).isBefore(LocalDateTime.now().plusDays(31));
  }
}
