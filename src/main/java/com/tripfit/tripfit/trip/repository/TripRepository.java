package com.tripfit.tripfit.trip.repository;

import com.tripfit.tripfit.trip.domain.Trip;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, UUID> {

  Optional<Trip> findByIdAndDeletedAtIsNull(UUID id);

  boolean existsByIdAndDeletedAtIsNull(UUID id);

  boolean existsByIdAndOwner_IdAndDeletedAtIsNull(UUID id, UUID ownerId);

  boolean existsByInviteCode(String inviteCode);

  Optional<Trip> findByInviteCodeAndDeletedAtIsNull(String inviteCode);

  // 최근 활동 시각만 직접 UPDATE한다. 엔티티를 고쳐 저장하면 버전 번호가 올라가서, 같은 순간에 참여하거나
  // 방을 수정하는 다른 요청을 실패시킨다. 활동 시각은 누가 먼저 썼는지가 중요하지 않은 값이라 버전을 거치지 않는다.
  @Modifying(flushAutomatically = true)
  @Query("UPDATE Trip t SET t.lastActivityAt = :now WHERE t.id = :tripId AND t.deletedAt IS NULL")
  int touchLastActivity(@Param("tripId") UUID tripId, @Param("now") LocalDateTime now);

  @Query("""
      SELECT t FROM Trip t
      WHERE t.deletedAt IS NULL
      AND t.status = com.tripfit.tripfit.trip.domain.TripStatus.ONGOING
      AND t.endRange < :today
      """)
  List<Trip> findExpiredOngoing(@Param("today") LocalDate today);
}
