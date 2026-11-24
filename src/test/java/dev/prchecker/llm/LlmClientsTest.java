package dev.prchecker.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmClientsTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsOllamaJsonFormatAndParsesJsonFindings() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        HttpServer server = startServer("/api/chat", requestBody,
                "{\"message\":{\"content\":\"{\\\"findings\\\":[]}\"}}");
        try {
            OllamaClient client = new OllamaClient(
                    "http://localhost:" + server.getAddress().getPort(),
                    "test-model"
            );

            assertTrue(client.review("review this").isEmpty());
            assertEquals("json", requestBody.get().path("format").asText());
            assertEquals("test-model", requestBody.get().path("model").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsOpenAiJsonModeAndBearerKey() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = """
                    {"choices":[{"message":{"content":"{\\"findings\\":[{\\"file\\":\\"A.java\\",\\"line\\":5,\\"severity\\":\\"LOW\\",\\"text\\":\\"Minor issue\\"}]}"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    "http://localhost:" + server.getAddress().getPort() + "/v1/",
                    "review-model",
                    "test-key"
            );

            assertEquals(
                    java.util.List.of(new LlmClient.Finding("A.java", 5, "LOW", "Minor issue")),
                    client.review("review this")
            );
            assertEquals(
                    "json_object",
                    requestBody.get().path("response_format").path("type").asText()
            );
            assertEquals("Bearer test-key", authorization.get());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(
            String path,
            AtomicReference<JsonNode> requestBody,
            String responseBody
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(path, exchange -> {
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }
}
