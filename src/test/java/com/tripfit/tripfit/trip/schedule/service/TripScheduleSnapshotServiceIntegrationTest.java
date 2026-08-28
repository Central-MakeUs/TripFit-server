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

    owner.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
    owner = userRepository.save(owner);

    LocalDate startRange = LocalDate.now().plusDays(60);
    LocalDate endRange = startRange.plusDays(4);
    LocalDate overriddenDate = startRange.plusDays(1);

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

    int beforeCount = snapshots.size();
    snapshotService.freezeTrip(trip);
    assertThat(snapshotRepository.findByTrip_IdOrderByUser_IdAscScheduleDateAsc(trip.getId()))
        .hasSize(beforeCount);
  }
}
