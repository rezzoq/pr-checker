package dev.prchecker.llm;

import java.io.IOException;
import java.util.List;

public interface LlmClient {
    List<Finding> review(String prompt) throws IOException, InterruptedException;

    record Finding(String file, Integer line, String severity, String text) {
    }
}
