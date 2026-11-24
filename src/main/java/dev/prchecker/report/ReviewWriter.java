package dev.prchecker.report;

import dev.prchecker.review.ReviewService;
import dev.prchecker.llm.LlmClient.Finding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ReviewWriter {
    public String format(ReviewService.ReviewResult result) {
        StringBuilder report = new StringBuilder("# PR review\n");
        appendTextSection(report, "Проверки правил", result.ruleFindings());
        appendFindingSection(report, "Замечания модели", result.modelFindings());
        if (result.ruleFindings().isEmpty() && result.modelFindings().isEmpty()) {
            if (result.chunkFailures().isEmpty() && result.skippedFiles().isEmpty()) {
                report.append("\nЗамечаний нет.\n");
            } else {
                report.append("\nВ проверенной части замечаний нет.\n");
            }
        }
        appendTextSection(report, "Ошибки проверки модели", result.chunkFailures());
        appendTextSection(
                report,
                "Пропущенные файлы (превышен лимит diff)",
                result.skippedFiles()
        );
        return report.toString();
    }

    public void write(Path path, String report) throws IOException {
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }

    private void appendTextSection(StringBuilder report, String title, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        report.append("\n## ").append(title).append('\n');
        for (String item : items) {
            report.append("- ").append(item.replace("\n", "\n  ")).append('\n');
        }
    }

    private void appendFindingSection(
            StringBuilder report,
            String title,
            List<Finding> findings
    ) {
        if (findings.isEmpty()) {
            return;
        }
        report.append("\n## ").append(title).append('\n');
        for (Finding finding : findings) {
            report.append("- **").append(finding.severity()).append("** `")
                    .append(finding.file());
            if (finding.line() != null && finding.line() > 0) {
                report.append(':').append(finding.line());
            }
            report.append("` — ").append(finding.text().replace("\n", "\n  "))
                    .append('\n');
        }
    }
}
