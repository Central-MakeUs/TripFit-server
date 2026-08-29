package com.tripfit.tripfit.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskerTest {

  @Test
  void mask_whenTextNull_returnsNull() {
    assertThat(PiiMasker.mask(null)).isNull();
  }

  @Test
  void mask_whenNoEmailPresent_returnsTextUnchanged() {
    assertThat(PiiMasker.mask("invalid_grant: token expired")).isEqualTo(
        "invalid_grant: token expired");
  }

  @Test
  void mask_whenLocalPartLongerThanTwoChars_keepsFirstTwoChars() {
    assertThat(PiiMasker.mask("contact user@example.com for help")).isEqualTo(
        "contact us***@example.com for help");
  }

  @Test
  void mask_whenLocalPartTwoCharsOrShorter_keepsWholeLocalPart() {
    assertThat(PiiMasker.mask("ab@example.com")).isEqualTo("ab***@example.com");
    assertThat(PiiMasker.mask("a@example.com")).isEqualTo("a***@example.com");
  }

  @Test
  void mask_whenMultipleEmailsPresent_masksAll() {
    String result = PiiMasker.mask("from user1@example.com to user2@other.co.kr");

    assertThat(result).isEqualTo("from us***@example.com to us***@other.co.kr");
  }

  @Test
  void mask_preservesSurroundingNonEmailText() {
    String result =
        PiiMasker.mask("{\"error\":\"invalid_grant\",\"email\":\"jane@tripfit.online\"}");

    assertThat(result)
        .isEqualTo("{\"error\":\"invalid_grant\",\"email\":\"ja***@tripfit.online\"}");
  }
}
