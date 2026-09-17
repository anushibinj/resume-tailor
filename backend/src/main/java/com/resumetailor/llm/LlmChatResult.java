package com.resumetailor.llm;

/** One completion, plus the usage numbers we persist on the run for cost visibility. */
public record LlmChatResult(String content, String model, Integer promptTokens, Integer completionTokens) {
}
