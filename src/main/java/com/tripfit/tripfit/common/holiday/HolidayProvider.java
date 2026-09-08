package com.tripfit.tripfit.common.holiday;

import java.time.LocalDate;
import java.util.Set;

// 대한민국 관공서 공휴일(대체공휴일 포함) 조회 포트 — 정기 일정의 "공휴일 휴무" 판정에 쓰인다.
//
// 구현체는 외부 API를 매번 호출하지 않고 캐시에서 읽는다(갱신은 HolidaySyncScheduler 담당). 조회에 실패하면
// 예외를 던지지 않고 빈 집합을 돌려주는 fail-open이 계약이다 — 공휴일을 몰라서 생기는 오차보다, 달력·추천
// 전체가 막히는 쪽이 손해가 크기 때문(RedisTokenRevocationChecker와 동일한 판단).
public interface HolidayProvider {

  Set<LocalDate> findHolidaysBetween(LocalDate startDate, LocalDate endDate);
}
