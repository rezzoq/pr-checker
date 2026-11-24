package dev.prchecker.review;

import dev.prchecker.diff.DiffSource;
import dev.prchecker.llm.LlmClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewServiceTest {
    @Test
    void checksRulesOnceAndSendsOneRequestPerChunk() throws Exception {
        String diff = fileDiff("A.java", "one") + fileDiff("B.java", "two");
        int limit = fileDiff("A.java", "one").length();
        StubLlmClient llmClient = new StubLlmClient();
        StubDiffSource diffSource = new StubDiffSource(diff);

        ReviewService service = new ReviewService(diffSource, llmClient, limit);
        ReviewService.ReviewResult result = service.review();

        assertEquals(1, diffSource.calls);
        assertEquals(2, llmClient.prompts.size());
        assertEquals(2, result.modelFindings().size());
        assertTrue(result.ruleFindings().get(0).contains("тестов"));
    }

    @Test
    void skipsOversizedFileWithoutSendingItToModel() throws Exception {
        String oversized = fileDiff("large.java", "too much");
        String accepted = fileDiff("Small.java", "ok");
        StubLlmClient llmClient = new StubLlmClient();
        ReviewService service = new ReviewService(
                new StubDiffSource(oversized + accepted),
                llmClient,
                accepted.length()
        );

        ReviewService.ReviewResult result = service.review();

        assertEquals(1, llmClient.prompts.size());
        assertTrue(llmClient.prompts.get(0).contains("Small.java"));
        assertEquals(List.of("large.java"), result.skippedFiles());
    }

    private String fileDiff(String name, String content) {
        return "diff --git a/" + name + " b/" + name + "\n"
                + "--- a/" + name + "\n"
                + "+++ b/" + name + "\n"
                + "@@ -1 +1 @@\n"
                + "+" + content + "\n";
    }

    private static final class StubDiffSource implements DiffSource {
        private final String diff;
        private int calls;

        private StubDiffSource(String diff) {
            this.diff = diff;
        }

        @Override
        public String getDiff() throws IOException {
            calls++;
            return diff;
        }
    }

    private static final class StubLlmClient implements LlmClient {
        private final List<String> prompts = new ArrayList<>();

        @Override
        public String review(String prompt) {
            prompts.add(prompt);
            return "Замечание из ответа " + prompts.size();
        }
    }
}
