package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;

@Schema(description = "홈 여행방 목록 조회 쿼리. GET /trips")
public record TripListQuery(
    @Schema(description = "목록 뷰. ongoing=진행 중 캐러셀, all=전체 보기 (기본 all)",
        defaultValue = "all") TripListScope scope,

    @Schema(
        description = "상태 필터. ALL(기본)=필터 없음 · ONGOING=조율 중 · CONFIRMED=일정 확정. scope=all일 때만 적용") Optional<TripStatus> statusFilter,

    @Schema(
        description = "내가 방장(OWNER)인 방만. scope=all일 때만 적용",
        defaultValue = "false") boolean ownerOnly
) {

  // statusFilter·ownerOnly는 scope=ALL 쿼리에서만 적용 (ONGOING scope는 Repository가 무시)
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
