package com.tripfit.tripfit.trip.recommendation.dto;

import com.tripfit.tripfit.trip.recommendation.domain.RecommendationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = """
    단일 추천 후보의 상세 근거 정보입니다. (GET /trips/{tripId}/recommendations/{rank})
    - 방장 전용 기능입니다.
    """)
public record RecommendationDetailResponse(
    @Schema(description = "추천 순위 (1=1순위)", example = "1") int rank,

    @Schema(description = "이 후보를 계산한 추천 모드") RecommendationMode mode,

    @Schema(description = "추천 여행 시작일", example = "2026-08-03") LocalDate startDate,

    @Schema(description = "추천 여행 종료일", example = "2026-08-06") LocalDate endDate,

    @Schema(description = "참석률(%). (전체참석+부분참석 인원)/응답 참여자 수", example = "92") int attendRate,

    @Schema(description = "부분 참석 인원 수", example = "1") int partialAttendCount,

    @Schema(description = "불확실 일정이 있는 인원 수", example = "1") int uncertainCount,

    @Schema(description = "총 연차 일수 (반차=0.5일 환산 합계)", example = "2.0") double totalVacationDays,

    @Schema(
        description = """
            참여자별 참석 세부 정보 목록입니다.
            - 클라이언트는 `attendance != FULL_ATTEND`이거나 `uncertainDays > 0`인 경우 '주의가 필요한 인원'으로, 그 외는 '참석 가능한 인원'으로 직접 그룹화합니다.
            """) List<MemberAttendanceResponse> members,

    @Schema(
        description = "방장이 해당 후보에 대해 남긴 피드백 정보입니다. 피드백이 없으면 null을 반환합니다.",
        nullable = true) RecommendationFeedbackResponse feedback
) {
}
