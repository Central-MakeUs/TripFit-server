package com.tripfit.tripfit.trip.repository.projection;

import java.util.UUID;

/** Native query projection — trip별 member·active 집계. */
public interface TripMemberCountProjection {

  UUID getTripId();

  long getJoinedMemberCount();

  long getActiveCount();
}
