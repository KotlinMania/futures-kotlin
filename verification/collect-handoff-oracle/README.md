# Collect and TryCollect collection handoff parity

## Selection and source provenance

This repository started clean at bb6c862. Futures has 30 graph consumers
(Rand 30, Tokio 33, Serde 56). This material omission affects two widely used
collection adapters' output/state handoff, with existing canonical list storage
and no public API or broad architecture change. Adjacent owners remain untouched;
prior build/platform checkpoint e2a8c62 remains separate and is not fully
platform-validated here. Earlier source repairs and oracle evidence are retained.

The existing native rust-kotlin CLI successfully translated the original
`tmp/futures-util/src/stream/stream/collect.rs` and
`tmp/futures-util/src/stream/try_stream/try_collect.rs` from futures-util 0.3.31,
independently, with this repository's gradle.properties and original source root.
Scratch outputs/logs are in build/rust-kotlin-collect-handoff. Unresolved
associated types/imports/projection and mem lowering prevent a claim that those
outputs run independently. This is manual exact-source adaptation informed by
translator output and original Rust, not a generator fix.

## Exact collection/state handoff

Collect::finish performs mem::take on collection; TryCollect performs mem::take
on items when input EOF produces success. Each hands the accumulated collection
to the result and replaces internal storage with Default::default(). Pending
retains accumulated data. TryCollect input errors return Err before taking the
collection, so a later source-specific poll still has that prefix. Collect's
FusedFuture implementation delegates to the underlying FusedStream, as
TryCollect already did; a separate completion flag is not upstream behavior.

Kotlin previously copied collection/items into a result and retained the old
internal contents, allowing them to reappear on a subsequent source-specific
completion. The repair swaps the private mutable-list reference for a new empty
list and returns the completed list through the existing read-only List API.
It does not clear the buffer on Pending or Err. Collect drops its artificial
done field and delegates termination using the existing runtime FusedStream
capability check. Public constructors/extensions/output signatures and existing
callers/assertions are unchanged. No new helper or canonical type owner is added.

Six added Kotlin cases exercise successful handoff followed by another EOF for
each adapter, already-terminated input for each, non-fused input resuming through
Pending after EOF with a fresh collection, and TryCollect retaining a nullable
prefix through Pending/Err before handing it off on success. Previously returned
lists are checked again to ensure later buffer replacement does not mutate them.
The old implementation runs six cases and fails five at runtime; TryCollect's
existing termination-delegation control passes. Failure logs and XML-derived
receipts remain in build/rust-kotlin-collect-handoff/red.log and red-failures.json.
No existing assertion or warning rule was weakened.

Six matching Rust cases execute actual unchanged upstream Collect/TryCollect
with Vec output, Context/Waker and scripted input producers. Producers that
resume after EOF implement Stream only, never a dishonest FusedStream; the
termination cases use genuine upstream stream::empty. Repeat polling in these
tests measures this concrete source implementation. It is not a promise that
all completed Future implementations may be repolled or resume safely.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-collect-handoff/cargo-target cargo test --manifest-path verification/collect-handoff-oracle/Cargo.toml --offline --locked
```

## Remaining limits

Kotlin's pre-existing List-only API does not represent upstream generic
Default + Extend collection C. Kotlin read-only List references and GC do not
reproduce Rust ownership, deterministic Drop or borrow/move/pin/Send/Sync rules;
this repair transfers the outer buffer state, not deep ownership of items.
Conditional FusedFuture trait bounds are still represented as an unconditional
Kotlin interface with a runtime input capability check. No downstream source or
binary compatibility, other targets/Swift or fresh publication metadata is
verified. Debug/accessor and broader collection-type API gaps remain outside
scope. No pins, repositories or build/runtime/publication configuration changed,
and no foreign dirty files are included. Earlier Native failure and oracle
correction/after-Pending evidence is preserved.

## Final-source local checks

All 564 JVM and 564 macOS ARM tests pass, including six new regressions and
unchanged existing Collect/TryCollect callers. All six actual upstream Rust
cases pass. Detekt has zero findings. Combined Gradle validation fails only
at ktlint: 619 common-main and 31 common-test findings. All 650 diagnostics
map to unchanged baseline bb6c862 lines; none is on an authored line. The new
anonymous fixture formatting was corrected before runtime/static checks were
rerun on actual final source. Final hashes include both production adapters
and both original Rust files; source/static receipts and CLI/red/oracle/Gradle
logs remain under build/rust-kotlin-collect-handoff. No prior dirty work is
included; earlier checkpoints remain separate. All ownership, generic-collection,
trait/platform and downstream limitations above remain explicit.
