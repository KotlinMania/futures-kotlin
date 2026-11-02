# BufReader vectored-read source parity

## Selection and source provenance

Graph counts: Serde 56 consumers, Tokio 33 and Futures 30. Their common
source/test trees were clean at selection. Futures offered an independent
missing I/O capability with canonical existing slice types, avoiding broader
architecture or another owner's work. This tree started clean at 8baa9e7.
Prior existing build/platform work remains separately checkpointed in e2a8c62,
not newly authored or fully platform-validated by this step.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/io/buf_reader.rs` using this repo's gradle.properties,
package io.github.kotlinmania.futures.io and source root tmp/futures-util/src
into build/rust-kotlin-buf-reader-vectored/BufReaderReview.kt. Direct unchanged
futures-util 0.3.31 inspection independently confirms the poll_read_vectored
override. Generated associated types/imports/trait mechanisms are not independently
compilable. This is a manual faithful adaptation of that bounded method, not a
generator repair or full BufReader parity claim.

## Exact behavior

The source sums every destination length. Only an empty internal buffer plus
a total destination size at least its capacity permits bypass to the underlying
reader's vectored method. Pending returns without discarding buffer state; a
ready result, including an error, discards the exhausted internal state before
returning. Otherwise the reader fills its buffer, scatters buffered bytes across
the destinations in order and consumes exactly that count. Existing buffered
bytes prevent bypass even for large destination vectors.

Kotlin had no override and inherited AsyncRead's generic default, which selects
one nonempty slice for scalar polling. It could therefore fill only the first
destination and miss required vectored bypass. The repair uses existing
IoSliceMut, pollFillBuf, consume and discardBuffer. It copies actual buffered
ranges and preserves destination offsets/length descriptors. Summation remains
Long until the path is selected, avoiding Int overflow during selection; copied
count is bounded by the Int-sized internal buffer. Full Rust usize results
remain unrepresentable in the existing inner Int API.

Six new Kotlin cases cover small scatter with offsets/empty slices, direct
capacity-sized bypass, existing-buffer priority, direct Pending/resume,
original-error propagation and empty vectors with nonzero buffer capacity.
The last case preserves the source's fill-even-with-no-destinations behavior.
Five fail before repair; that empty-vector case is the passing control. Existing
assertions were unchanged. Six actual Rust tests run local unchanged BufReader
via public AsyncRead/AsyncBufRead calls. Scripted readers supply explicit
scalar/vectored events and log requested lengths; they never choose the
BufReader bypass/buffer algorithm. Rust fixtures use ordinary std slice reads
only to deliver the configured direct data into destination vectors.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-buf-reader-vectored/cargo-target cargo test --manifest-path verification/buf-reader-vectored-oracle/Cargo.toml --offline --locked
```

## Remaining limits

Kotlin's existing pollFillBuf/buffer accessors copy data rather than borrowing
a slice, and mutable IoSliceMut fields permit invalid/overlapping ranges Rust
would reject. Such malformed or overlapping inputs, invalid underlying read
counts, extreme sizes, zero-capacity readers and seek/conditional trait gaps
are not exhausted by these six cases. No Rust move/borrow/pin/Send/Sync,
deterministic allocation/Drop or cross-thread equivalence is claimed. Prior
Native result-slot failure evidence under build/rust-kotlin-buf-writer-flush
remains preserved; this step does not alter that corrected representation.
No production pins, runtime/build/publication configuration, repositories or
unrelated owners changed. Other targets, downstream consumers, Swift, remote
CI and publication metadata remain outside local validation scope.

## Final-source local checks

The actual final tree passes all 480 JVM tests and 480 macOS ARM tests, including
the six added cases and prior Native error regressions. All six actual Rust
oracle cases pass; Detekt reports zero findings. Combined Gradle validation
fails at ktlint: 679 common-main and 31 common-test findings. All
710 locations map to unchanged baseline 8baa9e7 lines; no added source line
or new test has a finding. Existing assertions and production dependency/config
declarations are unchanged. Source hashes, baseline-line comparison and
translator/red/oracle/final-check logs remain untracked under
build/rust-kotlin-buf-reader-vectored.
