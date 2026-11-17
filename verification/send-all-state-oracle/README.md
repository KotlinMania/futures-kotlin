# SendAll nullable buffered-item and fused-input parity

## Selection and provenance

This repository started clean at 017fe57. Futures has 30 dependency-graph
consumers (Rand 30, Tokio 33, Serde 56). Direct original-source review confirmed
material nullable-item loss under sink readiness backpressure, using existing
canonical Try/Sink/StreamFuse types without API changes or a broad Option
migration. Adjacent owners remain untouched; prior build/platform checkpoint
e2a8c62 and earlier Native/oracle evidence remain separately attributed and
preserved. No previous dirty work is included.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/sink/send_all.rs` from futures-util 0.3.31 using this
repository's gradle.properties and original source root into
build/rust-kotlin-send-all-state/SendAllReview.kt. Associated types/imports and
Pin/borrow lowering remain unresolved, so scratch output is not claimed
runnable. The repair is manual exact-source adaptation informed by translator
output and independent source comparison, not a generator fix or copied
Forward error algorithm.

## Exact source behavior

SendAll takes a buffered item before retrying try_start_send. Pending readiness
stores it again; readiness Err returns without restoring it. This differs from
Forward's readiness-error buffering. Readiness success invokes start_send;
send errors also return before further input. Input Pending flushes then stays
Pending, while EOF flushes to Ready(Ok(())); any flush/input Err propagates
immediately. SendAll never closes the sink. Canonical Fuse prevents further
polls of an ended input while flushing may still be Pending.

Kotlin used Item? for private buffered state and lost a present null after
Pending. It now retains a canonical Try.Ok<Item> tag and sends its value,
distinguishing null from absence without another Option owner or cast. The
existing streamExhausted flag behaved correctly in the EOF control; it is
replaced with the actual canonical StreamFuse owner used by upstream's design,
not presented as another observed pre-existing EOF bug. The source take/rebuffer
order is preserved. Constructor/extension signatures, existing callers and
assertions are unchanged. No new FusedFuture interface or artificial done guard
is added. The existing private invariant check remains unchanged.

Seven Kotlin cases cover repeated readiness Pending with buffered null,
readiness/send error on that buffered null before further input, fused EOF with
Pending then successful flush, input Pending flush without completion followed
by ready null, input error after a successful send, and EOF flush error. Every
case verifies no sink close. The old implementation runs seven cases and fails
three at runtime (the buffered-null cases); the four controls pass. Logs and
XML-derived failure receipts remain in build/rust-kotlin-send-all-state. No
existing assertion or warning rule is relaxed.

Seven matching Rust cases execute actual unchanged SendAll and canonical Sink,
with real Context/Waker and scripted stream/sink producers. The EOF control
uses a Stream-only producer that could resume after EOF; the adapter's actual
Fuse prevents reading that later item. Fixtures implement neither SendAll nor
Fuse. Error cases stop at the error, so no failed-sink reuse or retry contract
is inferred from them.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-send-all-state/cargo-target cargo test --manifest-path verification/send-all-state-oracle/Cargo.toml --offline --locked
```

## Remaining limits

Kotlin reference ownership/GC does not reproduce Rust mutable borrow lifetimes,
move/pin/Unpin/Drop/Send/Sync or deterministic resource destruction. Other sink
adapters and Debug/type-relationship API gaps remain outside scope. No downstream
source/binary, Swift/other-target or fresh publication validation is claimed.
Prior concurrency fairness/thread-scheduling and Int/usize limits remain
unchanged. The test-only Cargo package enables upstream's sink feature;
production pins, repositories and runtime/build/publication settings are
unchanged. The canonical StreamFuse implementation is unchanged. No foreign
owner or UI session is touched; earlier original/corrected oracle receipts and
Native regression evidence remain intact.

## Final-source local checks

All 590 JVM tests and 590 macOS ARM tests pass, including seven new cases and
unchanged existing SinkTest.sendAll/fanout callers. All seven actual upstream
Rust cases pass. Detekt has zero findings. The combined Gradle command fails
only at ktlint: 596 common-main and 31 common-test findings. All 627 diagnostic
locations map to unchanged baseline 017fe57 lines; no authored line has a
finding. Source hashes include the unchanged canonical StreamFuse owner.
Final hashes/static-comparison and CLI/red/oracle/Gradle logs remain under
build/rust-kotlin-send-all-state. No earlier dirty work is included or claimed
as new coverage; prior checkpoints and representation/platform limitations
remain separate.
