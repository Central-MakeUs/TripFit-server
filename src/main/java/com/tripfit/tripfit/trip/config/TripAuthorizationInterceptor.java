package com.tripfit.tripfit.trip.config;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.port.out.UserDirectoryPort;
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

// @TripMemberOnly: 멤버 + ACTIVE + canEnterRoom (방 입장·상세·공유 데이터)
// @TripOwnerOnly: 방장만 (SCHEDULE_PENDING 허용 · ACTIVE/canEnterRoom 면제 — PATCH/DELETE 메타만.
// 초대 공유는 방 입장 후 → 상세 inviteCode · SCHEDULE_PENDING create 응답에 inviteCode 없음)
// @TripMembershipOnly: 멤버(역할 무관) + SCHEDULE_PENDING 허용 · ACTIVE/canEnterRoom 면제 (Pin 등 방 입장과 무관한 개인
// 설정)
@Component
public class TripAuthorizationInterceptor implements HandlerInterceptor {

  private final TripRepository tripRepository;

  private final TripServiceSupport support;

  private final UserDirectoryPort userDirectoryPort;

  public TripAuthorizationInterceptor(
      TripRepository tripRepository,
      TripServiceSupport support,
      UserDirectoryPort userDirectoryPort) {
    this.tripRepository = tripRepository;
    this.support = support;
    this.userDirectoryPort = userDirectoryPort;
  }

  // JWT·tripId로 @TripMemberOnly/@TripOwnerOnly/@TripMembershipOnly 권한 검사 — SCHEDULE_PENDING 방장은
  // 메타·Pin API만 면제
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
      // SCHEDULE_PENDING 방장도 PATCH/DELETE(메타)는 허용 — ACTIVE·입장 조건 검사 생략
      return true;
    }

    if (membershipOnly) {
      // 역할 무관 멤버십만 확인 — SCHEDULE_PENDING도 허용, ACTIVE·canEnterRoom 검사 생략(Pin 등)
      support.requireActiveMember(tripId, userId);
      return true;
    }

    TripMember membership = support.requireActiveMember(tripId, userId);

    // 이 방 일정 확인 미완료(SCHEDULE_PENDING) — 전역 입장 조건과 별개로 차단
    support.requireActive(membership);

    // 전역 입장 조건: 일정≥1 또는 전부 free
    userDirectoryPort.requireCanEnterRoom(userId);
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
