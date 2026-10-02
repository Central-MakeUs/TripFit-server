package com.tripfit.tripfit.trip.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.trip.repository.TripRepository;
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

  @BeforeEach
  void setUp() {
    aspect = new TripActivityAspect(tripRepository);
  }

  @Test
  void touchLastActivity_resolvesTripIdFromParameter() throws Exception {
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod())
        .thenReturn(DummyService.class.getMethod("mutate", UUID.class, UUID.class));
    when(joinPoint.getArgs()).thenReturn(new Object[] {TRIP_ID, UUID.randomUUID()});

    aspect.touchLastActivity(
        joinPoint,
        DummyService.class.getMethod("mutate", UUID.class, UUID.class)
            .getAnnotation(TripActivity.class));

    verify(tripRepository).touchLastActivity(eq(TRIP_ID), any());
  }

  @Test
  void touchLastActivity_updatesDirectlyWithoutLoadingTheEntity() throws Exception {
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod())
        .thenReturn(DummyService.class.getMethod("mutate", UUID.class, UUID.class));
    when(joinPoint.getArgs()).thenReturn(new Object[] {TRIP_ID, UUID.randomUUID()});

    aspect.touchLastActivity(
        joinPoint,
        DummyService.class.getMethod("mutate", UUID.class, UUID.class)
            .getAnnotation(TripActivity.class));

    // 엔티티를 불러와 고치면 버전 번호가 올라가 동시 참여를 실패시키므로, 조회 없이 UPDATE만 나가야 한다.
    verify(tripRepository, never()).findByIdAndDeletedAtIsNull(any());
    verify(tripRepository, never()).save(any());
  }

  static class DummyService {

    @TripActivity(tripIdParam = "tripId")
    public void mutate(UUID tripId, UUID userId) {}
  }
}
