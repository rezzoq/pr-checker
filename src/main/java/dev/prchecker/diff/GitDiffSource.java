package dev.prchecker.diff;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class GitDiffSource implements DiffSource {
    private final String baseRef;

    public GitDiffSource(String baseRef) {
        this.baseRef = Objects.requireNonNull(baseRef, "baseRef");
        if (baseRef.isBlank() || baseRef.startsWith("-") || baseRef.contains("\0")) {
            throw new IllegalArgumentException("Invalid base ref: " + baseRef);
        }
    }

    @Override
    public String getDiff() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "git", "diff", "--no-ext-diff", "--unified=3", "--merge-base", baseRef, "HEAD"
        ).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        String text = new String(output, StandardCharsets.UTF_8);
        if (exitCode != 0) {
            throw new IOException("git diff failed (exit " + exitCode + "): " + text.strip());
        }
        return text;
    }
}
