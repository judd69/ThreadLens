package dev.threadlens.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One thread from a jstack-style thread dump. */
public final class ThreadInfo {
    public final String name;
    public final int id;
    public final boolean daemon;
    public final int prio;
    public String state = "UNKNOWN";
    /** Raw state detail, e.g. "(on object monitor)" or "(parking)". May be empty. */
    public String stateDetail = "";
    /** Header suffix when no Thread.State line exists, e.g. "waiting for monitor entry". */
    public String headerState = "";
    /** Monitor / synchronizer this thread is waiting for, as a hex id like "0x00000000d6d6baf8". */
    public String waitingFor;
    public String waitingForClass;
    /** Kind of wait: "lock", "monitor", "parking", or "" when not waiting. */
    public String waitKind = "";
    /** Locks (monitor ids) this thread currently holds, in first-seen order. */
    public final List<String> locked = new ArrayList<>();
    /** Lock id -> class name for locks this thread holds. */
    public final Map<String, String> lockedClasses = new LinkedHashMap<>();
    /** Stack frames, first frame first, e.g. "com.example.Service.doWork(Service.java:42)". */
    public final List<String> frames = new ArrayList<>();

    public ThreadInfo(String name, int id, boolean daemon, int prio) {
        this.name = name;
        this.id = id;
        this.daemon = daemon;
        this.prio = prio;
    }

    /** Top stack frame, or "" when the thread has no frames. */
    public String topFrame() {
        return frames.isEmpty() ? "" : frames.get(0);
    }

    /** Top frame's method without its source location, e.g. "com.example.Service.doWork". */
    public String topMethod() {
        String frame = topFrame();
        int paren = frame.indexOf('(');
        return paren >= 0 ? frame.substring(0, paren) : frame;
    }

    public boolean isBlocked() {
        return "BLOCKED".equals(state);
    }

    public boolean isWaiting() {
        return "WAITING".equals(state) || "TIMED_WAITING".equals(state);
    }
}
