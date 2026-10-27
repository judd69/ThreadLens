package dev.threadlens.parse;

import dev.threadlens.model.ThreadInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses jstack-style thread dumps.
 *
 * Handles the classic HotSpot format:
 * <pre>
 * "worker-1" #12 daemon prio=5 os_prio=0 tid=0x... nid=0x... waiting for monitor entry [0x...]
 *    java.lang.Thread.State: BLOCKED (on object monitor)
 *         at com.example.Service.doWork(Service.java:42)
 *         - waiting to lock &lt;0x00000000d6d6baf8&gt; (a java.lang.Object)
 *         - locked &lt;0x00000000d6d6bb08&gt; (a java.lang.Object)
 * </pre>
 * plus parking waits ({@code - parking to wait for}), {@code - waiting on},
 * {@code Locked ownable synchronizers:} sections, and the JDK's trailing
 * "Found one Java-level deadlock:" footer (which restates threads and is
 * deliberately NOT re-parsed as new threads, because its lines carry no
 * {@code #id prio=} header).
 */
public final class ThreadDumpParser {

    private static final Pattern HEADER = Pattern.compile(
            "^\"([^\"]+)\"\\s+#(\\d+)(\\s+daemon)?\\s+prio=(\\d+)\\b.*$");
    private static final Pattern STATE_LINE = Pattern.compile(
            "^\\s*java\\.lang\\.Thread\\.State:\\s+([A-Z_]+)(.*)$");
    private static final Pattern WAITING_TO_LOCK = Pattern.compile(
            "-\\s+waiting to lock\\s+<(0x[0-9a-fA-F]+)>\\s+\\(a\\s+([^)]+)\\)");
    private static final Pattern WAITING_ON = Pattern.compile(
            "-\\s+waiting on\\s+<(0x[0-9a-fA-F]+)>\\s+\\(a\\s+([^)]+)\\)");
    private static final Pattern PARKING = Pattern.compile(
            "-\\s+parking to wait for\\s+<(0x[0-9a-fA-F]+)>\\s+\\(a\\s+([^)]+)\\)");
    private static final Pattern LOCKED = Pattern.compile(
            "-\\s+locked\\s+<(0x[0-9a-fA-F]+)>\\s+\\(a\\s+([^)]+)\\)");
    private static final Pattern OWNABLE_LINE = Pattern.compile(
            "-\\s+<(0x[0-9a-fA-F]+)>\\s+\\(a\\s+([^)]+)\\)");

    private ThreadDumpParser() {
    }

    public static List<ThreadInfo> parseFile(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static List<ThreadInfo> parse(String text) {
        List<ThreadInfo> threads = new ArrayList<>();
        ThreadInfo current = null;
        boolean inOwnableSynchronizers = false;

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.stripTrailing();
            String trimmed = line.strip();

            if (trimmed.isEmpty()) {
                inOwnableSynchronizers = false;
                continue;
            }

            Matcher header = HEADER.matcher(trimmed);
            if (header.matches()) {
                current = new ThreadInfo(
                        header.group(1),
                        Integer.parseInt(header.group(2)),
                        header.group(3) != null,
                        Integer.parseInt(header.group(4)));
                // Remember the header's own state words as a fallback.
                String lower = trimmed.toLowerCase();
                if (lower.contains("waiting for monitor entry")) {
                    current.headerState = "waiting for monitor entry";
                } else if (lower.contains("waiting on condition")) {
                    current.headerState = "waiting on condition";
                } else if (lower.contains("in object.wait()")) {
                    current.headerState = "in Object.wait()";
                } else if (lower.contains("runnable")) {
                    current.headerState = "runnable";
                }
                threads.add(current);
                inOwnableSynchronizers = false;
                continue;
            }

            if (current == null) {
                continue; // Preamble, JNI global refs, deadlock footer: not a thread block.
            }

            if (trimmed.startsWith("Locked ownable synchronizers:")) {
                inOwnableSynchronizers = true;
                continue;
            }
            if (trimmed.startsWith("Locked monitors:")) {
                inOwnableSynchronizers = false;
                continue;
            }

            Matcher state = STATE_LINE.matcher(trimmed);
            if (state.matches()) {
                current.state = state.group(1);
                current.stateDetail = state.group(2).strip();
                continue;
            }

            Matcher m;
            if ((m = WAITING_TO_LOCK.matcher(trimmed)).find()) {
                current.waitingFor = m.group(1);
                current.waitingForClass = m.group(2);
                current.waitKind = "lock";
                continue;
            }
            if ((m = PARKING.matcher(trimmed)).find()) {
                current.waitingFor = m.group(1);
                current.waitingForClass = m.group(2);
                current.waitKind = "parking";
                continue;
            }
            if ((m = WAITING_ON.matcher(trimmed)).find()) {
                current.waitingFor = m.group(1);
                current.waitingForClass = m.group(2);
                current.waitKind = "monitor";
                continue;
            }
            if ((m = LOCKED.matcher(trimmed)).find()) {
                addLocked(current, m.group(1), m.group(2));
                continue;
            }
            if (inOwnableSynchronizers && (m = OWNABLE_LINE.matcher(trimmed)).find()) {
                addLocked(current, m.group(1), m.group(2));
                continue;
            }
            if (trimmed.startsWith("at ")) {
                current.frames.add(trimmed.substring(3).strip());
            }
            // Anything else (native frames, "Locked monitors" entries, "- eliminated")
            // is ignored: it carries no wait/hold information ThreadLens reports on.
        }

        // Fallback state from the header when no Thread.State line was present.
        for (ThreadInfo t : threads) {
            if ("UNKNOWN".equals(t.state)) {
                switch (t.headerState) {
                    case "waiting for monitor entry" -> t.state = "BLOCKED";
                    case "waiting on condition" -> t.state = "WAITING";
                    case "in Object.wait()" -> t.state = "WAITING";
                    case "runnable" -> t.state = "RUNNABLE";
                    default -> {
                        // Leave UNKNOWN: better to say so than to guess.
                    }
                }
            }
        }
        return threads;
    }

    private static void addLocked(ThreadInfo thread, String id, String className) {
        if (!thread.locked.contains(id)) {
            thread.locked.add(id);
        }
        thread.lockedClasses.putIfAbsent(id, className);
    }
}
