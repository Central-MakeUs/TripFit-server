package com.tripfit.tripfit.trip.repository;

import com.tripfit.tripfit.trip.domain.Trip;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, UUID> {

  Optional<Trip> findByIdAndDeletedAtIsNull(UUID id);

  boolean existsByIdAndDeletedAtIsNull(UUID id);

  boolean existsByIdAndOwner_IdAndDeletedAtIsNull(UUID id, UUID ownerId);

  boolean existsByInviteCode(String inviteCode);

  // 초대코드로 방을 찾으면서 그 행을 잠근다(SELECT ... FOR UPDATE) — 참여 트랜잭션은 이 조회로 시작해야 한다.
  // 잠그지 않으면 정원 카운트와 멤버 INSERT 사이가 벌어져 동시 요청이 모두 "자리 있음"으로 판단하고,
  // 잠그더라도 이보다 먼저 다른 조회를 하면 REPEATABLE READ 스냅샷이 그 시점에 고정돼 뒤늦게 커밋된
  // 다른 참여자의 멤버 row가 카운트에서 누락된다
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM Trip t WHERE t.inviteCode = :inviteCode AND t.deletedAt IS NULL")
  Optional<Trip> findByInviteCodeForUpdate(@Param("inviteCode") String inviteCode);

  // endRange가 지난 ONGOING — 스냅샷 freeze 후 EXPIRED로 바꿀 대상
  @Query("""
      SELECT t FROM Trip t
      WHERE t.deletedAt IS NULL
      AND t.status = com.tripfit.tripfit.trip.domain.TripStatus.ONGOING
      AND t.endRange < :today
      """)
  List<Trip> findExpiredOngoing(@Param("today") LocalDate today);
}
