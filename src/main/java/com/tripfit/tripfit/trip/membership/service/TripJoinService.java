package com.tripfit.tripfit.trip.membership.service;

import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.dto.TripEntryResponse;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.service.TripServiceSupport;
import com.tripfit.tripfit.user.domain.User;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신규 초대 참여만 분리 — 이미 멤버인 재호출(멱등)은 여기까지 오지 않는다. */
@Service
@RequiredArgsConstructor
public class TripJoinService {

  private final TripMemberRepository tripMemberRepository;

  private final TripServiceSupport support;

  // 신규 멤버를 SCHEDULE_PENDING으로 등록한다 — 방 안 API는 일정 확인 후 activate로 ACTIVE가 돼야 열린다.
  // 호출자가 이미 trip 행을 잠근 상태로 넘겨야 한다(TripCommandService.joinTrip) — 그래야 카운트와 INSERT
  // 사이에 다른 요청이 끼어들지 못한다. SCHEDULE_PENDING도 자리를 차지하며, 일정 확인을 끝내지 않은 사람의
  // 자리는 자동 회수하지 않고 방 나가기로만 해제된다
  @Transactional
  public TripEntryResponse joinAsNewMember(Trip lockedTrip, User user) {
    long occupiedSeats = tripMemberRepository.countByTripIdAndDeletedAtIsNull(lockedTrip.getId());
    if (occupiedSeats >= lockedTrip.getMemberCount()) {
      throw new TripFitException(TripErrorCode.TRIP_MEMBER_FULL);
    }
    TripMember member =
        new TripMember(
            lockedTrip,
            user,
            TripMemberRole.MEMBER,
            TripMemberStatus.SCHEDULE_PENDING,
            LocalDateTime.now());
    tripMemberRepository.save(member);
    return support.toEntry(lockedTrip, member);
  }
}
