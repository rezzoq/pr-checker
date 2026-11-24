package dev.prchecker.llm;

import java.io.IOException;

public interface LlmClient {
    String review(String prompt) throws IOException, InterruptedException;
}
