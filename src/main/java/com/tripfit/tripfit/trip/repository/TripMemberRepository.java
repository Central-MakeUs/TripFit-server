package com.tripfit.tripfit.trip.repository;

import com.tripfit.tripfit.trip.domain.TripMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripMemberRepository extends JpaRepository<TripMember, UUID> {

  boolean existsByTripIdAndUserIdAndDeletedAtIsNull(UUID tripId, UUID userId);

  Optional<TripMember> findByTripIdAndUserIdAndDeletedAtIsNull(UUID tripId, UUID userId);

  List<TripMember> findByTripIdAndDeletedAtIsNull(UUID tripId);

  long countByTripIdAndDeletedAtIsNull(UUID tripId);

  long countByTripIdAndStatusAndDeletedAtIsNull(
      UUID tripId,
      com.tripfit.tripfit.trip.domain.TripMemberStatus status);

  // 홈 목록 정렬: Pin 우선 → 여행방 최근 수정순 (trip-room-api)
  @Query("""
      SELECT tm FROM TripMember tm
      JOIN FETCH tm.trip t
      WHERE tm.user.id = :userId
      AND tm.deletedAt IS NULL
      AND t.deletedAt IS NULL
      ORDER BY tm.pinned DESC, t.updatedAt DESC
      """)
  List<TripMember> findActiveMembershipsByUserId(@Param("userId") UUID userId);
}
