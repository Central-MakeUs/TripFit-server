package com.tripfit.tripfit.trip.membership.repository.projection;

import java.util.UUID;

public interface TripMemberCountProjection {

  UUID getTripId();

  long getJoinedMemberCount();

  long getActiveCount();
}
