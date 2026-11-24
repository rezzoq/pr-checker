package dev.prchecker.llm;

import java.util.Map;

public final class LlmClientFactory {
    private LlmClientFactory() {
    }

    public static LlmClient createFromEnvironment() {
        return create(System.getenv());
    }

    static LlmClient create(Map<String, String> environment) {
        String baseUrl = environment.get("LLM_BASE_URL");
        String model = environment.get("LLM_MODEL");
        String apiKey = environment.get("LLM_API_KEY");

        boolean hasBaseUrl = isSet(baseUrl);
        boolean hasModel = isSet(model);
        boolean hasApiKey = isSet(apiKey);
        if (!hasBaseUrl && !hasModel && !hasApiKey) {
            return new OllamaClient();
        }
        if (!hasBaseUrl || !hasModel || !hasApiKey) {
            throw new IllegalArgumentException(
                    "Set all three variables to use an OpenAI-compatible API: "
                            + "LLM_BASE_URL, LLM_MODEL, LLM_API_KEY"
            );
        }
        return new OpenAiCompatibleClient(baseUrl, model, apiKey);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
