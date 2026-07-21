package com.tripfit.tripfit.trip.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// TripAuthorizationInterceptor: 방장만 (JOINED 허용). RESPONDED·canEnterRoom 면제 — PATCH/DELETE (#39)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TripOwnerOnly {
}
