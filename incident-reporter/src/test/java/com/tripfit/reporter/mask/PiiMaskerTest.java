package com.tripfit.reporter.mask;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskerTest {

  @Test
  void mask_email_replaced() {
    assertThat(PiiMasker.mask("user codus@example.com failed")).isEqualTo("user <email> failed");
  }

  @Test
  void mask_jwtInBearerHeader_keepsJwtMarker() {
    String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl";

    assertThat(PiiMasker.mask("Authorization: Bearer " + jwt)).isEqualTo("Authorization: Bearer <jwt>");
  }

  @Test
  void mask_opaqueBearerToken_replaced() {
    assertThat(PiiMasker.mask("bearer abc.DEF-123_xyz")).isEqualTo("bearer <token>");
  }

  @Test
  void mask_uuidAndIp_replaced() {
    String log = "userId=3f2b8c1e-9a4d-4e2f-8b1a-7c6d5e4f3a2b from 203.0.113.7";

    assertThat(PiiMasker.mask(log)).isEqualTo("userId=<uuid> from <ip>");
  }

  @Test
  void mask_textWithoutPii_unchanged() {
    String log = "HikariPool-1 - Connection is not available, request timed out after 30000ms";

    assertThat(PiiMasker.mask(log)).isEqualTo(log);
  }

  @Test
  void mask_cookieAndQueryTokens_replaced() {
    assertThat(PiiMasker.mask("Cookie: refreshToken=abc123.def; Path=/"))
        .isEqualTo("Cookie: refreshToken=<cookie>; Path=/");
    assertThat(PiiMasker.mask("GET /callback?code=4/0AbCd&state=x")).isEqualTo("GET /callback?code=<cookie>&state=x");
  }

  @Test
  void mask_phoneNumbers_replaced() {
    assertThat(PiiMasker.mask("연락처 010-1234-5678, 01098765432")).isEqualTo("연락처 <phone>, <phone>");
  }

  @Test
  void mask_longMixedTokenLikeFcm_replaced() {
    String fcm = "dXk3:APA91bHPRgkF3JUikC4ENAHEeMrd41Zxv3hVZjC9KtT8OvPVGJ";

    assertThat(PiiMasker.mask("token=" + fcm + " failed")).doesNotContain(fcm);
    assertThat(PiiMasker.mask("device " + fcm + " failed")).isEqualTo("device <token> failed");
  }

  @Test
  void mask_longClassNameWithoutDigits_kept() {
    String name = "PrometheusMetricsExportAutoConfigurationSupport";

    assertThat(PiiMasker.mask(name + " started")).isEqualTo(name + " started");
  }
}
