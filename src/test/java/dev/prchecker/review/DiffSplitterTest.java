package dev.prchecker.review;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiffSplitterTest {
    private final DiffSplitter splitter = new DiffSplitter();

    @Test
    void groupsWholeFilesWithinCharacterLimit() {
        String first = fileDiff("A.java", "one");
        String second = fileDiff("B.java", "two");
        String third = fileDiff("C.java", "three");
        int limit = first.length() + second.length();

        DiffSplitter.SplitResult result = splitter.split(first + second + third, limit);

        assertEquals(2, result.chunks().size());
        assertEquals(first + second, result.chunks().get(0).diff());
        assertEquals(third, result.chunks().get(1).diff());
        assertEquals(limit, result.chunks().get(0).diff().length());
        assertEquals(java.util.List.of("A.java", "B.java"), result.chunks().get(0).files());
    }

    @Test
    void skipsWholeFileThatExceedsLimitAndKeepsFollowingFiles() {
        String oversized = fileDiff("large.java", "this file is too big");
        String accepted = fileDiff("small.java", "ok");
        int limit = accepted.length();

        DiffSplitter.SplitResult result = splitter.split(oversized + accepted, limit);

        assertEquals(java.util.List.of("large.java"), result.skippedFiles());
        assertEquals(1, result.chunks().size());
        assertEquals(accepted, result.chunks().get(0).diff());
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> splitter.split("diff", 0));
    }

    private String fileDiff(String name, String content) {
        return "diff --git a/" + name + " b/" + name + "\n"
                + "--- a/" + name + "\n"
                + "+++ b/" + name + "\n"
                + "@@ -1 +1 @@\n"
                + "+" + content + "\n";
    }
}
