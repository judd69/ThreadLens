package dev.threadlens.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything ThreadLens derives from one parsed dump. */
public final class DumpAnalysis {
    public final List<ThreadInfo> threads;
    public final Map<String, Integer> stateCounts = new LinkedHashMap<>();
    public int daemonCount;
    /** Lock id -> LockInfo, first-seen order. */
    public final Map<String, LockInfo> locks = new LinkedHashMap<>();
    /** Wait-for edges: waiter thread name -> holder thread name. */
    public final Map<String, String> waitFor = new LinkedHashMap<>();
    /** Deadlock cycles, each a list of thread names in wait order (last waits for first). */
    public final List<List<String>> deadlockCycles = new ArrayList<>();
    /** Wait chains: start thread -> ordered holder chain (does not repeat the start). */
    public final Map<String, List<String>> waitChains = new LinkedHashMap<>();
    /** Locks sorted by waiter count, most contended first. */
    public final List<LockInfo> contendedLocks = new ArrayList<>();
    /** Top frame -> thread count, most common first. */
    public final List<Map.Entry<String, Integer>> hotFrames = new ArrayList<>();
    /** Thread-name group (trailing -<digits> stripped) -> thread count. */
    public final List<Map.Entry<String, Integer>> threadGroups = new ArrayList<>();
    /** DEADLOCKED, CONTENTION, BLOCKED, or HEALTHY. */
    public String verdict = "HEALTHY";

    public DumpAnalysis(List<ThreadInfo> threads) {
        this.threads = threads;
    }

    public int blockedCount() {
        return stateCounts.getOrDefault("BLOCKED", 0);
    }

    public int total() {
        return threads.size();
    }

    public boolean hasDeadlock() {
        return !deadlockCycles.isEmpty();
    }

    public ThreadInfo findThread(String name) {
        for (ThreadInfo t : threads) {
            if (t.name.equals(name)) {
                return t;
            }
        }
        return null;
    }
}
