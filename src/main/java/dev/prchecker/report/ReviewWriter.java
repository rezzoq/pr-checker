package dev.prchecker.report;

import dev.prchecker.review.ReviewService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReviewWriter {
    public String format(ReviewService.ReviewResult result) {
        StringBuilder report = new StringBuilder("# PR review\n");
        appendSection(report, "Проверки правил", result.ruleFindings());
        appendSection(report, "Замечания модели", result.modelFindings());
        appendSection(report, "Ошибки проверки модели", result.chunkFailures());
        appendSection(report, "Пропущенные файлы (превышен лимит diff)", result.skippedFiles());
        if (result.ruleFindings().isEmpty()
                && result.modelFindings().isEmpty()
                && result.chunkFailures().isEmpty()
                && result.skippedFiles().isEmpty()) {
            report.append("\nEVERYTHING CORRECT\n");
        }
        return report.toString();
    }

    public void write(Path path, String report) throws IOException {
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }

    private void appendSection(StringBuilder report, String title, java.util.List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        report.append("\n## ").append(title).append('\n');
        for (String item : items) {
            report.append("- ").append(item.replace("\n", "\n  ")).append('\n');
        }
    }
}
