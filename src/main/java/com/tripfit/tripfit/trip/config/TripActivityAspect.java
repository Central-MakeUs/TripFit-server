package com.tripfit.tripfit.trip.config;

import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.repository.TripRepository;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Component;

// @TripActivity 성공 반환 후 last_activity_at 갱신 — 해석 실패 시 silent skip (호출 자체는 성공 유지)
@Aspect
@Component
public class TripActivityAspect {

  private final TripRepository tripRepository;

  private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

  public TripActivityAspect(TripRepository tripRepository) {
    this.tripRepository = tripRepository;
  }

  @AfterReturning(pointcut = "@annotation(tripActivity)", returning = "result")
  public void touchLastActivity(JoinPoint joinPoint, TripActivity tripActivity, Object result) {
    UUID tripId = resolveTripId(joinPoint, tripActivity, result);
    if (tripId == null) {
      return;
    }
    // soft-delete된 방은 갱신하지 않음
    tripRepository.findByIdAndDeletedAtIsNull(tripId).ifPresent(Trip::touchLastActivity);
  }

  // tripIdFromReturn=true → TripDetailResponse.tripId / 아니면 tripIdParam 이름의 UUID 인자
  private UUID resolveTripId(JoinPoint joinPoint, TripActivity tripActivity, Object result) {
    if (tripActivity.tripIdFromReturn()) {
      if (result instanceof TripDetailResponse detail) {
        return detail.tripId();
      }
      return null;
    }
    String paramName = tripActivity.tripIdParam();
    if (paramName.isBlank()) {
      return null;
    }
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    String[] names = parameterNames.getParameterNames(signature.getMethod());
    Object[] args = joinPoint.getArgs();
    if (names == null) {
      return null;
    }
    for (int i = 0; i < names.length; i++) {
      if (paramName.equals(names[i]) && args[i] instanceof UUID uuid) {
        return uuid;
      }
    }
    return null;
  }
}
