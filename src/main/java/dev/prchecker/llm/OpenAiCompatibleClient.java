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
import java.util.List;

public final class OpenAiCompatibleClient implements LlmClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LlmFindingParser findingParser;
    private final URI endpoint;
    private final String model;
    private final String apiKey;

    public OpenAiCompatibleClient(String baseUrl, String model, String apiKey) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("LLM_BASE_URL must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("LLM_MODEL must not be blank");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("LLM_API_KEY must not be blank");
        }
        String normalizedBaseUrl = baseUrl.replaceAll("/+$", "");
        this.endpoint = URI.create(normalizedBaseUrl.endsWith("/chat/completions")
                ? normalizedBaseUrl
                : normalizedBaseUrl + "/chat/completions");
        this.model = model;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.findingParser = new LlmFindingParser();
    }

    @Override
    public List<Finding> review(String prompt) throws IOException, InterruptedException {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("stream", false);
        requestBody.putObject("response_format").put("type", "json_object");
        ObjectNode message = requestBody.putArray("messages").addObject();
        message.put("role", "user");
        message.put("content", prompt);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "OpenAI-compatible API returned HTTP "
                            + response.statusCode() + ": " + response.body()
            );
        }

        JsonNode content = objectMapper.readTree(response.body())
                .path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) {
            throw new IOException("OpenAI-compatible response did not contain choices[0].message.content");
        }
        return findingParser.parse(content.asText());
    }
}
