package dev.threadlens.cli;

import java.nio.file.Path;

/** Parsed command-line arguments. See ThreadLens usage text for the full contract. */
public final class Args {
    public Path input;
    public String format = "text";
    public Path output;
    public boolean noColor;
    /** "deadlock", "blocked", or "none". */
    public String failOn = "none";

    public static Args parse(String[] argv) {
        Args args = new Args();
        for (int i = 0; i < argv.length; i++) {
            String arg = argv[i];
            switch (arg) {
                case "--help", "-h" -> {
                    System.out.print(usage());
                    System.exit(0);
                }
                case "--format" -> args.format = next(argv, ++i, "--format");
                case "--output" -> args.output = Path.of(next(argv, ++i, "--output"));
                case "--no-color" -> args.noColor = true;
                case "--fail-on" -> args.failOn = next(argv, ++i, "--fail-on");
                default -> {
                    if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown flag: " + arg);
                    }
                    if (args.input != null) {
                        throw new IllegalArgumentException("Only one input file is accepted, got: " + arg);
                    }
                    args.input = Path.of(arg);
                }
            }
        }
        if (args.input == null) {
            throw new IllegalArgumentException("Missing input file.\n" + usage());
        }
        if (!args.format.equals("text") && !args.format.equals("json") && !args.format.equals("html")) {
            throw new IllegalArgumentException("Unknown --format: " + args.format + " (expected text, json, or html)");
        }
        if (!args.failOn.equals("deadlock") && !args.failOn.equals("blocked") && !args.failOn.equals("none")) {
            throw new IllegalArgumentException("Unknown --fail-on: " + args.failOn + " (expected deadlock, blocked, or none)");
        }
        return args;
    }

    private static String next(String[] argv, int i, String flag) {
        if (i >= argv.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return argv[i];
    }

    public static String usage() {
        return """
                ThreadLens — X-ray a Java thread dump.

                Usage: java -jar threadlens.jar <dump.txt> [options]

                Options:
                  --format text|json|html   Report format (default: text)
                  --output <file>           Write the report to a file (default: stdout)
                  --no-color                Disable ANSI colour in text reports
                  --fail-on deadlock|blocked|none
                                            Exit 1 when a deadlock is found (deadlock),
                                            when any thread is BLOCKED (blocked),
                                            or never fail on findings (none, default)
                  --help, -h                Show this help

                Exit codes: 0 = analysed, 1 = --fail-on condition hit,
                            2 = bad input or flags.

                Get a dump with:  jstack <pid> > dump.txt
                """;
    }
}
