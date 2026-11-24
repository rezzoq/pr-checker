package dev.prchecker.diff;

import java.io.IOException;

public interface DiffSource {
    String getDiff() throws IOException, InterruptedException;
}
