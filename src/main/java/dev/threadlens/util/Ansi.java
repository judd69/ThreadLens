package dev.threadlens.util;

/** Tiny ANSI helper; every code is a no-op when colour is disabled. */
public final class Ansi {
    private final boolean enabled;

    public Ansi(boolean enabled) {
        this.enabled = enabled;
    }

    public String red(String s) {
        return wrap("\u001B[31m", s);
    }

    public String green(String s) {
        return wrap("\u001B[32m", s);
    }

    public String yellow(String s) {
        return wrap("\u001B[33m", s);
    }

    public String bold(String s) {
        return wrap("\u001B[1m", s);
    }

    public String dim(String s) {
        return wrap("\u001B[2m", s);
    }

    private String wrap(String code, String s) {
        return enabled ? code + s + "\u001B[0m" : s;
    }
}
