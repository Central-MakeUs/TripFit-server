package com.tripfit.tripfit.trip.dto;

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
        홈 여행방 카드. GET /trips.
        inviteCode 없음(공유는 입장 후 상세). myMemberStatus=JOINED면 탭 시 상세 말고 confirm 플로우.
        """)
// @formatter:off
public record TripHomeCardResponse(
    @Schema(description = "여행방 ID") UUID tripId,

    @Schema(description = "여행방 이름", maxLength = 15) String name,

    @Schema(description = "여행지. null=미정", nullable = true) String destination,

    @Schema(description = "희망 여행 기간 시작일") LocalDate startRange,

    @Schema(description = "희망 여행 기간 종료일") LocalDate endRange,

    @Schema(description = "희망 여행 일수 (m일). null=미정", nullable = true) Integer durationDays,

    @Schema(
        description = "희망 여행 박수 (n박). durationDays-1 파생(DB 저장 없음). null=미정",
        nullable = true,
        example = "3")
    Integer durationNights,

    @Schema(description = "모집 정원 (1~10)", example = "6", minimum = "1", maximum = "10")
    Integer memberCount,

    @Schema(
        description =
            "여행방 진행 상태(effectiveStatus). end_range 경과·방장 취소 등 반영된 화면 표시용")
    TripStatus status,

    @Schema(description = "여행방 최근 활동 시각") LocalDateTime lastActivityAt,

    @Schema(description = "본인이 이 방을 홈 상단에 Pin했는지") boolean pinned,

    @Schema(description = "본인 역할 (방장 OWNER / 일반 MEMBER)") TripMemberRole myRole,

    @Schema(
        description =
            "본인 멤버십 상태. JOINED=방장 create 직후만(입장 불가·공유 불가), RESPONDED=방장 confirm 후·멤버 join 시(입장 가능)")
    TripMemberStatus myMemberStatus,

    @Schema(description = "일정 확인 완료(RESPONDED) 멤버 수") int respondedCount,

    @Schema(description = "현재 참여 멤버 수 (trip_member row 수)") int joinedMemberCount,

    @Schema(
        description =
            """
            모집 충원율 joinedMemberCount ÷ memberCount (0.0~1.0, DB 저장 없음).
            respondedCount와 무관 — 참여 인원 기준.
            join·remove·정원 변경 시 갱신 — GET /trips 재호출.
            """,
        example = "0.67")
        double memberFillRate,

    @Schema(description = "참여자 미리보기 (방장 우선 · joinedAt DESC · 최대 4명)")
    List<MemberPreviewResponse> membersPreview,

    @Schema(
        description =
            "미리보기 초과 인원 (joinedMemberCount - 4, 최소 0). +N 배지 표시용")
    int membersPreviewOverflow
) {}
// @formatter:on
