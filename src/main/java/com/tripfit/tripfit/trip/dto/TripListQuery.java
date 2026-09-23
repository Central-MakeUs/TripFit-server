package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;

@Schema(description = "홈 화면의 여행방 목록 조회 파라미터입니다. (GET /trips)")
public record TripListQuery(
    @Schema(
        description = """
            조회할 목록의 뷰 타입입니다.
            - ongoing: 진행 중인 여행방 캐러셀
            - all: 전체 여행방 보기 (기본값)
            """,
        defaultValue = "all") TripListScope scope,

    @Schema(
        description = """
            여행방 상태 필터입니다.
            - ALL: 필터 없음 (기본값)
            - ONGOING: 조율 중
            - CONFIRMED: 일정 확정
            - 참고: 이 필터는 `scope=all`인 경우에만 적용됩니다.
            """) Optional<TripStatus> statusFilter,

    @Schema(
        description = """
            방장(OWNER)인 방만 필터링할지 여부입니다.
            - 참고: 이 필터는 `scope=all`인 경우에만 적용됩니다.
            """,
        defaultValue = "false") boolean ownerOnly
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
