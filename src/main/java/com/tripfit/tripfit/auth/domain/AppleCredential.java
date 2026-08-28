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
    description = "Apple Sign In refresh token credential. user당 1행, 탈퇴 시 revoke 호출 용도로만 보관. refresh token은 AES-256 암호화 저장")
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
      description = "refresh token AES-256-GCM 암호문 (Base64). 로그인 시 authorizationCode 교환으로 갱신, 탈퇴 시 revoke 호출 후 row 삭제")
  @Column(name = "refresh_token_ciphertext", nullable = false, columnDefinition = "TEXT")
  private String refreshTokenCiphertext;

  @Schema(
      description = "로그인 시 검증된 Apple client_id 원문(Bundle ID 또는 Services ID). iOS 네이티브 앱과 모바일 브라우저 로그인 경로가 서로 다른 client_id를 쓰기 때문에, 탈퇴 시 revoke 호출에 반드시 이 값을 그대로 재사용해야 함")
  @Column(name = "apple_client_id", nullable = false)
  private String appleClientId;

  public static AppleCredential create(
      User user,
      String refreshTokenCiphertext,
      String appleClientId) {
    AppleCredential credential = new AppleCredential();
    credential.user = user;
    credential.refreshTokenCiphertext = refreshTokenCiphertext;
    credential.appleClientId = appleClientId;
    return credential;
  }

  public void update(String refreshTokenCiphertext, String appleClientId) {
    this.refreshTokenCiphertext = refreshTokenCiphertext;
    this.appleClientId = appleClientId;
  }
}
