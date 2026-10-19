package com.tripfit.tripfit.notification.service;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

@Component
public class NotificationPushDispatcher {

  private static final Logger log = LoggerFactory.getLogger(NotificationPushDispatcher.class);

  private final Executor notificationPushExecutor;

  private final FcmService fcmService;

  // 실행기 빈이 둘 이상이라 @Qualifier가 생성자 파라미터에 있어야 하므로 수동 생성자를 쓴다.
  public NotificationPushDispatcher(
      @Qualifier("notificationPushExecutor") Executor notificationPushExecutor,
      FcmService fcmService) {
    this.notificationPushExecutor = notificationPushExecutor;
    this.fcmService = fcmService;
  }

  // 커밋된 알림의 푸시 발송을 전용 실행기에 넘긴다. 스레드와 대기열이 모두 차 있으면 발송을 건너뛰고 로그만 남긴다.
  // 알림 이력은 원래 트랜잭션에서 이미 저장됐으므로, 건너뛰어도 알림센터에서는 보인다.
  // 커밋 후 콜백에서 불리므로 여기서 예외가 나가면 이미 커밋된 원래 요청이 실패한 것처럼 보인다. 그래서 모두 로그로만 남긴다.
  public void submit(NotificationPush push) {
    try {
      notificationPushExecutor.execute(() -> send(push));
    } catch (TaskRejectedException exception) {
      log.warn(
          "Notification push skipped because the push executor is saturated. type={}, tokens={}",
          push.type(),
          push.historyIdByToken().size());
    } catch (RuntimeException exception) {
      log.error("Notification push could not be submitted. type={}", push.type(), exception);
    }
  }

  private void send(NotificationPush push) {
    try {
      fcmService.sendMulticast(
          push.historyIdByToken(),
          push.title(),
          push.body(),
          push.landingType(),
          push.tripId());
    } catch (RuntimeException exception) {
      log.error("Notification push failed. type={}", push.type(), exception);
    }
  }
}
