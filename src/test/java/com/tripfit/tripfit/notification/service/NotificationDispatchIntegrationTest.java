package com.tripfit.tripfit.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.tripfit.tripfit.common.config.TestcontainersConfig;
import com.tripfit.tripfit.notification.domain.DeviceType;
import com.tripfit.tripfit.notification.domain.UserDeviceToken;
import com.tripfit.tripfit.notification.repository.NotificationHistoryRepository;
import com.tripfit.tripfit.notification.repository.UserDeviceTokenRepository;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.event.TripInfoChangedEvent;
import com.tripfit.tripfit.trip.membership.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.trip.service.TripService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

// 알림 이력은 원래 트랜잭션과 함께 저장되고, FCM 발송은 커밋 후 트랜잭션 밖에서 실행되는지 실제 MySQL로 확인한다.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class NotificationDispatchIntegrationTest {

  @MockitoBean
  private FirebaseMessaging firebaseMessaging;

  @Autowired
  private TripService tripService;

  @Autowired
  private TripRepository tripRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private UserDeviceTokenRepository userDeviceTokenRepository;

  @Autowired
  private NotificationHistoryRepository notificationHistoryRepository;

  @Autowired
  private ApplicationEventPublisher applicationEventPublisher;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private DataSource dataSource;

  @Test
  void tripChange_savesHistoryInSameTransaction_andPushesAfterCommit() {
    when(firebaseMessaging.sendEachAsync(anyList()))
        .thenReturn(ApiFutures.immediateFuture(batch(List.of())));
    Room room = createRoom();

    tripService.patchTrip(room.tripId(), room.ownerId(), patchRequest("정보 변경"));

    assertThat(historyCountOf(room.memberId())).isEqualTo(1);
    verify(firebaseMessaging, timeout(5_000)).sendEachAsync(anyList());
  }

  @Test
  void rolledBackTransaction_leavesNoHistoryAndSendsNoPush() {
    Room room = createRoom();

    transactionTemplate.executeWithoutResult(
        status -> {
          applicationEventPublisher.publishEvent(new TripInfoChangedEvent(room.tripId()));
          status.setRollbackOnly();
        });

    assertThat(historyCountOf(room.memberId())).isZero();
    verify(firebaseMessaging, after(500).never()).sendEachAsync(anyList());
  }

  // FCM이 응답하지 않아 발송 작업자 4개가 모두 기다리는 동안에도 DB 커넥션은 하나도 쥐지 않는다.
  // 원래 작업은 바로 끝나고 이력도 남는다.
  @Test
  void fcmHang_holdsNoDatabaseConnection() {
    List<SettableApiFuture<BatchResponse>> pending = new CopyOnWriteArrayList<>();
    when(firebaseMessaging.sendEachAsync(anyList())).thenAnswer(invocation -> {
      SettableApiFuture<BatchResponse> future = SettableApiFuture.create();
      pending.add(future);
      return future;
    });
    try {
      for (int i = 0; i < 6; i++) {
        Room room = createRoom();
        tripService.patchTrip(room.tripId(), room.ownerId(), patchRequest("멈춤 " + i));
        assertThat(historyCountOf(room.memberId())).isEqualTo(1);
      }
      verify(firebaseMessaging, timeout(5_000).times(4)).sendEachAsync(anyList());

      HikariDataSource hikari = (HikariDataSource) dataSource;
      assertThat(hikari.getHikariPoolMXBean().getActiveConnections()).isZero();
    } finally {
      // 대기 중인 발송을 끝내 다른 테스트가 같은 작업자를 쓸 수 있게 한다.
      for (int i = 0; i < 20 && pending.size() < 6; i++) {
        pending.forEach(future -> future.set(batch(List.of())));
        sleepQuietly(100);
      }
      pending.forEach(future -> future.set(batch(List.of())));
    }
  }

  // FCM이 등록 해제된 토큰이라고 응답하면, 발송이 끝난 뒤 짧은 트랜잭션에서 그 토큰을 지운다.
  @Test
  void unregisteredToken_isDeletedAfterSend() {
    FirebaseMessagingException unregistered = mock(FirebaseMessagingException.class);
    when(unregistered.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
    SendResponse failed = mock(SendResponse.class);
    when(failed.isSuccessful()).thenReturn(false);
    when(failed.getException()).thenReturn(unregistered);
    when(firebaseMessaging.sendEachAsync(anyList()))
        .thenReturn(ApiFutures.immediateFuture(batch(List.of(failed))));
    Room room = createRoom();

    tripService.patchTrip(room.tripId(), room.ownerId(), patchRequest("토큰 정리"));

    verify(firebaseMessaging, timeout(5_000)).sendEachAsync(anyList());
    for (int i = 0; i < 50
        && userDeviceTokenRepository.findByToken(room.memberToken()).isPresent(); i++) {
      sleepQuietly(100);
    }
    assertThat(userDeviceTokenRepository.findByToken(room.memberToken())).isEmpty();
  }

  private long historyCountOf(UUID userId) {
    return notificationHistoryRepository.findAll().stream()
        .filter(history -> history.getUser().getId().equals(userId))
        .count();
  }

  // 방장(ACTIVE)과 멤버 1명(기기 토큰 1개)으로 된 방을 서비스 경로로 만든다.
  private Room createRoom() {
    User owner = createUser("owner");
    User member = createUser("member");
    String memberToken = "dispatch-token-" + UUID.randomUUID();
    userDeviceTokenRepository.save(new UserDeviceToken(member, memberToken, DeviceType.ANDROID));
    LocalDate startRange = LocalDate.now().plusDays(7);
    UUID tripId =
        tripService
            .createTrip(
                owner.getId(),
                new CreateTripRequest("알림 경로", startRange, startRange.plusDays(23), 3, 4, 3, null))
            .tripId();
    tripService.activateMembership(tripId, owner.getId());
    String inviteCode = tripRepository.findById(tripId).orElseThrow().getInviteCode();
    tripService.joinTrip(member.getId(), new JoinTripRequest(inviteCode));
    return new Room(tripId, owner.getId(), member.getId(), memberToken);
  }

  private User createUser(String label) {
    String subject = "dispatch-" + label + "-" + UUID.randomUUID();
    User user = new User(subject, SocialProvider.GOOGLE, subject + "@example.com", "nick", null);
    user.applyProfilePatch(label, "김", true);
    user.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
    return userRepository.save(user);
  }

  private static PatchTripRequest patchRequest(String name) {
    return new PatchTripRequest(name, 3, 4, 3, null);
  }

  private static BatchResponse batch(List<SendResponse> responses) {
    return new BatchResponse() {
      @Override
      public List<SendResponse> getResponses() {
        return responses;
      }

      @Override
      public int getSuccessCount() {
        return (int) responses.stream().filter(SendResponse::isSuccessful).count();
      }

      @Override
      public int getFailureCount() {
        return responses.size() - getSuccessCount();
      }
    };
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }

  private record Room(
      UUID tripId,
      UUID ownerId,
      UUID memberId,
      String memberToken
  ) {
  }
}
