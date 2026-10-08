package com.tripfit.tripfit.trip.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripfit.tripfit.common.retry.RetryOnVersionConflict;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TripServiceRetryAnnotationTest {

  // 여행방 행을 엔티티로 고치는 유스케이스다. 버전 번호는 행 전체에 걸리므로, 이 중 하나라도 재시도가 빠지면
  // 다른 사람의 참여와 겹쳤을 때 곧바로 409가 나간다.
  private static final Set<String> TRIP_WRITERS =
      Set.of(
          "joinTrip",
          "patchTrip",
          "deleteTrip",
          "removeMember",
          "leaveTrip",
          "generateRecommendations",
          "confirmSchedule",
          "unconfirm");

  @Test
  void everyTripWritingUseCase_retriesOnVersionConflict() {
    Set<String> retried =
        Arrays.stream(TripService.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(RetryOnVersionConflict.class))
            .map(Method::getName)
            .collect(Collectors.toSet());

    assertThat(retried).containsExactlyInAnyOrderElementsOf(TRIP_WRITERS);
  }
}
