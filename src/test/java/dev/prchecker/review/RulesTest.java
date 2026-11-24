package dev.prchecker.review;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesTest {
    private final Rules rules = new Rules();

    @Test
    void warnsWhenCodeChangesWithoutTestChanges() {
        String diff = "diff --git a/src/Main.java b/src/Main.java\n"
                + "--- a/src/Main.java\n+++ b/src/Main.java\n"
                + "@@ -1 +1 @@\n-old\n+new\n";

        var findings = rules.check(diff);

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).contains("тестов"));
    }

    @Test
    void doesNotWarnWhenTestsAreIncluded() {
        String diff = "diff --git a/src/Main.java b/src/Main.java\n"
                + "--- a/src/Main.java\n+++ b/src/Main.java\n@@ -1 +1 @@\n-old\n+new\n"
                + "diff --git a/src/test/MainTest.java b/src/test/MainTest.java\n"
                + "--- a/src/test/MainTest.java\n+++ b/src/test/MainTest.java\n";

        assertTrue(rules.check(diff).isEmpty());
    }

    @Test
    void flagsDiffAtTheConfiguredLargeDiffThreshold() {
        StringBuilder diff = new StringBuilder();
        for (int i = 0; i < 250; i++) {
            diff.append("+added\n-removed\n");
        }

        var findings = rules.check(diff.toString());

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).contains("500"));
    }
}
