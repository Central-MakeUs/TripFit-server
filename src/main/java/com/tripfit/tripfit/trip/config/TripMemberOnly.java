package com.tripfit.tripfit.trip.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// TripAuthorizationInterceptor: 참여자 + RESPONDED + canEnterRoom
// → 403 TRIP_ACCESS_DENIED / SCHEDULE_CONFIRM_REQUIRED / SCHEDULE_ENTRY_REQUIRED
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TripMemberOnly {
}
