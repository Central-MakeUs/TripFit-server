package com.tripfit.tripfit.trip.service;

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
import com.tripfit.tripfit.user.exception.UserErrorCode;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.user.schedule.service.ScheduleCalendarResolver;
import com.tripfit.tripfit.user.service.UserLookupService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// Trip command/query가 공유하는 매핑·검증·초대코드·권한 가드 (requireActive는 트래픽 게이트로 config 패키지 공유)
@Component
public class TripServiceSupport {

  static final int NAME_MAX_LENGTH = 15;

  static final int MEMBER_COUNT_MIN = 1;

  static final int MEMBER_COUNT_MAX = 10;

  static final int MAX_INVITE_CODE_ATTEMPTS = 20;

  static final int MEMBERS_PREVIEW_LIMIT = 4;

  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  private final UserLookupService userLookupService;

  private final UserRepository userRepository;

  public TripServiceSupport(
      TripRepository tripRepository,
      TripMemberRepository tripMemberRepository,
      UserLookupService userLookupService,
      UserRepository userRepository) {
    this.tripRepository = tripRepository;
    this.tripMemberRepository = tripMemberRepository;
    this.userLookupService = userLookupService;
    this.userRepository = userRepository;
  }

  // 홈 카드 DTO — 미리보기는 최대 4명, overflow = 참여 수 − 4
  TripHomeCardResponse toHomeCard(
      Trip trip,
      TripMember membership,
      int joinedMemberCount,
      int activeMemberCount,
      List<MemberPreviewResponse> previews) {
    return new TripHomeCardResponse(
        trip.getId(),
        trip.getName(),
        trip.getDestination(),
        trip.getStartRange(),
        trip.getEndRange(),
        trip.getDurationDays(),
        trip.getDurationNights(),
        trip.getMemberCount(),
        effectiveStatus(trip),
        trip.getLastActivityAt(),
        membership.isPinned(),
        membership.getRole(),
        membership.getStatus(),
        activeMemberCount,
        memberFillRate(activeMemberCount, trip.getMemberCount()),
        previews,
        previewOverflow(joinedMemberCount));
  }

  // 여행방 상세 DTO — inviteCode·본인 역할/상태·모집률·멤버 프리뷰 포함
  TripDetailResponse toDetail(Trip trip, TripMember membership) {
    UUID tripId = trip.getId();
    TripMemberCountProjection counts = loadMemberCountsByTripIds(List.of(tripId)).get(tripId);
    int joinedMemberCount = counts == null ? 0 : (int) counts.getJoinedMemberCount();
    int activeMemberCount = counts == null ? 0 : (int) counts.getActiveCount();
    List<MemberPreviewResponse> previews =
        loadMemberPreviewsByTripIds(List.of(tripId)).getOrDefault(tripId, List.of());

    return new TripDetailResponse(
        tripId,
        trip.getName(),
        trip.getDestination(),
        trip.getStartRange(),
        trip.getEndRange(),
        trip.getDurationDays(),
        trip.getDurationNights(),
        trip.getMemberCount(),
        effectiveStatus(trip),
        trip.getInviteCode(),
        trip.getConfirmedStartDate(),
        trip.getConfirmedEndDate(),
        trip.getConfirmedAttendCount(),
        trip.getConfirmedVacationMemberCount(),
        trip.getConfirmedUncertainCount(),
        trip.getLastRecommendationMode(),
        trip.getLastActivityAt(),
        membership.isPinned(),
        membership.getRole(),
        membership.getStatus(),
        activeMemberCount,
        memberFillRate(activeMemberCount, trip.getMemberCount()),
        previews,
        previewOverflow(joinedMemberCount));
  }

  // 모집 현황 비율(응답률) — activeMemberCount ÷ trip.memberCount (0.0~1.0)
  static double memberFillRate(int activeMemberCount, Integer memberCount) {
    if (memberCount == null || memberCount <= 0) {
      return 0.0;
    }
    return (double) activeMemberCount / memberCount;
  }

  // 멤버 프리뷰 초과 인원 — 참여 인원 - 4, 최소 0 (+N 배지 표시용, 홈카드·상세 공용)
  private static int previewOverflow(int joinedMemberCount) {
    return Math.max(0, joinedMemberCount - MEMBERS_PREVIEW_LIMIT);
  }

  // N+1 방지 — tripId 목록 일괄 집계
  Map<UUID, TripMemberCountProjection> loadMemberCountsByTripIds(List<UUID> tripIds) {
    return tripMemberRepository.countMembersByTripIds(tripIds).stream()
        .collect(Collectors.toMap(TripMemberCountProjection::getTripId, c -> c));
  }

  // N+1 방지 — 미리보기 row를 tripId별 리스트로 묶고, User는 한 번에 배치 조회해 방별 동명이인 displayName을 매김
  Map<UUID, List<MemberPreviewResponse>> loadMemberPreviewsByTripIds(List<UUID> tripIds) {
    List<TripMemberPreviewProjection> rows =
        tripMemberRepository.findMemberPreviewsByTripIds(tripIds);

    List<UUID> previewUserIds = rows.stream().map(TripMemberPreviewProjection::getUserId).toList();
    Map<UUID, User> usersById =
        userRepository.findAllById(previewUserIds).stream()
            .collect(Collectors.toMap(User::getId, user -> user));

    Map<UUID, List<TripMemberPreviewProjection>> rowsByTrip = new LinkedHashMap<>();
    for (TripMemberPreviewProjection row : rows) {
      rowsByTrip.computeIfAbsent(row.getTripId(), ignored -> new ArrayList<>()).add(row);
    }

    Map<UUID, List<MemberPreviewResponse>> byTrip = new HashMap<>();
    for (Map.Entry<UUID, List<TripMemberPreviewProjection>> entry : rowsByTrip.entrySet()) {
      List<TripMemberPreviewProjection> tripRows = entry.getValue();
      // 동명이인 접미사는 방 단위로만 부여 — 다른 방 참여자와는 섞이지 않도록 방별로 재계산
      List<User> usersInOrder =
          tripRows.stream().map(row -> usersById.get(row.getUserId())).toList();
      Map<UUID, String> displayNames =
          TripDisplayNameHelper.assignPreviewDisplayNames(usersInOrder);
      List<MemberPreviewResponse> previews =
          tripRows.stream()
              .map(
                  row -> new MemberPreviewResponse(
                      row.getUserId(),
                      displayNames.get(row.getUserId()),
                      row.getProfileImageUrl(),
                      TripMemberRole.valueOf(row.getRole())))
              .toList();
      byTrip.put(entry.getKey(), previews);
    }
    return byTrip;
  }

  // 활성 여행방 로드 — 없거나 삭제되면 TRIP_NOT_FOUND
  Trip requireActiveTrip(UUID tripId) {
    return tripRepository
        .findByIdAndDeletedAtIsNull(tripId)
        .orElseThrow(() -> new TripFitException(TripErrorCode.TRIP_NOT_FOUND));
  }

  // 정기+개별 일정을 로드해 합친 달력으로 만든다 — live 조회·snapshot freeze 공용. 멤버 목록을 배치 조회해 멤버 수만큼
  // 반복 쿼리하지 않게 함(N+1 방지, RecommendationEngine.loadContext와 동일 패턴)
  Map<UUID, List<CalendarDayResponse>> resolveMergedSchedules(
      RegularScheduleRepository regularScheduleRepository,
      PersonalScheduleRepository personalScheduleRepository,
      List<UUID> userIds,
      LocalDate startDate,
      LocalDate endDate,
      Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> googleBusyByUser) {
    Map<UUID, List<RegularSchedule>> regularsByUser =
        regularScheduleRepository.findByUserIdIn(userIds).stream()
            .collect(Collectors.groupingBy(regular -> regular.getUser().getId()));
    Map<UUID, List<PersonalSchedule>> personalsByUser =
        personalScheduleRepository
            .findByUserIdInAndScheduleDateBetween(userIds, startDate, endDate).stream()
            .collect(Collectors.groupingBy(personal -> personal.getUser().getId()));

    Map<UUID, List<CalendarDayResponse>> byUser = new HashMap<>();
    for (UUID userId : userIds) {
      byUser.put(
          userId,
          ScheduleCalendarResolver.resolve(
              regularsByUser.getOrDefault(userId, List.of()),
              personalsByUser.getOrDefault(userId, List.of()),
              startDate,
              endDate,
              googleBusyByUser.getOrDefault(userId, Map.of())));
    }
    return byUser;
  }

  // 활성 멤버 전원 — joinedAt 오름차순 (멤버 목록·달력 조회·스냅샷 freeze 공용 정렬 기준)
  List<TripMember> listActiveMembersSortedByJoinedAt(UUID tripId) {
    return tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId).stream()
        .sorted(Comparator.comparing(TripMember::getJoinedAt))
        .toList();
  }

  // 활성 멤버십 로드 — 비멤버·탈퇴는 TRIP_ACCESS_DENIED (방장 전용 FORBIDDEN과 구분). TripAuthorizationInterceptor 공용
  public TripMember requireActiveMember(UUID tripId, UUID userId) {
    return tripMemberRepository
        .findByTripIdAndUserIdAndDeletedAtIsNull(tripId, userId)
        .orElseThrow(() -> new TripFitException(TripErrorCode.TRIP_ACCESS_DENIED));
  }

  // 이 방 일정 확인 완료 여부 — SCHEDULE_PENDING(미확인)면 SCHEDULE_ACTIVATION_REQUIRED.
  // TripAuthorizationInterceptor·TripCommandService.joinTrip 공용
  public void requireActive(TripMember membership) {
    if (membership.getStatus() != TripMemberStatus.ACTIVE) {
      throw new TripFitException(UserErrorCode.SCHEDULE_ACTIVATION_REQUIRED);
    }
  }

  // 방장만 허용 — 아니면 TRIP_FORBIDDEN
  void requireOwner(Trip trip, UUID userId) {
    if (!trip.getOwner().getId().equals(userId)) {
      throw new TripFitException(TripErrorCode.TRIP_FORBIDDEN);
    }
  }

  // 조율 중(ONGOING)만 변경 허용 — effectiveStatus 기준(기간 경과면 EXPIRED로 봄)
  void requireOngoingForMutation(Trip trip) {
    if (effectiveStatus(trip) != TripStatus.ONGOING) {
      throw new TripFitException(TripErrorCode.TRIP_NOT_ONGOING);
    }
  }

  // 활성 여행방 로드 + 방장 검증 — TripCommandService·TripRecommendationService의 방장 전용 유스케이스 공용
  Trip requireOwnedTrip(UUID tripId, UUID userId) {
    Trip trip = requireActiveTrip(tripId);
    requireOwner(trip, userId);
    return trip;
  }

  // requireOwnedTrip + ONGOING 검증 — 방장 전용이면서 조율 중에만 허용하는 변경 유스케이스 공용
  Trip requireOwnedOngoingTrip(UUID tripId, UUID userId) {
    Trip trip = requireOwnedTrip(tripId, userId);
    requireOngoingForMutation(trip);
    return trip;
  }

  // 화면용 상태 — ONGOING이어도 endRange가 지났으면 EXPIRED (배치 전이라도 UX 동일)
  TripStatus effectiveStatus(Trip trip) {
    if (trip.getStatus() == TripStatus.ONGOING
        && trip.getEndRange().isBefore(LocalDate.now())) {
      return TripStatus.EXPIRED;
    }
    return trip.getStatus();
  }

  // 1. 이름 길이 2. 기간·인원 3. 박/일 쌍(둘 다 null=미정) 4. days ≤ range (있을 때)
  // nights+1 ≤ days ≤ nights+2 — 당일치기(0박)도 예외 없이 동일 범위 적용
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

  // 둘 다 null → null(미정). 둘 다 값 + nights≥0 + nights+1 ≤ days ≤ nights+2 → days.
  // 한쪽만·범위 밖·음수 박 → 400. 당일치기(nights=0)도 예외 없이 동일 범위(days 1~2)
  static Integer resolveDurationDays(Integer durationNights, Integer durationDays) {
    if (durationNights == null && durationDays == null) {
      return null;
    }
    if (durationNights == null
        || durationDays == null
        || durationNights < 0
        || durationDays < durationNights + 1
        || durationDays > durationNights + 2) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    return durationDays;
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

  // User 조회 SSOT는 UserLookupService — 여기서 재구현하지 않고 위임
  User findUser(UUID userId) {
    return userLookupService.requireUser(userId);
  }
}
