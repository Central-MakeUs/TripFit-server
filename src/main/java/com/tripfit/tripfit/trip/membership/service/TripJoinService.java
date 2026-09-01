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

@Service
@RequiredArgsConstructor
public class TripJoinService {

  private final TripMemberRepository tripMemberRepository;

  private final TripServiceSupport support;

  // 인원 제한을 검사한 뒤 새로운 여행 멤버를 추가(SCHEDULE_PENDING 상태)합니다.
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
