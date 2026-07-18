package com.tripfit.tripfit.trip.repository.projection;

import java.util.UUID;

/** 홈 카드 멤버 미리보기용 native 쿼리 projection (방당 배치 조회). */
public interface TripMemberPreviewProjection {

  UUID getTripId();

  UUID getUserId();

  String getProfileImageUrl();

  String getRole();
}
