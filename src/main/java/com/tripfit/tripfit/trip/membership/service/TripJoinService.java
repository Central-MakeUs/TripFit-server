package com.tripfit.tripfit.trip.membership.service;

import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.dto.TripEntryResponse;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.trip.service.TripServiceSupport;
import com.tripfit.tripfit.user.domain.User;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripJoinService {

  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  private final TripServiceSupport support;

  // 정원에 자리가 남아 있으면 새 멤버를 일정 확인 전(SCHEDULE_PENDING) 상태로 추가한다.
  // 다른 요청이 같은 순간에 이 여행방을 먼저 고쳤다면 버전 충돌 예외가 나고, 이 트랜잭션은 통째로
  // 취소된다. 다시 시도하는 일은 트랜잭션 바깥의 TripService가 맡는다.
  @Transactional
  public TripEntryResponse joinAsNewMember(Trip trip, User user) {
    // 1. 정원을 확인하고 참여 인원을 1 올린다.
    if (!trip.tryOccupySeat()) {
      throw new TripFitException(TripErrorCode.TRIP_MEMBER_FULL);
    }
    // 2. 인원 변경을 멤버 추가보다 먼저 DB에 보낸다. 멤버를 먼저 INSERT하면 MySQL이 외래키 확인을 위해
    // 여행방 행에 공유 잠금을 걸고, 동시에 들어온 두 요청이 그 잠금을 쥔 채 서로의 여행방 UPDATE를
    // 기다리다 데드락에 빠진다.
    tripRepository.saveAndFlush(trip);
    // 3. 멤버를 추가한다.
    TripMember member =
        new TripMember(
            trip,
            user,
            TripMemberRole.MEMBER,
            TripMemberStatus.SCHEDULE_PENDING,
            LocalDateTime.now());
    tripMemberRepository.save(member);
    return support.toEntry(trip, member);
  }
}
