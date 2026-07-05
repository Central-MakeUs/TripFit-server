package com.tripfit.tripfit.auth.repository;

import com.tripfit.tripfit.common.domain.BaseTimeEntity;
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
public class RefreshToken extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 255)
	private String token;

	@Column(name = "family_id", nullable = false, length = 36)
	private String familyId;

	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;

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
