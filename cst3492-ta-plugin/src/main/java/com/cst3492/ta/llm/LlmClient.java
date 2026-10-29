package com.cst3492.ta.llm;

import java.util.List;

/** Provider-agnostic chat interface. */
public interface LlmClient {

    /** Blocking completion; returns the assistant message content. */
    String complete(List<Message> messages, double temperature, int maxTokens);

    /** Completion that must yield a JSON object; returns the extracted JSON text. */
    default String completeJson(List<Message> messages, double temperature, int maxTokens) {
        String raw = complete(messages, temperature, maxTokens);
        String json = com.cst3492.ta.util.TextUtil.firstJsonObject(raw);
        if (json == null) {
            throw new IllegalStateException("The model response contained no JSON object");
        }
        return json;
    }
}
