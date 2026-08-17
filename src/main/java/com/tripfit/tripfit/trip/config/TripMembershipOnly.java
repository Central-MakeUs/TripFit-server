package com.tripfit.tripfit.trip.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 멤버십(soft delete 아님)만 요구 — ACTIVE·canEnterRoom 불필요. SCHEDULE_PENDING 방장도 허용.
// 방 안 콘텐츠를 노출하지 않는 개인 설정용(Pin 등). 상세·달력 등 방 입장 API는 @TripMemberOnly(ACTIVE) 경로.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TripMembershipOnly {
}
