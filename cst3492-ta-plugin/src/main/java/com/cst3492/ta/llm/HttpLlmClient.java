package com.cst3492.ta.llm;

import com.cst3492.ta.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * OpenAI-compatible chat client (DeepSeek by default) built on the JDK HttpClient, per the
 * design's risk-control decision: no external SDK, no classloader conflicts.
 */
public final class HttpLlmClient implements LlmClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final int timeoutSeconds;
    private final HttpClient http = HttpClient.newHttpClient();

    public HttpLlmClient(String baseUrl, String model, String apiKey, int timeoutSeconds) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.deepseek.com" : baseUrl;
        this.model = model == null || model.isBlank() ? "deepseek-chat" : model;
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60;
    }

    @Override
    public String complete(List<Message> messages, double temperature, int maxTokens) {
        try {
            ObjectNode root = JsonUtil.mapper().createObjectNode();
            root.put("model", model);
            root.put("temperature", temperature);
            root.put("max_tokens", maxTokens);
            root.put("stream", false);
            ArrayNode array = root.putArray("messages");
            for (Message message : messages) {
                ObjectNode node = array.addObject();
                node.put("role", message.role());
                node.put("content", message.content() == null ? "" : message.content());
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.mapper().writeValueAsString(root)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("chat endpoint returned HTTP " + response.statusCode()
                        + hint(response.statusCode()) + ": " + truncate(response.body()));
            }
            JsonNode content = JsonUtil.mapper().readTree(response.body())
                    .path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IOException("chat endpoint returned no content");
            }
            return content.asText();
        } catch (IOException e) {
            throw new IllegalStateException("chat request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while calling the model", e);
        }
    }

    private static String hint(int status) {
        return switch (status) {
            case 401 -> " (check the API key)";
            case 402 -> " (account balance too low)";
            case 429 -> " (rate limited; retry later)";
            default -> status >= 500 ? " (provider-side error; retry later)" : "";
        };
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
