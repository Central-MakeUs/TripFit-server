package com.tripfit.tripfit.trip.recommendation.dto;

import com.tripfit.tripfit.trip.recommendation.domain.RecommendationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "추천 근거 상세 (후보 1건). GET /trips/{tripId}/recommendations/{rank} (방장 전용)")

public record RecommendationDetailResponse(
    @Schema(description = "추천 순위 (1=1순위)", example = "1") int rank,

    @Schema(description = "이 후보를 계산한 추천 모드") RecommendationMode mode,

    @Schema(description = "추천 여행 시작일", example = "2026-08-03") LocalDate startDate,

    @Schema(description = "추천 여행 종료일", example = "2026-08-06") LocalDate endDate,

    @Schema(description = "참석률(%). (전체참석+부분참석 인원)/응답 참여자 수", example = "92") int attendRate,

    @Schema(description = "부분 참석 인원 수", example = "1") int partialAttendCount,

    @Schema(description = "불확실 일정이 있는 인원 수", example = "1") int uncertainCount,

    @Schema(description = "총 연차 일수 (반차=0.5일 환산 합계)", example = "2.0") double totalVacationDays,

    @Schema(description = "응답 참여자별 브레이크다운. 화면의 '주의가 필요한 인원'/'참석 가능한 인원' 그룹핑은"
        + " attendance != FULL_ATTEND 이거나 uncertainDays > 0 인지로 FE가 계산") List<MemberAttendanceResponse> members,

    @Schema(description = "방장이 이 후보에 남긴 피드백. 남긴 적 없으면 null",
        nullable = true) RecommendationFeedbackResponse feedback
) {
}
