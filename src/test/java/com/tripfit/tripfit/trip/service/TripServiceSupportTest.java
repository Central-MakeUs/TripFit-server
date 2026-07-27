package com.tripfit.tripfit.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import org.junit.jupiter.api.Test;

class TripServiceSupportTest {

  @Test
  void resolveDurationDays_bothNull_returnsNull() {
    assertThat(TripServiceSupport.resolveDurationDays(null, null)).isNull();
  }

  @Test
  void resolveDurationDays_nightsPlusOne_returnsDays() {
    assertThat(TripServiceSupport.resolveDurationDays(3, 4)).isEqualTo(4);
  }

  @Test
  void resolveDurationDays_nightsPlusTwo_returnsDays() {
    assertThat(TripServiceSupport.resolveDurationDays(3, 5)).isEqualTo(5);
  }

  @Test
  void resolveDurationDays_dayTrip_allowsOneOrTwoDays() {
    assertThat(TripServiceSupport.resolveDurationDays(0, 1)).isEqualTo(1);
    assertThat(TripServiceSupport.resolveDurationDays(0, 2)).isEqualTo(2);
  }

  @Test
  void resolveDurationDays_nightsPlusThree_throwsInvalidInput() {
    assertThatThrownBy(() -> TripServiceSupport.resolveDurationDays(3, 6))
        .isInstanceOf(TripFitException.class)
        .extracting(e -> ((TripFitException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  @Test
  void resolveDurationDays_daysLessThanNightsPlusOne_throwsInvalidInput() {
    assertThatThrownBy(() -> TripServiceSupport.resolveDurationDays(3, 3))
        .isInstanceOf(TripFitException.class);
  }

  @Test
  void resolveDurationDays_onlyOneSideNull_throwsInvalidInput() {
    assertThatThrownBy(() -> TripServiceSupport.resolveDurationDays(3, null))
        .isInstanceOf(TripFitException.class);
    assertThatThrownBy(() -> TripServiceSupport.resolveDurationDays(null, 4))
        .isInstanceOf(TripFitException.class);
  }

  @Test
  void resolveDurationDays_negativeNights_throwsInvalidInput() {
    assertThatThrownBy(() -> TripServiceSupport.resolveDurationDays(-1, 1))
        .isInstanceOf(TripFitException.class);
  }
}
