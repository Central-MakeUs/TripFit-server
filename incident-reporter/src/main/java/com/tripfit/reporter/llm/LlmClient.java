package com.tripfit.reporter.llm;

// 모델 제공자를 바꿀 수 있도록 호출을 이 인터페이스 뒤에 둔다. 이번 구현체는 Gemini 하나뿐이고,
// LLM_PROVIDER 환경 변수로 고르게 되어 있다.
public interface LlmClient {

  IncidentReport analyze(String systemPrompt, String userPrompt) throws LlmException;
}
