package com.tripfit.tripfit.trip.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// TripAuthorizationInterceptor: 방장 + canEnterRoom → 403 TRIP_FORBIDDEN / SCHEDULE_ENTRY_REQUIRED
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TripOwnerOnly {
}
