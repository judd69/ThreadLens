package dev.threadlens.analyze;

import dev.threadlens.model.DumpAnalysis;
import dev.threadlens.model.LockInfo;
import dev.threadlens.model.ThreadInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Derives state counts, lock contention, wait-for graph, deadlock cycles and hot frames. */
public final class Analyzer {

    private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.*)-\\d+$");

    private Analyzer() {
    }

    public static DumpAnalysis analyze(List<ThreadInfo> threads) {
        DumpAnalysis result = new DumpAnalysis(threads);

        for (ThreadInfo t : threads) {
            result.stateCounts.merge(t.state, 1, Integer::sum);
            if (t.daemon) {
                result.daemonCount++;
            }
        }

        // Lock table: holders first, then waiters (a waiter may name a lock whose
        // holder thread appears later in the dump, or not at all).
        for (ThreadInfo t : threads) {
            for (String id : t.locked) {
                LockInfo lock = result.locks.computeIfAbsent(id, LockInfo::new);
                if (lock.holder == null) {
                    lock.holder = t.name;
                }
                if (lock.className.isEmpty()) {
                    lock.className = t.lockedClasses.getOrDefault(id, "");
                }
            }
        }
        for (ThreadInfo t : threads) {
            if (t.waitingFor != null) {
                LockInfo lock = result.locks.computeIfAbsent(t.waitingFor, LockInfo::new);
                if (lock.className.isEmpty() && t.waitingForClass != null) {
                    lock.className = t.waitingForClass;
                }
                if (!lock.waiters.contains(t.name)) {
                    lock.waiters.add(t.name);
                }
            }
        }

        // Wait-for edges: waiter -> holder, only when the holder is a real thread.
        for (ThreadInfo t : threads) {
            if (t.waitingFor != null) {
                LockInfo lock = result.locks.get(t.waitingFor);
                if (lock != null && lock.holder != null && !lock.holder.equals(t.name)) {
                    result.waitFor.put(t.name, lock.holder);
                }
            }
        }

        result.deadlockCycles.addAll(findCycles(result.waitFor));

        // Wait chains: follow edges until a thread that waits for nobody, or a cycle.
        for (ThreadInfo t : threads) {
            List<String> chain = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            String next = result.waitFor.get(t.name);
            while (next != null && seen.add(next)) {
                chain.add(next);
                next = result.waitFor.get(next);
            }
            if (!chain.isEmpty()) {
                result.waitChains.put(t.name, chain);
            }
        }

        result.contendedLocks.addAll(result.locks.values());
        result.contendedLocks.sort((a, b) -> {
            int cmp = Integer.compare(b.waiters.size(), a.waiters.size());
            return cmp != 0 ? cmp : a.id.compareTo(b.id);
        });

        Map<String, Integer> hot = new LinkedHashMap<>();
        for (ThreadInfo t : threads) {
            String frame = t.topMethod();
            if (!frame.isEmpty()) {
                hot.merge(frame, 1, Integer::sum);
            }
        }
        result.hotFrames.addAll(hot.entrySet());
        result.hotFrames.sort((a, b) -> {
            int cmp = Integer.compare(b.getValue(), a.getValue());
            return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
        });

        Map<String, Integer> groups = new LinkedHashMap<>();
        for (ThreadInfo t : threads) {
            groups.merge(groupName(t.name), 1, Integer::sum);
        }
        result.threadGroups.addAll(groups.entrySet());
        result.threadGroups.sort((a, b) -> {
            int cmp = Integer.compare(b.getValue(), a.getValue());
            return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
        });

        if (result.hasDeadlock()) {
            result.verdict = "DEADLOCKED";
        } else if (!result.contendedLocks.isEmpty()
                && result.contendedLocks.get(0).waiters.size() >= 3) {
            result.verdict = "CONTENTION";
        } else if (result.blockedCount() > 0) {
            result.verdict = "BLOCKED";
        } else {
            result.verdict = "HEALTHY";
        }
        return result;
    }

    /** Groups "pool-2-thread-7" under "pool-2-thread", "main" under "main". */
    public static String groupName(String threadName) {
        var m = TRAILING_NUMBER.matcher(threadName);
        return m.matches() ? m.group(1) : threadName;
    }

    /**
     * Finds cycles in a functional graph (each node has at most one outgoing edge).
     * Each cycle is rotated so its lexicographically smallest name comes first,
     * and duplicate rotations of the same cycle are reported once.
     */
    static List<List<String>> findCycles(Map<String, String> edges) {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> seenCycleKeys = new HashSet<>();
        // 0 = unvisited, 1 = on current DFS stack, 2 = done.
        Map<String, Integer> color = new HashMap<>();
        for (String start : edges.keySet()) {
            if (color.getOrDefault(start, 0) != 0) {
                continue;
            }
            Deque<String> path = new ArrayDeque<>();
            String node = start;
            while (node != null && color.getOrDefault(node, 0) == 0) {
                color.put(node, 1);
                path.addLast(node);
                node = edges.get(node);
            }
            if (node != null && color.getOrDefault(node, 0) == 1) {
                // Cycle = path suffix starting at node.
                List<String> cycle = new ArrayList<>();
                boolean recording = false;
                for (String name : path) {
                    if (name.equals(node)) {
                        recording = true;
                    }
                    if (recording) {
                        cycle.add(name);
                    }
                }
                List<String> normalized = normalizeCycle(cycle);
                String key = String.join(">", normalized);
                if (seenCycleKeys.add(key)) {
                    cycles.add(normalized);
                }
            }
            for (String name : path) {
                color.put(name, 2);
            }
        }
        return cycles;
    }

    private static List<String> normalizeCycle(List<String> cycle) {
        if (cycle.isEmpty()) {
            return cycle;
        }
        int minIndex = 0;
        for (int i = 1; i < cycle.size(); i++) {
            if (cycle.get(i).compareTo(cycle.get(minIndex)) < 0) {
                minIndex = i;
            }
        }
        List<String> rotated = new ArrayList<>();
        for (int i = 0; i < cycle.size(); i++) {
            rotated.add(cycle.get((minIndex + i) % cycle.size()));
        }
        return rotated;
    }

    /** Longest wait chain length (0 when nothing waits on a known holder). */
    public static int longestChain(DumpAnalysis analysis) {
        int max = 0;
        for (List<String> chain : analysis.waitChains.values()) {
            max = Math.max(max, chain.size());
        }
        return max;
    }

    /** Most-contended lock, or null when no lock has any waiter. */
    public static LockInfo topContendedLock(DumpAnalysis analysis) {
        for (LockInfo lock : analysis.contendedLocks) {
            if (!lock.waiters.isEmpty()) {
                return lock;
            }
        }
        return null;
    }

}
