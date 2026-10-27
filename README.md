# ThreadLens 🔍🧵

**X-ray a Java thread dump in one command — no more scrolling through thousands of lines of `jstack` output to find the deadlock.**

A production JVM freezes. You run `jstack <pid>` and get a wall of stack traces.
Somewhere in there, two threads are waiting on each other's locks — or fifty
threads are piling up behind one contended lock. ThreadLens reads the dump and
tells you, in seconds: which threads are deadlocked, who is blocking whom,
which lock is most contended, and where every thread is actually stuck.

Day 9 of the [Lens arc](https://github.com/judd69): AIGuard audits your code,
JarLens audits your jars, CinderLens audits your speed, RepoScope audits any
repo, VulnLens audits your supply chain, CommitLens audits your history,
NetLens audits any address — **ThreadLens audits your threads.**

## Requirements

- Java 17+ (built with 21; no other dependencies — the shipped jar is pure `javac` output)
- A thread dump: `jstack <pid> > dump.txt` (or `jcmd <pid> Thread.print`)

## Build

```bash
./build.sh
```

Compiles all sources, runs the JUnit 5 suite (fails loudly on any failure),
packages `build/threadlens.jar`, and regenerates `docs/sample-report.html`.

## Run

```bash
# text report to stdout
java -jar build/threadlens.jar dump.txt

# self-contained HTML report (open it in a browser, or serve it anywhere)
java -jar build/threadlens.jar dump.txt --format html --output report.html

# machine-readable output for scripts and CI
java -jar build/threadlens.jar dump.txt --format json --output report.json

# CI gate: exit 1 if a deadlock is found, or if anything is BLOCKED
java -jar build/threadlens.jar dump.txt --fail-on deadlock
java -jar build/threadlens.jar dump.txt --fail-on blocked
```

### Example

`examples/deadlock.txt` is a small dump with a genuine two-thread deadlock
plus healthy bystander threads. Real output (trimmed):

```
ThreadLens — 6 threads, 2 daemon, verdict: DEADLOCKED
States: BLOCKED=2 RUNNABLE=2 WAITING=2

DEADLOCKS:
  1. pool-1-thread-1 -> pool-1-thread-2 -> pool-1-thread-1

Most contended lock: 0x00000000d6d6baf8 (java.lang.Object), held by pool-1-thread-2, 1 waiter(s): pool-1-thread-1

Threads:
  pool-1-thread-1  BLOCKED  waiting for 0x00000000d6d6baf8 held by pool-1-thread-2  @ com.example.BillingService.charge
  pool-1-thread-2  BLOCKED  waiting for 0x00000000d6d6bb08 held by pool-1-thread-1  @ com.example.InventoryService.reserve
  main  RUNNABLE  @ com.example.App.main
  http-nio-8080-exec-1  WAITING  waiting for 0x00000000e0012345 (holder not in dump)  @ jdk.internal.misc.Unsafe.park
```

A full interactive HTML report for that dump ships at
[`docs/sample-report.html`](docs/sample-report.html) — state breakdown,
deadlock cards, contended locks, hottest frames, and a searchable thread table.

## What it analyses

- **Thread states** — RUNNABLE / BLOCKED / WAITING / TIMED_WAITING counts, daemon vs non-daemon.
- **Deadlocks, detected independently** — ThreadLens builds its own wait-for
  graph (thread → lock holder) and finds cycles in it, so it catches deadlocks
  even when the JDK's own "Found one Java-level deadlock" footer is missing —
  and it does *not* double-count threads when that footer is present.
- **Lock contention** — every monitor id with its holder, class, and waiter
  list, most-contended first; a lock with 3+ waiters drives a CONTENTION verdict.
- **Wait chains** — for each waiting thread, the full holder chain it is stuck behind.
- **Hottest frames** — the most common top-of-stack methods across threads
  (where the JVM is actually spending its threads), plus thread-name groups
  (`http-nio-8080-exec-1`, `-2`, … group under `http-nio-8080-exec`).
- **Parsed formats** — classic `jstack` output: `waiting to lock`, `locked`,
  `waiting on`, `parking to wait for`, `Locked ownable synchronizers:`
  sections, daemon threads, and a header-state fallback when a thread has no
  `Thread.State:` line. A waiter whose lock holder is absent from the dump is
  reported honestly as "(holder not in dump)", never guessed.

## Exit codes

| Code | Meaning |
|------|---------|
| 0 | analysed (or `--fail-on` condition not hit) |
| 1 | `--fail-on deadlock` found a deadlock, or `--fail-on blocked` found a BLOCKED thread |
| 2 | bad input (missing/unreadable dump, bad flags) |

## Limitations

- **One dump at a time.** Comparing two dumps taken minutes apart (to spot
  threads stuck in the same place) is not supported yet — that is the natural
  next feature.
- **Monitor locks only.** Native frames, JNI locks, and locks held outside a
  thread block are not modelled; neither are virtual-thread carrier mappings.
- **No live attach.** ThreadLens reads a dump file you captured yourself; it
  does not attach to a running JVM (use `jstack` / `jcmd` for that).
- A thread waiting on a lock whose holder thread is not in the dump cannot be
  placed in a wait chain — it is reported, not resolved by guessing.

## Project layout

```
src/main/java/dev/threadlens/
  ThreadLens.java        entry point + exit codes
  cli/Args.java          flag parsing and usage
  parse/ThreadDumpParser.java   jstack parser
  analyze/Analyzer.java  wait-for graph, cycles, contention, hot frames
  report/                text, JSON, and self-contained HTML reports
  model/                 ThreadInfo, LockInfo, DumpAnalysis
  util/                  JSON escaping, ANSI colour
src/test/java/dev/threadlens/   JUnit 5 tests (parser, analyser, reports, CLI)
src/buildtools/java/     sample-report generator (build-time only)
examples/                deadlock.txt, healthy.txt, contention.txt
docs/                    landing page + generated sample report (GitHub Pages)
```

## License

MIT — see [LICENSE](LICENSE).
