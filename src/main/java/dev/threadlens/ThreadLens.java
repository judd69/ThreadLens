package dev.threadlens;

import dev.threadlens.analyze.Analyzer;
import dev.threadlens.cli.Args;
import dev.threadlens.model.DumpAnalysis;
import dev.threadlens.model.ThreadInfo;
import dev.threadlens.parse.ThreadDumpParser;
import dev.threadlens.report.HtmlReport;
import dev.threadlens.report.JsonReport;
import dev.threadlens.report.TextReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Entry point: parse a dump, analyse it, render one report. */
public final class ThreadLens {

    public static void main(String[] argv) {
        System.exit(run(argv));
    }

    /** Runs the CLI; returns the process exit code (kept separate for tests). */
    public static int run(String[] argv) {
        final Args args;
        try {
            args = Args.parse(argv);
        } catch (IllegalArgumentException e) {
            System.err.println("threadlens: " + e.getMessage());
            return 2;
        }

        final List<ThreadInfo> threads;
        try {
            threads = ThreadDumpParser.parseFile(args.input);
        } catch (IOException e) {
            System.err.println("threadlens: cannot read " + args.input + ": " + e.getMessage());
            return 2;
        }

        DumpAnalysis analysis = Analyzer.analyze(threads);
        String report = switch (args.format) {
            case "json" -> JsonReport.render(analysis);
            case "html" -> HtmlReport.render(analysis, args.input.getFileName().toString());
            default -> TextReport.render(analysis, !args.noColor && System.console() != null);
        };

        try {
            if (args.output != null) {
                Files.writeString(args.output, report, StandardCharsets.UTF_8);
            } else {
                System.out.print(report);
            }
        } catch (IOException e) {
            System.err.println("threadlens: cannot write report: " + e.getMessage());
            return 2;
        }

        if (args.failOn.equals("deadlock") && analysis.hasDeadlock()) {
            return 1;
        }
        if (args.failOn.equals("blocked") && analysis.blockedCount() > 0) {
            return 1;
        }
        return 0;
    }

    /** Convenience for embedding: analyse a dump file without CLI plumbing. */
    public static DumpAnalysis analyzeFile(Path input) throws IOException {
        return Analyzer.analyze(ThreadDumpParser.parseFile(input));
    }
}
