# BufWriter terminal flush cleanup parity

## Selection and translator provenance

Graph consumers: Serde 56, Tokio 33, Futures 30. Their common source/test trees
were clean at selection. Futures offered an exact independent partial-error
buffer-state gap without new runtime design or another owner's work. This tree
started clean at 71776c6. Prior existing build/platform work remains separately
checkpointed in e2a8c62, not newly authored or fully platform-validated here.
ReadLine/Lines were reviewed read-only; their distinct shared byte/string state
was not approximated or changed as part of this bounded writer repair.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/io/buf_writer.rs` with this repo's gradle.properties,
package io.github.kotlinmania.futures.io and source root tmp/futures-util/src
into build/rust-kotlin-buf-writer-flush/BufWriterReview.kt. Direct comparison of
unchanged futures-util 0.3.31 confirms a saved result and terminal loop break,
followed by draining written bytes and resetting the cursor. Generated types,
imports and unsafe/trait mechanisms are not independently compilable. This is
a manual faithful adaptation of that bounded path, not a generator repair or
full BufWriter parity claim.

## Exact state transition

Upstream flush_buf keeps a written cursor across Pending, returning before
cleanup in that case. On a write error or zero write it instead saves the
terminal error, breaks the loop, removes exactly the already-written prefix,
resets the cursor and returns the saved result. Complete success drains all
buffered bytes through the same cleanup. Kotlin returned early for terminal
errors too, retaining already-written bytes in buffer()/spareCapacity().

The repair delays only terminal error returns until cleanup. Original IoError
identity, WriteZero kind/message, Pending cursor state, unwritten suffix and
inner flush ordering are preserved. After a partial terminal error, a new
small write can now use the freed buffer capacity without an unnecessary
inner flush. Retries already skipped the prefix through the old cursor: this
step does not claim to repair duplicate retry output that was not reproduced.

Six new Kotlin cases cover partial error, partial WriteZero, immediate append
using freed capacity, retry without duplicate output, partial Pending/resume
and error before any progress. Three fail before repair; the latter three are
passing controls. Existing assertions were unchanged. Six actual Rust cases
execute local unchanged BufWriter through public AsyncWrite polling. Buffers
are seeded with two small writes, not inaccessible internal helper calls.
Scripted writers supply explicit counts/errors/Pending, record requested slices
and accepted bytes, and never implement buffer cleanup. Rust private capacity/
spare_capacity methods are not called: buffer contents and successful append
show the source capacity effect; Kotlin's existing public spareCapacity is
additionally checked.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-buf-writer-flush/cargo-target cargo test --manifest-path verification/buf-writer-flush-oracle/Cargo.toml --offline --locked
```

## Limits

Rust's private/public API visibility differs from this existing Kotlin port.
Kotlin buffer() returns a copy, not a borrowed slice. Conditional AsyncRead/
AsyncBufRead/AsyncSeek trait implementations, vectored writes, raw accessors,
malformed writer counts, extreme Int/usize arithmetic and other paths are not
repaired or exhaustively validated. No Rust borrow/pin/Send/Sync, deterministic
allocation/Drop or cross-thread equivalence is claimed. No production pins,
runtime/build/publication configuration, repositories or unrelated owners were
changed. Other platforms, downstream consumers, Swift, remote CI and publication
metadata remain outside the bounded local checks.

The first repair representation initialized a mutable canonical Try result to
Ok and assigned Err before the loop break. JVM passed all 468 cases, but Native
failed five error-path tests with an Err-to-Ok ClassCastException at flushBuf's
return. The final representation instead stores an optional non-null IoError
and constructs canonical Try after cleanup; the loop, drain/reset, error
identity and tests are unchanged. This is a bounded representation correction,
not a proven general compiler root cause, compiler/settings change or weakened
assertion. Initial failure receipts remain under the build evidence directory.

## Final-source local checks

The actual final representation passes all 468 JVM tests and 468 macOS ARM
tests, including all six added cases. All six actual Rust oracle cases pass;
Detekt has zero findings. Combined Gradle validation fails at ktlint only:
679 common-main and 31 common-test findings. All 710 locations map to
unchanged baseline 71776c6 lines; no changed source line or new test has a
finding. Existing assertions and production dependency/configuration declarations
were unchanged. Source hashes, baseline-line comparison, initial Native failure
receipts and translator/red/oracle/final-check logs remain untracked under
build/rust-kotlin-buf-writer-flush.
