package com.tripfit.tripfit.trip.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// TripAuthorizationInterceptor: 참여자(soft-delete 제외)만 허용 → 403 TRIP_ACCESS_DENIED
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TripMemberOnly {
}
