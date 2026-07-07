package com.tripfit.tripfit.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.time.LocalDateTime;

@MappedSuperclass
@Schema(description = "Soft delete 지원 베이스 (`deleted_at` 설정 시 논리 삭제)")
public abstract class SoftDeleteEntity extends BaseTimeEntity {

	@Schema(description = "Soft delete 시각. null이면 활성 레코드", nullable = true, example = "2026-07-07T12:00:00")
	@Column
	private LocalDateTime deletedAt;

	protected SoftDeleteEntity() {
	}

	public LocalDateTime getDeletedAt() {
		return deletedAt;
	}

	public void setDeletedAt(LocalDateTime deletedAt) {
		this.deletedAt = deletedAt;
	}
}
