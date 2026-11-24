package dev.prchecker;

import dev.prchecker.diff.GitDiffSource;
import dev.prchecker.llm.OllamaClient;
import dev.prchecker.report.ReviewWriter;
import dev.prchecker.review.DiffSplitter;
import dev.prchecker.review.ReviewService;

import java.io.IOException;
import java.nio.file.Path;

public final class Main {
    private static final int DEFAULT_MAX_CHUNK_CHARS = 12_000;

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length > 2) {
            System.err.println("Usage: java -jar pr-checker.jar [base-ref] [output.md]");
            System.exit(2);
        }

        String baseRef = args.length >= 1 ? args[0] : "main";
        Path outputPath = args.length == 2 ? Path.of(args[1]) : null;

        try {
            int maxChunkChars = maxChunkCharsFromEnvironment();
            var service = new ReviewService(
                    new GitDiffSource(baseRef),
                    new OllamaClient(),
                    maxChunkChars
            );
            String report = new ReviewWriter().format(service.review());
            if (outputPath == null) {
                System.out.println(report);
            } else {
                new ReviewWriter().write(outputPath, report);
                System.out.println("Review written to " + outputPath);
            }
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            System.err.println("Review failed: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.exit(1);
        }
    }

    private static int maxChunkCharsFromEnvironment() {
        String value = System.getenv(DiffSplitter.MAX_CHUNK_CHARS_ENV);
        if (value == null || value.isBlank()) {
            return DEFAULT_MAX_CHUNK_CHARS;
        }
        try {
            int limit = Integer.parseInt(value);
            if (limit <= 0) {
                throw new IllegalArgumentException(
                        DiffSplitter.MAX_CHUNK_CHARS_ENV + " must be a positive integer"
                );
            }
            return limit;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    DiffSplitter.MAX_CHUNK_CHARS_ENV + " must be a positive integer: " + value,
                    e
            );
        }
    }
}
