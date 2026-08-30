package com.tripfit.tripfit.user.googlecalendar.scheduler;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// shouldSkipThisCycle()의 해시 지터 슬롯 분산과, 한 유저의 syncUser() 실패가 다음 유저 처리를 막지 않는지 검증한다.
// JITTER_SLOT_COUNT·SYNC_INTERVAL_MS는 스케줄러 내부 private 상수와 값을 맞춰 테스트에서 동일 슬롯 계산에 사용한다
@ExtendWith(MockitoExtension.class)
class GoogleCalendarSyncSchedulerTest {

  private static final int JITTER_SLOT_COUNT = 6;

  private static final long SYNC_INTERVAL_MS = 30 * 60 * 1000L;

  @Mock
  private UserRepository userRepository;

  @Mock
  private GoogleCalendarService googleCalendarService;

  @InjectMocks
  private GoogleCalendarSyncScheduler scheduler;

  @Test
  void syncConnectedUsers_onlySyncsUsersInCurrentJitterSlot() {
    int currentSlot = currentJitterSlot();
    UUID inSlotUserId = userIdForSlot(currentSlot, 0);
    UUID otherSlotUserId = userIdForSlot(Math.floorMod(currentSlot + 1, JITTER_SLOT_COUNT), 0);
    User inSlotUser = userWithId(inSlotUserId);
    User otherSlotUser = userWithId(otherSlotUserId);
    when(userRepository.findByIsGoogleCalendarConnectedTrue())
        .thenReturn(List.of(inSlotUser, otherSlotUser));

    scheduler.syncConnectedUsers();

    verify(googleCalendarService).syncUser(inSlotUserId);
    verify(googleCalendarService, never()).syncUser(otherSlotUserId);
  }

  @Test
  void syncConnectedUsers_whenOneUserSyncThrows_stillSyncsNextUser() {
    int currentSlot = currentJitterSlot();
    UUID failingUserId = userIdForSlot(currentSlot, 0);
    UUID nextUserId = userIdForSlot(currentSlot, 1);
    User failingUser = userWithId(failingUserId);
    User nextUser = userWithId(nextUserId);
    when(userRepository.findByIsGoogleCalendarConnectedTrue())
        .thenReturn(List.of(failingUser, nextUser));
    doThrow(new IllegalStateException("unexpected"))
        .when(googleCalendarService)
        .syncUser(failingUserId);

    scheduler.syncConnectedUsers();

    verify(googleCalendarService).syncUser(eq(nextUserId));
  }

  private static User userWithId(UUID userId) {
    User user = mock(User.class);
    when(user.getId()).thenReturn(userId);
    return user;
  }

  private static int currentJitterSlot() {
    long cycle = System.currentTimeMillis() / SYNC_INTERVAL_MS;
    return Math.floorMod(cycle, JITTER_SLOT_COUNT);
  }

  // 스케줄러의 shouldSkipThisCycle()과 동일한 공식(userId.hashCode() mod JITTER_SLOT_COUNT)으로 targetSlot에
  // 속하는 UUID를 찾아낸다 — seed를 늘려가며 순서대로 index번째 매치를 반환(서로 다른 UUID가 필요할 때 사용)
  private static UUID userIdForSlot(int targetSlot, int index) {
    int found = 0;
    for (long seed = 0; seed < 100_000; seed++) {
      UUID candidate = new UUID(0L, seed);
      if (Math.floorMod(candidate.hashCode(), JITTER_SLOT_COUNT) == targetSlot) {
        if (found == index) {
          return candidate;
        }
        found++;
      }
    }
    throw new IllegalStateException("no UUID found for slot " + targetSlot + " index " + index);
  }
}
