package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.schedule.service.ScheduleService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

// 동명이인 표시명 — 첫 사람은 원명, 이후 `홍길동(2)` 형식
final class TripDisplayNameHelper {

  private TripDisplayNameHelper() {}

  static Map<UUID, String> assignDisplayNames(List<User> usersInOrder) {
    return assignDisplayNames(usersInOrder, ScheduleService::displayName);
  }

  // 홈 카드·상세 membersPreview 아바타 전용 — 성 없이 이름만("길동") 노출. 나머지 폴백(닉네임·"사용자")·동명이인 접미사는
  // assignDisplayNames와 동일
  static Map<UUID, String> assignPreviewDisplayNames(List<User> usersInOrder) {
    return assignDisplayNames(usersInOrder, TripDisplayNameHelper::previewBaseName);
  }

  private static String previewBaseName(User user) {
    if (user.hasProfileNameComplete()) {
      return user.getFirstName();
    }
    if (user.getNickname() != null && !user.getNickname().isBlank()) {
      return user.getNickname();
    }
    return "사용자";
  }

  private static Map<UUID, String> assignDisplayNames(
      List<User> usersInOrder,
      Function<User, String> baseNameFn) {
    Map<String, Integer> seen = new HashMap<>();
    Map<UUID, String> result = new HashMap<>();
    for (User user : usersInOrder) {
      String base = baseNameFn.apply(user);
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
