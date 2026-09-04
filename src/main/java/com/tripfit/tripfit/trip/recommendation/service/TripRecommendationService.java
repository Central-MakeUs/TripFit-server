package com.tripfit.tripfit.trip.recommendation.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.trip.schedule.service.TripScheduleSnapshotService;
import com.tripfit.tripfit.trip.service.TripDisplayNameHelper;
import com.tripfit.tripfit.trip.service.TripServiceSupport;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.config.TripActivity;
import com.tripfit.tripfit.trip.recommendation.domain.AttendanceType;
import com.tripfit.tripfit.trip.recommendation.domain.Recommendation;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationFeedback;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationFeedbackReason;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationFeedbackStatus;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationMode;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.recommendation.domain.UnconfirmReason;
import com.tripfit.tripfit.trip.recommendation.dto.ConfirmTripRequest;
import com.tripfit.tripfit.trip.recommendation.dto.MemberAttendanceResponse;
import com.tripfit.tripfit.trip.recommendation.dto.RecommendationDetailResponse;
import com.tripfit.tripfit.trip.recommendation.dto.RecommendationFeedbackResponse;
import com.tripfit.tripfit.trip.recommendation.dto.RecommendationItemResponse;
import com.tripfit.tripfit.trip.recommendation.dto.RecommendationListResponse;
import com.tripfit.tripfit.trip.recommendation.dto.SaveRecommendationFeedbackRequest;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.recommendation.dto.UnconfirmTripRequest;
import com.tripfit.tripfit.trip.event.TripConfirmCanceledEvent;
import com.tripfit.tripfit.trip.event.TripConfirmedEvent;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.recommendation.algorithm.MemberAttendanceDetail;
import com.tripfit.tripfit.trip.recommendation.algorithm.RecommendationCandidate;
import com.tripfit.tripfit.trip.recommendation.algorithm.RecommendationEngine;
import com.tripfit.tripfit.trip.recommendation.repository.RecommendationFeedbackRepository;
import com.tripfit.tripfit.trip.recommendation.repository.RecommendationRepository;
import com.tripfit.tripfit.trip.schedule.repository.TripMemberScheduleSnapshotRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 추천 후보 생성·조회·피드백·여행 날짜 확정/취소. 확정 시 멤버 일정 스냅샷도 같은 TX에서 고정한다. */
@Service
@RequiredArgsConstructor
public class TripRecommendationService {

  private final TripServiceSupport support;

  private final TripScheduleSnapshotService tripScheduleSnapshotService;

  private final TripMemberScheduleSnapshotRepository tripMemberScheduleSnapshotRepository;

  private final RecommendationRepository recommendationRepository;

  private final RecommendationFeedbackRepository recommendationFeedbackRepository;

  private final RecommendationEngine recommendationEngine;

  private final ApplicationEventPublisher applicationEventPublisher;

  // 방장이 추천 모드로 TOP3 후보를 재계산 — 기존 후보 hard DELETE 후 새로 저장, lastRecommendationMode 갱신
  @Transactional
  @TripActivity(tripIdParam = "tripId")
  public RecommendationListResponse generateRecommendations(
      UUID tripId,
      UUID ownerId,
      RecommendationMode mode) {
    Trip trip = support.requireOwnedOngoingTrip(tripId, ownerId);
    requireDurationSet(trip);

    List<TripMember> members = activeMembers(tripId);
    List<RecommendationCandidate> candidates = recommendationEngine.generate(trip, mode, members);

    recommendationRepository.deleteByTripId(tripId);
    List<Recommendation> rows = new ArrayList<>();
    for (int i = 0; i < candidates.size(); i++) {
      RecommendationCandidate candidate = candidates.get(i);
      rows.add(
          new Recommendation(
              trip,
              i + 1,
              candidate.startDate(),
              candidate.endDate(),
              candidate.attendRate(),
              candidate.partialAttendCount(),
              candidate.uncertainCount(),
              candidate.totalVacationDays(),
              candidate.score()));
    }
    recommendationRepository.saveAll(rows);
    trip.applyLastRecommendationMode(mode);

    return toListResponse(mode, rows);
  }

  // 희망 박/일 변경 등 추천 입력이 바뀌어 기존 후보가 무효화될 때 hard DELETE — TripCommandService.patchTrip 전용
  public void deleteRecommendationsForTrip(UUID tripId) {
    recommendationRepository.deleteByTripId(tripId);
  }

  // 방장이 저장된 추천 TOP3 카드 목록을 조회 — 방장 전용(참여자는 확정 일정만 조회 가능)
  @Transactional(readOnly = true)
  public RecommendationListResponse listRecommendations(UUID tripId, UUID ownerId) {
    Trip trip = support.requireOwnedTrip(tripId, ownerId);
    List<Recommendation> rows = recommendationRepository.findByTrip_IdOrderByRankAsc(tripId);
    return toListResponse(trip.getLastRecommendationMode(), rows);
  }

  // 추천 근거 상세 — 참여자별 브레이크다운은 저장하지 않고 그때그때 재계산(카드 목록의 무거운 페이로드 방지)
  @Transactional(readOnly = true)
  public RecommendationDetailResponse getRecommendationDetail(UUID tripId, UUID ownerId, int rank) {
    Trip trip = support.requireOwnedTrip(tripId, ownerId);
    Recommendation recommendation = requireRecommendation(tripId, rank);

    List<TripMember> members = activeMembers(tripId);
    List<MemberAttendanceDetail> details =
        recommendationEngine.classifyMembers(
            recommendation.getStartDate(),
            recommendation.getEndDate(),
            members);
    Map<UUID, String> displayNames =
        TripDisplayNameHelper.assignDisplayNames(
            members.stream().map(TripMember::getUser).toList());

    List<MemberAttendanceResponse> memberResponses =
        details.stream()
            .map(
                detail -> new MemberAttendanceResponse(
                    displayNames.get(detail.userId()),
                    detail.attendance(),
                    detail.uncertainDays(),
                    detail.vacationDays()))
            .toList();

    RecommendationFeedbackResponse feedbackResponse =
        recommendationFeedbackRepository
            .findByRecommendationId(recommendation.getId())
            .map(
                feedback -> new RecommendationFeedbackResponse(
                    feedback.getStatus(), feedback.getReason(), feedback.getReasonDetail()))
            .orElse(null);

    return new RecommendationDetailResponse(
        recommendation.getRank(),
        trip.getLastRecommendationMode(),
        recommendation.getStartDate(),
        recommendation.getEndDate(),
        recommendation.getAttendRate(),
        recommendation.getPartialAttendCount(),
        recommendation.getUncertainCount(),
        recommendation.getTotalVacationDays(),
        memberResponses,
        feedbackResponse);
  }

  // "이 추천이 도움이 되었나요" 피드백 upsert — 후보(recommendation)당 방장 1건. hard DELETE와 무관하게 스냅샷 보존
  @Transactional
  public void saveFeedback(
      UUID tripId,
      UUID ownerId,
      int rank,
      SaveRecommendationFeedbackRequest request) {
    Trip trip = support.requireOwnedTrip(tripId, ownerId);
    Recommendation recommendation = requireRecommendation(tripId, rank);
    requireValidFeedback(request);

    RecommendationFeedback feedback =
        recommendationFeedbackRepository
            .findByRecommendationId(recommendation.getId())
            .orElseGet(
                () -> new RecommendationFeedback(
                    trip,
                    recommendation.getId(),
                    trip.getLastRecommendationMode(),
                    recommendation.getRank(),
                    recommendation.getStartDate(),
                    recommendation.getEndDate(),
                    request.status(),
                    request.reason(),
                    request.reasonDetail()));
    feedback.applyFeedback(request.status(), request.reason(), request.reasonDetail());
    recommendationFeedbackRepository.save(feedback);
  }

  // 방장이 추천 후보(또는 직접 날짜)로 여행을 확정한다 — CONFIRMED 직후 일정 스냅샷 freeze + 확정 시점 통계 저장
  @Transactional
  @TripActivity(tripIdParam = "tripId")
  public TripDetailResponse confirmSchedule(UUID tripId, UUID ownerId, ConfirmTripRequest request) {
    Trip trip = support.requireOwnedOngoingTrip(tripId, ownerId);

    LocalDate[] resolvedDates = resolveConfirmDates(trip, tripId, request);
    LocalDate confirmedStartDate = resolvedDates[0];
    LocalDate confirmedEndDate = resolvedDates[1];

    List<TripMember> members = activeMembers(tripId);
    List<MemberAttendanceDetail> details =
        recommendationEngine.classifyMembers(confirmedStartDate, confirmedEndDate, members);
    int attendCount =
        (int) details.stream().filter(detail -> detail.attendance() != AttendanceType.NON_ATTEND)
            .count();
    int vacationMemberCount =
        (int) details.stream().filter(detail -> detail.vacationDays() > 0).count();
    int uncertainMemberCount =
        (int) details.stream().filter(detail -> detail.uncertainDays() > 0).count();

    trip.confirm(
        confirmedStartDate,
        confirmedEndDate,
        attendCount,
        vacationMemberCount,
        uncertainMemberCount);

    tripScheduleSnapshotService.freezeTrip(trip);
    applicationEventPublisher.publishEvent(new TripConfirmedEvent(tripId));

    TripMember ownerMembership = support.requireActiveMember(tripId, ownerId);
    return support.toDetail(trip, ownerMembership);
  }

  // 방장이 확정을 취소해 ONGOING으로 되돌린다 — 확정 통계·추천 hard DELETE, 스냅샷 폐기, 참여자 알림 이벤트 발행
  @Transactional
  @TripActivity(tripIdParam = "tripId")
  public void unconfirm(UUID tripId, UUID ownerId, UnconfirmTripRequest request) {
    Trip trip = support.requireOwnedTrip(tripId, ownerId);
    if (trip.getStatus() != TripStatus.CONFIRMED) {
      throw new TripFitException(TripErrorCode.TRIP_NOT_CONFIRMED);
    }
    requireValidUnconfirmReason(request);

    trip.unconfirm(request.reason(), request.reasonDetail());

    recommendationRepository.deleteByTripId(tripId);
    tripMemberScheduleSnapshotRepository.deleteByTripId(tripId);

    applicationEventPublisher.publishEvent(new TripConfirmCanceledEvent(tripId));
  }

  // recommendationRank 또는 (startDate+endDate) 정확히 하나 — 직접 입력이면 durationDays 일치까지 검증
  private LocalDate[] resolveConfirmDates(Trip trip, UUID tripId, ConfirmTripRequest request) {
    boolean hasRank = request.recommendationRank() != null;
    boolean hasCustomDates = request.startDate() != null || request.endDate() != null;
    if (hasRank == hasCustomDates) {
      throw new TripFitException(TripErrorCode.INVALID_CONFIRM_REQUEST);
    }
    if (hasRank) {
      Recommendation recommendation = requireRecommendation(tripId, request.recommendationRank());
      return new LocalDate[] {recommendation.getStartDate(), recommendation.getEndDate()};
    }
    LocalDate startDate = request.startDate();
    LocalDate endDate = request.endDate();
    if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    if (trip.getDurationDays() != null) {
      long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
      if (days != trip.getDurationDays()) {
        throw new TripFitException(TripErrorCode.CONFIRM_DURATION_MISMATCH);
      }
    }
    return new LocalDate[] {startDate, endDate};
  }

  private Recommendation requireRecommendation(UUID tripId, Integer rank) {
    return recommendationRepository
        .findByTrip_IdAndRank(tripId, rank)
        .orElseThrow(() -> new TripFitException(TripErrorCode.RECOMMENDATION_NOT_FOUND));
  }

  private void requireDurationSet(Trip trip) {
    if (trip.getDurationDays() == null) {
      throw new TripFitException(
          CommonErrorCode.INVALID_INPUT, "여행 일수가 정해지지 않아 추천을 계산할 수 없습니다.");
    }
  }

  private void requireValidFeedback(SaveRecommendationFeedbackRequest request) {
    if (request.status() != RecommendationFeedbackStatus.NOT_HELPFUL) {
      return;
    }
    if (request.reason() == null) {
      throw new TripFitException(TripErrorCode.INVALID_RECOMMENDATION_FEEDBACK);
    }
    if (request.reason() == RecommendationFeedbackReason.OTHER
        && (request.reasonDetail() == null || request.reasonDetail().isBlank())) {
      throw new TripFitException(TripErrorCode.INVALID_RECOMMENDATION_FEEDBACK);
    }
  }

  private void requireValidUnconfirmReason(UnconfirmTripRequest request) {
    if (request.reason() == null) {
      throw new TripFitException(TripErrorCode.INVALID_UNCONFIRM_REASON);
    }
    if (request.reason() == UnconfirmReason.OTHER
        && (request.reasonDetail() == null || request.reasonDetail().isBlank())) {
      throw new TripFitException(TripErrorCode.INVALID_UNCONFIRM_REASON);
    }
  }

  // last_activity_at touch 대상 없음(조회) — 순수 응답 조립
  private RecommendationListResponse toListResponse(
      RecommendationMode mode,
      List<Recommendation> rows) {
    List<RecommendationItemResponse> items =
        rows.stream()
            .sorted(Comparator.comparingInt(Recommendation::getRank))
            .map(
                row -> new RecommendationItemResponse(
                    row.getRank(),
                    row.getStartDate(),
                    row.getEndDate(),
                    row.getAttendRate(),
                    row.getPartialAttendCount(),
                    row.getUncertainCount(),
                    row.getTotalVacationDays()))
            .toList();
    return new RecommendationListResponse(mode, items);
  }

  // 방 참여자 중 일정 확인(ACTIVE)까지 마친 인원만 — 추천 계산·확정 통계의 응답 참여자 모집단
  private List<TripMember> activeMembers(UUID tripId) {
    return support.listActiveMembersSortedByJoinedAt(tripId).stream()
        .filter(member -> member.getStatus() == TripMemberStatus.ACTIVE)
        .toList();
  }
}
