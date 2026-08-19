package com.tripfit.tripfit.trip.membership.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripfit.tripfit.common.config.TestcontainersConfig;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.membership.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.trip.service.TripService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// 정원 보장은 Redis hold가 아니라 trip 행 비관적 락이 담당한다 — 단위 테스트로는 잡히지 않는 성질이라
// 실제 MySQL(Testcontainers)에서 여러 스레드가 마지막 자리를 동시에 요청하게 만들어 검증한다
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class TripJoinConcurrencyIntegrationTest {

  private static final int MEMBER_COUNT = 3;

  private static final int CONCURRENT_JOINS = 8;

  @Autowired
  private TripService tripService;

  @Autowired
  private TripRepository tripRepository;

  @Autowired
  private TripMemberRepository tripMemberRepository;

  @Autowired
  private UserRepository userRepository;

  @Test
  void concurrentJoins_neverExceedMemberCount() throws Exception {
    Trip trip = createTripWithOwner();
    List<User> candidates = createUsers(CONCURRENT_JOINS);

    AtomicInteger joined = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();
    // 모든 스레드를 같은 순간에 출발시켜야 카운트-INSERT 사이의 레이스가 실제로 재현된다
    CyclicBarrier startLine = new CyclicBarrier(CONCURRENT_JOINS);
    ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_JOINS);
    try {
      List<Future<Void>> results =
          pool.invokeAll(
              candidates.stream()
                  .map(user -> joinTask(trip, user, startLine, joined, rejected))
                  .toList());
      for (Future<Void> result : results) {
        result.get(30, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    long members = tripMemberRepository.countByTripIdAndDeletedAtIsNull(trip.getId());
    // 방장 1 + 참여자 = 정원을 절대 넘지 않는다
    assertThat(members).isEqualTo(MEMBER_COUNT);
    assertThat(joined.get()).isEqualTo(MEMBER_COUNT - 1);
    assertThat(rejected.get()).isEqualTo(CONCURRENT_JOINS - (MEMBER_COUNT - 1));
  }

  // 자리를 잡고 일정 확인을 끝내지 않은 멤버는 자동으로 사라지지 않는다 — TTL·배치를 나중에 실수로
  // 되살리는 것을 막기 위한 회귀 테스트
  @Test
  void abandonedSchedulePendingMember_isNotReclaimed() {
    Trip trip = createTripWithOwner();
    User candidate = createUsers(1).get(0);

    tripService.joinTrip(candidate.getId(), new JoinTripRequest(trip.getInviteCode()));

    TripMember pending =
        tripMemberRepository
            .findByTripIdAndUserIdAndDeletedAtIsNull(trip.getId(), candidate.getId())
            .orElseThrow();
    assertThat(pending.getStatus()).isEqualTo(TripMemberStatus.SCHEDULE_PENDING);
    assertThat(pending.getDeletedAt()).isNull();
    assertThat(tripMemberRepository.countByTripIdAndDeletedAtIsNull(trip.getId())).isEqualTo(2);
  }

  private Callable<Void> joinTask(
      Trip trip,
      User user,
      CyclicBarrier startLine,
      AtomicInteger joined,
      AtomicInteger rejected) {
    return () -> {
      startLine.await(30, TimeUnit.SECONDS);
      try {
        tripService.joinTrip(user.getId(), new JoinTripRequest(trip.getInviteCode()));
        joined.incrementAndGet();
      } catch (TripFitException exception) {
        assertThat(exception.getErrorCode()).isEqualTo(TripErrorCode.TRIP_MEMBER_FULL);
        rejected.incrementAndGet();
      }
      return null;
    };
  }

  private Trip createTripWithOwner() {
    User owner = createUsers(1).get(0);
    Trip trip =
        new Trip(
            owner,
            "동시 참여 테스트",
            LocalDate.now().plusDays(7),
            LocalDate.now().plusDays(30),
            3,
            4,
            MEMBER_COUNT,
            "CC" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(),
            TripStatus.ONGOING);
    tripRepository.save(trip);
    tripMemberRepository.save(
        new TripMember(
            trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE, LocalDateTime.now()));
    return trip;
  }

  private List<User> createUsers(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(
            index -> {
              String subject = "join-race-" + UUID.randomUUID();
              User user =
                  new User(subject, SocialProvider.GOOGLE, subject + "@example.com", "nick", null);
              user.applyProfilePatch("철수" + index, "김", null);
              return userRepository.save(user);
            })
        .toList();
  }
}
