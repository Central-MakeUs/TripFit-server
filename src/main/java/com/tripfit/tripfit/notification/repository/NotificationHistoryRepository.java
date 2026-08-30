package com.tripfit.tripfit.notification.repository;

import com.tripfit.tripfit.notification.domain.NotificationHistory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, UUID> {

  Optional<NotificationHistory> findByIdAndUser_Id(UUID id, UUID userId);

  // 알림센터 목록 — 최근 7일, 최신순(D9). trip fetch join으로 응답의 roomName 조회 시 N+1 방지
  @Query("SELECT h FROM NotificationHistory h LEFT JOIN FETCH h.trip "
      + "WHERE h.user.id = :userId AND h.sentAt >= :since ORDER BY h.sentAt DESC")
  List<NotificationHistory> findByUser_IdAndSentAtGreaterThanEqualOrderBySentAtDesc(
      @Param("userId") UUID userId,
      @Param("since") LocalDateTime since);
}
