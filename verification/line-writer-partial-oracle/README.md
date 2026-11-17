# LineWriter partial scalar-write parity

## Selection and source provenance

Graph counts: Serde 56 consumers, Tokio 33 and Futures 30. Their common
source/test trees were clean at selection. Futures offered a concrete
independent accepted-byte/buffering gap without new runtime design or another
owner's files. This repository started clean at 96e5821. Prior existing
build/platform work remains separately checkpointed in e2a8c62, not newly
authored or fully platform-validated by this step.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/io/line_writer.rs` with this repository's gradle.properties,
package io.github.kotlinmania.futures.io and source root tmp/futures-util/src
into build/rust-kotlin-line-writer-partial/LineWriterReview.kt. Direct unchanged
futures-util 0.3.31 comparison establishes the scalar-write three-way tail
selection below. Generated types/imports/trait mechanisms are not independently
compilable. This is manual faithful adaptation informed by that output, not a
generator repair or full LineWriter parity claim.

## Exact behavior

The source finds the last newline, flushes the existing buffered writer, and
asks the inner writer to write bytes through that newline. Zero accepted bytes
return Ok(0) without buffering. If the whole completed-line prefix was written,
the remaining input may be buffered. If only part was written, the source
buffers only the remaining completed-line prefix when it fits capacity, leaving
the later tail for the caller. When that remainder exceeds capacity, it scans
only a capacity-sized window and buffers through its last newline, or the whole
window if none exists. Returned count is actual inner bytes plus buffered bytes.

Kotlin instead always buffered a capacity-limited prefix of all remaining input.
It could accept a later tail or a partial next line that the source leaves
unaccepted. The repair restores the exact three-way boundary logic with the
existing BufWriter capacity/writeToBuf owners and byte-wise newline search,
including nonzero input offsets. A completed buffered line is then flushed
before a subsequent non-newline write, as source flush_if_completed_line does.
Source poll_flush before writing a newline is intentional and unchanged; it
was checked directly, not replaced by an inferred flush_buf shortcut.

Six new Kotlin cases cover short completed-line remainder plus following write,
last-newline selection in a bounded window, nonzero source offset, no newline
in that window, a fully accepted completed line and zero inner progress.
Three fail before repair; the latter three are passing controls. Existing
assertions are unchanged. Six actual Rust cases run local unchanged LineWriter
through public AsyncWrite. Scripted writers only choose accepted counts and
record requested/accepted bytes and flush calls; they never choose LineWriter
buffer boundaries. No line buffering algorithm simulation is used.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-line-writer-partial/cargo-target cargo test --manifest-path verification/line-writer-partial-oracle/Cargo.toml --offline --locked
```

## Limits

The existing missing LineWriter vectored override, Debug formatting, conditional
traits/accessors, malformed writer counts or input ranges, and extreme Int/usize
sizes remain outside this scalar path. These six deterministic cases do not
exhaust Pending/error handling or zero-capacity behavior. Kotlin buffer() copies
rather than borrowing; no full Rust move/borrow/pin/Send/Sync, allocation/Drop
or cross-thread equivalence is claimed. Prior Native flush result-slot failure
receipts and corrected representation remain intact in their existing build
directory and source. No production pins, runtime/build/publication configuration,
repositories or unrelated owners changed. Other platforms, downstream consumers,
Swift, remote CI and publication metadata remain unverified by local checks.

## Final-source local checks

The actual final tree passes all 486 JVM tests and 486 macOS ARM tests, including
six added cases and prior Native error regressions. All six actual Rust oracle
cases pass; Detekt has zero findings. Combined Gradle validation fails only at
ktlint: 679 common-main and 31 common-test findings. All 710 locations
map to unchanged baseline 96e5821 lines; no changed source line or new test has
a finding. Existing assertions and production pins/configuration are unchanged.
Source hashes, baseline-line comparison and translator/red/oracle/final-check
logs remain untracked in build/rust-kotlin-line-writer-partial.
