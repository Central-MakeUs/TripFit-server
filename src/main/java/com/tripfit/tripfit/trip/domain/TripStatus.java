package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

// TODO: UI 상태(응답대기중/조율중/일정확정)와 매핑 정책 확정 필요
@Schema(description = "여행방 진행 상태")
public enum TripStatus {

	@Schema(description = "진행 중 (일정 조율·응답 수집)")
	ONGOING,

	@Schema(description = "일정 확정 완료")
	CONFIRMED,

	@Schema(description = "취소됨")
	CANCELED
}
