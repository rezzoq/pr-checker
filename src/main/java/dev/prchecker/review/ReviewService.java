package dev.prchecker.review;

import dev.prchecker.diff.DiffSource;
import dev.prchecker.llm.LlmClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ReviewService {
    private static final String REVIEW_INSTRUCTIONS = """
            Проведи code review только изменений из переданного diff.
            Ищи конкретные ошибки, регрессии и явно бесполезный или избыточный код.
            Не делай общую сводку и не выдумывай проблемы.
            Для каждого замечания укажи файл, строку (если её можно определить), проблему и краткое объяснение.
            Если замечаний нет, ответь ровно: Замечаний нет.
            """;

    private final DiffSource diffSource;
    private final LlmClient llmClient;
    private final int maxChunkChars;
    private final Rules rules;
    private final DiffSplitter diffSplitter;

    public ReviewService(DiffSource diffSource, LlmClient llmClient, int maxChunkChars) {
        this(diffSource, llmClient, maxChunkChars, new Rules(), new DiffSplitter());
    }

    ReviewService(
            DiffSource diffSource,
            LlmClient llmClient,
            int maxChunkChars,
            Rules rules,
            DiffSplitter diffSplitter
    ) {
        this.diffSource = Objects.requireNonNull(diffSource, "diffSource");
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        if (maxChunkChars <= 0) {
            throw new IllegalArgumentException("maxChunkChars must be positive");
        }
        this.maxChunkChars = maxChunkChars;
        this.rules = Objects.requireNonNull(rules, "rules");
        this.diffSplitter = Objects.requireNonNull(diffSplitter, "diffSplitter");
    }

    public ReviewResult review() throws IOException, InterruptedException {
        String diff = diffSource.getDiff();
        List<String> ruleFindings = rules.check(diff);
        DiffSplitter.SplitResult splitResult = diffSplitter.split(diff, maxChunkChars);
        List<String> modelFindings = new ArrayList<>();

        for (DiffSplitter.Chunk chunk : splitResult.chunks()) {
            String response = llmClient.review(buildPrompt(chunk.diff()));
            if (response != null && !response.isBlank()
                    && !response.strip().equalsIgnoreCase("Замечаний нет")) {
                modelFindings.add(response.strip());
            }
        }
        return new ReviewResult(ruleFindings, modelFindings, splitResult.skippedFiles());
    }

    private String buildPrompt(String diff) {
        return REVIEW_INSTRUCTIONS + "\nDiff:\n```diff\n" + diff + "\n```";
    }

    public record ReviewResult(
            List<String> ruleFindings,
            List<String> modelFindings,
            List<String> skippedFiles
    ) {
        public ReviewResult {
            ruleFindings = List.copyOf(ruleFindings);
            modelFindings = List.copyOf(modelFindings);
            skippedFiles = List.copyOf(skippedFiles);
        }
    }
}
