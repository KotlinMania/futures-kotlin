# Unordered buffer wake-driven source parity

## Selection and translation evidence

Graph consumers: Futures 30, Tokio 33, Itertools 11. All three common source/test
trees were clean at selection. Futures offered a concrete material wake/poll
omission that can use the existing canonical queue rather than a new scheduler.
This repository started clean at e8f62f8. Prior existing build/platform work
remains separately checkpointed in e2a8c62; it is not newly authored or fully
platform-validated here. No generator, Iris-owned or unrelated work changed.

The native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/stream/buffer_unordered.rs` and
`stream/try_stream/try_buffer_unordered.rs` using this repository's
gradle.properties and source root tmp/futures-util/src. Output/logs are in
build/rust-kotlin-unordered-buffers. Both retain Fuse and FuturesUnordered,
zero capacity and direct queue polling. Direct upstream inspection independently
confirms the fallible adapter's immediate input-error priority and default
size hint. Generated unresolved types/imports are not independently compilable.
This is authored faithful adaptation, not a generator fix or new queue owner.

## Exact behavior

Unchanged futures-util 0.3.31 fills an in-progress FuturesUnordered queue up to
capacity, then polls that queue. Newly pushed futures are polled initially;
Pending futures then wait for their own wake notification. Polling the outer
adapter again does not rescan all idle futures. Kotlin instead used a list
and polled every earlier pending element again on each scan. Both adapters
now delegate to existing canonical FuturesUnordered and StreamFuse rather
than a local list scheduling substitute. A woken later future can yield its
nullable result or error without polling an earlier unwoken future again.

Input errors in the fallible adapter return immediately during refill, before
polling the queued futures. Future errors are yielded in readiness order,
not input order; neither kind of error terminates the adapter. Both adapters
accept capacity zero and stay Pending without reading input. Negative Kotlin
capacity is rejected because Rust usize cannot express it. BufferUnordered
adds queue length to fused input hints with saturating lower and checked upper
addition in SizeHint's Int domain. TryBufferUnordered has no source override
and now inherits the default (0, null), not an invented derived hint.

Eight new Kotlin cases cover no unwoken repolls, selective wake with nullable
output, zero capacity and fused terminal hints; fallible selective wake and
nullable error, immediate outer error plus recovery, zero capacity before an
outer error and default hints. Seven fail before repair; immediate-error
recovery is the passing control. Existing assertions were not modified.
Eight actual Rust cases run unchanged local futures-util with manually released
producer futures storing their real polling Waker. Inputs trace pulls and
reject post-End polling/hint queries. Fixtures never simulate queue or adapter
algorithms. Nullable Rust Some(None)/Err(None) and Kotlin Yield.Value(null)/
Try.Err(null) remain distinct from end-of-stream.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-unordered-buffers/cargo-target cargo test --manifest-path verification/unordered-buffers-oracle/Cargo.toml --offline --locked
```

## Limits

These deterministic wake cases do not audit every shared queue's fairness,
atomic/lock/wake behavior or cross-thread equivalence. No full Rust move/pin/
Send/Sync or deterministic allocation/Drop parity is claimed. Rust source has
no FusedStream impl for TryBufferUnordered; Kotlin's existing extra interface
and observed-End plus empty-queue predicate remain a representation difference.
Missing raw/mutable/pinned accessors and Sink forwarding are outside this step.
Int bounds cannot represent full usize and extreme bounds are not exhausted
by these cases. No pins, repositories, runtime/build/publication configuration
or unrelated owners changed. Other platforms, downstream consumers, Swift,
remote CI and publication metadata remain outside local validation scope.

## Final-source local checks

All 455 JVM tests and 455 macOS ARM tests pass on the actual final tree,
including the eight new regressions. All eight actual Rust oracle cases pass;
Detekt reports zero findings. Combined Gradle validation fails at ktlint:
687 common-main and 31 common-test findings. All 718 diagnostic locations
map to unchanged baseline e8f62f8 lines; no changed production line or added
test has a finding. Nine old style findings disappeared with the replaced
branches. Existing assertions and dependency/configuration declarations were
not changed. Source hashes, baseline-line comparison, translator output and
red/oracle/final-check logs remain untracked under
build/rust-kotlin-unordered-buffers.
