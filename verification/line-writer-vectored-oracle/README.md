# LineWriter vectored source parity

## Selection and translator provenance

Graph counts: Serde 56 consumers, Tokio 33 and Futures 30. Their common
source/test trees were clean at selection. Futures offered an independent
missing vectored algorithm using existing canonical writers/slices, without
new architecture or another owner's files. This tree started clean at 8d73aa5.
Prior existing build/platform work remains separately checkpointed in e2a8c62,
not newly authored or fully platform-validated by this step.

The successful bounded native translation of original
`tmp/futures-util/src/io/line_writer.rs` is reused from
build/rust-kotlin-line-writer-partial/LineWriterReview.kt after verifying the
upstream SHA256 against that completed step's final-source receipt. Reuse is
recorded in build/rust-kotlin-line-writer-vectored; no stalled CLI was retried.
Direct unchanged futures-util 0.3.31 comparison independently confirms this
method, not a scalar algorithm assumption. Generated associated types/imports
are not independently compilable. This is manual faithful adaptation, not a
generator change or complete LineWriter parity claim.

## Exact vectored algorithm

The source finds the last slice containing any newline, searching slices in
reverse and bytes forward. With none, it flushes an already-completed buffered
line if needed and delegates to canonical BufWriter's vectored method. With
a newline-containing slice, it flushes the underlying buffered writer, then
passes every whole slice through that slice to the inner vectored method.
Bytes after a newline within the same slice are deliberately included.

A zero inner count returns Ok(0). A partial count below the whole grouped
length returns that count without buffering either unwritten grouped bytes
or later tail. This differs from the scalar partial-line algorithm. Only
a completely written group allows buffering later nonempty slices until
capacity prevents more progress. Returned count is accepted inner plus tail
bytes; Pending/flush errors precede any vector acceptance.

Kotlin had no override and inherited a default selecting one slice and scalar
pollWrite. The repair uses existing IoSlice and BufWriter, and delegates directly
to the canonical inner AsyncWrite. The Rust private BufWriter helper only
forwards that call; no duplicate public helper owner was added. Group length
is compared in Long to avoid Int overflow during the partial-write decision.
The existing Int result remains a bound on representable accepted counts.

Six new Kotlin cases cover whole newline-containing slices with offset/empty
slices, partial vectored count without buffering, no-newline buffering across
slices, tail capacity across multiple/empty slices, Pending flush followed by
zero inner progress, and original flush-error priority. Five fail before repair;
flush-error priority is the passing control. Existing assertions were unchanged.
Six actual Rust cases execute local unchanged LineWriter via public AsyncWrite
and standard IoSlice. Scripted writers select explicit accepted counts and
record actual grouped requests/accepted bytes and flushes; they do not simulate
line grouping, tail capacity or the source adapter. No algorithm substitution
or assertion weakening is used.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-line-writer-vectored/cargo-target cargo test --manifest-path verification/line-writer-vectored-oracle/Cargo.toml --offline --locked
```

## Limits

Counts whose inner-plus-buffered total exceeds Int.MAX_VALUE remain an existing
Int-versus-usize representation limit; these finite tests do not establish full
pointer-width count parity. Malformed/aliased/mutated IoSlice ranges, zero
capacity, arbitrary inner error/wakeup patterns, Debug formatting and raw/pinned
accessor differences are not exhausted here. No full Rust move/borrow/pin/
Send/Sync, allocation/Drop or cross-thread equivalence is claimed. Prior Native
flush-result failure receipts and corrected representation remain unchanged.
No production pins, runtime/build/publication configuration, repositories or
unrelated owners changed. Other targets, downstream consumers, Swift, remote
CI and publication metadata remain outside local verification scope.

## Final-source local checks

The actual final tree passes all 492 JVM tests and 492 macOS ARM tests, including
six added cases and prior scalar/Native error regressions. All six actual Rust
oracle cases pass; Detekt has zero findings. Combined Gradle validation fails
only at ktlint: 679 common-main and 31 common-test findings. All
710 locations map to unchanged baseline 8d73aa5 lines; no added source line
or new test has a finding. Existing assertions and production pins/configuration
are unchanged. Source hashes, baseline-line comparison and translation-reuse,
red, oracle and final-check logs remain untracked under
build/rust-kotlin-line-writer-vectored.
