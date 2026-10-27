package dev.threadlens.analyze;

import dev.threadlens.model.DumpAnalysis;
import dev.threadlens.model.ThreadInfo;
import dev.threadlens.parse.ThreadDumpParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzerTest {

    private static ThreadInfo thread(String name, String state) {
        ThreadInfo t = new ThreadInfo(name, 1, false, 5);
        t.state = state;
        return t;
    }

    private static ThreadInfo waiting(String name, String waitingFor, String locked) {
        ThreadInfo t = thread(name, "BLOCKED");
        t.waitingFor = waitingFor;
        t.waitKind = "lock";
        if (locked != null) {
            t.locked.add(locked);
            t.lockedClasses.put(locked, "java.lang.Object");
        }
        return t;
    }

    @Test
    void detectsTwoThreadDeadlockCycle() {
        DumpAnalysis a = Analyzer.analyze(List.of(
                waiting("alpha", "0x1", "0x2"),
                waiting("beta", "0x2", "0x1")));
        assertEquals("DEADLOCKED", a.verdict);
        assertEquals(1, a.deadlockCycles.size());
        assertEquals(List.of("alpha", "beta"), a.deadlockCycles.get(0));
        assertEquals("beta", a.waitFor.get("alpha"));
    }

    @Test
    void detectsThreeThreadCycleAndNormalizesIt() {
        DumpAnalysis a = Analyzer.analyze(List.of(
                waiting("zeta", "0x1", "0x3"),
                waiting("alpha", "0x2", "0x1"),
                waiting("mid", "0x3", "0x2")));
        assertTrue(a.hasDeadlock());
        // alpha -> mid -> zeta -> alpha, rotated to start at alpha.
        assertEquals(List.of("alpha", "mid", "zeta"), a.deadlockCycles.get(0));
    }

    @Test
    void waitChainStopsAtNonWaitingHolder() {
        ThreadInfo realHolder = thread("real", "RUNNABLE");
        realHolder.locked.add("0x8");
        DumpAnalysis b = Analyzer.analyze(List.of(
                waiting("middle", "0x8", null),
                realHolder));
        assertEquals(List.of("real"), b.waitChains.get("middle"));
        assertEquals(1, Analyzer.longestChain(b));
        assertEquals("BLOCKED", b.verdict);
        assertFalse(b.hasDeadlock());
    }

    @Test
    void contentionVerdictNeedsThreeWaiters() {
        ThreadInfo holder = thread("writer", "RUNNABLE");
        holder.locked.add("0x5");
        holder.lockedClasses.put("0x5", "java.util.HashMap");
        DumpAnalysis a = Analyzer.analyze(List.of(
                holder,
                waiting("r1", "0x5", null),
                waiting("r2", "0x5", null),
                waiting("r3", "0x5", null)));
        assertEquals("CONTENTION", a.verdict);
        assertEquals(3, Analyzer.topContendedLock(a).waiters.size());
        assertEquals("writer", Analyzer.topContendedLock(a).holder);
    }

    @Test
    void healthyDumpHasNoCyclesOrContention() throws Exception {
        DumpAnalysis a = Analyzer.analyze(ThreadDumpParser.parseFile(Path.of("examples/healthy.txt")));
        assertEquals("HEALTHY", a.verdict);
        assertFalse(a.hasDeadlock());
        assertEquals(0, a.blockedCount());
        assertEquals(7, a.total());
    }

    @Test
    void deadlockExampleAnalysesToDeadlocked() throws Exception {
        DumpAnalysis a = Analyzer.analyze(ThreadDumpParser.parseFile(Path.of("examples/deadlock.txt")));
        assertEquals("DEADLOCKED", a.verdict);
        assertEquals(6, a.total());
        assertEquals(2, a.blockedCount());
        assertEquals(2, a.daemonCount);
        assertEquals(List.of("pool-1-thread-1", "pool-1-thread-2"), a.deadlockCycles.get(0));
    }

    @Test
    void contentionExampleAnalysesToContention() throws Exception {
        DumpAnalysis a = Analyzer.analyze(ThreadDumpParser.parseFile(Path.of("examples/contention.txt")));
        assertEquals("CONTENTION", a.verdict);
        assertEquals(5, Analyzer.topContendedLock(a).waiters.size());
    }

    @Test
    void hotFramesAndGroupsAreCounted() {
        ThreadInfo t1 = thread("http-nio-8080-exec-1", "RUNNABLE");
        t1.frames.add("com.example.Web.handle(Web.java:1)");
        ThreadInfo t2 = thread("http-nio-8080-exec-2", "RUNNABLE");
        t2.frames.add("com.example.Web.handle(Web.java:1)");
        ThreadInfo t3 = thread("main", "RUNNABLE");
        t3.frames.add("com.example.App.main(App.java:1)");
        DumpAnalysis a = Analyzer.analyze(List.of(t1, t2, t3));
        assertEquals("com.example.Web.handle", a.hotFrames.get(0).getKey());
        assertEquals(2, a.hotFrames.get(0).getValue());
        assertEquals("http-nio-8080-exec", a.threadGroups.get(0).getKey());
        assertEquals(2, a.threadGroups.get(0).getValue());
    }

    @Test
    void unknownHolderProducesNoWaitEdge() {
        DumpAnalysis a = Analyzer.analyze(List.of(waiting("solo", "0xdead", null)));
        assertTrue(a.waitFor.isEmpty());
        assertTrue(a.waitChains.isEmpty());
    }
}
