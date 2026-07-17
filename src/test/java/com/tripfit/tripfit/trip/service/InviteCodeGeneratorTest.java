package com.tripfit.tripfit.trip.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;

class InviteCodeGeneratorTest {

  private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

  @RepeatedTest(20)
  void generate_producesSixCharCrockfordCode() {
    String code = InviteCodeGenerator.generate();
    assertThat(code).hasSize(6);
    for (char c : code.toCharArray()) {
      assertThat(ALPHABET).contains(String.valueOf(c));
    }
  }
}
