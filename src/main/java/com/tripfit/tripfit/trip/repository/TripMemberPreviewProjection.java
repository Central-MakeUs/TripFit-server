package com.tripfit.tripfit.trip.repository;

import java.util.UUID;

/** Native query projection — 홈 membersPreview batch (#12 D5). */
public interface TripMemberPreviewProjection {

  UUID getTripId();

  UUID getUserId();

  String getProfileImageUrl();

  String getRole();
}
