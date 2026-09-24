package com.tripfit.tripfit.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Schema(description = "엔티티의 생성 및 수정 시각을 공통으로 관리하는 베이스 클래스입니다.")
public abstract class BaseTimeEntity {

  @Schema(description = "해당 레코드가 생성된 시각입니다.", example = "2026-07-07T12:00:00")
  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Schema(description = "해당 레코드가 가장 마지막으로 수정된 시각입니다.", example = "2026-07-07T12:00:00")
  @LastModifiedDate
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  protected BaseTimeEntity() {}
}
