package com.tripfit.tripfit.user.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.service.AppleCredentialService;
import com.tripfit.tripfit.auth.service.GoogleLoginCredentialService;
import com.tripfit.tripfit.user.client.KakaoUnlinkClient;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// 회원 탈퇴 유스케이스 — 소셜 provider revoke(외부 HTTP 최대 4회, best-effort)를 먼저 끝내고, cascade(참여
// 방 나가기·소유 방 삭제)·개인 데이터 hard delete·User soft delete는 UserWithdrawalPersistenceService의
// 짧은 트랜잭션에 위임한다 (A-2 — provider 장애가 DB 커넥션을 오래 붙잡지 않도록)
public class UserWithdrawalService {

  private static final Logger log = LoggerFactory.getLogger(UserWithdrawalService.class);

  private final UserLookupService userLookupService;

  private final GoogleCalendarService googleCalendarService;

  private final KakaoUnlinkClient kakaoUnlinkClient;

  private final AppleCredentialService appleCredentialService;

  private final GoogleLoginCredentialService googleLoginCredentialService;

  private final UserWithdrawalPersistenceService persistenceService;

  // 차단 없이 항상 진행 — provider revoke(외부 HTTP, best-effort)를 트랜잭션 밖에서 먼저 끝낸 뒤, cascade·hard
  // delete·soft delete는 persistenceService의 짧은 트랜잭션으로 처리한다(A-2)
  public void withdraw(UUID userId) {
    User user = userLookupService.requireUser(userId);
    if (user.getDeletedAt() != null) {
      // 이미 탈퇴한 계정(액세스 토큰 만료 전 재호출) — 중복 처리 없이 idempotent 종료
      return;
    }

    // 소셜 provider revoke·unlink (best-effort — 실패해도 탈퇴 자체는 계속 진행)
    revokeGoogleCalendarIfConnected(userId);
    googleLoginCredentialService.revokeAndDeleteIfPresent(userId);
    unlinkKakaoIfProvider(user);
    appleCredentialService.revokeAndDeleteIfPresent(userId);

    persistenceService.finalizeWithdrawal(userId);
  }

  // Google Calendar 연동돼 있으면 credential hard delete 전에 refresh token revoke 호출 (best-effort,
  // 소유 서비스 GoogleCalendarService에 위임 — round3 A-1) — 복호화 실패 등 예상 밖 오류도 탈퇴 자체를 막지
  // 않도록 여기서 흡수함
  private void revokeGoogleCalendarIfConnected(UUID userId) {
    try {
      googleCalendarService.revokeIfConnected(userId);
    } catch (Exception exception) {
      log.warn("Google Calendar credential revoke failed", exception);
    }
  }

  // 카카오로 가입한 사용자면 Admin Key 기반 unlink 호출 (best-effort, 사용자 access_token 저장 불필요)
  private void unlinkKakaoIfProvider(User user) {
    if (user.getProvider() == SocialProvider.KAKAO) {
      kakaoUnlinkClient.unlink(user.getSocialId());
    }
  }
}
