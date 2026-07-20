package com.tripfit.tripfit.trip.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// TripAuthorizationInterceptor: 방장만 허용 → 403 TRIP_FORBIDDEN
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TripOwnerOnly {
}
