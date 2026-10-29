package com.cst3492.ta.embedding;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Any OpenAI-compatible /embeddings endpoint. The default follows the course lecture:
 * SiliconFlow with BAAI/bge-m3.
 */
public final class HttpEmbeddingProvider implements EmbeddingProvider {

    private static final int BATCH_SIZE = 16;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final HttpClient http = HttpClient.newHttpClient();

    public HttpEmbeddingProvider(String baseUrl, String model, String apiKey) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.siliconflow.cn/v1" : baseUrl;
        this.model = model == null || model.isBlank() ? "BAAI/bge-m3" : model;
        this.apiKey = apiKey;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));
            out.addAll(embedBatch(batch));
        }
        return out;
    }

    private List<float[]> embedBatch(List<String> texts) {
        try {
            ObjectNode root = JsonUtil.mapper().createObjectNode();
            root.put("model", model);
            ArrayNode input = root.putArray("input");
            for (String text : texts) {
                input.add(text);
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/embeddings"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.mapper().writeValueAsString(root)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("embedding endpoint returned HTTP " + response.statusCode()
                        + ": " + truncate(response.body()));
            }
            JsonNode data = JsonUtil.mapper().readTree(response.body()).path("data");
            if (!data.isArray() || data.size() != texts.size()) {
                throw new IOException("embedding endpoint returned " + data.size() + " vectors for "
                        + texts.size() + " inputs");
            }
            List<float[]> out = new ArrayList<>(texts.size());
            for (JsonNode entry : data) {
                JsonNode vec = entry.path("embedding");
                float[] v = new float[vec.size()];
                for (int i = 0; i < v.length; i++) {
                    v[i] = (float) vec.get(i).asDouble();
                }
                out.add(v);
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("embedding request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while embedding", e);
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
