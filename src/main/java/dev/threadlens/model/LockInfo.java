package dev.threadlens.model;

import java.util.ArrayList;
import java.util.List;

/** One monitor / synchronizer id seen in a dump, with its holder and waiters. */
public final class LockInfo {
    public final String id;
    public String className = "";
    public String holder;
    public final List<String> waiters = new ArrayList<>();

    public LockInfo(String id) {
        this.id = id;
    }
}
