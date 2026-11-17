# Peekable conditional consumption and fusion parity

## Selection and translator provenance

Graph comparison: Serde 56 consumers, Tokio 33, Futures/Rand 30. Common
source/test ownership checks were clean. Futures offered a confirmed independent
nullable presence and callback-failure state gap using existing canonical
Yield/StreamFuse, without a broader representation migration or another owner's
files. This tree started clean at 4dafa26. Existing build/platform work remains
separately checkpointed in e2a8c62, not newly authored or fully platform-validated.
Cursor standard-I/O delegation was reviewed read-only, not replaced with an
unverified local helper after the installed std source file was unavailable.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/stream/peek.rs` with this repo's gradle.properties,
package io.github.kotlinmania.futures and source root tmp/futures-util/src into
build/rust-kotlin-peekable-next-if/PeekReview.kt. Direct unchanged futures-util
0.3.31 inspection confirms Fuse storage, NextIf consuming poll_next, taking the
conditional-future state before calling the predicate, and restoring a rejected
item. Generated types/imports/borrow mechanisms are not independently compilable.
This is a manual faithful adaptation of bounded behavior, not a generator repair.

## Exact behavior and canonical types

Rust NextIf polls Peekable::poll_next, consuming its cached/input item before
calling the predicate. It takes its own state before that call. Acceptance
returns the consumed item; rejection restores it to Peekable. A predicate panic
therefore leaves the item consumed and the conditional future terminated; a
further poll panics without invoking the predicate again. Pending leaves the
future active and does not call the predicate. NextIfEq inherits this behavior.

Kotlin previously peeked before testing, skipped predicates for null items and
retained an item when its predicate threw. The repair uses canonical Yield to
preserve item presence independently from its nullable payload, marks the
conditional future complete before calling the predicate, and restores only
on false. Its anonymous object now implements existing FusedFuture, while the
public return signature remains Future<T?>. No duplicate Option or stream owner
was introduced. The underlying stream now uses the existing canonical StreamFuse,
matching source end-of-stream latching. Upper hints use checked addition in the
existing Int domain, alongside the prior saturating lower addition.

Six new Kotlin cases cover accepting/rejecting null with predicate calls,
NextIfEq(null), throwing predicate consumption plus single-use future state,
Pending then rejection/restoration, and fused repeated peeks/polls after End.
Five fail before repair; Pending/rejection is the passing control. Existing
assertions are unchanged. Six actual Rust tests execute local unchanged
Peekable/NextIf with Option<i32> items, real Context/Waker and producer fixtures.
Caught panic tests observe the actual source future's termination and cursor,
not a simulated adapter. No global panic hook or runtime settings are changed.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-peekable-next-if/cargo-target cargo test --manifest-path verification/peekable-next-if-oracle/Cargo.toml --offline --locked
```

## Representation and coverage limits

The public Kotlin Poll<T?> return still cannot distinguish an accepted null
from rejection/absence, unlike Rust Some(None) versus None. The tests verify
predicate/cursor effects, not full Option API equivalence. The public concrete
NextIf/Peek/PeekMut types, mutable peek APIs, static return typing and borrowed
reference enforcement remain incomplete. The existing peek() future's own
completion contract is outside this nextIf repair. Rust FnOnce/move/pin/Send/Sync,
callback borrow restrictions, deterministic Drop and cross-thread equivalence
are not established by a Kotlin lambda and references. Checked hint arithmetic
is not exhaustively tested across Int or full Rust usize limits. Prior Native
failure receipts/corrected flush result representation are preserved unchanged.
No production pins, runtime/build/publication configuration, repositories or
unrelated owners changed. Other targets, downstream consumers, Swift, remote
CI and publication metadata remain outside local verification scope.

## Final-source local checks

After formatting the new multiline fixture declarations, the actual final tree
passes all 498 JVM tests and 498 macOS ARM tests, including six added cases and
prior Native error regressions. All six actual Rust oracle cases pass; Detekt
has zero findings. Combined Gradle validation fails only at ktlint:
677 common-main and 31 common-test findings, all 708 diagnostic
locations mapping to unchanged baseline 4dafa26 lines. No changed source line
or new test has a finding. Existing assertions and production pins/configuration
were unchanged. Source hashes, baseline-line comparison and translator/red/
oracle/final-check logs remain untracked under build/rust-kotlin-peekable-next-if.
