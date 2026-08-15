package com.tripfit.tripfit.user.googlecalendar.scheduler;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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

// 연동 유저 전원이 매 사이클 빠짐없이 동기화되는지(그룹 순번 분산 제거 후)와, 한 유저의 syncUser() 실패가
// 다음 유저 처리를 막지 않는지 검증한다.
@ExtendWith(MockitoExtension.class)
class GoogleCalendarSyncSchedulerTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private GoogleCalendarService googleCalendarService;

  @InjectMocks
  private GoogleCalendarSyncScheduler scheduler;

  @Test
  void syncConnectedUsers_syncsEveryConnectedUserEveryCycle() {
    UUID firstUserId = UUID.randomUUID();
    UUID secondUserId = UUID.randomUUID();
    User firstUser = userWithId(firstUserId);
    User secondUser = userWithId(secondUserId);
    when(userRepository.findByIsGoogleCalendarConnectedTrue())
        .thenReturn(List.of(firstUser, secondUser));

    scheduler.syncConnectedUsers();

    verify(googleCalendarService).syncUser(firstUserId);
    verify(googleCalendarService).syncUser(secondUserId);
  }

  @Test
  void syncConnectedUsers_whenOneUserSyncThrows_stillSyncsNextUser() {
    UUID failingUserId = UUID.randomUUID();
    UUID nextUserId = UUID.randomUUID();
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
}
