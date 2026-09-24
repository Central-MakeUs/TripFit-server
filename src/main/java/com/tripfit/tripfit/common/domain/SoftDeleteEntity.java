package com.tripfit.tripfit.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@MappedSuperclass
@Schema(description = "Soft delete 지원 베이스 (`deleted_at` 설정 시 논리 삭제)")
public abstract class SoftDeleteEntity extends BaseTimeEntity {

  @Schema(
      description = "데이터가 논리적으로 삭제(Soft delete)된 시각입니다. null인 경우 현재 활성화된 레코드를 의미합니다.",
      nullable = true,
      example = "2026-07-07T12:00:00")
  @Column
  private LocalDateTime deletedAt;

  protected SoftDeleteEntity() {}

  public void markDeleted() {
    this.deletedAt = LocalDateTime.now();
  }

  public void clearDeleted() {
    this.deletedAt = null;
  }
}
