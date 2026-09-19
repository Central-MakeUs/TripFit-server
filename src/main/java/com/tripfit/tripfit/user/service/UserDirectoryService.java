package com.tripfit.tripfit.user.service;

import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

// trip이 필요로 하는 User 조회·프로필 검증을 기존 User 조회·프로필 서비스에 위임해 제공한다.
//
// 메서드마다 위임 대상이 다르다(아래 각 메서드 주석 참고) — 이 클래스 자체는 라우팅만 하고 검증·부수 효과
// 로직은 전부 원래 서비스(UserLookupService·UserProfileService)에 남아 있다. 정책이 바뀌면(예: 입장 조건
// 변경) 이 클래스가 아니라 해당 서비스를 고쳐야 한다.
@Component
public class UserDirectoryService {

  private final UserLookupService userLookupService;

  private final UserRepository userRepository;

  private final UserProfileService userProfileService;

  public UserDirectoryService(
      UserLookupService userLookupService,
      UserRepository userRepository,
      UserProfileService userProfileService) {
    this.userLookupService = userLookupService;
    this.userRepository = userRepository;
    this.userProfileService = userProfileService;
  }

  // User 조회 SSOT(UserLookupService.requireUser)에 위임 — 여기서 재구현하지 않음.
  public User requireUser(UUID userId) {
    return userLookupService.requireUser(userId);
  }

  // Spring Data가 제공하는 배치 조회에 그대로 위임(JpaRepository.findAllById).
  public List<User> findAllById(List<UUID> userIds) {
    return userRepository.findAllById(userIds);
  }

  public void requireProfileNameComplete(User user) {
    userProfileService.requireProfileNameComplete(user);
  }
}
