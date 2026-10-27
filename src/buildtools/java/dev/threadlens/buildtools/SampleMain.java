package dev.threadlens.buildtools;

import dev.threadlens.ThreadLens;
import dev.threadlens.model.DumpAnalysis;
import dev.threadlens.report.HtmlReport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regenerates docs/sample-report.html from examples/deadlock.txt via the real pipeline. */
public final class SampleMain {
    public static void main(String[] argv) throws Exception {
        if (argv.length != 2) {
            System.err.println("Usage: SampleMain <dump.txt> <sample-report.html>");
            System.exit(2);
        }
        DumpAnalysis analysis = ThreadLens.analyzeFile(Path.of(argv[0]));
        Files.writeString(Path.of(argv[1]), HtmlReport.render(analysis, "examples/deadlock.txt"),
                StandardCharsets.UTF_8);
        System.out.println("Wrote " + argv[1] + " (" + analysis.total() + " threads, verdict "
                + analysis.verdict + ")");
    }
}
