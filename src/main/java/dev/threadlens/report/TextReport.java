package dev.threadlens.report;

import dev.threadlens.analyze.Analyzer;
import dev.threadlens.model.DumpAnalysis;
import dev.threadlens.model.LockInfo;
import dev.threadlens.model.ThreadInfo;
import dev.threadlens.util.Ansi;

import java.util.List;
import java.util.Map;

/** Human-readable terminal report. */
public final class TextReport {
    private TextReport() {
    }

    public static String render(DumpAnalysis a, boolean color) {
        Ansi ansi = new Ansi(color);
        StringBuilder out = new StringBuilder();
        String verdictLabel = switch (a.verdict) {
            case "DEADLOCKED" -> ansi.red("DEADLOCKED");
            case "CONTENTION" -> ansi.yellow("HIGH CONTENTION");
            case "BLOCKED" -> ansi.yellow("BLOCKED THREADS");
            default -> ansi.green("HEALTHY");
        };
        out.append(ansi.bold("ThreadLens")).append(" — ")
                .append(a.total()).append(" threads, ")
                .append(a.daemonCount).append(" daemon, verdict: ")
                .append(verdictLabel).append("\n");

        out.append("States:");
        for (Map.Entry<String, Integer> e : a.stateCounts.entrySet()) {
            out.append(" ").append(e.getKey()).append("=").append(e.getValue());
        }
        out.append("\n");

        if (a.hasDeadlock()) {
            out.append("\n").append(ansi.red(ansi.bold("DEADLOCKS:"))).append("\n");
            int i = 1;
            for (List<String> cycle : a.deadlockCycles) {
                out.append("  ").append(i++).append(". ")
                        .append(String.join(" -> ", cycle))
                        .append(" -> ").append(cycle.get(0)).append("\n");
            }
        }

        LockInfo top = Analyzer.topContendedLock(a);
        if (top != null && !top.waiters.isEmpty()) {
            out.append("\nMost contended lock: ").append(top.id)
                    .append(" (").append(top.className).append(")")
                    .append(", held by ").append(top.holder == null ? "(holder not in dump)" : top.holder)
                    .append(", ").append(top.waiters.size()).append(" waiter(s): ")
                    .append(String.join(", ", top.waiters)).append("\n");
        }

        if (!a.waitChains.isEmpty()) {
            out.append("\nWait chains (longest first):\n");
            a.waitChains.entrySet().stream()
                    .sorted((x, y) -> Integer.compare(y.getValue().size(), x.getValue().size()))
                    .limit(10)
                    .forEach(e -> out.append("  ").append(e.getKey()).append(" -> ")
                            .append(String.join(" -> ", e.getValue())).append("\n"));
        }

        if (!a.hotFrames.isEmpty()) {
            out.append("\nHottest top frames:\n");
            a.hotFrames.stream().limit(10).forEach(e ->
                    out.append("  ").append(e.getValue()).append("x  ").append(e.getKey()).append("\n"));
        }

        out.append("\nThreads:\n");
        for (ThreadInfo t : a.threads) {
            out.append("  ").append(t.daemon ? "[daemon] " : "")
                    .append(t.name).append("  ").append(t.state);
            if (t.waitingFor != null) {
                String holder = a.waitFor.get(t.name);
                out.append("  waiting for ").append(t.waitingFor)
                        .append(holder != null ? " held by " + holder : " (holder not in dump)");
            }
            if (!t.topMethod().isEmpty()) {
                out.append("  @ ").append(t.topMethod());
            }
            out.append("\n");
        }
        return out.toString();
    }
}
