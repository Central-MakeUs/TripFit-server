package com.tripfit.tripfit.trip.config;

import com.tripfit.tripfit.trip.repository.TripRepository;
import java.time.LocalDateTime;
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

  // @TripActivity가 붙은 유스케이스가 성공하면 여행방의 최근 활동 시각을 지금으로 갱신한다.
  // 유스케이스와 같은 트랜잭션 안에서 실행되므로, 유스케이스가 롤백되면 이 갱신도 함께 취소된다.
  // DB만 직접 고치기 때문에, 같은 트랜잭션에서 이미 불러 둔 Trip 객체의 활동 시각은 옛 값으로 남는다.
  @AfterReturning(pointcut = "@annotation(tripActivity)")
  public void touchLastActivity(JoinPoint joinPoint, TripActivity tripActivity) {
    UUID tripId = resolveTripId(joinPoint, tripActivity);
    if (tripId == null) {
      return;
    }

    tripRepository.touchLastActivity(tripId, LocalDateTime.now());
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
