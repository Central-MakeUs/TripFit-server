package com.tripfit.tripfit.auth.domain;

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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "google_login_credential")
@Schema(
    description = "Google 로그인 refresh token credential. user당 1행, 탈퇴 시 revoke 호출 용도로만 보관 — refresh token은 AES-256 암호화 저장")
public class GoogleLoginCredential extends BaseTimeEntity {

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
      description = "refresh token AES-256-GCM 암호문 (Base64) — 로그인 시 authorizationCode 교환으로 값이 있을 때만 갱신(재로그인은 Google이 최초 동의 시에만 refresh_token을 내려줌), 탈퇴 시 revoke 호출 후 row 삭제")
  @Column(name = "refresh_token_ciphertext", nullable = false, columnDefinition = "TEXT")
  private String refreshTokenCiphertext;

  public static GoogleLoginCredential create(User user, String refreshTokenCiphertext) {
    GoogleLoginCredential credential = new GoogleLoginCredential();
    credential.user = user;
    credential.refreshTokenCiphertext = refreshTokenCiphertext;
    return credential;
  }

  // 새 refresh token 값이 있을 때만 덮어씀 — Google이 재로그인마다 refresh_token을 내려주지 않으므로(최초 동의 시에만),
  // 값이 없는 호출부는 기존 값을 그대로 유지해야 함(Apple의 "매번 덮어씀"과 다른 지점)
  public void updateRefreshToken(String refreshTokenCiphertext) {
    this.refreshTokenCiphertext = refreshTokenCiphertext;
  }
}
