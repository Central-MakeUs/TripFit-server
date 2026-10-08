package com.tripfit.tripfit.trip.membership.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripfit.tripfit.common.config.TestcontainersConfig;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.trip.service.TripService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.service.UserWithdrawalService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

// 여행방의 참여 인원(joined_member_count)과 버전 번호가 실제 MySQL에서 동시 요청을 견디는지 확인한다.
// 작업별 결과는 "ok", 에러 코드 이름, 또는 예외 클래스 이름으로 모은다. 데드락처럼 예상에 없는 예외는
// 허용 목록에 없는 이름으로 남아 테스트를 실패시킨다.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class TripSeatOptimisticLockIntegrationTest {

  private static final String OK = "ok";

  private static final String FULL = "TRIP_MEMBER_FULL";

  // 다시 시도하고도 버전 충돌이 남은 경우다. API에서는 409로 나가는 정상적인 결과다.
  private static final String RETRIES_EXHAUSTED = "CONCURRENT_MODIFICATION";

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

  @Test
  void createTrip_startsWithOwnerOccupyingOneSeat() {
    Trip trip = createTrip(createUser("owner"), 3);

    assertThat(trip.getJoinedMemberCount()).isEqualTo(1);
    assertThat(liveMemberCount(trip.getId())).isEqualTo(1);
  }

  @Test
  void joinRemoveRejoin_keepsJoinedMemberCountEqualToLiveMembers() {
    User owner = createUser("owner");
    Trip trip = createTrip(owner, 3);
    User first = createUser("first");
    User second = createUser("second");
    User latecomer = createUser("latecomer");

    join(trip, first);
    join(trip, second);
    assertThat(outcomeOf(() -> join(trip, latecomer))).isEqualTo(FULL);

    tripService.removeMember(trip.getId(), owner.getId(), first.getId());
    assertThat(reload(trip).getJoinedMemberCount()).isEqualTo(2);

    join(trip, latecomer);

    assertThat(reload(trip).getJoinedMemberCount()).isEqualTo(3);
    assertThat(liveMemberCount(trip.getId())).isEqualTo(3);
  }

  @Test
  void activate_updatesLastActivityInDatabaseWithoutBumpingVersion() {
    Trip trip = createTrip(createUser("owner"), 3);
    User member = createUser("member");
    join(trip, member);
    Trip beforeActivate = reload(trip);

    tripService.activateMembership(trip.getId(), member.getId());

    // 일정 확인 완료는 활동 시각만 직접 갱신한다. 버전 번호를 올리면 같은 순간의 참여나 방 수정을 실패시킨다.
    Trip afterActivate = reload(trip);
    assertThat(afterActivate.getLastActivityAt()).isAfter(beforeActivate.getLastActivityAt());
    assertThat(afterActivate.getVersion()).isEqualTo(beforeActivate.getVersion());
  }

  @Test
  void sameUserJoiningConcurrently_occupiesSingleSeat() throws Exception {
    Trip trip = createTrip(createUser("owner"), 5);
    User member = createUser("double-tap");

    Map<String, Integer> outcomes =
        runConcurrently(
            IntStream.range(0, 4)
                .mapToObj(index -> (Runnable) () -> join(trip, member))
                .toList());

    assertThat(outcomes).containsOnlyKeys(OK);
    assertThat(liveMemberCount(trip.getId())).isEqualTo(2);
    assertThat(reload(trip).getJoinedMemberCount()).isEqualTo(2);
  }

  @Test
  void sixUsersJoiningAtOnce_allSucceedWithinRetryBudget() throws Exception {
    Trip trip = createTrip(createUser("owner"), 10);
    List<User> joiners = createUsers("six", 6);

    Map<String, Integer> outcomes =
        runConcurrently(
            joiners.stream().map(user -> (Runnable) () -> join(trip, user)).toList());

    // 한 요청이 밀려나려면 그때마다 다른 요청 하나가 먼저 저장에 성공해야 한다. 경쟁자가 다섯이면 최대
    // 다섯 번 밀리므로, 다시 시도 5회 안에 반드시 들어간다.
    assertThat(outcomes).containsOnlyKeys(OK);
    assertThat(liveMemberCount(trip.getId())).isEqualTo(7);
    assertThat(reload(trip).getJoinedMemberCount()).isEqualTo(7);
  }

  @Test
  void nineUsersJoiningTenSeatTripAtOnce_neverOverbooksOrDeadlocks() throws Exception {
    Trip trip = createTrip(createUser("owner"), 10);
    List<User> joiners = createUsers("burst", 9);

    Map<String, Integer> outcomes =
        runConcurrently(
            joiners.stream().map(user -> (Runnable) () -> join(trip, user)).toList());

    // 아홉 명이 한 행을 두고 다투면 이론상 한 요청이 여덟 번까지 밀릴 수 있어, 느린 환경에서는 다시 시도를
    // 다 쓰는 요청이 나올 수 있다. 그 경우에도 데드락이 없고 참여 인원이 실제 멤버 수와 맞아야 한다.
    assertThat(outcomes.keySet()).isSubsetOf(OK, RETRIES_EXHAUSTED);
    int joined = outcomes.getOrDefault(OK, 0);
    assertThat(liveMemberCount(trip.getId())).isEqualTo(1 + joined);
    assertThat(reload(trip).getJoinedMemberCount()).isEqualTo(1 + joined);
  }

  @Test
  void joinLeaveActivatePatchAtOnce_neverDeadlocksAndKeepsCountConsistent() throws Exception {
    for (int round = 0; round < 10; round++) {
      User owner = createUser("owner");
      Trip trip = createTrip(owner, 8);
      UUID tripId = trip.getId();
      List<User> members = createUsers("member", 4);
      members.forEach(user -> join(trip, user));
      List<User> joiners = createUsers("joiner", 6);

      List<Runnable> tasks = new ArrayList<>();
      joiners.forEach(user -> tasks.add(() -> join(trip, user)));
      tasks.add(() -> tripService.leaveTrip(tripId, members.get(0).getId()));
      tasks.add(() -> tripService.leaveTrip(tripId, members.get(1).getId()));
      tasks.add(() -> tripService.activateMembership(tripId, members.get(2).getId()));
      tasks.add(() -> tripService.activateMembership(tripId, members.get(3).getId()));
      tasks.add(
          () -> tripService.patchTrip(
              tripId,
              owner.getId(),
              new PatchTripRequest("수정된이름", 3, 4, 8, null)));

      Map<String, Integer> outcomes = runConcurrently(tasks);

      assertThat(outcomes.keySet()).isSubsetOf(OK, FULL, RETRIES_EXHAUSTED);
      Trip after = reload(trip);
      assertThat(after.getJoinedMemberCount()).isEqualTo(liveMemberCount(tripId));
      assertThat(after.getJoinedMemberCount()).isLessThanOrEqualTo(8);
    }
  }

  @Test
  void kickAndActivateOnSameMemberAtOnce_neverDeadlocksAndKeepsCountConsistent()
      throws Exception {
    for (int round = 0; round < 20; round++) {
      User owner = createUser("owner");
      Trip trip = createTrip(owner, 4);
      UUID tripId = trip.getId();
      User member = createUser("member");
      join(trip, member);

      Map<String, Integer> outcomes =
          runConcurrently(
              List.of(
                  () -> tripService.removeMember(tripId, owner.getId(), member.getId()),
                  () -> tripService.activateMembership(tripId, member.getId())));

      // 내보내기가 먼저 끝나면 일정 확인 완료는 멤버가 아니라는 이유로 거절된다.
      assertThat(outcomes.keySet()).isSubsetOf(OK, "TRIP_ACCESS_DENIED");
      assertThat(liveMemberCount(tripId)).isEqualTo(1);
      assertThat(reload(trip).getJoinedMemberCount()).isEqualTo(1);
    }
  }

  @Test
  void deleteRacingWithJoins_leavesNoLiveMemberBehind() throws Exception {
    User owner = createUser("owner");
    Trip trip = createTrip(owner, 8);
    UUID tripId = trip.getId();
    List<User> joiners = createUsers("joiner", 5);

    List<Runnable> tasks = new ArrayList<>();
    joiners.forEach(user -> tasks.add(() -> join(trip, user)));
    tasks.add(() -> tripService.deleteTrip(tripId, owner.getId()));

    Map<String, Integer> outcomes = runConcurrently(tasks);

    // 삭제보다 먼저 들어온 사람은 삭제와 함께 정리되고, 삭제 뒤에 들어온 사람은 초대 코드를 찾지 못한다.
    assertThat(outcomes.keySet()).isSubsetOf(OK, "INVITE_CODE_NOT_FOUND");
    assertThat(reload(trip).getDeletedAt()).isNotNull();
    assertThat(liveMemberCount(tripId)).isZero();
  }

  @Test
  void deleteRacingWithLeaveKickOrActivate_neverDeadlocks() throws Exception {
    for (int round = 0; round < 20; round++) {
      User owner = createUser("owner");
      Trip trip = createTrip(owner, 6);
      UUID tripId = trip.getId();
      List<User> members = createUsers("member", 3);
      members.forEach(user -> join(trip, user));

      Map<String, Integer> outcomes =
          runConcurrently(
              List.of(
                  () -> tripService.deleteTrip(tripId, owner.getId()),
                  () -> tripService.leaveTrip(tripId, members.get(0).getId()),
                  () -> tripService.removeMember(tripId, owner.getId(), members.get(1).getId()),
                  () -> tripService.activateMembership(tripId, members.get(2).getId())));

      // 삭제가 먼저 끝나면 나머지는 방이나 멤버를 찾지 못해 거절된다. 어느 쪽이든 데드락은 없어야 한다.
      assertThat(outcomes.keySet())
          .isSubsetOf(OK, "TRIP_NOT_FOUND", "TRIP_ACCESS_DENIED", "TRIP_MEMBER_NOT_FOUND");
      assertThat(reload(trip).getDeletedAt()).isNotNull();
      assertThat(liveMemberCount(tripId)).isZero();
    }
  }

  @Test
  void withdrawalRacingWithJoins_completesAndKeepsCountConsistent() throws Exception {
    for (int round = 0; round < 5; round++) {
      User leaver = createUser("leaver");
      Trip joinedTrip = createTrip(createUser("owner"), 8);
      join(joinedTrip, leaver);
      Trip ownedTrip = createTrip(leaver, 8);
      List<User> joinersOfJoinedTrip = createUsers("joiner-a", 2);
      List<User> joinersOfOwnedTrip = createUsers("joiner-b", 2);

      List<Runnable> tasks = new ArrayList<>();
      joinersOfJoinedTrip.forEach(user -> tasks.add(() -> join(joinedTrip, user)));
      joinersOfOwnedTrip.forEach(user -> tasks.add(() -> join(ownedTrip, user)));
      tasks.add(() -> userWithdrawalService.withdraw(leaver.getId()));

      Map<String, Integer> outcomes = runConcurrently(tasks);

      // 탈퇴가 밀려나는 것은 두 방에 걸친 참여 네 건이 먼저 저장될 때뿐이라, 다시 시도 5회 안에 반드시 끝난다.
      assertThat(outcomes.keySet()).isSubsetOf(OK, "INVITE_CODE_NOT_FOUND");
      assertThat(userRepository.findById(leaver.getId()).orElseThrow().getDeletedAt()).isNotNull();
      assertThat(
          tripMemberRepository.findByUser_IdAndRoleAndDeletedAtIsNull(
              leaver.getId(),
              TripMemberRole.MEMBER))
          .isEmpty();
      assertThat(reload(ownedTrip).getDeletedAt()).isNotNull();
      assertThat(liveMemberCount(ownedTrip.getId())).isZero();
      assertThat(reload(joinedTrip).getJoinedMemberCount())
          .isEqualTo(liveMemberCount(joinedTrip.getId()));
    }
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
                    "낙관적 락 테스트",
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

  private int liveMemberCount(UUID tripId) {
    return tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId).size();
  }

  private List<User> createUsers(String prefix, int count) {
    return IntStream.range(0, count).mapToObj(index -> createUser(prefix)).toList();
  }

  private User createUser(String prefix) {
    String subject = prefix + "-" + UUID.randomUUID();
    User user = new User(subject, SocialProvider.GOOGLE, subject + "@example.com", "닉", null);
    user.applyProfilePatch("철수", "김", null);
    user.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
    return userRepository.save(user);
  }

  // 모든 작업을 같은 순간에 출발시키고 작업별 결과를 센다.
  private Map<String, Integer> runConcurrently(List<Runnable> tasks) throws Exception {
    Map<String, Integer> outcomes = new ConcurrentHashMap<>();
    CyclicBarrier startLine = new CyclicBarrier(tasks.size());
    ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
    try {
      List<Callable<Void>> gated = new ArrayList<>();
      for (Runnable task : tasks) {
        gated.add(
            () -> {
              startLine.await(30, TimeUnit.SECONDS);
              outcomes.merge(outcomeOf(task), 1, Integer::sum);
              return null;
            });
      }
      for (Future<Void> result : pool.invokeAll(gated, 120, TimeUnit.SECONDS)) {
        assertThat(result.isCancelled()).as("작업이 제한 시간 안에 끝나야 한다").isFalse();
        result.get();
      }
    } finally {
      pool.shutdownNow();
    }
    return outcomes;
  }

  private String outcomeOf(Runnable task) {
    try {
      task.run();
      return OK;
    } catch (TripFitException exception) {
      return exception.getErrorCode().getCode();
    } catch (OptimisticLockingFailureException exception) {
      return RETRIES_EXHAUSTED;
    } catch (RuntimeException exception) {
      return exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }
  }
}
