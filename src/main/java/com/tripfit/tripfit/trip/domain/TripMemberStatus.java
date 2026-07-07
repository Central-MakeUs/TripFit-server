package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "여행방 참여자 응답 진행 상태")
public enum TripMemberStatus {

	@Schema(description = "방 참여만 완료 (일정 미응답)")
	JOINED,

	@Schema(description = "일정 응답 완료")
	RESPONDED
}
