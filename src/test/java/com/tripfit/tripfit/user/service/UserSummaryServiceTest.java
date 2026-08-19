package com.tripfit.tripfit.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.exception.UserErrorCode;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSummaryServiceTest {

  @Mock
  private RegularScheduleRepository regularScheduleRepository;

  @Mock
  private PersonalScheduleRepository personalScheduleRepository;

  @Mock
  private UserLookupService userLookupService;

  @InjectMocks
  private UserSummaryService userSummaryService;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User("google-sub", SocialProvider.GOOGLE, "user@example.com", "홍길동", null);
    user.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
  }

  @Test
  void toSummary_includesIsAllFreeFromUser() {
    user.applyAllFree(true);
    when(regularScheduleRepository.existsByUserId(user.getId())).thenReturn(false);
    when(personalScheduleRepository.existsByUserId(user.getId())).thenReturn(false);

    var summary = userSummaryService.toSummary(user);

    assertThat(summary.isAllFree()).isTrue();
    assertThat(summary.hasPreSchedule()).isFalse();
    assertThat(summary.hasRegularSchedule()).isFalse();
  }

  // 개별 일정만 등록한 사용자 — 두 파생 필드가 갈리는 유일한 조합
  @Test
  void toSummary_personalScheduleOnly_hasPreScheduleTrueButHasRegularScheduleFalse() {
    when(regularScheduleRepository.existsByUserId(user.getId())).thenReturn(false);
    when(personalScheduleRepository.existsByUserId(user.getId())).thenReturn(true);

    var summary = userSummaryService.toSummary(user);

    assertThat(summary.hasPreSchedule()).isTrue();
    assertThat(summary.hasRegularSchedule()).isFalse();
  }

  @Test
  void toSummary_regularScheduleExists_bothDerivedFieldsTrue() {
    when(regularScheduleRepository.existsByUserId(user.getId())).thenReturn(true);

    var summary = userSummaryService.toSummary(user);

    assertThat(summary.hasPreSchedule()).isTrue();
    assertThat(summary.hasRegularSchedule()).isTrue();
  }

  @Test
  void canEnterRoom_trueWhenAllFree() {
    user.applyAllFree(true);
    assertThat(userSummaryService.canEnterRoom(user)).isTrue();
  }

  @Test
  void canEnterRoom_trueWhenHasSchedule() {
    user.applyAllFree(false);
    when(regularScheduleRepository.existsByUserId(user.getId())).thenReturn(true);

    assertThat(userSummaryService.canEnterRoom(user)).isTrue();
  }

  @Test
  void canEnterRoom_falseWhenEmptyAndNotAllFree() {
    user.applyAllFree(false);
    when(regularScheduleRepository.existsByUserId(user.getId())).thenReturn(false);
    when(personalScheduleRepository.existsByUserId(user.getId())).thenReturn(false);

    assertThat(userSummaryService.canEnterRoom(user)).isFalse();
  }

  @Test
  void requireCanEnterRoom_throwsWhenBlocked() {
    user.applyAllFree(false);
    when(regularScheduleRepository.existsByUserId(user.getId())).thenReturn(false);
    when(personalScheduleRepository.existsByUserId(user.getId())).thenReturn(false);

    assertThatThrownBy(() -> userSummaryService.requireCanEnterRoom(user))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(UserErrorCode.SCHEDULE_ENTRY_REQUIRED);
  }

  @Test
  void markAllFreeIfNoSchedules_setsTrueWhenEmpty() {
    user.applyAllFree(false);
    when(regularScheduleRepository.existsByUserId(user.getId())).thenReturn(false);
    when(personalScheduleRepository.existsByUserId(user.getId())).thenReturn(false);

    userSummaryService.markAllFreeIfNoSchedules(user);

    assertThat(user.isAllFree()).isTrue();
  }

  @Test
  void markAllFreeIfNoSchedules_keepsFalseWhenHasSchedule() {
    user.applyAllFree(false);
    when(regularScheduleRepository.existsByUserId(user.getId())).thenReturn(true);

    userSummaryService.markAllFreeIfNoSchedules(user);

    assertThat(user.isAllFree()).isFalse();
  }
}
