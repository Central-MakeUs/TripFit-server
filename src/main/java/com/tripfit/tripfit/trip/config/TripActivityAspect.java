package com.tripfit.tripfit.trip.config;

import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.repository.TripRepository;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TripActivityAspect {

  private final TripRepository tripRepository;

  private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

  public TripActivityAspect(TripRepository tripRepository) {
    this.tripRepository = tripRepository;
  }

  @AfterReturning(pointcut = "@annotation(tripActivity)")
  public void touchLastActivity(JoinPoint joinPoint, TripActivity tripActivity) {
    UUID tripId = resolveTripId(joinPoint, tripActivity);
    if (tripId == null) {
      return;
    }

    tripRepository.findByIdAndDeletedAtIsNull(tripId).ifPresent(Trip::touchLastActivity);
  }

  private UUID resolveTripId(JoinPoint joinPoint, TripActivity tripActivity) {
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
