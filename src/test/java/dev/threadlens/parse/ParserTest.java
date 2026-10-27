package dev.threadlens.parse;

import dev.threadlens.model.ThreadInfo;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    private static final String TWO_THREAD_CYCLE = """
            "alpha" #12 prio=5 os_prio=0 tid=0x00007f000001 nid=0x2a03 waiting for monitor entry [0x00007f000000]
               java.lang.Thread.State: BLOCKED (on object monitor)
                    at com.example.A.work(A.java:10)
                    - waiting to lock <0x00000000aaaa0001> (a java.lang.Object)
                    - locked <0x00000000aaaa0002> (a java.lang.Object)
            "beta" #13 prio=5 os_prio=0 tid=0x00007f000002 nid=0x2a04 waiting for monitor entry [0x00007f000100]
               java.lang.Thread.State: BLOCKED (on object monitor)
                    at com.example.B.work(B.java:20)
                    - waiting to lock <0x00000000aaaa0002> (a java.lang.Object)
                    - locked <0x00000000aaaa0001> (a java.lang.Object)
            """;

    @Test
    void parsesBlockedThreadsWithLocks() {
        List<ThreadInfo> threads = ThreadDumpParser.parse(TWO_THREAD_CYCLE);
        assertEquals(2, threads.size());
        ThreadInfo alpha = threads.get(0);
        assertEquals("alpha", alpha.name);
        assertEquals(12, alpha.id);
        assertFalse(alpha.daemon);
        assertEquals("BLOCKED", alpha.state);
        assertEquals("0x00000000aaaa0001", alpha.waitingFor);
        assertEquals("java.lang.Object", alpha.waitingForClass);
        assertEquals("lock", alpha.waitKind);
        assertEquals(List.of("0x00000000aaaa0002"), alpha.locked);
        assertEquals("com.example.A.work(A.java:10)", alpha.topFrame());
        assertEquals("com.example.A.work", alpha.topMethod());
    }

    @Test
    void parsesDaemonAndParkingWaits() {
        String dump = """
                "pool-1-thread-1" #7 daemon prio=5 os_prio=0 tid=0x00007f000003 nid=0x2a05 waiting on condition [0x00007f000200]
                   java.lang.Thread.State: WAITING (parking)
                        at jdk.internal.misc.Unsafe.park(Native Method)
                        - parking to wait for  <0x00000000bbbb0001> (a java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject)
                """;
        List<ThreadInfo> threads = ThreadDumpParser.parse(dump);
        assertEquals(1, threads.size());
        assertTrue(threads.get(0).daemon);
        assertEquals("WAITING", threads.get(0).state);
        assertEquals("parking", threads.get(0).waitKind);
        assertEquals("0x00000000bbbb0001", threads.get(0).waitingFor);
    }

    @Test
    void parsesOwnableSynchronizersAsHeldLocks() {
        String dump = """
                "worker" #9 prio=5 os_prio=0 tid=0x00007f000004 nid=0x2a06 runnable [0x00007f000300]
                   java.lang.Thread.State: RUNNABLE
                        at com.example.Worker.run(Worker.java:5)
                   Locked ownable synchronizers:
                        - <0x00000000cccc0001> (a java.util.concurrent.locks.ReentrantLock$NonfairSync)
                """;
        List<ThreadInfo> threads = ThreadDumpParser.parse(dump);
        assertEquals(List.of("0x00000000cccc0001"), threads.get(0).locked);
        assertEquals("java.util.concurrent.locks.ReentrantLock$NonfairSync",
                threads.get(0).lockedClasses.get("0x00000000cccc0001"));
    }

    @Test
    void fallsBackToHeaderStateWithoutStateLine() {
        String dump = """
                "lonely" #4 prio=5 os_prio=0 tid=0x00007f000005 nid=0x2a07 waiting for monitor entry [0x00007f000400]
                        at com.example.X.y(X.java:1)
                """;
        assertEquals("BLOCKED", ThreadDumpParser.parse(dump).get(0).state);
    }

    @Test
    void deadlockFooterIsNotReparsedAsThreads() throws Exception {
        // Run from the project root (see build.sh): the checked-in fixture has
        // 6 real threads plus a JDK deadlock footer restating 2 of them.
        List<ThreadInfo> threads = ThreadDumpParser.parseFile(Path.of("examples/deadlock.txt"));
        assertEquals(6, threads.size());
        assertEquals(2, threads.stream().filter(t -> t.name.startsWith("pool-1-thread-")).count());
    }

    @Test
    void emptyInputYieldsNoThreads() {
        assertTrue(ThreadDumpParser.parse("").isEmpty());
        assertTrue(ThreadDumpParser.parse("Full thread dump, nothing else here.\n").isEmpty());
    }
}
