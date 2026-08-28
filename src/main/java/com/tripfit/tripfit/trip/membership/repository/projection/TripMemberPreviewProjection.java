package com.tripfit.tripfit.trip.membership.repository.projection;

import java.util.UUID;

public interface TripMemberPreviewProjection {

  UUID getTripId();

  UUID getUserId();

  String getProfileImageUrl();

  String getRole();
}
