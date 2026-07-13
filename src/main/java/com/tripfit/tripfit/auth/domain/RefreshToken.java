package com.tripfit.tripfit.auth.domain;

import com.tripfit.tripfit.common.domain.BaseTimeEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refresh_token", uniqueConstraints = @UniqueConstraint(columnNames = "token"))
@Schema(description = "리프레시 토큰 (opaque). wave 1: logout 시 row 삭제. wave 4: RTR 예정")
public class RefreshToken extends BaseTimeEntity {

  @Schema(
      description = "리프레시 토큰 레코드 ID (UUID v4)",
      example = "550e8400-e29b-41d4-a716-446655440000")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  private UUID id;

  @Schema(
      description = "소유 사용자 ID (FK → user.id)",
      example = "550e8400-e29b-41d4-a716-446655440000")
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "user_id", length = 36, nullable = false)
  private UUID userId;

  @Schema(
      description = "opaque refresh token 값 (UUID 등). UNIQUE",
      example = "550e8400-e29b-41d4-a716-446655440000")
  @Column(nullable = false, length = 255)
  private String token;

  @Schema(
      description = "login 체인 식별 UUID. wave 4 RTR reuse detection용",
      example = "550e8400-e29b-41d4-a716-446655440001")
  @Column(name = "family_id", nullable = false, length = 36)
  private String familyId;

  @Schema(description = "폐기 시각. wave 4 rotation용. wave 1 logout은 row delete", nullable = true)
  @Setter
  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  @Schema(description = "만료 시각", example = "2026-08-07T12:00:00")
  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  public RefreshToken(UUID userId, String token, String familyId, LocalDateTime expiresAt) {
    this.userId = userId;
    this.token = token;
    this.familyId = familyId;
    this.expiresAt = expiresAt;
  }

  public boolean isExpired() {
    return expiresAt.isBefore(LocalDateTime.now());
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }
}
