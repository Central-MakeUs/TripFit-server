package com.tripfit.tripfit.trip.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 방 입장 API용: 멤버 + RESPONDED + canEnterRoom.
// JOINED(방장 confirm 전) → SCHEDULE_CONFIRM_REQUIRED. 초대 공유 데이터도 이 게이트 뒤(상세).
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TripMemberOnly {
}
