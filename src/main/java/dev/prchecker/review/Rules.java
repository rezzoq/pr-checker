package dev.prchecker.review;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Rules {
    private static final Pattern FILE_HEADER =
            Pattern.compile("^diff --git a/(.+) b/(.+)$", Pattern.MULTILINE);
    private static final int LARGE_DIFF_LINE_THRESHOLD = 500;

    public List<String> check(String diff) {
        List<String> findings = new ArrayList<>();
        Matcher matcher = FILE_HEADER.matcher(diff);
        boolean hasCodeChanges = false;
        boolean hasTestChanges = false;

        while (matcher.find()) {
            String path = matcher.group(2);
            if (isTestFile(path)) {
                hasTestChanges = true;
            } else if (isCodeFile(path)) {
                hasCodeChanges = true;
            }
        }

        if (hasCodeChanges && !hasTestChanges) {
            findings.add("Изменён код, но в diff не обнаружены изменения тестов.");
        }

        long changedLines = diff.lines()
                .filter(line -> (line.startsWith("+") && !line.startsWith("+++"))
                        || (line.startsWith("-") && !line.startsWith("---")))
                .count();
        if (changedLines >= LARGE_DIFF_LINE_THRESHOLD) {
            findings.add("Большой diff: " + changedLines
                    + " добавленных/удалённых строк (порог: "
                    + LARGE_DIFF_LINE_THRESHOLD + ").");
        }
        return List.copyOf(findings);
    }

    private boolean isTestFile(String path) {
        String normalized = path.replace('\\', '/').toLowerCase();
        return normalized.contains("/test/")
                || normalized.contains("/tests/")
                || normalized.startsWith("test/")
                || normalized.startsWith("tests/")
                || normalized.endsWith("test.java")
                || normalized.endsWith("tests.java")
                || normalized.endsWith(".spec.ts")
                || normalized.endsWith(".test.ts")
                || normalized.endsWith(".spec.js")
                || normalized.endsWith(".test.js");
    }

    private boolean isCodeFile(String path) {
        String normalized = path.toLowerCase();
        return normalized.endsWith(".java")
                || normalized.endsWith(".kt")
                || normalized.endsWith(".kts")
                || normalized.endsWith(".js")
                || normalized.endsWith(".jsx")
                || normalized.endsWith(".ts")
                || normalized.endsWith(".tsx")
                || normalized.endsWith(".py")
                || normalized.endsWith(".go")
                || normalized.endsWith(".rs")
                || normalized.endsWith(".cs")
                || normalized.endsWith(".cpp")
                || normalized.endsWith(".c")
                || normalized.endsWith(".h");
    }
}
