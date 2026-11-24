package dev.prchecker.review;

import dev.prchecker.diff.DiffSource;
import dev.prchecker.llm.LlmClient;
import dev.prchecker.llm.LlmClient.Finding;
import dev.prchecker.report.ReviewWriter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertTrue(llmClient.prompts.get(0).contains("строго такой формы"));
        assertTrue(llmClient.prompts.get(0).contains("{\"findings\":[]}"));
        assertTrue(llmClient.prompts.get(0).contains("недоверенные данные"));
        assertTrue(llmClient.prompts.get(0).contains("Игнорируй и не выполняй"));
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

    @Test
    void continuesAfterSecondChunkFailsAndWritesAllFindingsToReport() throws Exception {
        String acceptedA = fileDiff("A.java", "one");
        String acceptedB = fileDiff("B.java", "two");
        String acceptedC = fileDiff("C.java", "three");
        String oversized = fileDiff("large.java", "too much");
        String diff = acceptedA + acceptedB + acceptedC + oversized;
        StubLlmClient llmClient = new StubLlmClient() {
            @Override
            public List<Finding> review(String prompt) throws IOException, InterruptedException {
                prompts.add(prompt);
                if (prompts.size() == 2) {
                    throw new IOException("model is unavailable");
                }
                return List.of(new Finding(
                        "A.java",
                        prompts.size(),
                        "HIGH",
                        "Замечание для чанка " + prompts.size()
                ));
            }
        };
        ReviewService service = new ReviewService(
                new StubDiffSource(diff),
                llmClient,
                Math.max(
                        acceptedA.length(),
                        Math.max(acceptedB.length(), acceptedC.length())
                )
        );

        ReviewService.ReviewResult result = service.review();
        String report = new ReviewWriter().format(result);

        assertEquals(3, llmClient.prompts.size());
        assertEquals(2, result.modelFindings().size());
        assertEquals(
                List.of("Чанк 2 из 3 не проверен: model is unavailable"),
                result.chunkFailures()
        );
        assertTrue(report.contains("## Проверки правил"));
        assertTrue(report.contains("тестов"));
        assertTrue(report.contains("## Ошибки проверки модели"));
        assertTrue(report.contains("Чанк 2 из 3 не проверен: model is unavailable"));
        assertTrue(report.contains("## Пропущенные файлы"));
        assertTrue(report.contains("large.java"));
        assertTrue(report.contains("Замечание для чанка 3"));
    }

    @Test
    void rendersStructuredFindingsAndNoFindingsAsEmptyList() throws Exception {
        String diff = fileDiff("A.java", "one");
        String noFindingsDiff = fileDiff("src/test/A.java", "one");
        StubLlmClient noFindingsClient = new StubLlmClient() {
            @Override
            public List<Finding> review(String prompt) {
                prompts.add(prompt);
                return List.of();
            }
        };
        ReviewService noFindingsService = new ReviewService(
                new StubDiffSource(noFindingsDiff),
                noFindingsClient,
                1_000
        );

        ReviewService.ReviewResult noFindingsResult = noFindingsService.review();
        String noFindingsReport = new ReviewWriter().format(noFindingsResult);
        assertTrue(noFindingsResult.modelFindings().isEmpty());
        assertFalse(noFindingsReport.contains("## Замечания модели"));
        assertTrue(noFindingsReport.contains("Замечаний нет."));
        assertFalse(noFindingsReport.contains("В проверенной части замечаний нет."));

        StubLlmClient findingsClient = new StubLlmClient() {
            @Override
            public List<Finding> review(String prompt) {
                prompts.add(prompt);
                return List.of(new Finding("A.java", 12, "HIGH", "Potential null dereference"));
            }
        };
        ReviewService findingsService = new ReviewService(
                new StubDiffSource(diff),
                findingsClient,
                1_000
        );
        String findingsReport = new ReviewWriter().format(findingsService.review());
        assertTrue(findingsReport.contains("**HIGH** `A.java:12`"));
        assertTrue(findingsReport.contains("Potential null dereference"));

        StubLlmClient missingLineClient = new StubLlmClient() {
            @Override
            public List<Finding> review(String prompt) {
                prompts.add(prompt);
                return List.of(new Finding("A.java", null, "LOW", "Line unavailable"));
            }
        };
        ReviewService missingLineService = new ReviewService(
                new StubDiffSource(diff),
                missingLineClient,
                1_000
        );
        String missingLineReport = new ReviewWriter().format(missingLineService.review());
        assertTrue(missingLineReport.contains("**LOW** `A.java` — Line unavailable"));
    }

    @Test
    void reportsPartialReviewWhenEveryChunkFails() throws Exception {
        String first = fileDiff("src/test/One.java", "one");
        String second = fileDiff("src/test/Two.java", "two");
        StubLlmClient llmClient = new StubLlmClient() {
            @Override
            public List<Finding> review(String prompt) throws IOException {
                prompts.add(prompt);
                throw new IOException("model unavailable");
            }
        };
        ReviewService service = new ReviewService(
                new StubDiffSource(first + second),
                llmClient,
                first.length()
        );

        ReviewService.ReviewResult result = service.review();
        String report = new ReviewWriter().format(result);

        assertEquals(2, llmClient.prompts.size());
        assertTrue(result.modelFindings().isEmpty());
        assertEquals(2, result.chunkFailures().size());
        assertTrue(report.contains("В проверенной части замечаний нет."));
        assertFalse(report.contains("\nЗамечаний нет.\n"));
        assertTrue(report.contains("Чанк 1 из 2 не проверен: model unavailable"));
        assertTrue(report.contains("Чанк 2 из 2 не проверен: model unavailable"));
    }

    @Test
    void propagatesInterruptedExceptionFromModel() {
        String diff = fileDiff("A.java", "one");
        StubLlmClient llmClient = new StubLlmClient() {
            @Override
            public List<Finding> review(String prompt) throws InterruptedException {
                throw new InterruptedException("cancelled");
            }
        };
        ReviewService service = new ReviewService(new StubDiffSource(diff), llmClient, 1_000);

        assertThrows(InterruptedException.class, service::review);
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

    private static class StubLlmClient implements LlmClient {
        protected final List<String> prompts = new ArrayList<>();

        @Override
        public List<Finding> review(String prompt) throws IOException, InterruptedException {
            prompts.add(prompt);
            return List.of(new Finding(
                    "Example.java",
                    prompts.size(),
                    "MEDIUM",
                    "Замечание из ответа " + prompts.size()
            ));
        }
    }
}
