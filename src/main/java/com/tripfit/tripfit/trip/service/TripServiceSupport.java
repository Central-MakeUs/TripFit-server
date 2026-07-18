package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.dto.MemberPreviewResponse;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.dto.TripHomeCardResponse;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.trip.repository.projection.TripMemberCountProjection;
import com.tripfit.tripfit.trip.repository.projection.TripMemberPreviewProjection;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// Trip command/query가 공유하는 매핑·검증·초대코드·권한 가드
@Component
class TripServiceSupport {

  static final int NAME_MAX_LENGTH = 15;

  static final int MEMBER_COUNT_MIN = 1;

  static final int MEMBER_COUNT_MAX = 10;

  static final int MAX_INVITE_CODE_ATTEMPTS = 20;

  static final int MEMBERS_PREVIEW_LIMIT = 4;

  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  private final UserRepository userRepository;

  TripServiceSupport(
      TripRepository tripRepository,
      TripMemberRepository tripMemberRepository,
      UserRepository userRepository) {
    this.tripRepository = tripRepository;
    this.tripMemberRepository = tripMemberRepository;
    this.userRepository = userRepository;
  }

  // 홈 카드 DTO — 미리보기는 최대 4명, overflow = 참여 수 − 4
  TripHomeCardResponse toHomeCard(
      Trip trip,
      TripMember membership,
      int joinedMemberCount,
      int respondedCount,
      List<MemberPreviewResponse> previews) {
    int overflow = Math.max(0, joinedMemberCount - MEMBERS_PREVIEW_LIMIT);
    return new TripHomeCardResponse(
        trip.getId(),
        trip.getName(),
        trip.getDestination(),
        trip.getStartRange(),
        trip.getEndRange(),
        trip.getDurationDays(),
        durationNights(trip.getDurationDays()),
        trip.getMemberCount(),
        effectiveStatus(trip),
        trip.getLastActivityAt(),
        membership.isPinned(),
        membership.getRole(),
        membership.getStatus(),
        respondedCount,
        joinedMemberCount,
        memberFillRate(joinedMemberCount, trip.getMemberCount()),
        previews,
        overflow);
  }

  // 여행방 상세 DTO — inviteCode·본인 역할/상태·모집률 포함
  TripDetailResponse toDetail(Trip trip, TripMember membership) {
    UUID tripId = trip.getId();
    long joinedMemberCount =
        tripMemberRepository.countByTripIdAndDeletedAtIsNull(tripId);
    int respondedCount =
        (int) tripMemberRepository.countByTripIdAndStatusAndDeletedAtIsNull(
            tripId,
            TripMemberStatus.RESPONDED);
    int joined = (int) joinedMemberCount;

    return new TripDetailResponse(
        tripId,
        trip.getName(),
        trip.getDestination(),
        trip.getStartRange(),
        trip.getEndRange(),
        trip.getDurationDays(),
        durationNights(trip.getDurationDays()),
        trip.getMemberCount(),
        effectiveStatus(trip),
        trip.getInviteCode(),
        trip.getConfirmedStartDate(),
        trip.getConfirmedEndDate(),
        trip.getLastRecommendationMode(),
        trip.getLastActivityAt(),
        membership.isPinned(),
        membership.getRole(),
        membership.getStatus(),
        respondedCount,
        joined,
        memberFillRate(joined, trip.getMemberCount()));
  }

  // 모집 현황 비율 — joinedMemberCount ÷ trip.memberCount (0.0~1.0)
  static double memberFillRate(int joinedMemberCount, Integer memberCount) {
    if (memberCount == null || memberCount <= 0) {
      return 0.0;
    }
    return (double) joinedMemberCount / memberCount;
  }

  // N+1 방지 — tripId 목록 일괄 집계
  Map<UUID, TripMemberCountProjection> loadMemberCountsByTripIds(List<UUID> tripIds) {
    return tripMemberRepository.countMembersByTripIds(tripIds).stream()
        .collect(Collectors.toMap(TripMemberCountProjection::getTripId, c -> c));
  }

  // N+1 방지 — 미리보기 row를 tripId별 리스트로 묶음 (상한은 Repository 쿼리)
  Map<UUID, List<MemberPreviewResponse>> loadMemberPreviewsByTripIds(List<UUID> tripIds) {
    List<TripMemberPreviewProjection> rows =
        tripMemberRepository.findMemberPreviewsByTripIds(tripIds);
    Map<UUID, List<MemberPreviewResponse>> byTrip = new HashMap<>();
    for (TripMemberPreviewProjection row : rows) {
      byTrip
          .computeIfAbsent(row.getTripId(), ignored -> new ArrayList<>())
          .add(
              new MemberPreviewResponse(
                  row.getUserId(),
                  row.getProfileImageUrl(),
                  TripMemberRole.valueOf(row.getRole())));
    }
    return byTrip;
  }

  // 활성 여행방 로드 — 없거나 삭제되면 TRIP_NOT_FOUND
  Trip requireActiveTrip(UUID tripId) {
    return tripRepository
        .findByIdAndDeletedAtIsNull(tripId)
        .orElseThrow(() -> new TripFitException(TripErrorCode.TRIP_NOT_FOUND));
  }

  // 활성 멤버십 로드 — 비멤버·탈퇴는 TRIP_ACCESS_DENIED (방장 전용 FORBIDDEN과 구분)
  TripMember requireActiveMember(UUID tripId, UUID userId) {
    return tripMemberRepository
        .findByTripIdAndUserIdAndDeletedAtIsNull(tripId, userId)
        .orElseThrow(() -> new TripFitException(TripErrorCode.TRIP_ACCESS_DENIED));
  }

  // 방장만 허용 — 아니면 TRIP_FORBIDDEN
  void requireOwner(Trip trip, UUID userId) {
    if (!trip.getOwner().getId().equals(userId)) {
      throw new TripFitException(TripErrorCode.TRIP_FORBIDDEN);
    }
  }

  // 조율 중(ONGOING)만 변경 허용 — effectiveStatus 기준(기간 경과면 TERMINATED로 봄)
  void requireOngoingForMutation(Trip trip) {
    if (effectiveStatus(trip) != TripStatus.ONGOING) {
      throw new TripFitException(TripErrorCode.TRIP_NOT_ONGOING);
    }
  }

  // 화면용 상태 — ONGOING이어도 endRange가 지났으면 TERMINATED (배치 전이라도 UX 동일)
  TripStatus effectiveStatus(Trip trip) {
    if (trip.getStatus() == TripStatus.ONGOING
        && trip.getEndRange().isBefore(LocalDate.now())) {
      return TripStatus.TERMINATED;
    }
    return trip.getStatus();
  }

  // 1. 이름 길이 2. 기간·인원 3. 박/일 쌍(둘 다 null=미정) 4. days ≤ range (있을 때)
  // 당일치기(0박1일) 허용 — nights==days-1 · days≥1 · nights≥0
  void validateTripMeta(
      String name,
      LocalDate startRange,
      LocalDate endRange,
      Integer durationNights,
      Integer durationDays,
      Integer memberCount) {
    if (name == null || name.isBlank() || name.trim().length() > NAME_MAX_LENGTH) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    if (startRange == null
        || endRange == null
        || endRange.isBefore(startRange)
        || memberCount == null
        || memberCount < MEMBER_COUNT_MIN
        || memberCount > MEMBER_COUNT_MAX) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    Integer resolvedDays = resolveDurationDays(durationNights, durationDays);
    if (resolvedDays != null) {
      long rangeDays = ChronoUnit.DAYS.between(startRange, endRange) + 1;
      if (resolvedDays > rangeDays) {
        throw new TripFitException(CommonErrorCode.INVALID_INPUT);
      }
    }
  }

  // 둘 다 null → null(미정). 둘 다 값 + nights==days-1 + days≥1 + nights≥0 → days.
  // 당일치기: nights=0, days=1. 한쪽만·관계 불일치·음수 박 → 400
  static Integer resolveDurationDays(Integer durationNights, Integer durationDays) {
    if (durationNights == null && durationDays == null) {
      return null;
    }
    if (durationNights == null
        || durationDays == null
        || durationDays < 1
        || durationNights < 0
        || durationNights != durationDays - 1) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    return durationDays;
  }

  static Integer durationNights(Integer durationDays) {
    return durationDays == null ? null : durationDays - 1;
  }

  // UNIQUE 충돌 재시도 — 한도 초과 시 INTERNAL_ERROR (클라이언트 재시도 유도)
  String generateUniqueInviteCode() {
    for (int attempt = 0; attempt < MAX_INVITE_CODE_ATTEMPTS; attempt++) {
      String code = InviteCodeGenerator.generate();
      if (!tripRepository.existsByInviteCode(code)) {
        return code;
      }
    }
    throw new TripFitException(CommonErrorCode.INTERNAL_ERROR);
  }

  static String normalizeDestination(String destination) {
    if (destination == null || destination.isBlank()) {
      return null;
    }
    return destination.trim();
  }

  User findUser(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new TripFitException(AuthErrorCode.AUTH_FORBIDDEN));
  }
}
