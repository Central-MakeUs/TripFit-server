package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.auth.domain.RefreshToken;
import com.tripfit.tripfit.auth.dto.LoginResponse;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.service.UserSummaryService;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DevAuthService {

  private static final SocialProvider DEV_PROVIDER = SocialProvider.KAKAO;

  private static final String DEFAULT_TEST_USER_ID = "chaeyeon";

  private static final String DEV_SOCIAL_ID_PREFIX = "dev-test-user-";

  private static final String DEV_EMAIL_DOMAIN = "@tripfit.online";

  private static final String DEV_NICKNAME_PREFIX = "테스트유저-";

  // 팀원 3인(채연·소은·기연) 고정 테스트 계정 — 그 외 임의 testUserId도 여전히 허용(제네릭 fallback)
  private static final Map<String, String> KNOWN_TEST_USER_NICKNAMES =
      Map.of(
          "chaeyeon",
          "채연",
          "soeun",
          "소은",
          "giyeon",
          "기연");

  // 팀원 3인 성·이름 프리필 — 미입력 시 trip 생성·참여 등에서 PROFILE_NAME_REQUIRED로 막히는 걸 방지
  private static final Map<String, TestIdentityName> KNOWN_TEST_USER_NAMES =
      Map.of(
          "chaeyeon",
          new TestIdentityName("손", "채연"),
          "giyeon",
          new TestIdentityName("방", "기연"),
          "soeun",
          new TestIdentityName("김", "소은"));

  private record TestIdentityName(
      String lastName,
      String firstName
  ) {
  }

  private final UserRepository userRepository;

  private final JwtService jwtService;

  private final RefreshTokenService refreshTokenService;

  private final UserSummaryService userSummaryService;

  public DevAuthService(
      UserRepository userRepository,
      JwtService jwtService,
      RefreshTokenService refreshTokenService,
      UserSummaryService userSummaryService) {
    this.userRepository = userRepository;
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
    this.userSummaryService = userSummaryService;
  }

  // 소셜 검증 없이 식별자별 테스트 계정으로 access·refresh를 발급함 — local/dev 프로필에서만 빈이 생성되어 prod에는 라우트가 없음
  @Transactional
  public LoginResponse devLogin(String testUserId) {
    // 1. 식별자별로 계정을 분리 — 팀원마다 다른 값을 주면 서로 다른 users row를 갖게 됨
    String key = testUserId == null || testUserId.isBlank() ? DEFAULT_TEST_USER_ID : testUserId;
    String socialId = DEV_SOCIAL_ID_PREFIX + key;

    User user =
        userRepository
            .findByProviderAndSocialId(DEV_PROVIDER, socialId)
            .map(this::requireActive)
            .orElseGet(() -> userRepository.save(createTestUser(key, socialId)));

    String accessToken = jwtService.createAccessToken(user.getId());
    RefreshToken refreshToken = refreshTokenService.create(user.getId());
    return new LoginResponse(
        accessToken,
        refreshToken.getToken(),
        jwtService.getAccessExpirationSeconds(),
        userSummaryService.toSummary(user));
  }

  // 신규 테스트 계정 생성 — 팀원 3인은 성·이름까지 프리필해 trip 생성·참여가 바로 가능하게 함
  private User createTestUser(String key, String socialId) {
    User user =
        new User(
            socialId,
            DEV_PROVIDER,
            "dev-test-" + key + DEV_EMAIL_DOMAIN,
            KNOWN_TEST_USER_NICKNAMES.getOrDefault(key, DEV_NICKNAME_PREFIX + key),
            null);
    TestIdentityName name = KNOWN_TEST_USER_NAMES.get(key);
    if (name != null) {
      user.setLastName(name.lastName());
      user.setFirstName(name.firstName());
    }
    return user;
  }

  // 테스트 계정이 탈퇴 상태면 실제 로그인과 동일하게 재발급을 막음
  private User requireActive(User user) {
    if (user.getDeletedAt() != null) {
      throw new TripFitException(AuthErrorCode.AUTH_WITHDRAWN_ACCOUNT);
    }
    return user;
  }
}
