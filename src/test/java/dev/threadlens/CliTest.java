package dev.threadlens;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CliTest {

    @Test
    void deadlockWithFailOnDeadlockExitsOne(@TempDir Path tmp) throws Exception {
        Path dump = tmp.resolve("dump.txt");
        Files.copy(Path.of("examples/deadlock.txt"), dump);
        Path out = tmp.resolve("report.txt");
        int code = ThreadLens.run(new String[]{
                dump.toString(), "--format", "text", "--no-color",
                "--output", out.toString(), "--fail-on", "deadlock"});
        assertEquals(1, code);
        assertTrue(Files.readString(out).contains("DEADLOCKED"));
    }

    @Test
    void healthyWithFailOnDeadlockExitsZero(@TempDir Path tmp) throws Exception {
        Path dump = tmp.resolve("dump.txt");
        Files.copy(Path.of("examples/healthy.txt"), dump);
        Path out = tmp.resolve("report.html");
        int code = ThreadLens.run(new String[]{
                dump.toString(), "--format", "html", "--output", out.toString(),
                "--fail-on", "deadlock"});
        assertEquals(0, code);
        assertTrue(Files.readString(out).contains("HEALTHY"));
    }

    @Test
    void blockedWithFailOnBlockedExitsOne(@TempDir Path tmp) throws Exception {
        Path dump = tmp.resolve("dump.txt");
        Files.copy(Path.of("examples/contention.txt"), dump);
        Path out = tmp.resolve("report.json");
        int code = ThreadLens.run(new String[]{
                dump.toString(), "--format", "json", "--output", out.toString(),
                "--fail-on", "blocked"});
        assertEquals(1, code);
        assertTrue(Files.readString(out).contains("\"verdict\": \"CONTENTION\""));
    }

    @Test
    void missingFileExitsTwo() {
        assertEquals(2, ThreadLens.run(new String[]{"no-such-dump.txt"}));
    }

    @Test
    void badFlagAndBadFormatExitTwo() {
        assertEquals(2, ThreadLens.run(new String[]{"examples/healthy.txt", "--bogus"}));
        assertEquals(2, ThreadLens.run(new String[]{"examples/healthy.txt", "--format", "pdf"}));
        assertEquals(2, ThreadLens.run(new String[]{}));
    }
}
