package dev.prchecker.llm;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class LlmFindingParser {
    private static final Set<String> SEVERITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> REQUIRED_FINDING_FIELDS = Set.of("file", "severity", "text");
    private static final Set<String> ALLOWED_FINDING_FIELDS =
            Set.of("file", "line", "severity", "text");

    private final ObjectMapper objectMapper = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build()
    ).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    List<LlmClient.Finding> parse(String json) throws IOException {
        JsonNode root = objectMapper.readTree(stripCodeFence(json));
        JsonNode findingsNode = root == null ? null : root.get("findings");
        if (root == null || !root.isObject() || root.size() != 1
                || findingsNode == null || !findingsNode.isArray()) {
            throw new IOException("LLM JSON response must contain only a findings array");
        }

        List<LlmClient.Finding> findings = new ArrayList<>();
        for (JsonNode finding : findingsNode) {
            Set<String> fields = finding.isObject() ? fieldNames(finding) : Set.of();
            if (!finding.isObject()
                    || !ALLOWED_FINDING_FIELDS.containsAll(fields)
                    || !fields.containsAll(REQUIRED_FINDING_FIELDS)) {
                throw new IOException(
                        "Each LLM finding must contain file, severity, text, and optional line"
                );
            }
            String file = requiredText(finding, "file");
            JsonNode lineNode = finding.get("line");
            Integer line = null;
            if (lineNode != null && !lineNode.isNull()) {
                if (!lineNode.isIntegralNumber() || !lineNode.canConvertToInt()
                        || lineNode.intValue() < 0) {
                    throw new IOException("LLM finding line must be a non-negative integer");
                }
                if (lineNode.intValue() > 0) {
                    line = lineNode.intValue();
                }
            }
            String severity = requiredText(finding, "severity").toUpperCase(Locale.ROOT);
            if (!SEVERITIES.contains(severity)) {
                throw new IOException("Unsupported LLM finding severity: " + severity);
            }
            String text = requiredText(finding, "text");
            findings.add(new LlmClient.Finding(file, line, severity, text));
        }
        return List.copyOf(findings);
    }

    private String stripCodeFence(String response) throws IOException {
        String normalized = response.strip();
        if (!normalized.startsWith("```")) {
            return normalized;
        }

        int openingLineEnd = normalized.indexOf('\n');
        if (openingLineEnd < 0 || !normalized.endsWith("```")) {
            throw new IOException("LLM JSON response contains an incomplete Markdown code fence");
        }
        String language = normalized.substring(3, openingLineEnd).strip();
        if (!language.isEmpty() && !language.equalsIgnoreCase("json")) {
            throw new IOException("LLM JSON response code fence must use the json language");
        }
        return normalized.substring(openingLineEnd + 1, normalized.length() - 3).strip();
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private String requiredText(JsonNode node, String field) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IOException("LLM finding field '" + field + "' must be a non-empty string");
        }
        return value.asText().strip();
    }
}
