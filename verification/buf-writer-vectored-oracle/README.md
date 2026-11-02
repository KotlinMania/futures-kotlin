# BufWriter vectored-write source parity

## Selection and translation evidence

Graph consumers: Serde 56, Tokio 33, Futures 30. Their common source/test trees
were clean at selection. Futures offered a confirmed independent missing
vectored-write capability using canonical IoSlice and the existing buffer,
without new architecture or another owner's files. This tree started clean
at ca13963. Prior existing build/platform work remains separately checkpointed
in e2a8c62, not reattributed as this repair or fully platform-validated.

The previous successful bounded CLI output for original
`tmp/futures-util/src/io/buf_writer.rs` is reused from
build/rust-kotlin-buf-writer-flush/BufWriterReview.kt. Its upstream source SHA256
matches that completed run's final-source receipt; the reuse check is recorded
under build/rust-kotlin-buf-writer-vectored. No stalled process was retried.
Direct unchanged futures-util 0.3.31 source comparison confirms the explicit
poll_write_vectored override. Generated unresolved/unsafe/trait constructs are
not independently compilable. This is manual faithful adaptation informed by
that output, not a generator change or a complete BufWriter parity claim.

## Exact behavior

The Rust override sums all slice lengths, flushes existing buffered bytes if
the combined input would exceed capacity, then delegates the original vectors
to the inner vectored method when the total is at least capacity. Smaller
vectors are appended in order to the buffer and return the total count. Pending
or errors while flushing return before any input vector is consumed/delegated.
Even an empty vector list is delegated when capacity is zero. This is the
concrete source algorithm; the generic AsyncWrite default is a different path.

Kotlin had no override and inherited a default that selects one nonempty slice
and calls scalar pollWrite. It could therefore buffer/return just that slice,
miss required pre-flushing, and bypass the inner vectored implementation. The
repair uses canonical IoSlice, existing writeToBuf and flushBuf, and delegates
the original list on the direct path. Slice offsets/lengths remain unchanged.
Totals are kept as Long until the buffered branch proves the count is below
Int capacity. For valid Int-sized slices in an Int-sized list, that sum fits
Long; no Int truncation is used to select buffering. The inner result remains
the existing Int API, not a full Rust usize result representation.

Six new Kotlin cases cover small vectors with offsets/empty slices, direct
vectored delegation at capacity, Pending flush/resume, original flush error
priority, all-empty vectors with spare space and zero-capacity empty delegation.
Five fail before repair; empty vectors with spare space are the passing control.
Existing assertions were unchanged. Six actual Rust tests execute local unchanged
BufWriter with public AsyncWrite methods and standard IoSlice. Scripted writers
only record scalar/vector requests and accepted bytes; they never reproduce
BufWriter's capacity/flush algorithm. Full vector writes are modeled explicitly;
partial inner vectored results are not exhaustively covered by these cases.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-buf-writer-vectored/cargo-target cargo test --manifest-path verification/buf-writer-vectored-oracle/Cargo.toml --offline --locked
```

## Earlier Native correction and remaining limits

Prior ca13963 initially stored a mutable Try value initialized to Ok and later
assigned Err on a terminal flush failure. JVM passed, but Native produced five
Err-to-Ok ClassCastExceptions at return. Its final representation stores only an
optional non-null IoError, then constructs Try after the unchanged drain/reset.
All 468 JVM/Native tests passed at that checkpoint. Initial failure receipts
remain in build/rust-kotlin-buf-writer-flush. That is observed local evidence,
not a proven general compiler root cause; no settings or tests were weakened.
This vectored step reuses that corrected flush path without changing it.

Canonical buffer-copy versus borrowed-slice behavior, missing raw/pinned APIs,
conditional read/seek traits, malformed IoSlice ranges or writer counts,
extreme scalar arithmetic and full usize results remain limitations. No Rust
borrow/pin/Send/Sync or deterministic allocation/Drop/cross-thread equivalence
is claimed. No production pins, runtime/build/publication configuration,
repositories or unrelated owners changed. Other platforms, downstream consumers,
Swift, remote CI and publication metadata remain outside local checks.

## Final-source local checks

The actual final tree passes all 474 JVM tests and 474 macOS ARM tests,
including the six new regressions and prior flush-error cases. All six actual
Rust oracle cases pass; Detekt has zero findings. Combined Gradle validation
fails at ktlint: 679 common-main and 31 common-test findings. All
710 diagnostic locations map to unchanged baseline ca13963 lines; no added
source line or new test has a finding. Existing assertions and production
dependency/configuration declarations were unchanged. Source hashes, baseline
line comparison and translator-reuse/red/oracle/final-check logs remain
untracked under build/rust-kotlin-buf-writer-vectored.
