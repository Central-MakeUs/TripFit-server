package com.tripfit.tripfit.trip.membership.repository;

import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.repository.projection.TripMemberCountProjection;
import com.tripfit.tripfit.trip.membership.repository.projection.TripMemberPreviewProjection;
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

  Optional<TripMember> findByTripIdAndUserIdAndDeletedAtIsNull(UUID tripId, UUID userId);

  List<TripMember> findByUser_IdAndRoleAndDeletedAtIsNull(UUID userId, TripMemberRole role);

  @Query("""
      SELECT tm FROM TripMember tm
      JOIN FETCH tm.user
      WHERE tm.trip.id = :tripId
      AND tm.deletedAt IS NULL
      """)
  List<TripMember> findByTripIdAndDeletedAtIsNull(@Param("tripId") UUID tripId);

  long countByTripIdAndDeletedAtIsNull(UUID tripId);

  long countByTripIdAndActivatedAtIsNotNullAndDeletedAtIsNull(UUID tripId);

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

  @Query("""
      SELECT tm FROM TripMember tm
      JOIN FETCH tm.trip t
      WHERE tm.user.id = :userId
      AND tm.deletedAt IS NULL
      AND t.deletedAt IS NULL
      AND (:ownerOnly = false OR tm.role = com.tripfit.tripfit.trip.membership.domain.TripMemberRole.OWNER)
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
                 SUM(CASE WHEN tm.activated_at IS NOT NULL THEN 1 ELSE 0 END) AS activeCount
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

  @Query("""
      SELECT MAX(tm.trip.endRange) FROM TripMember tm
      WHERE tm.user.id = :userId
      AND tm.deletedAt IS NULL
      AND tm.trip.deletedAt IS NULL
      AND tm.trip.status = com.tripfit.tripfit.trip.domain.TripStatus.ONGOING
      """)
  LocalDate findMaxOngoingEndRangeByUserId(@Param("userId") UUID userId);
}
