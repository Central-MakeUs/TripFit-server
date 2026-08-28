package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.user.domain.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class TripDisplayNameHelper {

  private TripDisplayNameHelper() {}

  public static Map<UUID, String> assignDisplayNames(List<User> usersInOrder) {
    return assignDisplayNames(usersInOrder, User::displayName);
  }

  public static Map<UUID, String> assignPreviewDisplayNames(List<User> usersInOrder) {
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
