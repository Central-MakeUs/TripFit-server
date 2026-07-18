package com.tripfit.tripfit.trip.config;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
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

@Component
public class TripAuthorizationInterceptor implements HandlerInterceptor {

  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  public TripAuthorizationInterceptor(
      TripRepository tripRepository, TripMemberRepository tripMemberRepository) {
    this.tripRepository = tripRepository;
    this.tripMemberRepository = tripMemberRepository;
  }

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

    if (!tripRepository.existsByIdAndDeletedAtIsNull(tripId)) {
      throw new TripFitException(TripErrorCode.TRIP_NOT_FOUND);
    }

    if (ownerOnly) {
      if (!tripRepository.existsByIdAndOwner_IdAndDeletedAtIsNull(tripId, userId)) {
        throw new TripFitException(TripErrorCode.TRIP_FORBIDDEN);
      }
      return true;
    }

    if (!tripMemberRepository.existsByTripIdAndUserIdAndDeletedAtIsNull(tripId, userId)) {
      throw new TripFitException(TripErrorCode.TRIP_ACCESS_DENIED);
    }
    return true;
  }

  private static UUID requireAuthenticatedUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
      throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
    }
    return userId;
  }

  @SuppressWarnings("unchecked")
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
