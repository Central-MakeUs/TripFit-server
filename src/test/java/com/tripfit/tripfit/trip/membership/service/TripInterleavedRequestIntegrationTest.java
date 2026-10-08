package com.tripfit.tripfit.trip.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.tripfit.tripfit.common.config.TestcontainersConfig;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.event.TripJoinCompletedEvent;
import com.tripfit.tripfit.trip.membership.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.trip.scheduler.TripHomeScheduler;
import com.tripfit.tripfit.trip.service.TripHomeMaintenanceService;
import com.tripfit.tripfit.trip.service.TripService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.service.UserWithdrawalPersistenceService;
import com.tripfit.tripfit.user.service.UserWithdrawalService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 두 요청이 겹치는 순서를 정확히 고정해서, 한 요청이 다른 요청의 변경을 되돌리거나 참여 인원을 어긋나게
// 만들지 않는지 확인한다. 무작위 동시 실행으로는 좀처럼 걸리지 않는 좁은 틈을 매번 재현하기 위한 테스트다.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class TripInterleavedRequestIntegrationTest {

  @TestConfiguration
  static class Hooks {

    @Bean
    ActivationHook activationHook() {
      return new ActivationHook();
    }
  }

  // 일정 확인 완료 트랜잭션이 여행방과 멤버를 읽은 뒤, 저장하기 전인 시점에 다른 요청을 끼워 넣는다.
  // 참여 완료 이벤트가 그 시점에 같은 스레드에서 발행되는 것을 이용한다.
  static class ActivationHook {

    private volatile Runnable action;

    void runDuringNextActivation(Runnable action) {
      this.action = action;
    }

    @EventListener
    public void onJoinCompleted(TripJoinCompletedEvent event) {
      Runnable current = action;
      action = null;
      if (current != null) {
        CompletableFuture.runAsync(current).join();
      }
    }
  }

  @Autowired
  private ActivationHook activationHook;

  @Autowired
  private TripService tripService;

  @Autowired
  private TripRepository tripRepository;

  @Autowired
  private TripMemberRepository tripMemberRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private UserWithdrawalService userWithdrawalService;

  @Autowired
  private TripHomeScheduler tripHomeScheduler;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @MockitoSpyBean
  private UserWithdrawalPersistenceService userWithdrawalPersistenceService;

  @MockitoSpyBean
  private TripHomeMaintenanceService tripHomeMaintenanceService;

  @Test
  void patchCommittedDuringActivation_isNotRevertedByActivityTouch() {
    User owner = createUser("owner");
    Trip trip = createTrip(owner, 3);
    User member = createUser("member");
    join(trip, member);
    activationHook.runDuringNextActivation(
        () -> tripService.patchTrip(
            trip.getId(),
            owner.getId(),
            new PatchTripRequest("수정된이름", 3, 4, 7, null)));

    tripService.activateMembership(trip.getId(), member.getId());

    Trip after = reload(trip);
    assertThat(after.getName()).isEqualTo("수정된이름");
    assertThat(after.getMemberCount()).isEqualTo(7);
  }

  @Test
  void kickCommittedDuringActivation_keepsMemberRemovedAndCountConsistent() {
    User owner = createUser("owner");
    Trip trip = createTrip(owner, 3);
    User member = createUser("member");
    join(trip, member);
    activationHook.runDuringNextActivation(
        () -> tripService.removeMember(trip.getId(), owner.getId(), member.getId()));

    tripService.activateMembership(trip.getId(), member.getId());

    // 내보낸 멤버가 일정 확인 완료의 저장으로 되살아나면, 참여 인원은 이미 줄었는데 멤버는 남아 정원이 뚫린다.
    assertThat(liveMemberCount(trip)).isEqualTo(1);
    assertThat(reload(trip).getJoinedMemberCount()).isEqualTo(1);
  }

  @Test
  void leaveCommittedDuringActivation_keepsMemberRemovedAndCountConsistent() {
    Trip trip = createTrip(createUser("owner"), 3);
    User member = createUser("member");
    join(trip, member);
    activationHook.runDuringNextActivation(
        () -> tripService.leaveTrip(trip.getId(), member.getId()));

    tripService.activateMembership(trip.getId(), member.getId());

    assertThat(liveMemberCount(trip)).isEqualTo(1);
    assertThat(reload(trip).getJoinedMemberCount()).isEqualTo(1);
  }

  @Test
  void seatUpdate_doesNotRevertActivityTouchCommittedAfterTripWasRead() {
    Trip trip = createTrip(createUser("owner"), 3);
    UUID tripId = trip.getId();
    LocalDateTime touchedAt = LocalDateTime.of(2030, 1, 1, 0, 0);
    TransactionTemplate tx = new TransactionTemplate(transactionManager);

    // 참여 요청이 여행방을 읽어 둔 사이 다른 멤버의 활동 시각 갱신이 커밋된다. 참여 요청이 인원을 저장할 때
    // 읽어 둔 옛 활동 시각까지 함께 써서 그 갱신을 되돌리면 안 된다.
    tx.executeWithoutResult(
        status -> {
          Trip loaded = tripRepository.findByIdAndDeletedAtIsNull(tripId).orElseThrow();
          CompletableFuture.runAsync(
              () -> tx.executeWithoutResult(
                  inner -> tripRepository.touchLastActivity(tripId, touchedAt)))
              .join();
          loaded.tryOccupySeat();
        });

    Trip after = reload(trip);
    assertThat(after.getJoinedMemberCount()).isEqualTo(2);
    assertThat(after.getLastActivityAt()).isEqualTo(touchedAt);
  }

  @Test
  void withdrawal_whenVersionConflictHitsOnce_retriesInNewTransactionAndCompletes() {
    User leaver = createUser("leaver");
    Trip trip = createTrip(createUser("owner"), 3);
    join(trip, leaver);
    doThrow(new ObjectOptimisticLockingFailureException(Trip.class, trip.getId()))
        .doCallRealMethod()
        .when(userWithdrawalPersistenceService)
        .finalizeWithdrawal(leaver.getId());

    userWithdrawalService.withdraw(leaver.getId());

    verify(userWithdrawalPersistenceService, times(2)).finalizeWithdrawal(leaver.getId());
    assertThat(userRepository.findById(leaver.getId()).orElseThrow().getDeletedAt()).isNotNull();
    assertThat(reload(trip).getJoinedMemberCount()).isEqualTo(1);
    assertThat(liveMemberCount(trip)).isEqualTo(1);
  }

  @Test
  void dailyMaintenance_whenVersionConflictHitsOnce_retries() {
    doThrow(new ObjectOptimisticLockingFailureException(Trip.class, "trip"))
        .doCallRealMethod()
        .when(tripHomeMaintenanceService)
        .runForDate(any());

    tripHomeScheduler.runDailyMaintenance();

    verify(tripHomeMaintenanceService, times(2)).runForDate(any());
  }

  private void join(Trip trip, User user) {
    tripService.joinTrip(user.getId(), new JoinTripRequest(trip.getInviteCode()));
  }

  private Trip createTrip(User owner, int memberCount) {
    UUID tripId =
        tripService
            .createTrip(
                owner.getId(),
                new CreateTripRequest(
                    "끼어들기 테스트",
                    LocalDate.now().plusDays(7),
                    LocalDate.now().plusDays(30),
                    3,
                    4,
                    memberCount,
                    null))
            .tripId();
    return tripRepository.findById(tripId).orElseThrow();
  }

  private Trip reload(Trip trip) {
    return tripRepository.findById(trip.getId()).orElseThrow();
  }

  private int liveMemberCount(Trip trip) {
    return tripMemberRepository.findByTripIdAndDeletedAtIsNull(trip.getId()).size();
  }

  private User createUser(String prefix) {
    String subject = prefix + "-" + UUID.randomUUID();
    User user = new User(subject, SocialProvider.GOOGLE, subject + "@example.com", "닉", null);
    user.applyProfilePatch("철수", "김", null);
    user.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
    return userRepository.save(user);
  }
}
