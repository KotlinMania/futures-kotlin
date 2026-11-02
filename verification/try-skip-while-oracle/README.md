# TrySkipWhile retained error-state parity

## Selection and provenance

This Futures repository started clean at 7a4a369. Futures has 30 graph consumers
(alongside Rand 30, Tokio 33, Serde 56). The selected material private-state gap
changes observable nullable-item hints, error retention and panic ordering
without new API or canonical dependency types. Adjacent owners remain untouched;
prior build/platform checkpoint e2a8c62 is separately attributed and not fully
platform-validated by this step. Prior oracle correction and after-Pending
coverage commits/evidence remain intact.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/try_stream/try_skip_while.rs` from futures-util
0.3.31 with this repository's gradle.properties and original source root into
build/rust-kotlin-try-skip-while/TrySkipWhileReview.kt. Generated associated types,
imports and projection/Pin/loop lowering are not claimed compilable. This repair
is manual exact-source adaptation informed by that output and original Rust,
not a generator repair or generic representation redesign.

## Exact source behavior

Upstream stores a new predicate future before storing its item. Predicate
Ready clears that future, then propagates an Err before taking the stored item.
Consequently predicate Err retains the previous item; a subsequent input Err
or EOF also leaves it retained. This affects size_hint and is_terminated even
when no future remains. These seemingly unusual states are preserved rather
than normalized away. Ok takes the stored item: true skips and loops, false
sets done_skipping and yields that item, then future calls delegate directly
to the input. Even after done_skipping, the source lower hint remains zero.
Termination checks absence of pending item and terminated input, not absence
of pending future. Upper hint uses checked addition of the pending-item count.

Kotlin previously cleared the item on predicate Err, counted null items as
absent, tested termination using the future instead of the item, and cached a
new item before calling a predicate that might throw. The repair retains a
canonical Try.Ok<T> tag privately, distinguishing a present null from absence,
and follows the exact source ordering/retention. It also follows checked upper
addition within Kotlin's existing Int count domain. No new public type,
synchronous helper or Option owner is introduced. Public signatures/callers
are unchanged; no downstream compatibility claim is made.

Six Kotlin cases cover pending null + false/direct delegation, predicate Err
retained through input EOF, creation failure, polling failure, input Err after
predicate Err, and true/null skipping followed by false without over-read.
The old implementation executes six tests and fails five at runtime; the
true/null control case passes. Failure logs and XML-derived failure details
are retained in build/rust-kotlin-try-skip-while/red.log and red-failures.json.
All existing assertions/warning rules are unchanged.

Six matching Rust cases execute unchanged upstream TrySkipWhile with actual
Context/Waker, manually released predicate futures and caught panics. Only
producers are fixtures, not the adapter algorithm. Producer size_hint counts
all queued Yield-equivalent events with Iterator::filter, including errors;
it was written directly and not renamed from another adapter fixture.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-try-skip-while/cargo-target cargo test --manifest-path verification/try-skip-while-oracle/Cargo.toml --offline --locked
```

## Remaining limits

Rust borrow/move/pin/Drop, conditional FusedStream bounds, concrete generic
future relationships and Send/Sync/cross-thread behavior are not fully
represented. Existing raw getRef/intoInner receiver-reuse/accessor limitations
remain. Counts retain Kotlin Int width rather than usize; extreme overflow is
source-reviewed but not covered by the six normal-size oracle cases. Debug/Sink
forwarding remains outside this repair. Other adapters are not modified.
No production pins, repositories or runtime/build/publication settings changed.
No foreign dirty work is included. Swift, other targets, downstream builds and
fresh publication metadata remain unverified. Earlier Native failure evidence
and original/corrected oracle receipts are preserved.

## Final-source local checks

All 551 JVM and 551 macOS ARM tests pass, including six new regressions and
the unchanged StreamTryStreamTest.trySkipWhileAfterErr caller. All six actual
upstream Rust cases pass. Detekt has zero findings. Combined Gradle validation
fails only at ktlint: 636 common-main and 31 common-test findings, all mapped to
unchanged baseline 7a4a369 lines; none is on an authored line. After correcting
the new fixture class formatting, runtime/static checks were rerun against
actual final source. Source hashes and baseline comparison receipts remain in
build/rust-kotlin-try-skip-while with CLI/red/oracle/final-check logs.
A transient exec transport disconnect happened after Gradle completion; a
read-only recovery check succeeded and final reports/files were reconciled
without duplicate jobs. No pre-existing dirty files were checkpointed here.
