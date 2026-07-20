package com.tripfit.tripfit.trip.repository;

import java.util.UUID;

/** Native query projection — trip별 member·responded 집계. */
public interface TripMemberCountProjection {

  UUID getTripId();

  long getMemberCount();

  long getRespondedCount();
}
