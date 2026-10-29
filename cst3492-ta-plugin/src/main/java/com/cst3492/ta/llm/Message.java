package com.cst3492.ta.llm;

/** One chat message in the OpenAI-compatible protocol. */
public record Message(String role, String content) {
}
