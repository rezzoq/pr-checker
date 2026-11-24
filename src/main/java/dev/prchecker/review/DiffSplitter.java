package dev.prchecker.review;

import java.util.ArrayList;
import java.util.List;

public final class DiffSplitter {
    public static final String MAX_CHUNK_CHARS_ENV = "PR_CHECKER_MAX_CHUNK_CHARS";
    private static final String UNKNOWN_FILE = "(diff without file header)";

    public SplitResult split(String diff, int maxChunkChars) {
        if (maxChunkChars <= 0) {
            throw new IllegalArgumentException("maxChunkChars must be positive");
        }
        if (diff == null || diff.isEmpty()) {
            return new SplitResult(List.of(), List.of());
        }

        List<FileDiff> files = parseFiles(diff);
        List<Chunk> chunks = new ArrayList<>();
        List<String> skippedFiles = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        List<String> currentFiles = new ArrayList<>();

        for (FileDiff file : files) {
            int fileChars = file.content().length();
            if (fileChars > maxChunkChars) {
                flush(currentChunk, currentFiles, chunks);
                skippedFiles.add(file.path());
                continue;
            }
            if (!currentChunk.isEmpty() && currentChunk.length() + fileChars > maxChunkChars) {
                flush(currentChunk, currentFiles, chunks);
            }
            currentChunk.append(file.content());
            currentFiles.add(file.path());
        }
        flush(currentChunk, currentFiles, chunks);
        return new SplitResult(chunks, skippedFiles);
    }

    private List<FileDiff> parseFiles(String diff) {
        List<FileDiff> files = new ArrayList<>();
        int firstHeader = findNextHeader(diff, 0);
        if (firstHeader > 0) {
            files.add(new FileDiff(UNKNOWN_FILE, diff.substring(0, firstHeader)));
        }
        if (firstHeader < 0) {
            files.add(new FileDiff(UNKNOWN_FILE, diff));
            return files;
        }

        int start = firstHeader;
        while (start < diff.length()) {
            int nextHeader = findNextHeader(diff, lineEndAfter(diff, start));
            int end = nextHeader < 0 ? diff.length() : nextHeader;
            String fileDiff = diff.substring(start, end);
            files.add(new FileDiff(filePath(fileDiff), fileDiff));
            if (nextHeader < 0) {
                break;
            }
            start = nextHeader;
        }
        return files;
    }

    private int findNextHeader(String diff, int fromIndex) {
        int lineStart = fromIndex;
        while (lineStart < diff.length()) {
            if (diff.startsWith("diff --git ", lineStart)) {
                return lineStart;
            }
            int newline = diff.indexOf('\n', lineStart);
            if (newline < 0) {
                return -1;
            }
            lineStart = newline + 1;
        }
        return -1;
    }

    private int lineEndAfter(String diff, int lineStart) {
        int newline = diff.indexOf('\n', lineStart);
        return newline < 0 ? diff.length() : newline + 1;
    }

    private String filePath(String fileDiff) {
        int headerEnd = fileDiff.indexOf('\n');
        String header = headerEnd < 0 ? fileDiff : fileDiff.substring(0, headerEnd);
        int destination = header.lastIndexOf(" b/");
        if (destination < 0) {
            return UNKNOWN_FILE;
        }
        return header.substring(destination + 3).replaceAll("^\"|\"$", "");
    }

    private void flush(StringBuilder content, List<String> files, List<Chunk> chunks) {
        if (!content.isEmpty()) {
            chunks.add(new Chunk(content.toString(), files));
            content.setLength(0);
            files.clear();
        }
    }

    private record FileDiff(String path, String content) {
    }

    public record Chunk(String diff, List<String> files) {
        public Chunk {
            files = List.copyOf(files);
        }
    }

    public record SplitResult(List<Chunk> chunks, List<String> skippedFiles) {
        public SplitResult {
            chunks = List.copyOf(chunks);
            skippedFiles = List.copyOf(skippedFiles);
        }
    }
}
