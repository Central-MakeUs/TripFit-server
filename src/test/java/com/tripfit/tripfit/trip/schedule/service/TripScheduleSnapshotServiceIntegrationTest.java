package com.tripfit.tripfit.trip.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.schedule.domain.TripMemberScheduleSnapshot;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.schedule.repository.TripMemberScheduleSnapshotRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.tripfit.tripfit.common.config.TestcontainersConfig;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// O1.4(개별 일정 슬롯 단위 오버라이드)가 방 확정·종료 시 스냅샷 freeze 경로에도 그대로 반영되는지
// 실제 MySQL(Testcontainers) DB로 검증한다 — TripScheduleSnapshotService는 package-private이라 같은 패키지에서
// @SpringBootTest로 실제 빈을 주입받아 호출한다(Mockito 단위 테스트는 TripScheduleSnapshotServiceTest 참고)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class TripScheduleSnapshotServiceIntegrationTest {

  @Autowired
  private TripScheduleSnapshotService snapshotService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TripRepository tripRepository;

  @Autowired
  private TripMemberRepository tripMemberRepository;

  @Autowired
  private RegularScheduleRepository regularScheduleRepository;

  @Autowired
  private PersonalScheduleRepository personalScheduleRepository;

  @Autowired
  private TripMemberScheduleSnapshotRepository snapshotRepository;

  @Test
  void freezeTrip_persistsPartialOverride_slotByslot() {
    User owner =
        new User("snapshot-sub", SocialProvider.GOOGLE, "snap@example.com", "방장", null);
    // 연차·휴일 정보는 이제 User 소유 값
    owner.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
    owner = userRepository.save(owner);

    LocalDate startRange = LocalDate.now().plusDays(60);
    LocalDate endRange = startRange.plusDays(4);
    LocalDate overriddenDate = startRange.plusDays(1);

    // 정기(09~18시)에 오후만 오버라이드 — 프리즈된 스냅샷도 "부분 오버라이드"를 정확히 반영해야 한다
    regularScheduleRepository.save(
        RegularSchedule.create(
            owner,
            "출근",
            "MON,TUE,WED,THU,FRI,SAT,SUN",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0)));
    personalScheduleRepository.save(
        PersonalSchedule.create(
            owner,
            overriddenDate,
            ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.POSSIBLE,
            ScheduleStatus.POSSIBLE,
            false));

    Trip trip =
        tripRepository.save(
            new Trip(
                owner,
                "스냅샷 freeze 테스트",
                startRange,
                endRange,
                3,
                4,
                4,
                "SNAP01",
                TripStatus.ONGOING));
    tripMemberRepository.save(
        new TripMember(trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE,
            LocalDateTime.now()));

    snapshotService.freezeTrip(trip);

    List<TripMemberScheduleSnapshot> snapshots =
        snapshotRepository.findByTrip_IdOrderByUser_IdAscScheduleDateAsc(trip.getId());
    TripMemberScheduleSnapshot overridden =
        snapshots.stream()
            .filter(s -> s.getScheduleDate().equals(overriddenDate))
            .findFirst()
            .orElseThrow();

    assertThat(overridden.getSlotStatuses().getMorningStatus())
        .isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(overridden.getSlotStatuses().getAfternoonStatus())
        .isEqualTo(ScheduleStatus.POSSIBLE);
    assertThat(overridden.getSlotStatuses().getEveningStatus())
        .isEqualTo(ScheduleStatus.POSSIBLE);

    // idempotent — 이미 freeze된 방은 재호출해도 중복 저장되지 않아야 한다
    int beforeCount = snapshots.size();
    snapshotService.freezeTrip(trip);
    assertThat(snapshotRepository.findByTrip_IdOrderByUser_IdAscScheduleDateAsc(trip.getId()))
        .hasSize(beforeCount);
  }
}
