package com.tripfit.tripfit.trip.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 방장 메타 API(PATCH/DELETE): SCHEDULE_PENDING여도 허용(ACTIVE·입장 면제).
// 주의: 이것은 “입장·공유 허용”이 아님. 공유·상세는 @TripMemberOnly(ACTIVE) 경로.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TripOwnerOnly {
}
