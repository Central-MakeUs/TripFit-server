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

/** 신규 초대 참여만 분리 — 이미 멤버인 재접속(idempotent)은 last_activity touch를 하지 않는다. */
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

  // 신규 멤버를 RESPONDED로 등록하고 상세를 반환한다 — 일정 0건이면 전부 free 처리
  @Transactional
  @TripActivity(tripIdFromReturn = true)
  public TripDetailResponse joinAsNewMember(Trip trip, User user) {
    // 일정이 없으면 전부 free로 표시 (입장 조건 충족용)
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
