package com.tripfit.tripfit.user.service;

import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

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

  public User requireUser(UUID userId) {
    return userLookupService.requireUser(userId);
  }

  public List<User> findAllById(List<UUID> userIds) {
    return userRepository.findAllById(userIds);
  }

  public void requireProfileNameComplete(User user) {
    userProfileService.requireProfileNameComplete(user);
  }
}
