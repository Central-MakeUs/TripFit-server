package com.tripfit.tripfit.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFutures;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.SendResponse;
import com.tripfit.tripfit.common.config.TestcontainersConfig;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.notification.domain.DeviceType;
import com.tripfit.tripfit.notification.domain.UserDeviceToken;
import com.tripfit.tripfit.notification.repository.UserDeviceTokenRepository;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.membership.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.recommendation.dto.ConfirmTripRequest;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.trip.service.TripService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.service.UserWithdrawalService;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 알림 이력을 원래 트랜잭션의 커밋 직전에 저장하면서 새 잠금 순서가 데드락을 만들지 않는지, 실제 MySQL에서 같은 방에
// 두 요청을 동시에 반복해 확인한다. 알림 이력 INSERT는 외래키 때문에 여행방·사용자 행에 공유 잠금을 건다.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class NotificationLockOrderIntegrationTest {

  private static final int REPETITIONS = 30;

  @MockitoBean
  private FirebaseMessaging firebaseMessaging;

  @Autowired
  private TripService tripService;

  @Autowired
  private UserWithdrawalService userWithdrawalService;

  @Autowired
  private TripRepository tripRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private UserDeviceTokenRepository userDeviceTokenRepository;

  @BeforeEach
  void respondImmediately() {
    when(firebaseMessaging.sendEachAsync(anyList()))
        .thenReturn(ApiFutures.immediateFuture(emptyBatch()));
  }

  // 두 멤버가 같은 방에서 동시에 일정 확인을 마친다. 둘 다 여행방 활동 시각을 고치고 방장에게 참여 완료 알림을 남긴다.
  @Test
  void concurrentActivations_inSameTrip_neverDeadlock() throws Exception {
    List<Throwable> failures = new ArrayList<>();
    for (int i = 0; i < REPETITIONS; i++) {
      Room room = createRoom(3, 2);
      failures.addAll(
          runConcurrently(
              () -> tripService.activateMembership(room.tripId(), room.members().get(0)),
              () -> tripService.activateMembership(room.tripId(), room.members().get(1))));
    }
    assertThat(failures).as(describe(failures)).isEmpty();
  }

  // 방장이 방 정보를 고치는 동안 멤버가 일정 확인을 마친다. 두 요청 모두 여행방 행을 고치고 서로에게 알림을 남긴다.
  @Test
  void patchAndActivation_inSameTrip_neverDeadlock() throws Exception {
    List<Throwable> failures = new ArrayList<>();
    for (int i = 0; i < REPETITIONS; i++) {
      Room room = createRoom(3, 2);
      int index = i;
      failures.addAll(
          runConcurrently(
              () -> tripService.patchTrip(room.tripId(), room.ownerId(), patchRequest(index)),
              () -> tripService.activateMembership(room.tripId(), room.members().get(0))));
    }
    assertThat(failures).as(describe(failures)).isEmpty();
  }

  // 방장이 방 정보를 고치는 동안 새 사용자가 남은 자리에 참여한다. 버전 번호로 정원을 지키는 코드에서는 이 조합이
  // 커밋 직전 flush에서 버전 충돌을 일으키므로, 그 충돌이 실패로 끝나지 않고 다시 시도되는지도 함께 확인된다.
  @Test
  void patchAndJoin_inSameTrip_bothSucceed() throws Exception {
    List<Throwable> failures = new ArrayList<>();
    for (int i = 0; i < REPETITIONS; i++) {
      Room room = createRoom(3, 1);
      int index = i;
      User joiner = createUser("joiner");
      String inviteCode = tripRepository.findById(room.tripId()).orElseThrow().getInviteCode();
      failures.addAll(
          runConcurrently(
              () -> tripService.patchTrip(room.tripId(), room.ownerId(), patchRequest(index)),
              () -> tripService.joinTrip(joiner.getId(), new JoinTripRequest(inviteCode))));
    }
    assertThat(failures).as(describe(failures)).isEmpty();
  }

  // 방장이 방 정보를 고치는 동안 알림을 받을 멤버가 탈퇴한다. 탈퇴는 여행방 행과 사용자 행을 모두 고친다.
  @Test
  void patchAndRecipientWithdrawal_neverDeadlock() throws Exception {
    List<Throwable> failures = new ArrayList<>();
    for (int i = 0; i < REPETITIONS; i++) {
      Room room = createRoom(3, 2);
      int index = i;
      failures.addAll(
          runConcurrently(
              () -> tripService.patchTrip(room.tripId(), room.ownerId(), patchRequest(index)),
              () -> userWithdrawalService.withdraw(room.members().get(0))));
    }
    assertThat(failures).as(describe(failures)).isEmpty();
  }

  // 방장이 일정을 확정하는 동안 알림을 받을 멤버가 탈퇴한다.
  @Test
  void confirmAndRecipientWithdrawal_neverDeadlock() throws Exception {
    List<Throwable> failures = new ArrayList<>();
    for (int i = 0; i < REPETITIONS; i++) {
      Room room = createRoom(3, 2);
      LocalDate start = LocalDate.now().plusDays(7);
      failures.addAll(
          runConcurrently(
              () -> tripService.confirmSchedule(
                  room.tripId(),
                  room.ownerId(),
                  new ConfirmTripRequest(null, start, start.plusDays(3))),
              () -> userWithdrawalService.withdraw(room.members().get(0))));
    }
    assertThat(failures).as(describe(failures)).isEmpty();
  }

  // 멤버가 일정 확인을 마치는 동안(방장에게 참여 완료 알림) 방장이 탈퇴한다. 탈퇴는 방장의 사용자 행과 여행방 행을 모두
  // 고치고, 알림 이력 INSERT는 방장 사용자 행에 공유 잠금을 건다. 방장 탈퇴가 먼저 끝나면 방이 사라져 일정 확인이
  // 업무 오류로 실패할 수 있으므로, 잠금 실패나 예상하지 못한 예외가 없는지만 본다.
  @Test
  void activationAndOwnerWithdrawal_neverDeadlock() throws Exception {
    List<Throwable> failures = new ArrayList<>();
    for (int i = 0; i < REPETITIONS; i++) {
      Room room = createRoom(3, 2);
      failures.addAll(
          runConcurrently(
              () -> tripService.activateMembership(room.tripId(), room.members().get(0)),
              () -> userWithdrawalService.withdraw(room.ownerId())));
    }
    List<Throwable> unexpected =
        failures.stream().filter(failure -> !(failure instanceof TripFitException)).toList();
    assertThat(unexpected).as(describe(unexpected)).isEmpty();
  }

  private List<Throwable> runConcurrently(Runnable first, Runnable second) throws Exception {
    CyclicBarrier startLine = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<Throwable> failures = new ArrayList<>();
    try {
      List<Future<Void>> results =
          pool.invokeAll(List.of(task(first, startLine), task(second, startLine)));
      for (Future<Void> result : results) {
        try {
          result.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
          failures.add(exception.getCause());
        }
      }
    } finally {
      pool.shutdownNow();
    }
    return failures;
  }

  private static Callable<Void> task(Runnable action, CyclicBarrier startLine) {
    return () -> {
      startLine.await(30, TimeUnit.SECONDS);
      action.run();
      return null;
    };
  }

  // 방장(ACTIVE) 1명과 일정 확인 전(SCHEDULE_PENDING) 멤버 여럿으로 된 방을 만든다. 멤버는 모두 기기 토큰이 있다.
  // 정원·참여 인원 같은 파생 값이 실제 경로와 같게 맞춰지도록 생성·입장·참여를 서비스로 거친다.
  private Room createRoom(int memberCount, int pendingMembers) {
    User owner = createUser("owner");
    LocalDate startRange = LocalDate.now().plusDays(7);
    UUID tripId =
        tripService
            .createTrip(
                owner.getId(),
                new CreateTripRequest(
                    "잠금 순서", startRange, startRange.plusDays(23), 3, 4, memberCount, null))
            .tripId();
    tripService.activateMembership(tripId, owner.getId());
    String inviteCode = tripRepository.findById(tripId).orElseThrow().getInviteCode();
    List<UUID> members = new ArrayList<>();
    for (int i = 0; i < pendingMembers; i++) {
      User member = createUser("member" + i);
      tripService.joinTrip(member.getId(), new JoinTripRequest(inviteCode));
      members.add(member.getId());
    }
    return new Room(tripId, owner.getId(), members);
  }

  private User createUser(String label) {
    String subject = "lock-order-" + label + "-" + UUID.randomUUID();
    User user = new User(subject, SocialProvider.GOOGLE, subject + "@example.com", "nick", null);
    user.applyProfilePatch(label, "김", true);
    user.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
    User saved = userRepository.save(user);
    userDeviceTokenRepository.save(new UserDeviceToken(saved, "token-" + subject, DeviceType.IOS));
    return saved;
  }

  private static PatchTripRequest patchRequest(int index) {
    return new PatchTripRequest("잠금 순서 " + index, 3, 4, 3, null);
  }

  private static String describe(List<Throwable> failures) {
    long lockFailures =
        failures.stream().filter(NotificationLockOrderIntegrationTest::isLockFailure).count();
    return "failures=" + failures.size() + " (lock/deadlock=" + lockFailures + "): "
        + failures.stream().limit(3).map(Throwable::toString).toList();
  }

  private static boolean isLockFailure(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && "40001".equals(sql.getSQLState())) {
        return true;
      }
      String name = cause.getClass().getSimpleName();
      if (name.contains("Deadlock") || name.contains("CannotAcquireLock")) {
        return true;
      }
    }
    return false;
  }

  private static BatchResponse emptyBatch() {
    return new BatchResponse() {
      @Override
      public List<SendResponse> getResponses() {
        return List.of();
      }

      @Override
      public int getSuccessCount() {
        return 0;
      }

      @Override
      public int getFailureCount() {
        return 0;
      }
    };
  }

  private record Room(
      UUID tripId,
      UUID ownerId,
      List<UUID> members
  ) {
  }
}
