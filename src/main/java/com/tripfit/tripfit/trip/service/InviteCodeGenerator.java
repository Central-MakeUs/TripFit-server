package com.tripfit.tripfit.trip.service;

import java.security.SecureRandom;

// Crockford Base32 (0/O/I/1 제외) 6자 초대 코드 생성
public final class InviteCodeGenerator {

  private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

  private static final int CODE_LENGTH = 6;

  private static final SecureRandom RANDOM = new SecureRandom();

  private InviteCodeGenerator() {}

  public static String generate() {
    var code = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) {
      code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return code.toString();
  }
}
