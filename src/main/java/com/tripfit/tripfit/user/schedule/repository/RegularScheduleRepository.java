package com.tripfit.tripfit.user.schedule.repository;

import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegularScheduleRepository extends JpaRepository<RegularSchedule, UUID> {

  List<RegularSchedule> findByUserIdOrderByCreatedAtAsc(UUID userId);

  // 추천 계산 등 여러 멤버를 한 번에 로드할 때 N+1 방지용 batch 조회
  List<RegularSchedule> findByUserIdIn(Collection<UUID> userIds);

  Optional<RegularSchedule> findByIdAndUserId(UUID id, UUID userId);

  // 정기 일정 전체 삭제 — 회원 탈퇴 cascade, 그리고 "정기 일정이 있나요? → 없어요" 선택 시
  void deleteByUserId(UUID userId);
}
