package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "여행방 참여자 멤버십 상태")
public enum TripMemberStatus {
  /** @deprecated 신규 플로우 미사용 — create/join은 {@link #RESPONDED}만 INSERT (#22) */
  @Deprecated
  @Schema(description = "deprecated — 신규 미사용. 구 미확인 상태")
  JOINED,

  @Schema(description = "확인·가입 완료 (멤버 = RESPONDED)")
  RESPONDED
}
