package com.tripfit.tripfit.user.googlecalendar.domain;

import com.tripfit.tripfit.common.domain.BaseTimeEntity;
import com.tripfit.tripfit.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "google_calendar_credential")
@Schema(
    description = "Google Calendar OAuth credential. user당 1행, refresh·access token AES-256 암호화 저장")
public class GoogleCalendarCredential extends BaseTimeEntity {

  @Schema(
      description = "credential ID (UUID v4)",
      example = "550e8400-e29b-41d4-a716-446655440000")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  private UUID id;

  @Schema(description = "소유 사용자 (UNIQUE)")
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Schema(
      description = "연동에 사용한 Google 계정 이메일. API Must 미노출(재연동 UX·운영용). 조회 실패 시 null",
      nullable = true,
      example = "user@gmail.com")
  @Column(name = "google_account_email")
  private String googleAccountEmail;

  @Schema(description = "refresh token AES-256-GCM 암호문 (Base64)")
  @Column(name = "refresh_token_ciphertext", nullable = false, columnDefinition = "TEXT")
  private String refreshTokenCiphertext;

  @Schema(description = "access token AES-256-GCM 암호문 (Base64, 캐시)", nullable = true)
  @Column(name = "access_token_ciphertext", columnDefinition = "TEXT")
  private String accessTokenCiphertext;

  @Schema(description = "access token 만료 시각 (UTC)", nullable = true)
  @Column(name = "access_token_expires_at")
  private Instant accessTokenExpiresAt;

  @Schema(description = "마지막 freeBusy sync 시각 (내부용, API 미노출)", nullable = true)
  @Column(name = "last_synced_at")
  private Instant lastSyncedAt;

  @Schema(description = "마지막 sync 오류 메시지 (내부용)", nullable = true)
  @Column(name = "last_sync_error", columnDefinition = "TEXT")
  private String lastSyncError;

  public static GoogleCalendarCredential create(
      User user,
      String refreshTokenCiphertext,
      String accessTokenCiphertext,
      Instant accessTokenExpiresAt,
      String googleAccountEmail) {
    GoogleCalendarCredential credential = new GoogleCalendarCredential();
    credential.user = user;
    credential.refreshTokenCiphertext = refreshTokenCiphertext;
    credential.accessTokenCiphertext = accessTokenCiphertext;
    credential.accessTokenExpiresAt = accessTokenExpiresAt;
    credential.googleAccountEmail = blankToNull(googleAccountEmail);
    return credential;
  }

  public void updateTokens(
      String refreshTokenCiphertext,
      String accessTokenCiphertext,
      Instant accessTokenExpiresAt,
      String googleAccountEmail) {
    this.refreshTokenCiphertext = refreshTokenCiphertext;
    this.accessTokenCiphertext = accessTokenCiphertext;
    this.accessTokenExpiresAt = accessTokenExpiresAt;
    if (googleAccountEmail != null && !googleAccountEmail.isBlank()) {
      this.googleAccountEmail = googleAccountEmail.trim();
    }
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  public void updateAccessTokenCache(String accessTokenCiphertext, Instant accessTokenExpiresAt) {
    this.accessTokenCiphertext = accessTokenCiphertext;
    this.accessTokenExpiresAt = accessTokenExpiresAt;
  }

  public void markSynced() {
    this.lastSyncedAt = Instant.now();
    this.lastSyncError = null;
  }

  public void markSyncError(String error) {
    this.lastSyncError = error;
  }
}
