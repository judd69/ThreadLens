package dev.threadlens.report;

import dev.threadlens.model.DumpAnalysis;
import dev.threadlens.model.LockInfo;
import dev.threadlens.model.ThreadInfo;
import dev.threadlens.util.Json;

import java.util.List;
import java.util.Map;

/** Machine-readable report: summary + deadlocks + locks + every thread. */
public final class JsonReport {
    private JsonReport() {
    }

    public static String render(DumpAnalysis a) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"verdict\": ").append(Json.quoted(a.verdict)).append(",\n");
        out.append("  \"totalThreads\": ").append(a.total()).append(",\n");
        out.append("  \"daemonThreads\": ").append(a.daemonCount).append(",\n");
        out.append("  \"stateCounts\": {");
        boolean first = true;
        for (Map.Entry<String, Integer> e : a.stateCounts.entrySet()) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(Json.quoted(e.getKey())).append(": ").append(e.getValue());
        }
        out.append("},\n");

        out.append("  \"deadlockCycles\": [");
        first = true;
        for (List<String> cycle : a.deadlockCycles) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append("[");
            boolean innerFirst = true;
            for (String name : cycle) {
                if (!innerFirst) {
                    out.append(", ");
                }
                innerFirst = false;
                out.append(Json.quoted(name));
            }
            out.append("]");
        }
        out.append("],\n");

        out.append("  \"contendedLocks\": [");
        first = true;
        for (LockInfo lock : a.contendedLocks) {
            if (lock.waiters.isEmpty() && lock.holder == null) {
                continue;
            }
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append("{\"id\": ").append(Json.quoted(lock.id))
                    .append(", \"className\": ").append(Json.quoted(lock.className))
                    .append(", \"holder\": ").append(lock.holder == null ? "null" : Json.quoted(lock.holder))
                    .append(", \"waiters\": [");
            boolean wFirst = true;
            for (String w : lock.waiters) {
                if (!wFirst) {
                    out.append(", ");
                }
                wFirst = false;
                out.append(Json.quoted(w));
            }
            out.append("]}");
        }
        out.append("],\n");

        out.append("  \"threads\": [\n");
        first = true;
        for (ThreadInfo t : a.threads) {
            if (!first) {
                out.append(",\n");
            }
            first = false;
            out.append("    {\"name\": ").append(Json.quoted(t.name))
                    .append(", \"id\": ").append(t.id)
                    .append(", \"daemon\": ").append(t.daemon)
                    .append(", \"state\": ").append(Json.quoted(t.state))
                    .append(", \"waitingFor\": ").append(t.waitingFor == null ? "null" : Json.quoted(t.waitingFor))
                    .append(", \"waitingForHolder\": ");
            String holder = a.waitFor.get(t.name);
            out.append(holder == null ? "null" : Json.quoted(holder));
            out.append(", \"topFrame\": ").append(Json.quoted(t.topFrame()))
                    .append("}");
        }
        out.append("\n  ]\n}\n");
        return out.toString();
    }
}
