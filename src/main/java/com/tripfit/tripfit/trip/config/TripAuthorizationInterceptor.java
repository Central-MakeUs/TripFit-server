package com.tripfit.tripfit.trip.config;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.trip.service.TripServiceSupport;
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

  private final TripServiceSupport support;

  public TripAuthorizationInterceptor(
      TripRepository tripRepository, TripServiceSupport support) {
    this.tripRepository = tripRepository;
    this.support = support;
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
    boolean membershipOnly =
        handlerMethod.getMethodAnnotation(TripMembershipOnly.class) != null;
    if (!ownerOnly && !memberOnly && !membershipOnly) {
      return true;
    }

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

    if (membershipOnly) {

      support.requireMembership(tripId, userId);
      return true;
    }

    TripMember membership = support.requireMembership(tripId, userId);

    support.requireActive(membership);
    return true;
  }

  private static UUID requireAuthenticatedUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
      throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
    }
    return userId;
  }

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
