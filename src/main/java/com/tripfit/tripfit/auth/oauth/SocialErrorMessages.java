package com.tripfit.tripfit.auth.oauth;

import java.util.Locale;

// 소셜 provider 에러 메시지·응답 본문에 "만료" 문구가 포함됐는지 판별하는 공용 휴리스틱 — Apple/Google/Kakao가
// 만료를 별도 코드로 문서화하지 않아 문자열로 판별해야 함(provider 응답 포맷이 바뀌면 여기 1곳만 조정)
public final class SocialErrorMessages {

  private SocialErrorMessages() {}

  public static boolean containsExpired(String message) {
    return message != null && message.toLowerCase(Locale.ROOT).contains("expired");
  }
}
