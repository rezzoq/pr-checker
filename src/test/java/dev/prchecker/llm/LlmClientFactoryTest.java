package dev.prchecker.llm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmClientFactoryTest {
    @Test
    void usesOllamaWhenOpenAiCompatibleSettingsAreAbsent() {
        assertInstanceOf(OllamaClient.class, LlmClientFactory.create(Map.of()));
    }

    @Test
    void usesOpenAiCompatibleClientWhenAllSettingsArePresent() {
        assertInstanceOf(
                OpenAiCompatibleClient.class,
                LlmClientFactory.create(Map.of(
                        "LLM_BASE_URL", "https://api.example.test/v1",
                        "LLM_MODEL", "review-model",
                        "LLM_API_KEY", "test-key"
                ))
        );
    }

    @Test
    void rejectsPartiallyConfiguredOpenAiCompatibleSettings() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LlmClientFactory.create(Map.of("LLM_MODEL", "review-model"))
        );
    }
}
