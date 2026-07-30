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
@Table(name = "apple_credential")
@Schema(
    description = "Apple Sign In refresh token credential. user당 1행, 탈퇴 시 revoke 호출 용도로만 보관 — refresh token은 AES-256 암호화 저장")
public class AppleCredential extends BaseTimeEntity {

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
      description = "refresh token AES-256-GCM 암호문 (Base64) — 로그인 시 authorizationCode 교환으로 갱신, 탈퇴 시 revoke 호출 후 row 삭제")
  @Column(name = "refresh_token_ciphertext", nullable = false, columnDefinition = "TEXT")
  private String refreshTokenCiphertext;

  public static AppleCredential create(User user, String refreshTokenCiphertext) {
    AppleCredential credential = new AppleCredential();
    credential.user = user;
    credential.refreshTokenCiphertext = refreshTokenCiphertext;
    return credential;
  }

  // 재로그인마다 새로 오는 authorizationCode로 교환한 refresh token으로 덮어씀 — 이전 값은 폐기
  public void updateRefreshToken(String refreshTokenCiphertext) {
    this.refreshTokenCiphertext = refreshTokenCiphertext;
  }
}
