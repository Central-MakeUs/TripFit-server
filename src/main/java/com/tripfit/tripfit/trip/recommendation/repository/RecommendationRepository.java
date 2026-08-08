package com.tripfit.tripfit.trip.recommendation.repository;

import com.tripfit.tripfit.trip.recommendation.domain.Recommendation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

  // 여행방 메타(희망 기간·일수 등) 변경·모드 재생성·unconfirm 시 기존 추천 후보를 hard DELETE
  @Modifying
  @Query("DELETE FROM Recommendation r WHERE r.trip.id = :tripId")
  void deleteByTripId(@Param("tripId") UUID tripId);

  // 카드 목록 조회 — 순위 오름차순(1~3)
  List<Recommendation> findByTrip_IdOrderByRankAsc(UUID tripId);

  // 추천 근거 상세·confirm rank 선택 조회
  Optional<Recommendation> findByTrip_IdAndRank(UUID tripId, Integer rank);
}
