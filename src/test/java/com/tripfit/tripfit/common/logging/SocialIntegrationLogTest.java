package com.tripfit.tripfit.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.tripfit.tripfit.user.domain.SocialProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SocialIntegrationLogTest {

  private Logger logger;

  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(SocialIntegrationLogTest.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
  }

  @Test
  void warn_withThrowable_masksEmailInThrowableMessage() {
    IllegalStateException exception =
        new IllegalStateException("provider error for user@example.com");

    SocialIntegrationLog.warn(logger, context(), "sync failed", exception);

    IThrowableProxy proxy = appender.list.get(0).getThrowableProxy();
    assertThat(proxy.getMessage()).contains("us***@example.com").doesNotContain(
        "user@example.com");
  }

  @Test
  void warn_withThrowable_preservesOriginalTypeNameInMessage() {
    IllegalStateException exception = new IllegalStateException("token expired");

    SocialIntegrationLog.warn(logger, context(), "sync failed", exception);

    IThrowableProxy proxy = appender.list.get(0).getThrowableProxy();
    assertThat(proxy.getMessage()).startsWith("IllegalStateException:");
  }

  @Test
  void warn_withThrowable_preservesStackTraceLength() {
    IllegalStateException exception = new IllegalStateException("token expired");
    int originalFrames = exception.getStackTrace().length;

    SocialIntegrationLog.warn(logger, context(), "sync failed", exception);

    IThrowableProxy proxy = appender.list.get(0).getThrowableProxy();
    assertThat(proxy.getStackTraceElementProxyArray()).hasSize(originalFrames);
  }

  @Test
  void warn_withThrowableCauseChain_masksEmailInEachCause() {
    IllegalStateException rootCause =
        new IllegalStateException("root cause for admin@tripfit.online");
    RuntimeException wrapped = new RuntimeException("wrapped failure", rootCause);

    SocialIntegrationLog.warn(logger, context(), "sync failed", wrapped);

    IThrowableProxy proxy = appender.list.get(0).getThrowableProxy();
    assertThat(proxy.getCause().getMessage()).contains("ad***@tripfit.online").doesNotContain(
        "admin@tripfit.online");
  }

  @Test
  void warn_withoutThrowable_stillLogsMessage() {
    SocialIntegrationLog.warn(logger, context(), "sync started");

    assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("sync started");
    assertThat(appender.list.get(0).getThrowableProxy()).isNull();
  }

  private static SocialLogContext context() {
    return SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.CALENDAR_SYNC);
  }
}
