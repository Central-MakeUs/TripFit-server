package com.tripfit.tripfit.trip.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TripSeatTest {

  @Test
  void newTrip_startsWithOwnerSeatOccupied() {
    assertThat(tripWithCapacity(3).getJoinedMemberCount()).isEqualTo(1);
  }

  @Test
  void tryOccupySeat_fillsUpToCapacityThenRefuses() {
    Trip trip = tripWithCapacity(3);

    assertThat(trip.tryOccupySeat()).isTrue();
    assertThat(trip.tryOccupySeat()).isTrue();
    assertThat(trip.tryOccupySeat()).isFalse();

    assertThat(trip.getJoinedMemberCount()).isEqualTo(3);
  }

  @Test
  void releaseSeat_freesSeatForNextMember() {
    Trip trip = tripWithCapacity(2);
    trip.tryOccupySeat();

    trip.releaseSeat();

    assertThat(trip.getJoinedMemberCount()).isEqualTo(1);
    assertThat(trip.tryOccupySeat()).isTrue();
  }

  @Test
  void releaseSeat_whenOnlyOwnerSeatRemains_failsInsteadOfGoingBelowOne() {
    Trip trip = tripWithCapacity(3);

    assertThatThrownBy(trip::releaseSeat).isInstanceOf(IllegalStateException.class);
    assertThat(trip.getJoinedMemberCount()).isEqualTo(1);
  }

  private static Trip tripWithCapacity(int memberCount) {
    User owner = new User("sub", SocialProvider.GOOGLE, "owner@example.com", "nick", null);
    return new Trip(
        owner,
        "좌석 테스트",
        LocalDate.now().plusDays(7),
        LocalDate.now().plusDays(30),
        3,
        4,
        memberCount,
        "SEAT01",
        TripStatus.ONGOING);
  }
}
