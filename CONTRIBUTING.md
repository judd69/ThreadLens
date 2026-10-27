# Contributing to ThreadLens

Thanks for helping make ThreadLens better. It is a zero-dependency Java CLI, so contributing stays simple.

## Quick start

```bash
./build.sh   # compiles, runs the 24 JUnit 5 tests, builds threadlens.jar
java -jar build/threadlens.jar examples/deadlock.txt
```

Requirements: Java 17+ (built with 21). No other dependencies.

## How to contribute

1. Fork the repo, create a branch from `main` for one focused change.
2. Add or update a test for any parser or analyser change — the suite must stay green.
3. Open a pull request with: what changed, an example dump (or test) that shows it, and the output before/after if behaviour changed.
4. Small, focused PRs merge fastest. One feature or fix per PR, please.

## Reporting a dump that fails to parse

Open an issue with:
- A redacted sample of the dump (remove hostnames, IPs, and proprietary class names if you need to)
- The command you ran and the error or wrong output you got
- Your Java version (`java -version`)

ThreadLens never uploads your dump — issues should contain only samples you are comfortable sharing publicly.

## Code style

- Plain Java, no frameworks. Keep new code in the existing package layout (`parse/`, `analyze/`, `report/`, `model/`, `util/`).
- Never guess a lock holder: a waiter whose holder is absent from the dump is reported as "(holder not in dump)".
- Keep the CLI fast and offline. No network calls at runtime.

## License

By contributing, you agree your work is released under the MIT license in LICENSE.
