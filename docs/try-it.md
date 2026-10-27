# Try ThreadLens in 60 seconds

No install needed to see what ThreadLens reports.

## Live demo

- Landing page: https://judd69.github.io/ThreadLens/
- Full interactive sample report: https://judd69.github.io/ThreadLens/sample-report.html

The demo uses the `examples/deadlock.txt` dump: 6 threads, 2 daemon, verdict DEADLOCKED, with the cycle `pool-1-thread-1 -> pool-1-thread-2 -> pool-1-thread-1`.

## Local run

```bash
./build.sh
java -jar build/threadlens.jar examples/deadlock.txt
java -jar build/threadlens.jar examples/healthy.txt
java -jar build/threadlens.jar examples/contention.txt --format html --output report.html
```

Open `report.html` in a browser for the self-contained report — no server, no upload.

## CI gate

```bash
java -jar build/threadlens.jar dump.txt --fail-on deadlock   # exit 1 on deadlock
java -jar build/threadlens.jar dump.txt --fail-on blocked    # exit 1 on any BLOCKED thread
```
