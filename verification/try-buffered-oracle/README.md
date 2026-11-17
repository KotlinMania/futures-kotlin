# TryBuffered independent source parity

## Selection and provenance

Graph consumers: Futures 30, Tokio 33, Itertools 11. All three common source/test
trees were clean at selection. Futures offered an independently confirmed
material progress/error-order gap without new scheduling design or Iris-owned
work. This tree started clean at c14c411. Prior existing build/platform work is
separately checkpointed in e2a8c62, not newly authored or fully platform-validated.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/try_stream/try_buffered.rs` using this repository's
gradle.properties and source root tmp/futures-util/src into
build/rust-kotlin-try-buffered/TryBufferedReview.kt. Direct inspection of
unchanged futures-util 0.3.31 independently establishes the contracts below.
Generated unresolved associated types/imports and error representation are not
independently compilable. This is a manual source-faithful repair using existing
StreamFuse, FuturesOrdered and Try owners, not a generator fix, new queue,
coroutine migration or copied assumption from Buffered.

## Exact error, poll and hint contracts

Upstream fills its ordered in-progress queue before polling the queue. Input
stream errors propagate immediately, without polling already queued futures.
The queue drives later futures while earlier ones wait, but returns their
successes and errors in input order. Completed out-of-order errors still occupy
capacity. After yielding an earlier item frees capacity, a newly encountered
input error during refill takes priority over an already buffered future error.
Neither kind of error is end-of-stream, and polling can continue afterwards.
The repair retains this source ordering with the existing canonical queue.

The input is fused, and zero capacity is accepted: repeated polls stay Pending
without even reading an input error. Negative Kotlin capacity is still rejected
because Rust usize cannot express it. Source TryBuffered does not override
Stream::size_hint and therefore reports (0, None), independently of known input
bounds. This differs from Buffered's explicit formula. The invented Kotlin
hint calculation was removed to inherit the matching default (0, null).

Six added Kotlin cases cover concurrent ordered nullable success/future error,
immediate outer error plus recovery, queued-error capacity/refill priority,
zero capacity, default hints and Pending followed by new work. Four fail before
repair; both existing-contract controls pass. During fixture construction the
new hint-case output check was corrected to require the immediate outer error
before the queued success, matching direct source and the actual Rust case;
no pre-existing test/assertion was edited. Six Rust tests execute unchanged
local futures-util, with producer futures that save the real poll Waker and
wake it when manually released. Producers reject post-completion polls; input
fixtures trace pulls and reject post-End polls. The oracle uses actual source
IntoStream/IntoFuture/FuturesOrdered/Fuse, never an algorithm simulation.
Kotlin already models TryStream as Stream<Try> and futures as Future<Try>,
so no duplicate IntoStream/IntoFuture owner or Throwable narrowing is needed.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-try-buffered/cargo-target cargo test --manifest-path verification/try-buffered-oracle/Cargo.toml --offline --locked
```

## Remaining limits

Rust does not supply a FusedStream impl for this TryBuffered source; Kotlin's
existing extra FusedStream API remains, with its prior observed-input-End plus
empty-queue predicate. No source trait-bound equivalence is claimed for it.
Rust pin/move/Send/Sync and deterministic Drop/allocation are not equivalent to
Kotlin references. Canonical queue wake/lock/iterator and retained-reference
limitations remain outside this step. Missing raw/mutable/pinned accessors and
Sink forwarding are unchanged. Int capacity cannot express full Rust usize.
No pins, runtime/build/publication configuration or repositories changed. Other
platforms, downstream consumers, Swift, remote CI and publication metadata
remain outside the bounded local validation scope.

## Final-source local checks

After making the touched multiline branches consistently formatted, the actual
final tree passes all 447 JVM tests and 447 macOS ARM tests, including the six
new regressions. All six actual Rust oracle cases pass; Detekt has zero findings.
Combined Gradle validation still fails at ktlint: 696 common-main and
31 common-test findings. All 727 locations map to unchanged baseline
c14c411 lines; no changed source line or new test has a finding. No existing
assertions or dependency/configuration declarations were changed. Final hashes,
baseline-line comparison and translator/red/oracle/final-check logs remain
untracked in build/rust-kotlin-try-buffered.
