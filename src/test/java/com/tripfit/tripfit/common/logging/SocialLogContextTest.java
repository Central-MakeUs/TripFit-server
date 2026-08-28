package com.tripfit.tripfit.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tripfit.tripfit.user.domain.SocialProvider;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class SocialLogContextTest {

  private Logger logger;

  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(SocialLogContextTest.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
  }

  @Test
  void withProviderError_masksEmailInProviderErrorMessage() {
    SocialLogContext context =
        SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.CALENDAR_SYNC)
            .withProviderError("invalid_grant", "refresh failed for user@example.com");

    assertThat(context.providerErrorMessage()).contains("us***@example.com").doesNotContain(
        "user@example.com");
  }

  @Test
  void withProviderError_nullRawMessage_staysNull() {
    SocialLogContext context =
        SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.CALENDAR_SYNC)
            .withProviderError("invalid_grant", null);

    assertThat(context.providerErrorMessage()).isNull();
  }

  @Test
  void info_populatesMdcWithAllContextFieldsUnderExactKeys() {
    UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    SocialLogContext context =
        SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.CALENDAR_SYNC)
            .withUserId(userId)
            .withHttpStatus(400)
            .withProviderError("invalid_grant", "refresh failed for user@example.com")
            .withTrigger("scheduler")
            .withGrantedScope("calendar.readonly");

    SocialIntegrationLog.info(logger, context, "sync failed");

    var mdc = appender.list.get(0).getMDCPropertyMap();
    assertThat(mdc.get("provider")).isEqualTo("GOOGLE");
    assertThat(mdc.get("action")).isEqualTo("CALENDAR_SYNC");
    assertThat(mdc.get("userId")).isEqualTo(userId.toString());
    assertThat(mdc.get("httpStatus")).isEqualTo("400");
    assertThat(mdc.get("providerErrorReason")).isEqualTo("invalid_grant");
    assertThat(mdc.get("providerErrorMessage")).contains("us***@example.com");
    assertThat(mdc.get("trigger")).isEqualTo("scheduler");
    assertThat(mdc.get("grantedScope")).isEqualTo("calendar.readonly");
  }

  @Test
  void info_removesMdcFieldsAfterLogging() {
    SocialLogContext context =
        SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.CALENDAR_SYNC);

    SocialIntegrationLog.info(logger, context, "sync started");

    assertThat(MDC.get("provider")).isNull();
    assertThat(MDC.get("action")).isNull();
  }

  @Test
  void info_omitsUnsetOptionalFieldsFromMdc() {
    SocialLogContext context =
        SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.CALENDAR_SYNC);

    SocialIntegrationLog.info(logger, context, "sync started");

    var mdc = appender.list.get(0).getMDCPropertyMap();
    assertThat(mdc).doesNotContainKey("userId");
    assertThat(mdc).doesNotContainKey("httpStatus");
    assertThat(mdc).doesNotContainKey("providerErrorReason");
  }
}
