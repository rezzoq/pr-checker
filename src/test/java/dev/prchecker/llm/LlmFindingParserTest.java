package dev.prchecker.llm;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmFindingParserTest {
    private final LlmFindingParser parser = new LlmFindingParser();

    @Test
    void parsesFindingsAndEmptyList() throws Exception {
        assertEquals(
                List.of(new LlmClient.Finding(
                        "src/Main.java",
                        12,
                        "HIGH",
                        "Potential null dereference"
                )),
                parser.parse("""
                        {"findings":[{"file":"src/Main.java","line":12,"severity":"high",
                        "text":"Potential null dereference"}]}
                        """)
        );
        assertEquals(List.of(), parser.parse("{\"findings\":[]}"));
    }

    @Test
    void parsesJsonWrappedInMarkdownCodeFence() throws Exception {
        assertEquals(
                List.of(new LlmClient.Finding(
                        "src/Main.java",
                        12,
                        "HIGH",
                        "Potential null dereference"
                )),
                parser.parse("""
                        ```json
                        {"findings":[{"file":"src/Main.java","line":12,"severity":"HIGH",
                        "text":"Potential null dereference"}]}
                        ```
                        """)
        );
        assertEquals(
                List.of(),
                parser.parse("""
                        ```
                        {"findings":[]}
                        ```
                        """)
        );
    }

    @Test
    void keepsFindingWhenLineIsMissingZeroOrNull() throws Exception {
        assertEquals(
                List.of(
                        new LlmClient.Finding("A.java", null, "HIGH", "Missing line"),
                        new LlmClient.Finding("B.java", null, "LOW", "Zero line"),
                        new LlmClient.Finding("C.java", null, "MEDIUM", "Null line")
                ),
                parser.parse("""
                        {"findings":[
                          {"file":"A.java","severity":"HIGH","text":"Missing line"},
                          {"file":"B.java","line":0,"severity":"LOW","text":"Zero line"},
                          {"file":"C.java","line":null,"severity":"MEDIUM","text":"Null line"}
                        ]}
                        """)
        );
    }

    @Test
    void rejectsMalformedOrIncompleteFinding() {
        assertThrows(IOException.class, () -> parser.parse("not json"));
        assertThrows(IOException.class, () -> parser.parse("{\"findings\":[]} trailing"));
        assertThrows(
                IOException.class,
                () -> parser.parse("{\"findings\":[{\"file\":\"A.java\",\"line\":1}]}")
        );
        assertThrows(
                IOException.class,
                () -> parser.parse("{\"findings\":[{\"file\":\"A.java\",\"line\":-1,"
                        + "\"severity\":\"HIGH\",\"text\":\"bad line\"}]}")
        );
        assertThrows(
                IOException.class,
                () -> parser.parse("{\"findings\":[],\"comment\":\"extra text\"}")
        );
        assertThrows(
                IOException.class,
                () -> parser.parse("{\"findings\":[],\"findings\":[]}"));
    }
}
