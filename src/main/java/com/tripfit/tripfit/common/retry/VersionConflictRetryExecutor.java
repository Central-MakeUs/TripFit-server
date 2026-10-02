package com.tripfit.tripfit.common.retry;

import org.springframework.stereotype.Component;

// 메서드에 어노테이션을 붙일 수 없는 자리에서 버전 충돌 재시도를 걸기 위한 실행기다. 같은 빈 안의 메서드를
// 직접 부르면 재시도가 적용되지 않으므로, 트랜잭션을 여는 호출을 이 빈에 넘겨서 실행한다.
@Component
public class VersionConflictRetryExecutor {

  @RetryOnVersionConflict
  public void run(Runnable action) {
    action.run();
  }
}
