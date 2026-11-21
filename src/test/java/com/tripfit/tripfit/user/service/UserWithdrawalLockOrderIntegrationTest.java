package com.tripfit.tripfit.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripfit.tripfit.common.config.TestcontainersConfig;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.membership.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.trip.service.TripService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// 방장 탈퇴는 여행방 행을 사용자 행보다 먼저 고쳐야 한다. 같은 방에서 알림 이력을 저장하는 요청(예: 멤버의 일정 확인
// 완료)은 여행방 행을 잠근 뒤 수신자(방장) 사용자 행에 공유 잠금을 걸기 때문에, 탈퇴가 반대 순서로 잠그면 데드락이 될 수
// 있다. 그 창은 1ms도 안 돼 동시 실행 반복으로는 잡기 어려우므로, 실제로 DB에 나가는 UPDATE 순서를 기록해 확인한다.
@SpringBootTest(
    properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
        + "com.tripfit.tripfit.user.service.RecordingStatementInspector")
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class UserWithdrawalLockOrderIntegrationTest {

  @Autowired
  private UserWithdrawalService userWithdrawalService;

  @Autowired
  private TripService tripService;

  @Autowired
  private TripRepository tripRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Autowired
  private UserWithdrawalPersistenceService withdrawalPersistenceService;

  @Test
  void memberWithdrawal_skipsRoomDeletedAfterSnapshot() {
    Room room = createRoomWithMember();
    var activityBefore = tripRepository.findById(room.tripId()).orElseThrow().getLastActivityAt();
    TransactionTemplate withdrawal = new TransactionTemplate(transactionManager);
    TransactionTemplate ownerDeletion = new TransactionTemplate(transactionManager);
    ownerDeletion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    withdrawal.executeWithoutResult(ignored -> {
      assertThat(tripRepository.findByIdAndDeletedAtIsNull(room.tripId())).isPresent();
      ownerDeletion.executeWithoutResult(
          inner -> tripService.deleteTrip(room.tripId(), room.ownerId()));
      withdrawalPersistenceService.finalizeWithdrawal(room.memberId());
    });

    assertThat(userRepository.findById(room.memberId()).orElseThrow().getDeletedAt()).isNotNull();
    var deletedTrip = tripRepository.findById(room.tripId()).orElseThrow();
    assertThat(deletedTrip.getDeletedAt()).isNotNull();
    assertThat(deletedTrip.getLastActivityAt()).isEqualTo(activityBefore);
  }

  @Test
  void ownerWithdrawal_updatesTripBeforeUser() {
    Room room = createRoomWithMember();
    List<String> statements = recordSql(() -> userWithdrawalService.withdraw(room.ownerId()));

    int tripUpdate = firstIndex(statements, sql -> sql.startsWith("update trip set"));
    int userUpdate = firstIndex(statements, sql -> sql.startsWith("update users set"));
    assertThat(tripUpdate).as("trip UPDATE가 실행돼야 한다").isNotNegative();
    assertThat(userUpdate).as("users UPDATE가 실행돼야 한다").isNotNegative();
    assertThat(tripUpdate).as("trip UPDATE가 users UPDATE보다 먼저 나가야 한다").isLessThan(userUpdate);
  }

  @Test
  void activation_locksTripBeforeUpdatingMember() {
    Room room = createRoomWithMember();
    List<String> statements =
        recordSql(() -> tripService.activateMembership(room.tripId(), room.memberId()));

    assertTripLockedBeforeMemberUpdate(statements);
  }

  @Test
  void removal_locksTripBeforeUpdatingMember() {
    Room room = createRoomWithMember();
    List<String> statements = recordSql(
        () -> tripService.removeMember(room.tripId(), room.ownerId(), room.memberId()));

    assertTripLockedBeforeMemberUpdate(statements);
  }

  @Test
  void memberWithdrawal_locksTripBeforeUpdatingMember() {
    Room room = createRoomWithMember();
    List<String> statements = recordSql(() -> userWithdrawalService.withdraw(room.memberId()));

    assertTripLockedBeforeMemberUpdate(statements);
  }

  private static void assertTripLockedBeforeMemberUpdate(List<String> statements) {
    int tripLock = firstIndex(
        statements,
        sql -> sql.contains(" from trip ") && sql.contains(" for update"));
    int memberUpdate = firstIndex(statements, sql -> sql.startsWith("update trip_member set"));
    assertThat(tripLock).as("여행방 행을 먼저 잠가야 한다").isNotNegative();
    assertThat(memberUpdate).as("멤버 상태를 갱신해야 한다").isNotNegative();
    assertThat(tripLock).isLessThan(memberUpdate);
  }

  private Room createRoomWithMember() {
    User owner = createUser("owner");
    User member = createUser("member");
    LocalDate startRange = LocalDate.now().plusDays(7);
    UUID tripId =
        tripService
            .createTrip(
                owner.getId(),
                new CreateTripRequest("잠금 순서", startRange, startRange.plusDays(23), 3, 4, 3, null))
            .tripId();
    tripService.activateMembership(tripId, owner.getId());
    String inviteCode = tripRepository.findById(tripId).orElseThrow().getInviteCode();
    tripService.joinTrip(member.getId(), new JoinTripRequest(inviteCode));
    return new Room(tripId, owner.getId(), member.getId());
  }

  private static List<String> recordSql(Runnable action) {
    RecordingStatementInspector.STATEMENTS.clear();
    RecordingStatementInspector.recording = true;
    try {
      action.run();
    } finally {
      RecordingStatementInspector.recording = false;
    }

    return RecordingStatementInspector.STATEMENTS.stream()
        .map(sql -> sql.toLowerCase(Locale.ROOT))
        .toList();
  }

  private static int firstIndex(List<String> statements, Predicate<String> matches) {
    for (int i = 0; i < statements.size(); i++) {
      if (matches.test(statements.get(i))) {
        return i;
      }
    }
    return -1;
  }

  private record Room(
      UUID tripId,
      UUID ownerId,
      UUID memberId
  ) {
  }

  private User createUser(String label) {
    String subject = "withdraw-order-" + label + "-" + UUID.randomUUID();
    User user = new User(subject, SocialProvider.GOOGLE, subject + "@example.com", "nick", null);
    user.applyProfilePatch(label, "김", true);
    user.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
    return userRepository.save(user);
  }
}
