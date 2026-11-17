# Forward nullable buffering and successful-close state parity

## Selection and source provenance

This repository started clean at 3de5129. Futures has 30 dependency-graph
consumers (Rand 30, Tokio 33, Serde 56). Direct source review found material
nullable-item data loss and incorrect close-error termination using existing
canonical Try/Poll/Yield/Sink/StreamFuse types, without API changes or a broad
Option migration. Adjacent owners remain untouched. Prior build/platform
checkpoint e2a8c62 remains separate and not fully platform-validated here;
earlier source, oracle correction and Native failure evidence is preserved.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/stream/forward.rs` from futures-util 0.3.31 with
this repository's gradle.properties and original source root into
build/rust-kotlin-forward-state/ForwardReview.kt. Unresolved associated types,
imports and projection/Pin lowering prevent a runnable-output claim. This repair
is manual exact-source adaptation informed by translator output and original
Rust, not a generator fix or alternative forwarding algorithm.

## Exact source state and repair

Upstream stores Option<Item> and always handles a buffered item before reading
more input. Readiness Pending or Err leaves the buffered item present; after
readiness succeeds, start_send takes it before propagating any send error.
Input Pending flushes the sink and returns Pending unless that flush errors.
Input EOF closes the sink; only successful close clears the sink Option and
marks the Forward future terminated. A close error returns Err while retaining
the sink. Input is wrapped in canonical Fuse so repeated close polls do not
re-read an ended input.

Kotlin used Item? as its private buffer, losing a present null. It now retains
an existing canonical Try.Ok<Item> tag, which distinguishes null from absence
without another Option owner or public representation change. It clears the
tag before startSend exactly as the source does, and clears activeSink only
after successful close. An unnecessary private constructor sink property is
removed so activeSink is the sole retained sink field, matching the source
state structure; the public constructor parameter type/name remain unchanged.
Existing callers, signatures, assertions and the canonical StreamFuse owner
are unchanged.

Seven new Kotlin cases cover null send order, null under Pending readiness
without reading ahead, close error not marking completion, Pending then
successful close over fused input, readiness/send error propagation and flush
error while input is Pending. The first fixture run stopped at Kotlin type
inference for listOf(1) versus List<Int?>; explicitly writing listOf<Int?>(1)
changed no expected value. That compile receipt remains under
build/rust-kotlin-forward-state/initial-fixture-compile.log. The unchanged old
production then ran all seven tests with three runtime failures (both nullable
cases and close-error termination); four controls passed. Runtime red log and
XML-derived failures are retained separately.

Seven matching Rust cases execute actual unchanged Forward and canonical Sink
with Context/Waker, scripted input and sink responses. Producers implement
neither the adapter nor Fuse. Every error case stops after observing error/state:
these tests make no promise that failed sinks can safely be reused or errors
retried. The successful Pending-close case tests retry only before success,
without an intervening error. No existing assertion or warning rule is relaxed.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-forward-state/cargo-target cargo test --manifest-path verification/forward-state-oracle/Cargo.toml --offline --locked
```

## Remaining limits

Rust borrow/move/pin/Drop/Send/Sync and deterministic resource destruction are
not fully represented by Kotlin reference ownership/GC. The existing public
nullable-sink constructor shape remains unlike the upstream non-optional new
argument; this bounded repair does not migrate that API. Debug and other API
coverage remain outside scope. Other sink adapters are not repaired here.
No downstream source/binary, Swift/other-target or fresh-publication validation
is claimed. Prior shared-queue fairness/thread-scheduling and usize limits are
unchanged. The test-only Cargo package enables upstream's sink feature to run
the actual oracle; production pins, repositories and runtime/build/publication
settings are unchanged. No foreign dirty work or UI session is touched.

## Final-source local checks

All 583 JVM tests and 583 macOS ARM tests pass, including seven new cases and
unchanged existing callers. All seven actual upstream Rust cases pass. Detekt
has zero findings. Combined Gradle validation fails only at ktlint: 608
common-main and 31 common-test findings. All 639 diagnostics map to unchanged
baseline 3de5129 lines; none is on an authored line. Final source hashes include
the unchanged canonical StreamFuse owner. Hash/static-comparison receipts and
CLI/initial-fixture-compile/runtime-red/oracle/Gradle logs remain under
build/rust-kotlin-forward-state. No prior dirty work was included or attributed
as newly validated. Earlier checkpoints and all representation/platform limits
remain separate.
