package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;

@Schema(description = "GET /trips 쿼리 (D5)")
public record TripListQuery(
    @Schema(description = "ongoing | all", defaultValue = "all") TripListScope scope,

    @Schema(
        description = "ALL(기본) | ONGOING | CONFIRMED — scope=all만") Optional<TripStatus> statusFilter,

    @Schema(description = "내가 생성(OWNER)한 방만 — scope=all만", defaultValue = "false") boolean ownerOnly
) {

  public static TripListQuery parse(String scope, String status, boolean ownerOnly) {
    return new TripListQuery(parseScope(scope), parseStatusFilter(status), ownerOnly);
  }

  private static TripListScope parseScope(String scope) {
    try {
      return TripListScope.valueOf(scope.trim().toUpperCase());
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
  }

  // TripStatus 재사용. ALL=필터 없음. ONGOING|CONFIRMED만 (CANCELED/TERMINATED 단독 필터 금지)
  private static Optional<TripStatus> parseStatusFilter(String status) {
    if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
      return Optional.empty();
    }
    try {
      TripStatus tripStatus = TripStatus.valueOf(status.trim().toUpperCase());
      if (tripStatus != TripStatus.ONGOING && tripStatus != TripStatus.CONFIRMED) {
        throw new TripFitException(CommonErrorCode.INVALID_INPUT);
      }
      return Optional.of(tripStatus);
    } catch (IllegalArgumentException ex) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
  }
}
