package com.tripfit.tripfit.trip.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 성공한 유스케이스에서 여행방 last_activity_at을 갱신하도록 Aspect에 표시한다. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TripActivity {

  /** 메서드 파라미터 이름(UUID tripId). {@link #tripIdFromReturn()} 와 배타. */
  String tripIdParam() default "";

  /** 반환 {@code TripDetailResponse.tripId()} 사용 (신규 join 등). */
  boolean tripIdFromReturn() default false;
}
