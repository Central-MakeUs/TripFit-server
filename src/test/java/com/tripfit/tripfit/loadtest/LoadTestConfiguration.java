package com.tripfit.tripfit.loadtest;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.google.api.core.SettableApiFuture;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.SendResponse;
import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.auth.oauth.GoogleTokenVerifier;
import com.tripfit.tripfit.auth.oauth.OAuthProfile;
import com.tripfit.tripfit.auth.oauth.SocialTokenVerifier;
import com.tripfit.tripfit.notification.domain.DeviceType;
import com.tripfit.tripfit.notification.domain.UserDeviceToken;
import com.tripfit.tripfit.notification.repository.UserDeviceTokenRepository;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.membership.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.trip.service.TripService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// 부하 테스트 실행에만 더하는 설정이다. @TestConfiguration이라 일반 테스트의 컴포넌트 스캔에는 잡히지 않는다.
// 외부 호출 중 WireMock으로 돌릴 수 없는 것(FCM, Google ID 토큰 서명 검증)만 대역으로 바꾸고,
// 시나리오가 쓸 사용자·여행방·기기 토큰을 만든 뒤 액세스 토큰을 파일로 남긴다.
@TestConfiguration(proxyBeanMethods = false)
public class LoadTestConfiguration {

  private static final Logger log = LoggerFactory.getLogger(LoadTestConfiguration.class);

  // FCM 주소는 SDK 안에 고정돼 있어 WireMock으로 돌릴 수 없으므로, 지정한 시간만큼 늦게 응답하는 대역으로 바꾼다.
  // 동기 발송(sendEach)은 호출 스레드를 그 시간만큼 붙잡고, 비동기 발송(sendEachAsync)은 그 시간 뒤에 완료되는 Future를 돌려준다.
  @Bean
  @Primary
  FirebaseMessaging loadTestFirebaseMessaging(@Value("${loadtest.fcm.delay-ms}") long delayMs)
      throws FirebaseMessagingException {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
    doAnswer(invocation -> {
      Thread.sleep(delayMs);
      return successResponse(invocation.<List<?>>getArgument(0).size());
    }).when(firebaseMessaging).sendEach(anyList());
    doAnswer(invocation -> {
      SettableApiFuture<BatchResponse> future = SettableApiFuture.create();
      int size = invocation.<List<?>>getArgument(0).size();
      scheduler.schedule(() -> future.set(successResponse(size)), delayMs, TimeUnit.MILLISECONDS);
      return future;
    }).when(firebaseMessaging).sendEachAsync(anyList());
    return firebaseMessaging;
  }

  // Google 로그인의 ID 토큰은 Google 서명 키로 검증하므로 대역으로 바꾼다. 토큰 문자열을 그대로 사용자 식별자로 쓴다.
  // 시나리오 S3가 재는 인가 코드 교환은 대역이 아니라 실제 GoogleOAuthClient가 WireMock을 호출한다.
  @Bean
  static BeanPostProcessor loadTestGoogleVerifierReplacer() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof GoogleTokenVerifier) {
          return (SocialTokenVerifier) new LoadTestGoogleTokenVerifier();
        }
        return bean;
      }
    };
  }

  // 시나리오가 쓸 데이터를 만든다. 방장 1명과 멤버 여럿으로 된 여행방을 만들고, 멤버마다 기기 토큰을 등록한다.
  // 방장 토큰은 여행방 정보 수정(알림 발생)에, 멤버 토큰은 여행방 목록 조회에 쓴다.
  @Bean
  ApplicationRunner loadTestSeeder(
      UserRepository userRepository,
      UserDeviceTokenRepository userDeviceTokenRepository,
      TripService tripService,
      TripRepository tripRepository,
      JwtService jwtService,
      @Value("${loadtest.seed.trips}") int tripCount,
      @Value("${loadtest.seed.members-per-trip}") int membersPerTrip,
      @Value("${loadtest.seed.output}") String output) {
    return args -> {
      LocalDate startRange = LocalDate.now().plusDays(30);
      List<String> owners = new ArrayList<>();
      List<String> readers = new ArrayList<>();
      for (int t = 0; t < tripCount; t++) {
        User owner = userRepository.save(seedUser("owner-" + t));
        UUID tripId =
            tripService
                .createTrip(
                    owner.getId(),
                    new CreateTripRequest(
                        "부하" + t,
                        startRange,
                        startRange.plusDays(10),
                        null,
                        null,
                        membersPerTrip + 1,
                        null))
                .tripId();
        tripService.activateMembership(tripId, owner.getId());
        String inviteCode = tripRepository.findById(tripId).orElseThrow().getInviteCode();
        for (int m = 0; m < membersPerTrip; m++) {
          User member = userRepository.save(seedUser("member-" + t + "-" + m));
          tripService.joinTrip(member.getId(), new JoinTripRequest(inviteCode));
          userDeviceTokenRepository.save(
              new UserDeviceToken(member, "loadtest-token-" + t + "-" + m, DeviceType.ANDROID));
          readers.add(jwtService.createAccessToken(member.getId()));
        }
        owners.add(
            "{\"token\":\"" + jwtService.createAccessToken(owner.getId()) + "\",\"tripId\":\""
                + tripId + "\"}");
      }
      writeSeed(Path.of(output), owners, readers);
      log.warn(
          "loadtest seed ready: trips={}, membersPerTrip={}, output={}",
          tripCount,
          membersPerTrip,
          output);
    };
  }

  private static User seedUser(String key) {
    User user =
        new User("loadtest-" + key, SocialProvider.KAKAO, key + "@loadtest.local", key, null);
    user.applyProfilePatch("부하", key, true);
    user.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
    return user;
  }

  private static void writeSeed(Path path, List<String> owners, List<String> readers)
      throws IOException {
    Files.createDirectories(path.getParent());
    String readerJson =
        String.join(",", readers.stream().map(token -> "\"" + token + "\"").toList());
    Files.writeString(
        path,
        "{\"owners\":[" + String.join(",", owners) + "],\"readers\":[" + readerJson + "]}");
  }

  private static BatchResponse successResponse(int size) {
    return new BatchResponse() {
      @Override
      public List<SendResponse> getResponses() {
        return List.of();
      }

      @Override
      public int getSuccessCount() {
        return size;
      }

      @Override
      public int getFailureCount() {
        return 0;
      }
    };
  }

  private static final class LoadTestGoogleTokenVerifier implements SocialTokenVerifier {

    @Override
    public SocialProvider getProvider() {
      return SocialProvider.GOOGLE;
    }

    @Override
    public OAuthProfile verify(String token) {
      return new OAuthProfile(
          SocialProvider.GOOGLE, token, token + "@loadtest.local", "부하", null, null);
    }
  }
}
