package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "일정 응답 시간대")
public enum TimeSlot {

	@Schema(description = "오전")
	MORNING,

	@Schema(description = "오후")
	AFTERNOON,

	@Schema(description = "저녁")
	EVENING
}
