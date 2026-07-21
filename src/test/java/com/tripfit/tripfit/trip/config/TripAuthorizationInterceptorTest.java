package com.tripfit.tripfit.trip.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.jwt.JwtAuthentication;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.exception.UserErrorCode;
import com.tripfit.tripfit.user.service.UserSummaryService;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(MockitoExtension.class)
class TripAuthorizationInterceptorTest {

  private static final UUID TRIP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

  private static final UUID USER_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");

  @Mock
  private TripRepository tripRepository;

  @Mock
  private TripMemberRepository tripMemberRepository;

  @Mock
  private UserSummaryService userSummaryService;

  private TripAuthorizationInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor =
        new TripAuthorizationInterceptor(tripRepository, tripMemberRepository, userSummaryService);
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthentication(USER_ID));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void preHandle_noAnnotation_passesWithoutRepositoryCalls() throws Exception {
    HandlerMethod handlerMethod = handlerMethod("unannotated", UUID.class);

    boolean allowed =
        interceptor.preHandle(
            requestWithTripId(TRIP_ID),
            new MockHttpServletResponse(),
            handlerMethod);

    assertThat(allowed).isTrue();
    verify(tripRepository, never()).existsByIdAndDeletedAtIsNull(TRIP_ID);
  }

  @Test
  void preHandle_tripMemberOnly_activeMember_passes() throws Exception {
    when(tripRepository.existsByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(true);
    TripMember membership = membership(TripMemberStatus.RESPONDED);
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, USER_ID))
        .thenReturn(Optional.of(membership));

    boolean allowed =
        interceptor.preHandle(
            requestWithTripId(TRIP_ID),
            new MockHttpServletResponse(),
            handlerMethod("memberOnly", UUID.class));

    assertThat(allowed).isTrue();
    verify(userSummaryService).requireCanEnterRoom(USER_ID);
  }

  @Test
  void preHandle_tripMemberOnly_joined_throwsConfirmRequired() throws Exception {
    when(tripRepository.existsByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(true);
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, USER_ID))
        .thenReturn(Optional.of(membership(TripMemberStatus.JOINED)));

    assertThatThrownBy(
        () -> interceptor.preHandle(
            requestWithTripId(TRIP_ID),
            new MockHttpServletResponse(),
            handlerMethod("memberOnly", UUID.class)))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(UserErrorCode.SCHEDULE_CONFIRM_REQUIRED);
    verify(userSummaryService, never()).requireCanEnterRoom(USER_ID);
  }

  @Test
  void preHandle_tripMemberOnly_notMember_throwsAccessDenied() throws Exception {
    when(tripRepository.existsByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(true);
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, USER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
        () -> interceptor.preHandle(
            requestWithTripId(TRIP_ID),
            new MockHttpServletResponse(),
            handlerMethod("memberOnly", UUID.class)))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_ACCESS_DENIED);
    verify(userSummaryService, never()).requireCanEnterRoom(USER_ID);
  }

  @Test
  void preHandle_tripOwnerOnly_owner_passesWithoutEntryGate() throws Exception {
    when(tripRepository.existsByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(true);
    when(tripRepository.existsByIdAndOwner_IdAndDeletedAtIsNull(TRIP_ID, USER_ID))
        .thenReturn(true);

    boolean allowed =
        interceptor.preHandle(
            requestWithTripId(TRIP_ID),
            new MockHttpServletResponse(),
            handlerMethod("ownerOnly", UUID.class));

    assertThat(allowed).isTrue();
    verify(userSummaryService, never()).requireCanEnterRoom(USER_ID);
  }

  @Test
  void preHandle_tripOwnerOnly_notOwner_throwsForbidden() throws Exception {
    when(tripRepository.existsByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(true);
    when(tripRepository.existsByIdAndOwner_IdAndDeletedAtIsNull(TRIP_ID, USER_ID))
        .thenReturn(false);

    assertThatThrownBy(
        () -> interceptor.preHandle(
            requestWithTripId(TRIP_ID),
            new MockHttpServletResponse(),
            handlerMethod("ownerOnly", UUID.class)))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_FORBIDDEN);
    verify(userSummaryService, never()).requireCanEnterRoom(USER_ID);
  }

  @Test
  void preHandle_entryGateFails_throwsScheduleEntryRequired() throws Exception {
    when(tripRepository.existsByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(true);
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, USER_ID))
        .thenReturn(Optional.of(membership(TripMemberStatus.RESPONDED)));
    org.mockito.Mockito.doThrow(
        new TripFitException(UserErrorCode.SCHEDULE_ENTRY_REQUIRED))
        .when(userSummaryService)
        .requireCanEnterRoom(USER_ID);

    assertThatThrownBy(
        () -> interceptor.preHandle(
            requestWithTripId(TRIP_ID),
            new MockHttpServletResponse(),
            handlerMethod("memberOnly", UUID.class)))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(UserErrorCode.SCHEDULE_ENTRY_REQUIRED);
  }

  @Test
  void preHandle_missingTrip_throwsNotFound() throws Exception {
    when(tripRepository.existsByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(false);

    assertThatThrownBy(
        () -> interceptor.preHandle(
            requestWithTripId(TRIP_ID),
            new MockHttpServletResponse(),
            handlerMethod("memberOnly", UUID.class)))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_NOT_FOUND);
  }

  @Test
  void preHandle_unauthenticated_throwsInvalidToken() throws Exception {
    SecurityContextHolder.clearContext();

    assertThatThrownBy(
        () -> interceptor.preHandle(
            requestWithTripId(TRIP_ID),
            new MockHttpServletResponse(),
            handlerMethod("memberOnly", UUID.class)))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_INVALID_TOKEN);
  }

  @Test
  void preHandle_nonHandlerMethod_passes() {
    boolean allowed =
        interceptor.preHandle(
            requestWithTripId(TRIP_ID),
            new MockHttpServletResponse(),
            new Object());

    assertThat(allowed).isTrue();
    verify(tripRepository, never()).existsByIdAndDeletedAtIsNull(TRIP_ID);
  }

  private static MockHttpServletRequest requestWithTripId(UUID tripId) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
        Map.of("tripId", tripId.toString()));
    return request;
  }

  private static HandlerMethod handlerMethod(String methodName, Class<?>... parameterTypes)
      throws NoSuchMethodException {
    Method method = StubController.class.getDeclaredMethod(methodName, parameterTypes);
    return new HandlerMethod(new StubController(), method);
  }

  private static TripMember membership(TripMemberStatus status) {
    User user = new User("sub", SocialProvider.GOOGLE, "u@example.com", "nick", null);
    user.setId(USER_ID);
    return new TripMember(null, user, TripMemberRole.MEMBER, status, LocalDateTime.now());
  }

  @SuppressWarnings("unused")
  static class StubController {

    @TripMemberOnly
    void memberOnly(@PathVariable UUID tripId) {}

    @TripOwnerOnly
    void ownerOnly(@PathVariable UUID tripId) {}

    void unannotated(@PathVariable UUID tripId) {}
  }
}
