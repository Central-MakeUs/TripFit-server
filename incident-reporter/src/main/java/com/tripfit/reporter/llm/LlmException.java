package com.tripfit.reporter.llm;

// 모델 호출이 실패했거나 응답을 리포트로 쓸 수 없을 때 던진다. 메시지는 Discord의 "AI 분석 실패: {사유}"에 그대로 쓰인다.
public class LlmException extends Exception {

  // 모델 쪽 일시적 과부하(503)·호출 한도(429)처럼 다른 모델로 다시 물어볼 만한 실패인지다.
  private final boolean retryable;

  public LlmException(String message) {
    this(message, false);
  }

  public LlmException(String message, boolean retryable) {
    super(message);
    this.retryable = retryable;
  }

  public boolean retryable() {
    return retryable;
  }

  public LlmException(String message, Throwable cause) {
    super(message, cause);
    this.retryable = false;
  }
}
