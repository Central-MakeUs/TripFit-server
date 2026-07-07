package com.tripfit.tripfit.auth.domain;

import com.tripfit.tripfit.common.domain.BaseTimeEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
		name = "refresh_token",
		uniqueConstraints = @UniqueConstraint(columnNames = "token")
)
@Schema(description = "리프레시 토큰 (opaque). wave 1: logout 시 row 삭제. wave 4: RTR 예정")
public class RefreshToken extends BaseTimeEntity {

	@Schema(description = "리프레시 토큰 레코드 ID", example = "1")
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Schema(description = "소유 사용자 ID (FK → user.id)", example = "1")
	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Schema(description = "opaque refresh token 값 (UUID 등). UNIQUE", example = "550e8400-e29b-41d4-a716-446655440000")
	@Column(nullable = false, length = 255)
	private String token;

	@Schema(description = "login 체인 식별 UUID. wave 4 RTR reuse detection용", example = "550e8400-e29b-41d4-a716-446655440001")
	@Column(name = "family_id", nullable = false, length = 36)
	private String familyId;

	@Schema(description = "폐기 시각. wave 4 rotation용. wave 1 logout은 row delete", nullable = true)
	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;

	@Schema(description = "만료 시각", example = "2026-08-07T12:00:00")
	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	protected RefreshToken() {
	}

	public RefreshToken(Long userId, String token, String familyId, LocalDateTime expiresAt) {
		this.userId = userId;
		this.token = token;
		this.familyId = familyId;
		this.expiresAt = expiresAt;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getToken() {
		return token;
	}

	public String getFamilyId() {
		return familyId;
	}

	public LocalDateTime getRevokedAt() {
		return revokedAt;
	}

	public void setRevokedAt(LocalDateTime revokedAt) {
		this.revokedAt = revokedAt;
	}

	public LocalDateTime getExpiresAt() {
		return expiresAt;
	}

	public boolean isExpired() {
		return expiresAt.isBefore(LocalDateTime.now());
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}
}
