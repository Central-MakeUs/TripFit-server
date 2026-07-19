package com.tripfit.tripfit.trip.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripActivityAspectTest {

  private static final UUID TRIP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

  @Mock
  private TripRepository tripRepository;

  @Mock
  private JoinPoint joinPoint;

  @Mock
  private MethodSignature methodSignature;

  private TripActivityAspect aspect;

  private Trip trip;

  @BeforeEach
  void setUp() {
    aspect = new TripActivityAspect(tripRepository);
    trip = sampleTrip();
    trip.setLastActivityAt(LocalDateTime.of(2026, 1, 1, 0, 0));
  }

  @Test
  void touchLastActivity_resolvesTripIdFromParameter() throws Exception {
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod())
        .thenReturn(DummyService.class.getMethod("mutate", UUID.class, UUID.class));
    when(joinPoint.getArgs()).thenReturn(new Object[] {TRIP_ID, UUID.randomUUID()});
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));

    aspect.touchLastActivity(
        joinPoint,
        DummyService.class.getMethod("mutate", UUID.class, UUID.class)
            .getAnnotation(TripActivity.class),
        null);

    assertThat(trip.getLastActivityAt()).isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
    verify(tripRepository).findByIdAndDeletedAtIsNull(TRIP_ID);
  }

  @Test
  void touchLastActivity_resolvesTripIdFromReturnValue() throws Exception {
    TripDetailResponse response = sampleDetailResponse();
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));

    aspect.touchLastActivity(
        joinPoint,
        DummyService.class.getMethod("join").getAnnotation(TripActivity.class),
        response);

    assertThat(trip.getLastActivityAt()).isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
  }

  private static TripDetailResponse sampleDetailResponse() {
    return new TripDetailResponse(
        TRIP_ID,
        "제주",
        null,
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 10),
        4,
        6,
        TripStatus.ONGOING,
        "ABC123",
        null,
        null,
        null,
        LocalDateTime.now(),
        false,
        null,
        null,
        0,
        1,
        1.0 / 6.0);
  }

  private static Trip sampleTrip() {
    User owner = new User("sub", SocialProvider.GOOGLE, "u@example.com", "nick", null);
    Trip t =
        new Trip(
            owner,
            "제주",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 10),
            4,
            6,
            "ABC123",
            TripStatus.ONGOING);
    t.setId(TRIP_ID);
    return t;
  }

  static class DummyService {

    @TripActivity(tripIdParam = "tripId")
    public void mutate(UUID tripId, UUID userId) {}

    @TripActivity(tripIdFromReturn = true)
    public TripDetailResponse join() {
      return sampleDetailResponse();
    }
  }
}
