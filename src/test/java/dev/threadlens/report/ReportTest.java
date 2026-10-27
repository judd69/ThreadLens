package dev.threadlens.report;

import dev.threadlens.model.DumpAnalysis;
import dev.threadlens.parse.ThreadDumpParser;
import dev.threadlens.analyze.Analyzer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReportTest {

    private DumpAnalysis deadlock() throws Exception {
        return Analyzer.analyze(ThreadDumpParser.parseFile(Path.of("examples/deadlock.txt")));
    }

    @Test
    void textReportNamesDeadlockedThreads() throws Exception {
        String text = TextReport.render(deadlock(), false);
        assertTrue(text.contains("DEADLOCKED"));
        assertTrue(text.contains("pool-1-thread-1"));
        assertTrue(text.contains("pool-1-thread-2"));
        assertTrue(text.contains("BLOCKED=2"));
        assertFalse(text.contains("\u001B["), "no ANSI codes when colour is off");
    }

    @Test
    void jsonReportIsStructuredAndEscapesNames() throws Exception {
        String json = JsonReport.render(deadlock());
        assertTrue(json.contains("\"verdict\": \"DEADLOCKED\""));
        assertTrue(json.contains("\"totalThreads\": 6"));
        assertTrue(json.contains("pool-1-thread-1"));
        assertTrue(json.startsWith("{") && json.trim().endsWith("}"));
    }

    @Test
    void htmlReportIsSelfContained() throws Exception {
        String html = HtmlReport.render(deadlock(), "examples/deadlock.txt");
        assertTrue(html.contains("pool-1-thread-1"));
        assertTrue(html.contains("Deadlock cycles"));
        assertFalse(html.contains("src=\"http"), "no external scripts");
        assertFalse(html.contains("href=\"http"), "no external stylesheets");
        assertFalse(html.contains("<link"), "no external link tags");
    }

    @Test
    void htmlEscapesThreadNames() {
        DumpAnalysis a = Analyzer.analyze(ThreadDumpParser.parse(
                "\"<evil>&\" #1 prio=5 os_prio=0 tid=0x1 nid=0x1 runnable [0x0]\n"
                        + "   java.lang.Thread.State: RUNNABLE\n"
                        + "        at com.example.X.y(X.java:1)\n"));
        String html = HtmlReport.render(a, "test");
        assertFalse(html.contains("\"<evil>&\""));
        assertTrue(html.contains("&lt;evil&gt;&amp;"));
    }
}
