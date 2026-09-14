package com.tripfit.tripfit.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserSummaryServiceTest {

  private final UserSummaryService userSummaryService = new UserSummaryService();

  private User user;

  @BeforeEach
  void setUp() {
    user = new User("google-sub", SocialProvider.GOOGLE, "user@example.com", "홍길동", null);
    user.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
  }

  @Test
  void toSummary_vacationApplyPeriodNeverSaved_hasCompletedPreScheduleFalse() {
    var summary = userSummaryService.toSummary(user);

    assertThat(summary.hasCompletedPreSchedule()).isFalse();
  }

  // 연차·휴일 정보를 저장하면 일정 row가 하나도 없어도 "갱신 입력"이 된다 — 판정이 일정 존재 여부와 무관함을 고정
  @Test
  void toSummary_vacationPolicySavedWithoutAnySchedule_hasCompletedPreScheduleTrue() {
    user.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);

    var summary = userSummaryService.toSummary(user);

    assertThat(summary.hasCompletedPreSchedule()).isTrue();
  }

  // 탈퇴 스크럽으로 사전 신청일이 지워지면 재가입 사용자는 다시 "최초 입력"이다
  @Test
  void toSummary_afterWithdrawalScrub_hasCompletedPreScheduleFalseAgain() {
    user.applyVacationPolicy(5, VacationApplyPeriod.ONE_WEEK_BEFORE, true, false);
    user.scrubPiiForWithdrawal();

    var summary = userSummaryService.toSummary(user);

    assertThat(summary.hasCompletedPreSchedule()).isFalse();
  }
}
