package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.RecommendationMode;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(
    description = """
        여행방 상세. GET/join/confirm/patch/pin 등.
        호출 전제=ACTIVE+입장조건. inviteCode는 여기(입장 후)에만 있음 — 방장 공유용.
        SCHEDULE_PENDING 방장이 호출하면 SCHEDULE_CONFIRM_REQUIRED.
        """)
// @formatter:off
public record TripDetailResponse(
    @Schema(description = "여행방 ID") UUID tripId,

    @Schema(description = "여행방 이름", maxLength = 15) String name,

    @Schema(description = "여행지. null=미정", nullable = true) String destination,

    @Schema(description = "희망 여행 기간 시작일") LocalDate startRange,

    @Schema(description = "희망 여행 기간 종료일") LocalDate endRange,

    @Schema(description = "희망 여행 일수 (m일). null=미정", nullable = true) Integer durationDays,

    @Schema(
        description = "희망 여행 박수 (n박). durationDays와 쌍으로 저장(nights+1 ≤ days ≤ nights+2). null=미정",
        nullable = true,
        example = "3")
    Integer durationNights,

    @Schema(description = "모집 정원 (1~10)", example = "6", minimum = "1", maximum = "10")
    Integer memberCount,

    @Schema(
        description =
            "여행방 진행 상태(effectiveStatus). end_range 경과 등 반영된 화면 표시용")
    TripStatus status,

    @Schema(
            description =
                "초대 코드 (6자). 방 입장(ACTIVE) 후 상세에서만 노출 — 공유용. create(SCHEDULE_PENDING) 응답에는 없음")
        String inviteCode,

    @Schema(description = "확정 시작일. CONFIRMED/EXPIRED에서만 값 있음", nullable = true)
    LocalDate confirmedStartDate,

    @Schema(description = "확정 종료일. CONFIRMED/EXPIRED에서만 값 있음", nullable = true)
    LocalDate confirmedEndDate,

    @Schema(description = "마지막 추천 모드. 아직 추천 전이면 null", nullable = true)
    RecommendationMode lastRecommendationMode,

    @Schema(description = "여행방 최근 활동 시각") LocalDateTime lastActivityAt,

    @Schema(description = "본인이 이 방을 홈 상단에 Pin했는지") boolean pinned,

    @Schema(description = "본인 역할 (방장 OWNER / 일반 MEMBER)") TripMemberRole myRole,

    @Schema(
        description =
            "본인 멤버십 상태. SCHEDULE_PENDING=방장 create 직후만(입장·공유 불가), ACTIVE=방장 confirm 후·멤버 join 시(입장 가능)")
    TripMemberStatus myMemberStatus,

    @Schema(description = "일정 확인 완료(ACTIVE) 멤버 수") int activeMemberCount,

    @Schema(
        description =
            """
            모집 충원율(응답률) activeMemberCount ÷ memberCount (0.0~1.0, DB 저장 없음).
            join·remove·정원 변경·일정 확인 완료 시 갱신 — GET /trips 또는 GET /trips/{tripId} 재호출.
            """,
        example = "0.5")
        double memberFillRate,

    @Schema(description = "참여자 미리보기 (방장 우선 · joinedAt DESC · 최대 4명)")
    List<MemberPreviewResponse> membersPreview,

    @Schema(
        description =
            "미리보기 초과 인원 (참여 인원 - 4, 최소 0). +N 배지 표시용")
    int membersPreviewOverflow
) {}
// @formatter:on
