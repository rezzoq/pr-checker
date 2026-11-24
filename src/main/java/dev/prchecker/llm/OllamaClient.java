package dev.prchecker.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class OllamaClient implements LlmClient {
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "qwen2.5-coder:7b";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LlmFindingParser findingParser;
    private final URI endpoint;
    private final String model;

    public OllamaClient() {
        this(
                System.getenv().getOrDefault("OLLAMA_BASE_URL", DEFAULT_BASE_URL),
                System.getenv().getOrDefault("OLLAMA_MODEL", DEFAULT_MODEL)
        );
    }

    public OllamaClient(String baseUrl, String model) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Ollama base URL must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Ollama model must not be blank");
        }
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/api/chat");
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.findingParser = new LlmFindingParser();
    }

    @Override
    public java.util.List<Finding> review(String prompt) throws IOException, InterruptedException {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("stream", false);
        requestBody.put("format", "json");
        ObjectNode message = requestBody.putArray("messages").addObject();
        message.put("role", "user");
        message.put("content", prompt);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "Ollama returned HTTP " + response.statusCode() + ": " + response.body()
            );
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode content = root.path("message").path("content");
        if (!content.isTextual()) {
            throw new IOException("Ollama response did not contain message.content");
        }
        return findingParser.parse(content.asText());
    }
}
