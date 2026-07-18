package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.schedule.service.ScheduleService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 동명이인 표시명 — 첫 사람은 원명, 이후 `홍길동(2)` 형식
final class TripDisplayNameHelper {

  private TripDisplayNameHelper() {}

  static Map<UUID, String> assignDisplayNames(List<User> usersInOrder) {
    Map<String, Integer> seen = new HashMap<>();
    Map<UUID, String> result = new HashMap<>();
    for (User user : usersInOrder) {
      String base = ScheduleService.displayName(user);
      int count = seen.merge(base, 1, Integer::sum);
      if (count == 1) {
        result.put(user.getId(), base);
      } else {
        result.put(user.getId(), base + "(" + count + ")");
      }
    }
    return result;
  }
}
