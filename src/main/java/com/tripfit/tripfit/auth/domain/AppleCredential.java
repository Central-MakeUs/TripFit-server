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
    description = "Apple 로그인의 리프레시 토큰 자격 증명입니다. 탈퇴 시 토큰 폐기를 위해 보관하며 암호화되어 저장됩니다.")
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
      description = "암호화된 리프레시 토큰 값입니다. 로그인 시 인증 코드로 교환하여 갱신되며 탈퇴 시 삭제됩니다.")
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
