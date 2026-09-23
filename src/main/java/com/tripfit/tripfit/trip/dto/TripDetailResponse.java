package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.membership.dto.MemberPreviewResponse;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationMode;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = """
    여행방 상세 정보 응답입니다.
    - 호출 전제: 사용자가 이 여행방에서 ACTIVE 상태여야 합니다. (SCHEDULE_PENDING 상태인 방장이 호출 시 에러 발생)
    - 초대 코드(inviteCode)는 이 상세 응답에서만 제공됩니다. (입장 후 공유용)
    """)

public record TripDetailResponse(
    @Schema(description = "여행방 ID") UUID tripId,

    @Schema(description = "여행방 이름", maxLength = 15) String name,

    @Schema(description = "여행지. null=미정", nullable = true) String destination,

    @Schema(description = "희망 여행 기간 시작일") LocalDate startRange,

    @Schema(description = "희망 여행 기간 종료일") LocalDate endRange,

    @Schema(description = "희망 여행 일수 (m일). null=미정", nullable = true) Integer durationDays,

    @Schema(
        description = """
            희망 여행 박수(n박)입니다.
            - 미정일 경우 null을 반환합니다.
            - 값이 있을 경우 durationDays와 함께 nights+1 ≤ days ≤ nights+2 범위를 가집니다.
            """,
        nullable = true,
        example = "3") Integer durationNights,

    @Schema(description = "모집 정원 (1~10)", example = "6", minimum = "1",
        maximum = "10") Integer memberCount,

    @Schema(
        description = "여행방 진행 상태(effectiveStatus)입니다. (기간 경과, 방장 취소 등이 반영된 최종 상태)") TripStatus status,

    @Schema(
        description = "초대 코드(6자리)입니다. (공유용, 방 입장 후 상세 조회에서만 노출됩니다)") String inviteCode,

    @Schema(description = "확정 시작일. CONFIRMED/EXPIRED에서만 값 있음",
        nullable = true) LocalDate confirmedStartDate,

    @Schema(description = "확정 종료일. CONFIRMED/EXPIRED에서만 값 있음",
        nullable = true) LocalDate confirmedEndDate,

    @Schema(
        description = "확정 시점의 참석 인원 수입니다. (CONFIRMED/EXPIRED 상태일 때만 값이 존재합니다)",
        nullable = true) Integer confirmedAttendCount,

    @Schema(
        description = "확정 시점의 연차 필요 인원 수입니다. (CONFIRMED/EXPIRED 상태일 때만 값이 존재합니다)",
        nullable = true) Integer confirmedVacationMemberCount,

    @Schema(
        description = "확정 시점의 불확실 일정 인원 수입니다. (CONFIRMED/EXPIRED 상태일 때만 값이 존재합니다)",
        nullable = true) Integer confirmedUncertainCount,

    @Schema(description = "마지막 추천 모드. 아직 추천 전이면 null",
        nullable = true) RecommendationMode lastRecommendationMode,

    @Schema(description = "여행방 최근 활동 시각") LocalDateTime lastActivityAt,

    @Schema(description = "본인이 이 방을 홈 상단에 Pin했는지") boolean pinned,

    @Schema(description = "본인 역할 (방장 OWNER / 일반 MEMBER)") TripMemberRole myRole,

    @Schema(
        description = """
            호출자 본인의 멤버십 상태입니다.
            - SCHEDULE_PENDING: 방장 생성 직후 (입장/공유 불가)
            - ACTIVE: 방장 activate 완료 후 또는 일반 멤버 join 완료 후 (입장 가능)
            """) TripMemberStatus myMemberStatus,

    @Schema(description = "일정 확인 완료(ACTIVE) 멤버 수") int activeMemberCount,

    @Schema(
        description = """
            모집 충원율(응답률)입니다. (activeMemberCount ÷ memberCount)
            - 0.0 ~ 1.0 사이의 값이며, DB에 저장되지 않고 계산됩니다.
            """,
        example = "0.5") double memberFillRate,

    @Schema(
        description = "참여자 미리보기 (방장 우선 · joinedAt DESC · 최대 4명)") List<MemberPreviewResponse> membersPreview,

    @Schema(
        description = "미리보기 초과 인원 (참여 인원 - 4, 최소 0). +N 배지 표시용") int membersPreviewOverflow
) {
}
