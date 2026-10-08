package com.tripfit.tripfit.common.retry;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;

/**
 * 다른 요청이 같은 행을 먼저 고쳐서 저장에 실패했을 때(버전 충돌), 메서드를 처음부터 다시 실행한다. 최대 5번까지 다시 시도하고, 시도 사이에는 50~150ms 사이에서
 * 무작위로 쉰다. 쉬는 시간을 무작위로 두는 이유는, 같은 순간에 부딪힌 요청들이 다시 같은 순간에 몰려 또 부딪히지 않게 하기 위해서다.
 *
 * <p>
 * 트랜잭션을 여는 메서드에 직접 붙이지 않는다. 실패한 트랜잭션 안에서는 다시 시도해도 소용이 없으므로, 트랜잭션을 여는 빈을 바깥에서 호출하는 메서드에 붙여 매번 새
 * 트랜잭션으로 시작하게 한다.
 *
 * <p>
 * 데드락 같은 다른 실패는 다시 시도하지 않는다. 버전 충돌이 아닌 실패까지 조용히 넘기면, 잠금 순서가 잘못된 코드가 들어와도 알아챌 수 없다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Retryable(includes = OptimisticLockingFailureException.class, maxRetries = 5, delay = 50,
    jitter = 100)
public @interface RetryOnVersionConflict {
}
