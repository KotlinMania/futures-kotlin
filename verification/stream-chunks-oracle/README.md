# Stream chunk adapter parity

## Scope and source evidence

Graph impact: Futures has 30 consumers, Tokio 33 and Itertools 11; all three
common source/test trees were clean at selection. Futures offered an exact,
independent buffering/end-of-stream gap without a new runtime design or work
in Iris-owned repositories. This tree was clean at 8093591. Prior dirty build
and platform work remains separately checkpointed in e2a8c62 and is not newly
validated across every platform by this step.

The current native rust-kotlin CLI successfully translated the original
`tmp/futures-util/src/stream/stream/chunks.rs` and `ready_chunks.rs` using this
repository's gradle.properties, package io.github.kotlinmania.futures and
source root tmp/futures-util/src. Scratch output/logs are under
build/rust-kotlin-stream-chunks. Both outputs preserve Fuse wrapping; chunks
also preserves saturating lower-bound addition and checked upper addition
without dividing the upper bound. Generated associated types/imports are
not independently compilable. This is an authored source-faithful repair
informed by translator diffs, not a generator fix or generated-build claim.

## Behavioral repair

Unchanged futures-util 0.3.31 stores each adapter's underlying stream in Fuse.
After End, subsequent polls return End without polling that input again;
size hints become zero. Kotlin stored the raw stream plus a completion bit
but never consulted that bit before polling. A source that rejects polling
after End therefore failed on a further adapter poll, even after the final
partial chunk had already been returned. Both adapters now use the existing
canonical StreamFuse, with public accessors still returning the raw stream.
No duplicate fuse helper or type owner was introduced.

Chunks size_hint keeps a buffered-chunk contribution, divides only the lower
bound by capacity, then adds that contribution to both bounds. The upper bound
is intentionally conservative; it is not the rounded exact chunk count.
The lower saturating addition and upper checked addition use the existing
SizeHint Int domain: bounds exceeding Int.MAX_VALUE become saturated lower
or unknown upper. Full Rust usize bounds are not representable in this API;
no pointer-width equivalence is claimed. ReadyChunks already retained the
correct upper-bound formula and continues flushing buffered values on Pending.

Six new Kotlin cases cover fused final-partial chunks in both adapters,
Chunks conservative upper bound, nullable buffering across Pending,
ReadyChunks flushing on Pending and full chunk item order/nulls. Four failed
before repair. Existing assertions were not changed. Six actual Rust tests
execute unchanged local futures-util using scripted producers and real
Context/Waker values. Fixtures implement Stream/FusedStream, count input polls
and reject polling after End; they never simulate chunking or Fuse. Nullable
items are Rust Some(None), distinct from end None, and Kotlin Yield.Value(null),
distinct from Yield.End.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-stream-chunks/cargo-target cargo test --manifest-path verification/stream-chunks-oracle/Cargo.toml --offline --locked
```

## Limits

Rust move/pin/Send/Sync and deterministic allocation/Drop behavior are not
claimed equivalent to Kotlin references. Raw stream accessor identity is
preserved; consuming/pinned/mutable receiver enforcement remains a Kotlin
limitation. The existing Chunks FusedStream trait-bound difference, missing
Sink forwarding and unrelated helper/API omissions are not repaired here.
The six cases do not exhaust the full usize or Int size-hint domain. No pins,
repositories, runtime/build/publication configuration or other owners changed.
Other targets, downstream builds, Swift, CI and publication metadata remain
outside this bounded local check.

## Final-source local validation

All 422 JVM tests and 422 macOS ARM tests passed, including the six new
regressions. All six actual Rust oracle cases passed. Detekt reports zero
findings. Combined Gradle validation still fails at ktlint: 706 common-main
and 31 common-test findings, all mapped to unchanged baseline 8093591 lines.
No changed production line or added test has a static finding. Existing test
assertions, dependency pins and configuration remain unchanged. Final-source
hashes, diagnostic-line comparison and red/oracle/translator/final-check logs
are under build/rust-kotlin-stream-chunks and remain untracked.
