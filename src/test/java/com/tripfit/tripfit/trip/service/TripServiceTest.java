package com.tripfit.tripfit.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.holiday.HolidayProvider;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.config.TripActivityAspect;
import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.schedule.domain.TripMemberScheduleSnapshot;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.membership.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.dto.TripListQuery;
import com.tripfit.tripfit.trip.dto.TripListScope;
import com.tripfit.tripfit.trip.dto.UpdateTripPinRequest;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.membership.service.TripJoinHoldService;
import com.tripfit.tripfit.trip.membership.service.TripJoinService;
import com.tripfit.tripfit.trip.membership.service.TripMemberQueryService;
import com.tripfit.tripfit.trip.port.out.GoogleCalendarPort;
import com.tripfit.tripfit.trip.port.out.SchedulePort;
import com.tripfit.tripfit.trip.port.out.UserDirectoryPort;
import com.tripfit.tripfit.trip.recommendation.algorithm.RecommendationEngine;
import com.tripfit.tripfit.trip.recommendation.repository.RecommendationFeedbackRepository;
import com.tripfit.tripfit.trip.recommendation.repository.RecommendationRepository;
import com.tripfit.tripfit.trip.recommendation.service.TripRecommendationService;
import com.tripfit.tripfit.trip.schedule.repository.TripMemberScheduleSnapshotRepository;
import com.tripfit.tripfit.trip.schedule.service.TripScheduleSnapshotService;
import com.tripfit.tripfit.trip.membership.repository.projection.TripMemberCountProjection;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.exception.UserErrorCode;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarPortAdapter;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.user.schedule.service.ScheduleAvailabilityAdapter;
import com.tripfit.tripfit.user.service.UserDirectoryAdapter;
import com.tripfit.tripfit.user.service.UserLookupService;
import com.tripfit.tripfit.user.service.UserProfileService;
import com.tripfit.tripfit.user.service.UserSummaryService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

  private final HolidayProvider holidayProvider = (start, end) -> Set.of();

  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  private static final UUID MEMBER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

  private static final UUID TRIP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

  // 희망 기간을 오늘 기준 상대 날짜로 고정 — 하드코딩된 절대 날짜는 시간이 지나면 endRange가 과거가 되어
  // effectiveStatus()가 EXPIRED로 판정해버리는 date bomb이 됨(TripServiceSupport.effectiveStatus 참고)
  private static final LocalDate TRIP_START = LocalDate.now();

  private static final LocalDate TRIP_END = TRIP_START.plusDays(9);

  private static final LocalDate OTHER_TRIP_START = TRIP_START.plusMonths(1);

  private static final LocalDate OTHER_TRIP_END = OTHER_TRIP_START.plusDays(9);

  @Mock
  private TripRepository tripRepository;

  @Mock
  private TripMemberRepository tripMemberRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserProfileService userProfileService;

  @Mock
  private RegularScheduleRepository regularScheduleRepository;

  @Mock
  private PersonalScheduleRepository personalScheduleRepository;

  @Mock
  private RecommendationRepository recommendationRepository;

  @Mock
  private RecommendationFeedbackRepository recommendationFeedbackRepository;

  @Mock
  private TripMemberScheduleSnapshotRepository snapshotRepository;

  @Mock
  private GoogleCalendarService googleCalendarService;

  @Mock
  private ApplicationEventPublisher applicationEventPublisher;

  @Mock
  private TripJoinHoldService tripJoinHoldService;

  private TripService tripService;

  private User owner;

  private User member;

  private Trip trip;

  @BeforeEach
  void setUp() {
    owner = user(OWNER_ID, "홍", "길동");
    member = user(MEMBER_ID, "김", "철수");
    trip = ongoingTrip();

    UserLookupService userLookupService = new UserLookupService(userRepository);
    UserSummaryService userSummaryService =
        new UserSummaryService(
            regularScheduleRepository,
            personalScheduleRepository,
            userLookupService);
    UserDirectoryPort userDirectoryPort =
        new UserDirectoryAdapter(
            userLookupService, userRepository, userProfileService, userSummaryService);
    TripServiceSupport support =
        new TripServiceSupport(tripRepository, tripMemberRepository, userDirectoryPort);
    TripQueryService tripQueryService = new TripQueryService(tripMemberRepository, support);
    SchedulePort schedulePort =
        new ScheduleAvailabilityAdapter(
            regularScheduleRepository, personalScheduleRepository, userRepository,
            holidayProvider);
    // resolveMergedSchedules 내부의 공휴일 휴무(holidayRest) 배치 조회 — 이 테스트 파일은 캘린더 세부값이
    // 아니라 트립 흐름만 검증하므로 모든 테스트에 필요하진 않아 lenient로 둔다(UnnecessaryStubbingException 방지)
    lenient().when(userRepository.findAllById(any())).thenReturn(List.of(owner, member));
    GoogleCalendarPort googleCalendarPort = new GoogleCalendarPortAdapter(googleCalendarService);
    TripMemberQueryService tripMemberQueryService =
        new TripMemberQueryService(
            snapshotRepository,
            support,
            schedulePort,
            googleCalendarPort);
    TripJoinService tripJoinService = new TripJoinService(tripMemberRepository, support);
    TripActivityAspect tripActivityAspect = new TripActivityAspect(tripRepository);
    AspectJProxyFactory joinProxyFactory = new AspectJProxyFactory(tripJoinService);
    joinProxyFactory.addAspect(tripActivityAspect);
    TripJoinService proxiedJoinService = joinProxyFactory.getProxy();

    TripScheduleSnapshotService tripScheduleSnapshotService =
        new TripScheduleSnapshotService(
            snapshotRepository,
            schedulePort,
            googleCalendarPort,
            support);
    RecommendationEngine recommendationEngine =
        new RecommendationEngine(schedulePort, googleCalendarPort, holidayProvider);
    TripRecommendationService tripRecommendationServiceRaw =
        new TripRecommendationService(
            support,
            tripScheduleSnapshotService,
            snapshotRepository,
            recommendationRepository,
            recommendationFeedbackRepository,
            recommendationEngine,
            applicationEventPublisher);
    AspectJProxyFactory recommendationProxyFactory =
        new AspectJProxyFactory(tripRecommendationServiceRaw);
    recommendationProxyFactory.addAspect(tripActivityAspect);
    TripRecommendationService tripRecommendationService = recommendationProxyFactory.getProxy();

    TripCommandService tripCommandServiceRaw =
        new TripCommandService(
            tripRepository,
            tripMemberRepository,
            support,
            proxiedJoinService,
            tripJoinHoldService,
            tripRecommendationService,
            tripMemberQueryService,
            userDirectoryPort,
            applicationEventPublisher);
    AspectJProxyFactory commandProxyFactory = new AspectJProxyFactory(tripCommandServiceRaw);
    commandProxyFactory.addAspect(tripActivityAspect);
    TripCommandService tripCommandService = commandProxyFactory.getProxy();

    tripService =
        new TripService(
            tripCommandService, tripQueryService, tripMemberQueryService,
            tripRecommendationService);
  }

  @Test
  void createTrip_allowsDayTrip_zeroNightsOneDay() {
    when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
    when(tripRepository.existsByInviteCode(any())).thenReturn(false);
    when(tripRepository.save(any(Trip.class)))
        .thenAnswer(
            invocation -> {
              Trip saved = invocation.getArgument(0);
              saved.setId(TRIP_ID);
              return saved;
            });

    tripService.createTrip(
        OWNER_ID,
        new CreateTripRequest(
            "서울 당일치기",
            TRIP_START,
            TRIP_END,
            0,
            1,
            4,
            "서울"));

    ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
    verify(tripRepository).save(tripCaptor.capture());
    assertThat(tripCaptor.getValue().getDurationDays()).isEqualTo(1);
  }

  @Test
  void createTrip_issuesOwnerMemberAndInviteCode() {
    when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
    when(tripRepository.existsByInviteCode(any())).thenReturn(false);
    when(tripRepository.save(any(Trip.class)))
        .thenAnswer(
            invocation -> {
              Trip saved = invocation.getArgument(0);
              saved.setId(TRIP_ID);
              return saved;
            });

    var response =
        tripService.createTrip(
            OWNER_ID,
            new CreateTripRequest(
                "제주 3박4일",
                TRIP_START,
                TRIP_END,
                3,
                4,
                6,
                "제주"));

    assertThat(response.tripId()).isEqualTo(TRIP_ID);
    assertThat(response.status()).isEqualTo(TripStatus.ONGOING);
    // create는 SCHEDULE_PENDING — inviteCode는 응답에 없음(방 입장·공유는 activate 후)

    ArgumentCaptor<TripMember> memberCaptor = ArgumentCaptor.forClass(TripMember.class);
    verify(tripMemberRepository).save(memberCaptor.capture());
    assertThat(memberCaptor.getValue().getRole()).isEqualTo(TripMemberRole.OWNER);
    assertThat(memberCaptor.getValue().getStatus()).isEqualTo(TripMemberStatus.SCHEDULE_PENDING);
    assertThat(memberCaptor.getValue().getActivatedAt()).isNull();
    assertThat(response.myMemberStatus()).isEqualTo(TripMemberStatus.SCHEDULE_PENDING);

    ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
    verify(tripRepository).save(tripCaptor.capture());
    assertThat(tripCaptor.getValue().getInviteCode()).hasSize(6);
  }

  @Test
  void createTrip_doesNotTouchSchedules() {
    when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
    when(tripRepository.existsByInviteCode(any())).thenReturn(false);
    when(tripRepository.save(any(Trip.class)))
        .thenAnswer(
            invocation -> {
              Trip saved = invocation.getArgument(0);
              saved.setId(TRIP_ID);
              return saved;
            });

    tripService.createTrip(
        OWNER_ID,
        new CreateTripRequest(
            "제주 3박4일",
            TRIP_START,
            TRIP_END,
            3,
            4,
            6,
            "제주"));

    verify(regularScheduleRepository, never()).existsByUserId(OWNER_ID);
  }

  @Test
  void activateMembership_pendingToActive() {
    TripMember joined =
        new TripMember(trip, owner, TripMemberRole.OWNER, TripMemberStatus.SCHEDULE_PENDING,
            LocalDateTime.now());
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(joined));

    var detail = tripService.activateMembership(TRIP_ID, OWNER_ID);

    assertThat(joined.getStatus()).isEqualTo(TripMemberStatus.ACTIVE);
    assertThat(joined.getActivatedAt()).isNotNull();
    assertThat(detail.myMemberStatus()).isEqualTo(TripMemberStatus.ACTIVE);
  }

  @Test
  void activateMembership_alreadyActive_idempotent() {
    TripMember active = tripMember(owner, TripMemberRole.OWNER);
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(active));

    var detail = tripService.activateMembership(TRIP_ID, OWNER_ID);

    assertThat(detail.myMemberStatus()).isEqualTo(TripMemberStatus.ACTIVE);
  }

  @Test
  void createTrip_rejectsNameOver15Chars() {
    when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));

    assertThatThrownBy(
        () -> tripService.createTrip(
            OWNER_ID,
            new CreateTripRequest(
                "가".repeat(16),
                TRIP_START,
                TRIP_END,
                3,
                4,
                6,
                null)))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  @Test
  void createTrip_rejectsMemberCountOver10() {
    when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));

    assertThatThrownBy(
        () -> tripService.createTrip(
            OWNER_ID,
            new CreateTripRequest(
                "제주 3박4일",
                TRIP_START,
                TRIP_END,
                3,
                4,
                11,
                null)))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  @Test
  void createTrip_requiresProfileName() {
    when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
    org.mockito.Mockito.doThrow(new TripFitException(UserErrorCode.PROFILE_NAME_REQUIRED))
        .when(userProfileService)
        .requireProfileNameComplete(owner);

    assertThatThrownBy(
        () -> tripService.createTrip(
            OWNER_ID,
            new CreateTripRequest(
                "제주",
                TRIP_START,
                TRIP_END,
                3,
                4,
                6,
                null)))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(UserErrorCode.PROFILE_NAME_REQUIRED);
  }

  @Test
  void joinTrip_newMember_touchesLastActivity() {
    ReflectionTestUtils.setField(trip, "lastActivityAt", LocalDateTime.of(2026, 1, 1, 0, 0));
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.empty());
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(1L);

    tripService.joinTrip(MEMBER_ID, new JoinTripRequest("ABC234"));

    assertThat(trip.getLastActivityAt()).isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
    verify(tripMemberRepository).save(any());
    verify(applicationEventPublisher)
        .publishEvent(
            new com.tripfit.tripfit.trip.event.TripJoinCompletedEvent(TRIP_ID, MEMBER_ID));
    verify(applicationEventPublisher, never())
        .publishEvent(any(com.tripfit.tripfit.trip.event.AllMembersSubmittedEvent.class));
  }

  @Test
  void joinTrip_reachesCapacity_alsoPublishesAllMembersSubmitted() {
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.empty());
    // join 전 정원 체크에서 5<6 통과 — join 후 판정은 재조회 없이 이 값+1(=6)로 계산됨
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(5L);

    tripService.joinTrip(MEMBER_ID, new JoinTripRequest("ABC234"));

    verify(applicationEventPublisher)
        .publishEvent(new com.tripfit.tripfit.trip.event.AllMembersSubmittedEvent(TRIP_ID));
  }

  @Test
  void joinTrip_requiresProfileName() {
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    org.mockito.Mockito.doThrow(new TripFitException(UserErrorCode.PROFILE_NAME_REQUIRED))
        .when(userProfileService)
        .requireProfileNameComplete(member);

    assertThatThrownBy(() -> tripService.joinTrip(MEMBER_ID, new JoinTripRequest("ABC234")))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(UserErrorCode.PROFILE_NAME_REQUIRED);

    verify(tripMemberRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void joinTrip_succeedsWithNoSchedules() {
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.empty());
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(1L);

    var detail = tripService.joinTrip(MEMBER_ID, new JoinTripRequest("ABC234"));

    assertThat(detail.myMemberStatus()).isEqualTo(TripMemberStatus.ACTIVE);
    verify(regularScheduleRepository, never()).existsByUserId(MEMBER_ID);
  }

  @Test
  void joinTrip_idempotentForExistingMemberOnConfirmedTrip() {
    ReflectionTestUtils.setField(trip, "status", TripStatus.CONFIRMED);
    TripMember existing = tripMember(member, TripMemberRole.MEMBER);
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.of(existing));

    var summary = tripService.joinTrip(MEMBER_ID, new JoinTripRequest("ABC234"));

    assertThat(summary.tripId()).isEqualTo(TRIP_ID);
    verify(tripMemberRepository, never()).save(any());
  }

  @Test
  void joinTrip_rejectsNewMemberOnConfirmedTrip() {
    ReflectionTestUtils.setField(trip, "status", TripStatus.CONFIRMED);
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> tripService.joinTrip(MEMBER_ID, new JoinTripRequest("ABC234")))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_ALREADY_CONFIRMED);
  }

  @Test
  void joinTrip_rejectsWhenMemberFull() {
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.empty());
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(6L);

    assertThatThrownBy(() -> tripService.joinTrip(MEMBER_ID, new JoinTripRequest("ABC234")))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_MEMBER_FULL);
  }

  // #35 — join()이 유효한 hold를 갖고 있으면, 실제 카운트가 이미 정원에 도달했어도(hold가 미리 보장한 자리이므로)
  // 재확인 없이 성공하고 hold를 소비(release)한다
  @Test
  void joinTrip_withActiveHold_skipsCapacityRecheckAndReleasesHold() {
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.empty());
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(6L);
    when(tripJoinHoldService.hasActiveHold(TRIP_ID, MEMBER_ID)).thenReturn(true);

    tripService.joinTrip(MEMBER_ID, new JoinTripRequest("ABC234"));

    verify(tripMemberRepository).save(any());
    verify(tripJoinHoldService, never()).countActiveHolds(any());
    verify(tripJoinHoldService).release(TRIP_ID, MEMBER_ID);
  }

  // hold 없이 join하는 호출자는 확정 멤버 수뿐 아니라 다른 사용자의 활성 hold 수까지 합쳐 정원을 체크해야
  // 한다 — 그렇지 않으면 hold로 자리를 예약해둔 다른 사용자의 자리를 가로챌 수 있음
  @Test
  void joinTrip_withoutHold_countsOtherActiveHoldsTowardCapacity() {
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.empty());
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(5L);
    when(tripJoinHoldService.hasActiveHold(TRIP_ID, MEMBER_ID)).thenReturn(false);
    when(tripJoinHoldService.countActiveHolds(TRIP_ID)).thenReturn(1L);

    assertThatThrownBy(() -> tripService.joinTrip(MEMBER_ID, new JoinTripRequest("ABC234")))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_MEMBER_FULL);

    verify(tripMemberRepository, never()).save(any());
  }

  @Test
  void joinTrip_withoutHoldAndRoomLeftAfterOtherHolds_succeeds() {
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.empty());
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(4L);
    when(tripJoinHoldService.hasActiveHold(TRIP_ID, MEMBER_ID)).thenReturn(false);
    when(tripJoinHoldService.countActiveHolds(TRIP_ID)).thenReturn(1L);

    tripService.joinTrip(MEMBER_ID, new JoinTripRequest("ABC234"));

    verify(tripMemberRepository).save(any());
  }

  // #35 — 초대코드 미리보기 + hold 생성. 신규 참여자는 tryHold를 호출해 자리를 선점한다
  @Test
  void previewAndHold_newMember_triesHoldAndReturnsPreview() {
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.empty());
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(3L);
    when(tripJoinHoldService.tryHold(TRIP_ID, MEMBER_ID, 3L, 6)).thenReturn(true);

    var preview = tripService.previewAndHold(MEMBER_ID, new JoinTripRequest("ABC234"));

    assertThat(preview.tripId()).isEqualTo(TRIP_ID);
    assertThat(preview.memberCount()).isEqualTo(6);
    verify(tripJoinHoldService).tryHold(TRIP_ID, MEMBER_ID, 3L, 6);
  }

  // hold 발급 자체가 원자적으로 실패하면(자리 없음) 409 — 신규 ErrorCode 없이 기존 TRIP_MEMBER_FULL 재사용
  @Test
  void previewAndHold_rejectsWhenHoldFails() {
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.empty());
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(6L);
    when(tripJoinHoldService.tryHold(TRIP_ID, MEMBER_ID, 6L, 6)).thenReturn(false);

    assertThatThrownBy(
        () -> tripService.previewAndHold(MEMBER_ID, new JoinTripRequest("ABC234")))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_MEMBER_FULL);
  }

  // 이미 ACTIVE 멤버가 재호출하면 hold를 만들지 않고 동일 정보만 반환(idempotent 재진입)
  @Test
  void previewAndHold_existingActiveMember_skipsHold() {
    TripMember existing = tripMember(member, TripMemberRole.MEMBER);
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.of(existing));

    var preview = tripService.previewAndHold(MEMBER_ID, new JoinTripRequest("ABC234"));

    assertThat(preview.tripId()).isEqualTo(TRIP_ID);
    verify(tripJoinHoldService, never()).tryHold(
        any(),
        any(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyInt());
  }

  // CONFIRMED 방은 hold 시도 자체를 하지 않고 바로 거부
  @Test
  void previewAndHold_rejectsConfirmedTripWithoutAttemptingHold() {
    trip.confirm(LocalDate.now().plusDays(2), LocalDate.now().plusDays(4), null, null, null);
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
        () -> tripService.previewAndHold(MEMBER_ID, new JoinTripRequest("ABC234")))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_ALREADY_CONFIRMED);

    verify(tripJoinHoldService, never()).tryHold(
        any(),
        any(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyInt());
  }

  // 플로우 첫 화면 이탈 — hold 서비스에 그대로 위임(idempotent no-op 포함은 TripJoinHoldServiceTest가 커버)
  @Test
  void releaseJoinHold_delegatesToHoldService() {
    tripService.releaseJoinHold(TRIP_ID, MEMBER_ID);

    verify(tripJoinHoldService).release(TRIP_ID, MEMBER_ID);
  }

  @Test
  void patchTrip_rejectsNonOwner() {
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));

    assertThatThrownBy(
        () -> tripService.patchTrip(
            TRIP_ID,
            MEMBER_ID,
            patchRequest()))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_FORBIDDEN);
  }

  @Test
  void patchTrip_rejectsWhenNotOngoing() {
    ReflectionTestUtils.setField(trip, "status", TripStatus.CONFIRMED);
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));

    assertThatThrownBy(
        () -> tripService.patchTrip(
            TRIP_ID,
            OWNER_ID,
            patchRequest()))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_NOT_ONGOING);
  }

  @Test
  void patchTrip_deletesRecommendationsWhenDurationChanges() {
    ReflectionTestUtils.setField(trip, "lastActivityAt", LocalDateTime.of(2026, 1, 1, 0, 0));
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    TripMember ownerMember = tripMember(owner, TripMemberRole.OWNER);
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(ownerMember));

    tripService.patchTrip(
        TRIP_ID,
        OWNER_ID,
        new PatchTripRequest(
            "제주",
            2,
            3,
            6,
            "제주"));

    verify(recommendationRepository).deleteByTripId(TRIP_ID);
    assertThat(trip.getDurationDays()).isEqualTo(3);
    assertThat(trip.getLastActivityAt()).isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
    verify(applicationEventPublisher)
        .publishEvent(new com.tripfit.tripfit.trip.event.TripInfoChangedEvent(TRIP_ID));
  }

  @Test
  void patchTrip_noOp_doesNotPublishTripInfoChanged() {
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    TripMember ownerMember = tripMember(owner, TripMemberRole.OWNER);
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(ownerMember));

    tripService.patchTrip(
        TRIP_ID,
        OWNER_ID,
        new PatchTripRequest(trip.getName(), trip.getDurationNights(), trip.getDurationDays(),
            trip.getMemberCount(), null));

    verify(applicationEventPublisher, never())
        .publishEvent(any(com.tripfit.tripfit.trip.event.TripInfoChangedEvent.class));
  }

  @Test
  void createTrip_allowsUndecidedDuration() {
    when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
    when(tripRepository.existsByInviteCode(any())).thenReturn(false);
    ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
    when(tripRepository.save(tripCaptor.capture()))
        .thenAnswer(
            invocation -> {
              Trip saved = invocation.getArgument(0);
              saved.setId(TRIP_ID);
              return saved;
            });

    tripService.createTrip(
        OWNER_ID,
        new CreateTripRequest(
            "제주",
            TRIP_START,
            TRIP_END,
            null,
            null,
            6,
            null));

    assertThat(tripCaptor.getValue().getDurationDays()).isNull();
  }

  @Test
  void createTrip_rejectsMismatchedNightsAndDays() {
    when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));

    assertThatThrownBy(
        () -> tripService.createTrip(
            OWNER_ID,
            new CreateTripRequest(
                "제주",
                TRIP_START,
                TRIP_END,
                3,
                6,
                6,
                null)))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  @Test
  void createTrip_allowsNightsPlusTwoDays() {
    when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
    when(tripRepository.existsByInviteCode(any())).thenReturn(false);
    when(tripRepository.save(any(Trip.class)))
        .thenAnswer(
            invocation -> {
              Trip saved = invocation.getArgument(0);
              saved.setId(TRIP_ID);
              return saved;
            });

    tripService.createTrip(
        OWNER_ID,
        new CreateTripRequest(
            "제주",
            TRIP_START,
            TRIP_END,
            3,
            5,
            6,
            null));

    ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
    verify(tripRepository).save(tripCaptor.capture());
    assertThat(tripCaptor.getValue().getDurationNights()).isEqualTo(3);
    assertThat(tripCaptor.getValue().getDurationDays()).isEqualTo(5);
  }

  @Test
  void updatePin_togglesPinnedAndPinnedAt() {
    TripMember membership = tripMember(owner, TripMemberRole.OWNER);
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(membership));

    var summary =
        tripService.updatePin(TRIP_ID, OWNER_ID, new UpdateTripPinRequest(true));

    assertThat(summary.pinned()).isTrue();
    assertThat(membership.isPinned()).isTrue();
    assertThat(membership.getPinnedAt()).isNotNull();
  }

  @Test
  void listMyTrips_ongoing_usesPreviewBatchQuery() {
    TripMember membership = tripMember(owner, TripMemberRole.OWNER);
    when(tripMemberRepository.findOngoingMembershipsByUserId(eq(OWNER_ID), any(LocalDate.class)))
        .thenReturn(List.of(membership));
    when(tripMemberRepository.countMembersByTripIds(any()))
        .thenReturn(List.of(countProjection(1, 0)));
    when(tripMemberRepository.findMemberPreviewsByTripIds(any())).thenReturn(List.of());

    var response =
        tripService.listMyTrips(
            OWNER_ID,
            new TripListQuery(TripListScope.ONGOING, Optional.empty(), false));

    assertThat(response.trips()).hasSize(1);
    assertThat(response.trips().get(0).activeMemberCount()).isEqualTo(0);
  }

  @Test
  void listMyTrips_all_withStatusFilter() {
    TripMember membership = tripMember(owner, TripMemberRole.OWNER);
    when(
        tripMemberRepository.findAllMembershipsByUserId(
            eq(OWNER_ID),
            any(LocalDate.class),
            eq("ONGOING"),
            eq(true)))
        .thenReturn(List.of(membership));
    when(tripMemberRepository.countMembersByTripIds(any()))
        .thenReturn(List.of(countProjection(1, 0)));
    when(tripMemberRepository.findMemberPreviewsByTripIds(any())).thenReturn(List.of());

    var response =
        tripService.listMyTrips(
            OWNER_ID,
            new TripListQuery(TripListScope.ALL, Optional.of(TripStatus.ONGOING), true));

    assertThat(response.trips()).hasSize(1);
    assertThat(response.trips().get(0).myRole()).isEqualTo(TripMemberRole.OWNER);
  }

  private static TripMemberCountProjection countProjection(int joinedMemberCount, int active) {
    return new TripMemberCountProjection() {
      @Override
      public UUID getTripId() {
        return TRIP_ID;
      }

      @Override
      public long getJoinedMemberCount() {
        return joinedMemberCount;
      }

      @Override
      public long getActiveCount() {
        return active;
      }
    };
  }

  @Test
  void listMembers_assignsDuplicateDisplayNames() {
    User dup1 = user(UUID.fromString("550e8400-e29b-41d4-a716-446655440003"), "홍", "길동");
    User dup2 = user(UUID.fromString("550e8400-e29b-41d4-a716-446655440004"), "홍", "길동");
    TripMember m1 =
        tripMember(dup1, TripMemberRole.MEMBER, LocalDateTime.of(2026, 7, 1, 10, 0));
    TripMember m2 =
        tripMember(dup2, TripMemberRole.MEMBER, LocalDateTime.of(2026, 7, 2, 10, 0));

    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(tripMember(owner, TripMemberRole.OWNER)));
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(List.of(m1, m2));

    var response = tripService.listMembers(TRIP_ID, OWNER_ID);

    assertThat(response.members())
        .extracting(m -> m.displayName())
        .containsExactly("홍길동", "홍길동(2)");
  }

  @Test
  void removeMember_softDeletesMemberAndReturnsRemainingList() {
    TripMember ownerMembership = tripMember(owner, TripMemberRole.OWNER);
    TripMember target = tripMember(member, TripMemberRole.MEMBER);
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.of(target));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(ownerMembership));
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(TRIP_ID))
        .thenReturn(List.of(ownerMembership));

    var response = tripService.removeMember(TRIP_ID, OWNER_ID, MEMBER_ID);

    assertThat(target.getDeletedAt()).isNotNull();
    assertThat(response.activeMemberCount()).isEqualTo(1);
    assertThat(response.members()).extracting(m -> m.userId()).containsExactly(OWNER_ID);
    verify(recommendationRepository, never()).deleteByTripId(any());
  }

  @Test
  void removeMember_whenTargetIsOwner_throwsCannotRemoveOwner() {
    TripMember ownerMembership = tripMember(owner, TripMemberRole.OWNER);
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(ownerMembership));

    assertThatThrownBy(() -> tripService.removeMember(TRIP_ID, OWNER_ID, OWNER_ID))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.CANNOT_REMOVE_OWNER);
  }

  @Test
  void removeMember_whenTargetMissing_throwsNotFound() {
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> tripService.removeMember(TRIP_ID, OWNER_ID, MEMBER_ID))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_MEMBER_NOT_FOUND);
  }

  @Test
  void removeMember_whenTripNotOngoing_throwsConflict() {
    ReflectionTestUtils.setField(trip, "status", TripStatus.CONFIRMED);
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));

    assertThatThrownBy(() -> tripService.removeMember(TRIP_ID, OWNER_ID, MEMBER_ID))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_NOT_ONGOING);
  }

  @Test
  void leaveTrip_softDeletesMembership_touchesLastActivity() {
    ReflectionTestUtils.setField(trip, "lastActivityAt", LocalDateTime.of(2026, 1, 1, 0, 0));
    TripMember target = tripMember(member, TripMemberRole.MEMBER);
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.of(target));

    tripService.leaveTrip(TRIP_ID, MEMBER_ID);

    assertThat(target.getDeletedAt()).isNotNull();
    assertThat(trip.getLastActivityAt()).isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
  }

  @Test
  void leaveTrip_whenTripConfirmedOrTerminated_stillSucceeds() {
    ReflectionTestUtils.setField(trip, "status", TripStatus.CONFIRMED);
    TripMember target = tripMember(member, TripMemberRole.MEMBER);
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.of(target));

    tripService.leaveTrip(TRIP_ID, MEMBER_ID);

    assertThat(target.getDeletedAt()).isNotNull();
  }

  @Test
  void leaveTrip_whenCallerIsOwner_throwsOwnerCannotLeave() {
    TripMember ownerMembership = tripMember(owner, TripMemberRole.OWNER);
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(ownerMembership));

    assertThatThrownBy(() -> tripService.leaveTrip(TRIP_ID, OWNER_ID))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_OWNER_CANNOT_LEAVE);
    assertThat(ownerMembership.getDeletedAt()).isNull();
  }

  @Test
  void leaveTrip_whenCallerNotMember_throwsAccessDenied() {
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> tripService.leaveTrip(TRIP_ID, MEMBER_ID))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_ACCESS_DENIED);
  }

  @Test
  void leaveTrip_whenTripNotFound_throwsNotFound() {
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> tripService.leaveTrip(TRIP_ID, MEMBER_ID))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_NOT_FOUND);
  }

  @Test
  void leaveAllActiveTripsAsMember_leavesEveryActiveMembership() {
    UUID tripId2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440011");
    Trip trip2 = otherTrip(tripId2);
    TripMember membership1 = tripMember(member, TripMemberRole.MEMBER);
    TripMember membership2 =
        new TripMember(trip2, member, TripMemberRole.MEMBER, TripMemberStatus.ACTIVE,
            LocalDateTime.now());

    when(
        tripMemberRepository
            .findByUser_IdAndRoleAndDeletedAtIsNull(MEMBER_ID, TripMemberRole.MEMBER))
        .thenReturn(List.of(membership1, membership2));
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripRepository.findByIdAndDeletedAtIsNull(tripId2)).thenReturn(Optional.of(trip2));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.of(membership1));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(tripId2, MEMBER_ID))
        .thenReturn(Optional.of(membership2));

    tripService.leaveAllActiveTripsAsMember(MEMBER_ID);

    assertThat(membership1.getDeletedAt()).isNotNull();
    assertThat(membership2.getDeletedAt()).isNotNull();
  }

  @Test
  void leaveAllActiveTripsAsMember_whenNoMemberships_doesNothing() {
    when(
        tripMemberRepository
            .findByUser_IdAndRoleAndDeletedAtIsNull(MEMBER_ID, TripMemberRole.MEMBER))
        .thenReturn(List.of());

    tripService.leaveAllActiveTripsAsMember(MEMBER_ID);

    verify(tripRepository, never()).findByIdAndDeletedAtIsNull(any());
  }

  @Test
  void deleteAllOwnedActiveTrips_deletesEveryOwnedTrip() {
    UUID tripId2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440012");
    Trip trip2 = otherTrip(tripId2);
    TripMember ownerMembership1 = tripMember(owner, TripMemberRole.OWNER);
    TripMember ownerMembership2 =
        new TripMember(trip2, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE,
            LocalDateTime.now());

    when(
        tripMemberRepository.findByUser_IdAndRoleAndDeletedAtIsNull(OWNER_ID, TripMemberRole.OWNER))
        .thenReturn(List.of(ownerMembership1, ownerMembership2));
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripRepository.findByIdAndDeletedAtIsNull(tripId2)).thenReturn(Optional.of(trip2));
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(List.of());
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId2)).thenReturn(List.of());

    tripService.deleteAllOwnedActiveTrips(OWNER_ID);

    assertThat(trip.getDeletedAt()).isNotNull();
    assertThat(trip2.getDeletedAt()).isNotNull();
  }

  @Test
  void getMemberScheduleCalendar_whenExpired_readsSnapshots() {
    ReflectionTestUtils.setField(trip, "status", TripStatus.EXPIRED);
    TripMember ownerMembership = tripMember(owner, TripMemberRole.OWNER);
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(ownerMembership));
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(TRIP_ID))
        .thenReturn(List.of(ownerMembership));
    TripMemberScheduleSnapshot snap =
        TripMemberScheduleSnapshot.create(
            trip,
            owner,
            TRIP_START.plusDays(2),
            ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.POSSIBLE,
            ScheduleStatus.POSSIBLE,
            false,
            LocalDateTime.now());
    when(snapshotRepository.findByTrip_IdOrderByUser_IdAscScheduleDateAsc(TRIP_ID))
        .thenReturn(List.of(snap));

    var response = tripService.getMemberScheduleCalendar(TRIP_ID, OWNER_ID);

    assertThat(response.readOnly()).isTrue();
    assertThat(response.members()).hasSize(1);
    assertThat(response.members().getFirst().days()).hasSize(1);
    assertThat(response.members().getFirst().days().getFirst().morningStatus())
        .isEqualTo(ScheduleStatus.IMPOSSIBLE);
    verify(regularScheduleRepository, never()).findByUserIdOrderByCreatedAtAsc(any());
  }

  private static PatchTripRequest patchRequest() {
    return new PatchTripRequest(
        "제주",
        3,
        4,
        6,
        "제주");
  }

  private Trip ongoingTrip() {
    Trip t =
        new Trip(
            owner,
            "제주 3박4일",
            TRIP_START,
            TRIP_END,
            3,
            4,
            6,
            "ABC234",
            TripStatus.ONGOING);
    t.setId(TRIP_ID);
    return t;
  }

  private Trip otherTrip(UUID id) {
    Trip t =
        new Trip(
            owner,
            "부산 2박3일",
            OTHER_TRIP_START,
            OTHER_TRIP_END,
            2,
            3,
            4,
            "XYZ999",
            TripStatus.ONGOING);
    t.setId(id);
    return t;
  }

  private TripMember tripMember(User user, TripMemberRole role) {
    return tripMember(user, role, LocalDateTime.now());
  }

  private TripMember tripMember(User user, TripMemberRole role, LocalDateTime joinedAt) {
    return new TripMember(trip, user, role, TripMemberStatus.ACTIVE, joinedAt);
  }

  private static User user(UUID id, String lastName, String firstName) {
    User u = new User("sub-" + id, SocialProvider.GOOGLE, "u@example.com", "nick", null);
    u.setId(id);
    u.applyProfilePatch(firstName, lastName, null);
    return u;
  }
}
