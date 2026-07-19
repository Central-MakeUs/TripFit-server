package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.config.TripActivity;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.service.UserSummaryService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신규 join만 분리 — idempotent 재접속은 {@link TripActivity} 미적용 (#26 L1). */
@Service
class TripJoinService {

  private final TripMemberRepository tripMemberRepository;

  private final TripQueryService tripQueryService;

  private final UserSummaryService userSummaryService;

  TripJoinService(
      TripMemberRepository tripMemberRepository,
      TripQueryService tripQueryService,
      UserSummaryService userSummaryService) {
    this.tripMemberRepository = tripMemberRepository;
    this.tripQueryService = tripQueryService;
    this.userSummaryService = userSummaryService;
  }

  @Transactional
  @TripActivity(tripIdFromReturn = true)
  public TripDetailResponse joinAsNewMember(Trip trip, User user) {
    // Skip+0행 → is_all_free=true (D-JOIN-TRIP-FLOW)
    userSummaryService.markAllFreeIfNoSchedules(user);

    TripMember member =
        new TripMember(
            trip,
            user,
            TripMemberRole.MEMBER,
            TripMemberStatus.RESPONDED,
            LocalDateTime.now());
    tripMemberRepository.save(member);
    return tripQueryService.toDetail(trip, member);
  }
}
