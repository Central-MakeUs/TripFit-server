package com.tripfit.tripfit.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Schema(description = "생성·수정 시각 공통 베이스 (JPA Auditing)")
public abstract class BaseTimeEntity {

	@Schema(description = "레코드 생성 시각", example = "2026-07-07T12:00:00")
	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Schema(description = "레코드 최종 수정 시각", example = "2026-07-07T12:00:00")
	@LastModifiedDate
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	protected BaseTimeEntity() {
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
