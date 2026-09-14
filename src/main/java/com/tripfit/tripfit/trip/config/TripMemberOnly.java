package com.tripfit.tripfit.trip.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 방 입장 API용: 멤버 + ACTIVE.
// SCHEDULE_PENDING(방장·참여자 모두 activate 전) → SCHEDULE_ACTIVATION_REQUIRED. 초대 공유 데이터도 이 게이트 뒤(상세).
// 방 입장과 무관한 개인 설정(Pin 등)은 @TripMembershipOnly.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TripMemberOnly {
}
