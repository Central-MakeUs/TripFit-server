package com.tripfit.tripfit.trip.repository;

import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.repository.projection.TripMemberCountProjection;
import com.tripfit.tripfit.trip.repository.projection.TripMemberPreviewProjection;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripMemberRepository extends JpaRepository<TripMember, UUID> {

  boolean existsByTripIdAndUserIdAndDeletedAtIsNull(UUID tripId, UUID userId);

  Optional<TripMember> findByTripIdAndUserIdAndDeletedAtIsNull(UUID tripId, UUID userId);

  @Query("""
      SELECT tm FROM TripMember tm
      JOIN FETCH tm.user
      WHERE tm.trip.id = :tripId
      AND tm.deletedAt IS NULL
      """)
  List<TripMember> findByTripIdAndDeletedAtIsNull(@Param("tripId") UUID tripId);

  long countByTripIdAndDeletedAtIsNull(UUID tripId);

  long countByTripIdAndStatusAndDeletedAtIsNull(
      UUID tripId,
      com.tripfit.tripfit.trip.domain.TripMemberStatus status);

  // 진행 중 캐러셀: endRange≥today · Pin 우선 → pinnedAt → lastActivityAt
  @Query("""
      SELECT tm FROM TripMember tm
      JOIN FETCH tm.trip t
      WHERE tm.user.id = :userId
      AND tm.deletedAt IS NULL
      AND t.deletedAt IS NULL
      AND t.endRange >= :today
      ORDER BY tm.pinned DESC, tm.pinnedAt DESC NULLS LAST, t.lastActivityAt DESC
      """)
  List<TripMember> findOngoingMembershipsByUserId(
      @Param("userId") UUID userId,
      @Param("today") LocalDate today);

  // 전체 보기: lastActivityAt만 · status/ownerOnly 필터
  @Query("""
      SELECT tm FROM TripMember tm
      JOIN FETCH tm.trip t
      WHERE tm.user.id = :userId
      AND tm.deletedAt IS NULL
      AND t.deletedAt IS NULL
      AND (:ownerOnly = false OR tm.role = com.tripfit.tripfit.trip.domain.TripMemberRole.OWNER)
      AND (
        :statusFilter = 'ALL'
        OR (:statusFilter = 'ONGOING' AND t.status = com.tripfit.tripfit.trip.domain.TripStatus.ONGOING
            AND t.endRange >= :today)
        OR (:statusFilter = 'CONFIRMED' AND t.status = com.tripfit.tripfit.trip.domain.TripStatus.CONFIRMED)
      )
      ORDER BY t.lastActivityAt DESC
      """)
  List<TripMember> findAllMembershipsByUserId(
      @Param("userId") UUID userId,
      @Param("today") LocalDate today,
      @Param("statusFilter") String statusFilter,
      @Param("ownerOnly") boolean ownerOnly);

  // 홈 카드 멤버 미리보기: 방당 최대 4 · 방장 우선 · joinedAt 내림차순
  @Query(
      value = """
          SELECT ranked.trip_id AS tripId, ranked.user_id AS userId,
                 ranked.profile_image_url AS profileImageUrl, ranked.role AS role
          FROM (
            SELECT tm.trip_id, u.id AS user_id, u.profile_image_url, tm.role,
                   ROW_NUMBER() OVER (
                     PARTITION BY tm.trip_id
                     ORDER BY CASE WHEN tm.role = 'OWNER' THEN 0 ELSE 1 END, tm.joined_at DESC
                   ) AS rn
            FROM trip_member tm
            INNER JOIN users u ON u.id = tm.user_id
            WHERE tm.trip_id IN (:tripIds) AND tm.deleted_at IS NULL
          ) ranked
          WHERE ranked.rn <= 4
          """,
      nativeQuery = true)
  List<TripMemberPreviewProjection> findMemberPreviewsByTripIds(
      @Param("tripIds") Collection<UUID> tripIds);

  @Query(
      value = """
          SELECT tm.trip_id AS tripId,
                 COUNT(*) AS joinedMemberCount,
                 SUM(CASE WHEN tm.status = 'RESPONDED' THEN 1 ELSE 0 END) AS respondedCount
          FROM trip_member tm
          WHERE tm.trip_id IN (:tripIds) AND tm.deleted_at IS NULL
          GROUP BY tm.trip_id
          """,
      nativeQuery = true)
  List<TripMemberCountProjection> countMembersByTripIds(@Param("tripIds") Collection<UUID> tripIds);

  @Modifying
  @Query("""
      UPDATE TripMember tm SET tm.pinned = false, tm.pinnedAt = null
      WHERE tm.deletedAt IS NULL
      AND tm.pinned = true
      AND tm.trip.deletedAt IS NULL
      AND tm.trip.endRange < :today
      """)
  int clearExpiredPins(@Param("today") LocalDate today);
}
