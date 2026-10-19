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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

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

  @Test
  void ownerWithdrawal_updatesTripBeforeUser() {
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

    RecordingStatementInspector.STATEMENTS.clear();
    RecordingStatementInspector.recording = true;
    try {
      userWithdrawalService.withdraw(owner.getId());
    } finally {
      RecordingStatementInspector.recording = false;
    }

    List<String> statements =
        RecordingStatementInspector.STATEMENTS.stream()
            .map(sql -> sql.toLowerCase(Locale.ROOT))
            .toList();
    int tripUpdate = firstIndex(statements, "update trip set");
    int userUpdate = firstIndex(statements, "update users set");
    assertThat(tripUpdate).as("trip UPDATE가 실행돼야 한다").isNotNegative();
    assertThat(userUpdate).as("users UPDATE가 실행돼야 한다").isNotNegative();
    assertThat(tripUpdate).as("trip UPDATE가 users UPDATE보다 먼저 나가야 한다").isLessThan(userUpdate);
  }

  private static int firstIndex(List<String> statements, String prefix) {
    for (int i = 0; i < statements.size(); i++) {
      if (statements.get(i).startsWith(prefix)) {
        return i;
      }
    }
    return -1;
  }

  private User createUser(String label) {
    String subject = "withdraw-order-" + label + "-" + UUID.randomUUID();
    User user = new User(subject, SocialProvider.GOOGLE, subject + "@example.com", "nick", null);
    user.applyProfilePatch(label, "김", true);
    user.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
    return userRepository.save(user);
  }
}
