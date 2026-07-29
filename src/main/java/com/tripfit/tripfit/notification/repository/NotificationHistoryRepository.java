package com.tripfit.tripfit.notification.repository;

import com.tripfit.tripfit.notification.domain.NotificationHistory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, UUID> {

  Optional<NotificationHistory> findByIdAndUser_Id(UUID id, UUID userId);

  // 알림센터 목록 — 최근 7일, 최신순(D9)
  List<NotificationHistory> findByUser_IdAndSentAtGreaterThanEqualOrderBySentAtDesc(
      UUID userId,
      LocalDateTime since);
}
