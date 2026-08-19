package com.tripfit.tripfit.trip.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

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
    ReflectionTestUtils.setField(trip, "lastActivityAt", LocalDateTime.of(2026, 1, 1, 0, 0));
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
            .getAnnotation(TripActivity.class));

    assertThat(trip.getLastActivityAt()).isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
    verify(tripRepository).findByIdAndDeletedAtIsNull(TRIP_ID);
  }

  private static Trip sampleTrip() {
    User owner = new User("sub", SocialProvider.GOOGLE, "u@example.com", "nick", null);
    Trip t =
        new Trip(
            owner,
            "제주",
            LocalDate.now(),
            LocalDate.now().plusDays(9),
            3,
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
  }
}
