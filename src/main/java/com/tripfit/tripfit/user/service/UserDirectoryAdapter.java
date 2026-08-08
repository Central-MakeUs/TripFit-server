package com.tripfit.tripfit.user.service;

import com.tripfit.tripfit.trip.port.out.UserDirectoryPort;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

// trip.port.out.UserDirectoryPort 구현체 — 기존 User 조회·프로필·요약 서비스 4개에 그대로 위임한다.
//
// 메서드마다 위임 대상이 다르다(아래 각 메서드 주석 참고) — 이 클래스 자체는 라우팅만 하고 검증·부수 효과
// 로직은 전부 원래 서비스(UserLookupService·UserProfileService·UserSummaryService)에 남아 있다. 정책이
// 바뀌면(예: 입장 조건 변경) 이 어댑터가 아니라 해당 서비스를 고쳐야 한다.
@Component
public class UserDirectoryAdapter implements UserDirectoryPort {

  private final UserLookupService userLookupService;

  private final UserRepository userRepository;

  private final UserProfileService userProfileService;

  private final UserSummaryService userSummaryService;

  public UserDirectoryAdapter(
      UserLookupService userLookupService,
      UserRepository userRepository,
      UserProfileService userProfileService,
      UserSummaryService userSummaryService) {
    this.userLookupService = userLookupService;
    this.userRepository = userRepository;
    this.userProfileService = userProfileService;
    this.userSummaryService = userSummaryService;
  }

  // User 조회 SSOT(UserLookupService.requireUser)에 위임 — 여기서 재구현하지 않음.
  @Override
  public User requireUser(UUID userId) {
    return userLookupService.requireUser(userId);
  }

  // Spring Data가 제공하는 배치 조회에 그대로 위임(JpaRepository.findAllById).
  @Override
  public List<User> findAllById(List<UUID> userIds) {
    return userRepository.findAllById(userIds);
  }

  @Override
  public void requireProfileNameComplete(User user) {
    userProfileService.requireProfileNameComplete(user);
  }

  @Override
  public void markAllFreeIfNoSchedules(User user) {
    userSummaryService.markAllFreeIfNoSchedules(user);
  }

  // UserSummaryService에 오버로드된 두 requireCanEnterRoom(User/UUID) 중 UUID 버전에 위임 — trip 쪽 호출부는
  // User 엔티티를 로드해 둔 상태가 아닐 수도 있어(예: JWT의 userId만 있는 인터셉터) UUID 버전을 쓴다.
  @Override
  public void requireCanEnterRoom(UUID userId) {
    userSummaryService.requireCanEnterRoom(userId);
  }
}
