# TryForEachConcurrent wake queue and limit parity

## Selection and provenance

This repository started clean at d40f8f1. Futures has 30 dependency-graph
consumers (Rand 30, Tokio 33, Serde 56). The selected material omission concerns
concurrent action scheduling, limit interpretation and error reset, using the
existing canonical FuturesUnordered owner rather than a substitute scheduler.
Adjacent owners are untouched. Prior build/platform checkpoint e2a8c62 remains
separate and not fully platform-validated here; all earlier oracle/error/native
failure evidence remains intact. This step used shell/source tools only and never used the desktop cursor.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/try_stream/try_for_each_concurrent.rs` from
futures-util 0.3.31 using this repository's gradle.properties and original source
root into build/rust-kotlin-try-for-each-concurrent/TryForEachConcurrentReview.kt.
Unresolved associated types/imports and projection/Pin/mem lowering mean that
scratch output is not claimed runnable. This repair is manual exact-source
adaptation informed by translator output and original upstream review, not a
generator repair, generic redesign or coroutine migration.

## Exact upstream algorithm

The source stores Option<stream>, a FuturesUnordered queue and an optional
nonzero limit. Zero is ignored, so zero and None both mean unlimited. Every
loop iteration polls at most one input event if the queue is below the limit,
then polls one queue result. Creating an action or receiving a completed action
counts as progress. With no progress the adapter returns Pending. Input EOF
removes the stream. Input or action Err removes the stream, replaces the queue
with a new empty FuturesUnordered and returns that error. Termination is absent
stream AND empty queue; after an error a source-specific repoll yields Ok(())
without revisiting cancelled work.

Kotlin previously stored a mutable list, rescanned all sleeping actions on
unrelated polls, rejected zero and retained the stream behind separate done
flags. The repair stores the optional input and canonical FuturesUnordered,
preserves the source's exact interleaving/progress/error order, replaces the
queue on errors, and ignores zero. Negative Int limits are still rejected
because upstream usize has no negative values. Public constructor/extension
signatures and existing callers/assertions are unchanged. No new helper owner,
compatibility shortcut or substitute polling algorithm is introduced.

Six Kotlin cases cover zero as unlimited, sleeping actions polled only after
their wake, one-slot input backpressure, input error cancellation, action error
without rescanning a sleeping sibling, and immediate action/input interleaving.
The old implementation executes all six cases and fails four at runtime;
input-error and immediate-action controls pass. Red logs and XML-derived
failure receipts are retained under build/rust-kotlin-try-for-each-concurrent.
No existing assertion or warning rule was relaxed.

Six matching Rust cases execute unchanged upstream TryForEachConcurrent and
FuturesUnordered with actual Context/Waker, manually released gates and scripted
input producers. The fixtures do not implement either adapter or a queue.
Tests assert exact poll/input/callback counts and completion/error results for
these bounded single-thread scenarios.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-try-for-each-concurrent/cargo-target cargo test --manifest-path verification/try-for-each-concurrent-oracle/Cargo.toml --offline --locked
```

## Remaining limits

The reused canonical Kotlin FuturesUnordered has not been proven equivalent
for all fairness/budgeting, wake races or cross-thread schedules by these cases.
Replacing references and GC do not reproduce deterministic Rust Drop/cancellation
or borrow/move/pin/Send/Sync guarantees. The old queue may remain reachable from
a saved task waker; tests establish that this adapter never polls it after
replacement, not immediate destruction of all references. Counts and limits
retain Int width instead of usize. Concrete generic future relationships,
Debug and other API gaps remain outside scope. Source-specific post-error
repoll tests do not guarantee that arbitrary completed Future types may be
repolled. No downstream source/binary compatibility, Swift/other-target or
fresh-publication metadata verification is claimed. No pins, repositories,
runtime/build/publication settings, foreign dirty work or UI interactions are
included. Earlier checkpoints and coverage limitations remain separate.

## Final-source local checks

All 570 JVM tests and 570 macOS ARM tests pass, including six new regressions
and unchanged existing TryStreamCombinatorsTest concurrent success/error callers.
All six actual upstream Rust cases pass. Detekt has zero findings. The combined
Gradle command fails only at ktlint: 613 common-main and 31 common-test findings.
All 644 diagnostic locations map to unchanged baseline d40f8f1 lines; none is
on an authored line. Final source hashes/static comparison and CLI/red/oracle/
Gradle logs remain in build/rust-kotlin-try-for-each-concurrent. The reused
canonical FuturesUnordered implementation is unchanged and its hash is included
in the final receipt. No previous dirty work was included. Source algorithms
and prior checkpoints remain separately attributed, with all concurrency,
ownership, limit-width and downstream coverage caveats above.
