package com.tripfit.tripfit.trip.config;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.exception.UserErrorCode;
import com.tripfit.tripfit.user.service.UserSummaryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

// @TripMemberOnly: 멤버 + RESPONDED + canEnterRoom
// @TripOwnerOnly: 방장만 (JOINED 허용 · RESPONDED/canEnterRoom 면제 — PATCH/DELETE)
@Component
public class TripAuthorizationInterceptor implements HandlerInterceptor {

  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  private final UserSummaryService userSummaryService;

  public TripAuthorizationInterceptor(
      TripRepository tripRepository,
      TripMemberRepository tripMemberRepository,
      UserSummaryService userSummaryService) {
    this.tripRepository = tripRepository;
    this.tripMemberRepository = tripMemberRepository;
    this.userSummaryService = userSummaryService;
  }

  // JWT·tripId로 @TripMemberOnly/@TripOwnerOnly 권한 게이트 (#39 JOINED 방장 면제)
  @Override
  public boolean preHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }

    boolean ownerOnly =
        handlerMethod.getMethodAnnotation(TripOwnerOnly.class) != null;
    boolean memberOnly =
        handlerMethod.getMethodAnnotation(TripMemberOnly.class) != null;
    if (!ownerOnly && !memberOnly) {
      return true;
    }

    // JWT(SecurityContext) + tripId 경로 변수 기준으로 Controller @Trip*Only 권한 검사
    UUID userId = requireAuthenticatedUserId();
    UUID tripId = requireTripId(request);

    // 존재하지 않거나 soft-delete된 tripId(형식 오류 포함) → NOT_FOUND로 통일 (정보 누수 방지)
    if (!tripRepository.existsByIdAndDeletedAtIsNull(tripId)) {
      throw new TripFitException(TripErrorCode.TRIP_NOT_FOUND);
    }

    // OWNER 실패=FORBIDDEN, MEMBER 실패=ACCESS_DENIED — 클라이언트 분기용
    if (ownerOnly) {
      if (!tripRepository.existsByIdAndOwner_IdAndDeletedAtIsNull(tripId, userId)) {
        throw new TripFitException(TripErrorCode.TRIP_FORBIDDEN);
      }
      // JOINED 방장 PATCH/DELETE 허용 — RESPONDED·canEnterRoom 게이트 면제 (#39)
      return true;
    }

    TripMember membership =
        tripMemberRepository
            .findByTripIdAndUserIdAndDeletedAtIsNull(tripId, userId)
            .orElseThrow(() -> new TripFitException(TripErrorCode.TRIP_ACCESS_DENIED));

    // trip별 일정 확인 미완료 — 전역 canEnterRoom과 별개 (#39)
    if (membership.getStatus() != TripMemberStatus.RESPONDED) {
      throw new TripFitException(UserErrorCode.SCHEDULE_CONFIRM_REQUIRED);
    }

    // D-JOIN-ENTRY: 전역 일정 또는 is_all_free
    userSummaryService.requireCanEnterRoom(userId);
    return true;
  }

  private static UUID requireAuthenticatedUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
      throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
    }
    return userId;
  }

  // path variable tripId 파싱 실패도 NOT_FOUND (400으로 UUID 형식만 노출하지 않음)
  private static UUID requireTripId(HttpServletRequest request) {
    Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (!(attribute instanceof Map<?, ?> variables)) {
      throw new TripFitException(TripErrorCode.TRIP_NOT_FOUND);
    }
    Object tripIdValue = variables.get("tripId");
    if (tripIdValue == null) {
      throw new TripFitException(TripErrorCode.TRIP_NOT_FOUND);
    }
    try {
      return UUID.fromString(tripIdValue.toString());
    } catch (IllegalArgumentException exception) {
      throw new TripFitException(TripErrorCode.TRIP_NOT_FOUND);
    }
  }
}
