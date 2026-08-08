package com.tripfit.tripfit.trip.membership.service;

import com.tripfit.tripfit.trip.service.TripServiceSupport;
import com.tripfit.tripfit.trip.config.TripActivity;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.port.out.UserDirectoryPort;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.user.domain.User;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신규 초대 참여만 분리 — 이미 멤버인 재접속(idempotent)은 last_activity touch를 하지 않는다. */
@Service
public class TripJoinService {

  private final TripMemberRepository tripMemberRepository;

  private final TripServiceSupport support;

  private final UserDirectoryPort userDirectoryPort;

  public TripJoinService(
      TripMemberRepository tripMemberRepository,
      TripServiceSupport support,
      UserDirectoryPort userDirectoryPort) {
    this.tripMemberRepository = tripMemberRepository;
    this.support = support;
    this.userDirectoryPort = userDirectoryPort;
  }

  // 신규 멤버를 ACTIVE로 등록하고 상세를 반환한다 — 일정 0건이면 전부 free 처리
  @Transactional
  @TripActivity(tripIdFromReturn = true)
  public TripDetailResponse joinAsNewMember(Trip trip, User user) {
    // 일정이 없으면 전부 free로 표시 (입장 조건 충족용)
    userDirectoryPort.markAllFreeIfNoSchedules(user);

    TripMember member =
        new TripMember(
            trip,
            user,
            TripMemberRole.MEMBER,
            TripMemberStatus.ACTIVE,
            LocalDateTime.now());
    tripMemberRepository.save(member);
    return support.toDetail(trip, member);
  }
}
