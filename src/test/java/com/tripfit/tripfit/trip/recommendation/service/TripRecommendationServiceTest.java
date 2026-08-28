package com.tripfit.tripfit.trip.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.schedule.service.TripScheduleSnapshotService;
import com.tripfit.tripfit.trip.service.TripServiceSupport;
import com.tripfit.tripfit.trip.recommendation.domain.AttendanceType;
import com.tripfit.tripfit.trip.recommendation.domain.Recommendation;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationFeedback;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationFeedbackReason;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationFeedbackStatus;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationMode;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.recommendation.domain.UnconfirmReason;
import com.tripfit.tripfit.trip.recommendation.dto.ConfirmTripRequest;
import com.tripfit.tripfit.trip.recommendation.dto.RecommendationDetailResponse;
import com.tripfit.tripfit.trip.recommendation.dto.RecommendationListResponse;
import com.tripfit.tripfit.trip.recommendation.dto.SaveRecommendationFeedbackRequest;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.recommendation.dto.UnconfirmTripRequest;
import com.tripfit.tripfit.trip.event.TripConfirmCanceledEvent;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.recommendation.algorithm.MemberAttendanceDetail;
import com.tripfit.tripfit.trip.recommendation.algorithm.RecommendationCandidate;
import com.tripfit.tripfit.trip.recommendation.algorithm.RecommendationEngine;
import com.tripfit.tripfit.trip.recommendation.repository.RecommendationFeedbackRepository;
import com.tripfit.tripfit.trip.recommendation.repository.RecommendationRepository;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.schedule.repository.TripMemberScheduleSnapshotRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.service.UserDirectoryService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TripRecommendationServiceTest {

  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  private static final UUID TRIP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

  @Mock
  private TripRepository tripRepository;

  @Mock
  private TripMemberRepository tripMemberRepository;

  @Mock
  private UserDirectoryService userDirectoryService;

  @Mock
  private TripScheduleSnapshotService tripScheduleSnapshotService;

  @Mock
  private TripMemberScheduleSnapshotRepository tripMemberScheduleSnapshotRepository;

  @Mock
  private RecommendationRepository recommendationRepository;

  @Mock
  private RecommendationFeedbackRepository recommendationFeedbackRepository;

  @Mock
  private RecommendationEngine recommendationEngine;

  @Mock
  private ApplicationEventPublisher applicationEventPublisher;

  private TripRecommendationService service;

  private Trip trip;

  private TripMember ownerMembership;

  @BeforeEach
  void setUp() {
    User owner = user(OWNER_ID);
    trip =
        new Trip(
            owner, "제주 여행", LocalDate.now(), LocalDate.now().plusDays(30), 2, 3, 6,
            "ABC123", TripStatus.ONGOING);
    trip.setId(TRIP_ID);
    ownerMembership =
        new TripMember(trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE,
            LocalDateTime.now());

    TripServiceSupport support =
        new TripServiceSupport(tripRepository, tripMemberRepository, userDirectoryService);
    service =
        new TripRecommendationService(
            support,
            tripScheduleSnapshotService,
            tripMemberScheduleSnapshotRepository,
            recommendationRepository,
            recommendationFeedbackRepository,
            recommendationEngine,
            applicationEventPublisher);

    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
  }

  @Test
  void generateRecommendations_replacesExistingRowsAndUpdatesLastMode() {
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(TRIP_ID))
        .thenReturn(List.of(ownerMembership));
    RecommendationCandidate candidate =
        new RecommendationCandidate(
            LocalDate.now().plusDays(2), LocalDate.now().plusDays(4), 80, 1, 1, 2.0, 91.5, 1);
    when(recommendationEngine.generate(eq(trip), eq(RecommendationMode.BASIC), any()))
        .thenReturn(List.of(candidate));

    RecommendationListResponse response =
        service.generateRecommendations(TRIP_ID, OWNER_ID, RecommendationMode.BASIC);

    verify(recommendationRepository).deleteByTripId(TRIP_ID);
    verify(recommendationRepository).saveAll(any());
    assertThat(trip.getLastRecommendationMode()).isEqualTo(RecommendationMode.BASIC);
    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).rank()).isEqualTo(1);

    verifyNoInteractions(recommendationFeedbackRepository);
  }

  @Test
  void generateRecommendations_notOwner_throwsForbidden() {
    UUID stranger = UUID.randomUUID();

    assertThatThrownBy(
        () -> service.generateRecommendations(TRIP_ID, stranger, RecommendationMode.BASIC))
        .isInstanceOfSatisfying(
            TripFitException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(TripErrorCode.TRIP_FORBIDDEN));
  }

  @Test
  void generateRecommendations_notOngoing_throwsNotOngoing() {
    ReflectionTestUtils.setField(trip, "status", TripStatus.CONFIRMED);

    assertThatThrownBy(
        () -> service.generateRecommendations(TRIP_ID, OWNER_ID, RecommendationMode.BASIC))
        .isInstanceOfSatisfying(
            TripFitException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(TripErrorCode.TRIP_NOT_ONGOING));
  }

  @Test
  void getRecommendationDetail_returnsMemberBreakdownAndNullFeedbackWhenNoneSaved() {
    Recommendation recommendation =
        new Recommendation(
            trip, 1, LocalDate.now().plusDays(2), LocalDate.now().plusDays(4), 80, 1, 1, 2.0, 91.5);
    when(recommendationRepository.findByTrip_IdAndRank(TRIP_ID, 1))
        .thenReturn(Optional.of(recommendation));
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(TRIP_ID))
        .thenReturn(List.of(ownerMembership));
    UUID memberUserId = ownerMembership.getUser().getId();
    when(
        recommendationEngine.classifyMembers(
            eq(LocalDate.now().plusDays(2)),
            eq(LocalDate.now().plusDays(4)),
            any()))
        .thenReturn(
            List.of(
                new MemberAttendanceDetail(memberUserId, AttendanceType.PARTIAL_ATTEND, 2, 1.5)));
    when(recommendationFeedbackRepository.findByRecommendationId(recommendation.getId()))
        .thenReturn(Optional.empty());

    RecommendationDetailResponse response =
        service.getRecommendationDetail(TRIP_ID, OWNER_ID, 1);

    assertThat(response.rank()).isEqualTo(1);
    assertThat(response.members()).hasSize(1);
    assertThat(response.members().get(0).name()).isEqualTo("테스터");
    assertThat(response.members().get(0).attendance()).isEqualTo(AttendanceType.PARTIAL_ATTEND);
    assertThat(response.members().get(0).uncertainDays()).isEqualTo(2);
    assertThat(response.members().get(0).vacationDaysNeeded()).isEqualTo(1.5);
    assertThat(response.feedback()).isNull();
  }

  @Test
  void getRecommendationDetail_returnsPreviouslySavedFeedback() {
    Recommendation recommendation =
        new Recommendation(
            trip, 1, LocalDate.now().plusDays(2), LocalDate.now().plusDays(4), 80, 1, 1, 2.0, 91.5);
    when(recommendationRepository.findByTrip_IdAndRank(TRIP_ID, 1))
        .thenReturn(Optional.of(recommendation));
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(TRIP_ID))
        .thenReturn(List.of(ownerMembership));
    UUID memberUserId = ownerMembership.getUser().getId();
    when(recommendationEngine.classifyMembers(any(), any(), any()))
        .thenReturn(
            List.of(new MemberAttendanceDetail(memberUserId, AttendanceType.FULL_ATTEND, 0, 0.0)));
    RecommendationFeedback savedFeedback =
        new RecommendationFeedback(
            trip,
            recommendation.getId(),
            RecommendationMode.BASIC,
            1,
            LocalDate.now().plusDays(2),
            LocalDate.now().plusDays(4),
            RecommendationFeedbackStatus.NOT_HELPFUL,
            RecommendationFeedbackReason.TOO_FEW_ATTENDEES,
            null);
    when(recommendationFeedbackRepository.findByRecommendationId(recommendation.getId()))
        .thenReturn(Optional.of(savedFeedback));

    RecommendationDetailResponse response =
        service.getRecommendationDetail(TRIP_ID, OWNER_ID, 1);

    assertThat(response.feedback()).isNotNull();
    assertThat(response.feedback().status()).isEqualTo(RecommendationFeedbackStatus.NOT_HELPFUL);
    assertThat(response.feedback().reason())
        .isEqualTo(RecommendationFeedbackReason.TOO_FEW_ATTENDEES);
  }

  @Test
  void getRecommendationDetail_notOwner_throwsForbidden() {
    UUID stranger = UUID.randomUUID();

    assertThatThrownBy(() -> service.getRecommendationDetail(TRIP_ID, stranger, 1))
        .isInstanceOfSatisfying(
            TripFitException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(TripErrorCode.TRIP_FORBIDDEN));
  }

  @Test
  void getRecommendationDetail_rankNotFound_throwsRecommendationNotFound() {
    when(recommendationRepository.findByTrip_IdAndRank(TRIP_ID, 99)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getRecommendationDetail(TRIP_ID, OWNER_ID, 99))
        .isInstanceOfSatisfying(
            TripFitException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(TripErrorCode.RECOMMENDATION_NOT_FOUND));
  }

  @Test
  void confirmSchedule_byRank_setsConfirmedFieldsAndFreezesSnapshot() {
    Recommendation recommendation =
        new Recommendation(
            trip, 1, LocalDate.now().plusDays(2), LocalDate.now().plusDays(4), 80, 1, 1, 2.0, 91.5);
    when(recommendationRepository.findByTrip_IdAndRank(TRIP_ID, 1))
        .thenReturn(Optional.of(recommendation));
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(TRIP_ID))
        .thenReturn(List.of(ownerMembership));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(ownerMembership));
    UUID memberUserId = ownerMembership.getUser().getId();
    when(
        recommendationEngine.classifyMembers(
            eq(LocalDate.now().plusDays(2)),
            eq(LocalDate.now().plusDays(4)),
            any()))
        .thenReturn(
            List.of(new MemberAttendanceDetail(memberUserId, AttendanceType.FULL_ATTEND, 0, 1.0)));

    TripDetailResponse response =
        service.confirmSchedule(TRIP_ID, OWNER_ID, new ConfirmTripRequest(1, null, null));

    assertThat(response.status()).isEqualTo(TripStatus.CONFIRMED);
    assertThat(trip.getConfirmedStartDate()).isEqualTo(LocalDate.now().plusDays(2));
    assertThat(trip.getConfirmedAttendCount()).isEqualTo(1);
    assertThat(trip.getConfirmedVacationMemberCount()).isEqualTo(1);
    verify(tripScheduleSnapshotService).freezeTrip(trip);
  }

  @Test
  void confirmSchedule_byCustomDates_setsConfirmedFieldsAndFreezesSnapshot() {
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(TRIP_ID))
        .thenReturn(List.of(ownerMembership));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(ownerMembership));
    UUID memberUserId = ownerMembership.getUser().getId();
    LocalDate customStart = LocalDate.now().plusDays(9);
    LocalDate customEnd = LocalDate.now().plusDays(11);
    when(recommendationEngine.classifyMembers(eq(customStart), eq(customEnd), any()))
        .thenReturn(
            List.of(new MemberAttendanceDetail(memberUserId, AttendanceType.FULL_ATTEND, 0, 1.0)));

    TripDetailResponse response =
        service.confirmSchedule(
            TRIP_ID,
            OWNER_ID,
            new ConfirmTripRequest(null, customStart, customEnd));

    assertThat(response.status()).isEqualTo(TripStatus.CONFIRMED);
    assertThat(trip.getConfirmedStartDate()).isEqualTo(customStart);
    assertThat(trip.getConfirmedEndDate()).isEqualTo(customEnd);
    assertThat(trip.getConfirmedAttendCount()).isEqualTo(1);
    assertThat(trip.getConfirmedVacationMemberCount()).isEqualTo(1);
    verify(tripScheduleSnapshotService).freezeTrip(trip);
  }

  @Test
  void confirmSchedule_customDatesDurationMismatch_throws() {
    assertThatThrownBy(
        () -> service.confirmSchedule(
            TRIP_ID,
            OWNER_ID,
            new ConfirmTripRequest(null, LocalDate.now().plusDays(2), LocalDate.now().plusDays(3))))
        .isInstanceOfSatisfying(
            TripFitException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(TripErrorCode.CONFIRM_DURATION_MISMATCH));
  }

  @Test
  void confirmSchedule_bothRankAndDates_throwsInvalidConfirmRequest() {
    assertThatThrownBy(
        () -> service.confirmSchedule(
            TRIP_ID,
            OWNER_ID,
            new ConfirmTripRequest(1, LocalDate.now().plusDays(2), LocalDate.now().plusDays(4))))
        .isInstanceOfSatisfying(
            TripFitException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(TripErrorCode.INVALID_CONFIRM_REQUEST));
  }

  @Test
  void confirmSchedule_neitherRankNorDates_throwsInvalidConfirmRequest() {
    assertThatThrownBy(
        () -> service.confirmSchedule(TRIP_ID, OWNER_ID, new ConfirmTripRequest(null, null, null)))
        .isInstanceOfSatisfying(
            TripFitException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(TripErrorCode.INVALID_CONFIRM_REQUEST));
  }

  @Test
  void unconfirm_success_clearsConfirmedFieldsAndDeletesRecommendationsAndSnapshot() {
    trip.confirm(LocalDate.now().plusDays(2), LocalDate.now().plusDays(4), 5, 1, 0);

    service.unconfirm(
        TRIP_ID,
        OWNER_ID,
        new UnconfirmTripRequest(UnconfirmReason.NEW_SCHEDULE_ADDED, null));

    assertThat(trip.getStatus()).isEqualTo(TripStatus.ONGOING);
    assertThat(trip.getConfirmedStartDate()).isNull();
    assertThat(trip.getConfirmedAttendCount()).isNull();
    assertThat(trip.getUnconfirmReason()).isEqualTo(UnconfirmReason.NEW_SCHEDULE_ADDED);
    verify(recommendationRepository).deleteByTripId(TRIP_ID);
    verify(tripMemberScheduleSnapshotRepository).deleteByTripId(TRIP_ID);
    verify(applicationEventPublisher).publishEvent(any(TripConfirmCanceledEvent.class));
  }

  @Test
  void unconfirm_notConfirmed_throwsTripNotConfirmed() {
    assertThatThrownBy(
        () -> service.unconfirm(
            TRIP_ID,
            OWNER_ID,
            new UnconfirmTripRequest(UnconfirmReason.NEW_SCHEDULE_ADDED, null)))
        .isInstanceOfSatisfying(
            TripFitException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(TripErrorCode.TRIP_NOT_CONFIRMED));
  }

  @Test
  void unconfirm_missingReason_throwsInvalidUnconfirmReason() {
    ReflectionTestUtils.setField(trip, "status", TripStatus.CONFIRMED);

    assertThatThrownBy(
        () -> service.unconfirm(TRIP_ID, OWNER_ID, new UnconfirmTripRequest(null, null)))
        .isInstanceOfSatisfying(
            TripFitException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(TripErrorCode.INVALID_UNCONFIRM_REASON));
  }

  @Test
  void unconfirm_otherReasonWithoutDetail_throwsInvalidUnconfirmReason() {
    ReflectionTestUtils.setField(trip, "status", TripStatus.CONFIRMED);

    assertThatThrownBy(
        () -> service
            .unconfirm(TRIP_ID, OWNER_ID, new UnconfirmTripRequest(UnconfirmReason.OTHER, null)))
        .isInstanceOfSatisfying(
            TripFitException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(TripErrorCode.INVALID_UNCONFIRM_REASON));
    verify(recommendationRepository, never()).deleteByTripId(any());
  }

  @Test
  void saveFeedback_notHelpfulWithoutReason_throwsInvalidRecommendationFeedback() {
    Recommendation recommendation =
        new Recommendation(
            trip, 1, LocalDate.now().plusDays(2), LocalDate.now().plusDays(4), 80, 1, 1, 2.0, 91.5);
    when(recommendationRepository.findByTrip_IdAndRank(TRIP_ID, 1))
        .thenReturn(Optional.of(recommendation));

    assertThatThrownBy(
        () -> service.saveFeedback(
            TRIP_ID,
            OWNER_ID,
            1,
            new SaveRecommendationFeedbackRequest(
                RecommendationFeedbackStatus.NOT_HELPFUL, null, null)))
        .isInstanceOfSatisfying(
            TripFitException.class,
            e -> assertThat(e.getErrorCode())
                .isEqualTo(TripErrorCode.INVALID_RECOMMENDATION_FEEDBACK));
  }

  @Test
  void saveFeedback_otherReasonWithoutDetail_throwsInvalidRecommendationFeedback() {
    Recommendation recommendation =
        new Recommendation(
            trip, 1, LocalDate.now().plusDays(2), LocalDate.now().plusDays(4), 80, 1, 1, 2.0, 91.5);
    when(recommendationRepository.findByTrip_IdAndRank(TRIP_ID, 1))
        .thenReturn(Optional.of(recommendation));

    assertThatThrownBy(
        () -> service.saveFeedback(
            TRIP_ID,
            OWNER_ID,
            1,
            new SaveRecommendationFeedbackRequest(
                RecommendationFeedbackStatus.NOT_HELPFUL, RecommendationFeedbackReason.OTHER,
                null)))
        .isInstanceOfSatisfying(
            TripFitException.class,
            e -> assertThat(e.getErrorCode())
                .isEqualTo(TripErrorCode.INVALID_RECOMMENDATION_FEEDBACK));
  }

  @Test
  void saveFeedback_upsertsNewRow() {
    Recommendation recommendation =
        new Recommendation(
            trip, 1, LocalDate.now().plusDays(2), LocalDate.now().plusDays(4), 80, 1, 1, 2.0, 91.5);
    when(recommendationRepository.findByTrip_IdAndRank(TRIP_ID, 1))
        .thenReturn(Optional.of(recommendation));
    when(recommendationFeedbackRepository.findByRecommendationId(any()))
        .thenReturn(Optional.empty());

    service.saveFeedback(
        TRIP_ID,
        OWNER_ID,
        1,
        new SaveRecommendationFeedbackRequest(
            RecommendationFeedbackStatus.NOT_HELPFUL,
            RecommendationFeedbackReason.TOO_FEW_ATTENDEES, null));

    verify(recommendationFeedbackRepository, times(1)).save(any(RecommendationFeedback.class));
  }

  @Test
  void saveFeedback_existingRow_overwritesStatusAndReason() {
    Recommendation recommendation =
        new Recommendation(
            trip, 1, LocalDate.now().plusDays(2), LocalDate.now().plusDays(4), 80, 1, 1, 2.0, 91.5);
    when(recommendationRepository.findByTrip_IdAndRank(TRIP_ID, 1))
        .thenReturn(Optional.of(recommendation));
    RecommendationFeedback existing =
        new RecommendationFeedback(
            trip,
            recommendation.getId(),
            RecommendationMode.BASIC,
            1,
            LocalDate.now().plusDays(2),
            LocalDate.now().plusDays(4),
            RecommendationFeedbackStatus.HELPFUL,
            null,
            null);
    when(recommendationFeedbackRepository.findByRecommendationId(any()))
        .thenReturn(Optional.of(existing));

    service.saveFeedback(
        TRIP_ID,
        OWNER_ID,
        1,
        new SaveRecommendationFeedbackRequest(
            RecommendationFeedbackStatus.NOT_HELPFUL,
            RecommendationFeedbackReason.TOO_FEW_ATTENDEES, null));

    assertThat(existing.getStatus()).isEqualTo(RecommendationFeedbackStatus.NOT_HELPFUL);
    assertThat(existing.getReason()).isEqualTo(RecommendationFeedbackReason.TOO_FEW_ATTENDEES);
    verify(recommendationFeedbackRepository, times(1)).save(existing);
  }

  @Test
  void unconfirm_success_doesNotCascadeDeleteRecommendationFeedback() {
    trip.confirm(LocalDate.now().plusDays(2), LocalDate.now().plusDays(4), null, null, null);

    service.unconfirm(
        TRIP_ID,
        OWNER_ID,
        new UnconfirmTripRequest(UnconfirmReason.NEW_SCHEDULE_ADDED, null));

    verify(recommendationRepository).deleteByTripId(TRIP_ID);
    verifyNoInteractions(recommendationFeedbackRepository);
  }

  private static User user(UUID id) {
    User user = new User("social-" + id, SocialProvider.GOOGLE, null, "테스터", null);
    user.setId(id);
    return user;
  }
}
