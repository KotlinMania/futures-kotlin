# Fallible chunk stream parity

## Selection and translation provenance

Graph counts are Futures 30 consumers, Tokio 33 and Itertools 11. All three
common source/test trees were clean when this independent error/buffering gap
was selected. Futures started clean at 53da4e2. Existing build/platform work
is separately checkpointed in e2a8c62; this step does not reattribute it or
claim complete platform validation. No competing Iris or generator work changed.

The native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/try_stream/try_chunks.rs` and
`try_ready_chunks.rs` using this repository's gradle.properties and source root
`tmp/futures-util/src`, into build/rust-kotlin-try-chunks scratch files. Both
retain Fuse-wrapped inputs and undivided upper size bounds; TryChunks also
retains buffered contribution with checked upper addition. Generated associated
types/imports need adaptation and are not independently compilable. The source
repair is manual faithful adaptation using the existing StreamFuse and Try
owners, not a generator repair or fully generated-build claim.

## Behavior

Both upstream adapters wrap IntoStream(input) in Fuse. Kotlin TryStream already
extends Stream<Try<T,E>>, so its existing canonical StreamFuse directly supplies
the same end-of-input latch; no duplicate adapter/type owner is needed. After
End, both adapters must keep returning End without polling the raw input. Their
terminal size hints become zero, including after emitting a final partial chunk.
Kotlin previously kept a termination bit but did not check it before polling.

Each input error may emit one output error, so dividing an upper item count by
capacity can underestimate output count: three errors at capacity four yield
three output errors, not zero. TryChunks divides only the lower bound and adds
one for a nonempty saved buffer; its upper is the underlying upper plus that
contribution with checked addition. TryReadyChunks keeps the underlying upper
unchanged. The existing saturating lower and checked upper respect Kotlin's
SizeHint Int domain, not the unrepresentable full Rust usize domain.

Errors are not end-of-stream. Both adapters continue polling after returning
an error, with the preceding buffered items attached to that error, in order.
TryChunks retains items across Pending; TryReadyChunks flushes them at Pending.
Nullable successful payloads and nullable non-Throwable errors remain tagged
in canonical Try, separate from Yield.End. Existing error wrapper/formatting
implementations are unchanged by this repair.

Seven new Kotlin cases cover final-partial fusion for both adapters,
error-only upper bounds, Pending buffering plus nullable error, recovery after
error and Pending flush before a later error. The initial fixture compile
required explicitly implementing existing TryStream (Kotlin lacks Rust's
blanket implementation); only the test fixture was corrected. Five of seven cases failed at runtime before repair; the two error-resumption
and Pending-flush controls passed. Existing assertions and interfaces were not
weakened. Seven actual Rust cases execute
unchanged local futures-util 0.3.31 with producer fixtures and real Waker/Context,
not a simulated chunk algorithm. Rust blanket TryStream and actual IntoStream
and Fuse are used by the real upstream adapters.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-try-chunks/cargo-target cargo test --manifest-path verification/try-chunks-oracle/Cargo.toml --offline --locked
```

## Limits

No Rust move/pin/Send/Sync, deterministic allocation/Drop timing or full usize
representation equivalence is claimed. The existing Kotlin FusedStream trait
bounds, missing raw/pinned/mutable accessors and Sink forwarding are outside
this step. Error Display/Debug formatting remains a separate source gap; no
new approximate formatter is introduced here. Seven finite cases do not prove
all extreme size hints. No pins, runtime/build/publication configuration or
repositories changed. Other platforms, downstream consumers, Swift, CI and
publication metadata remain unverified by these local checks.

## Final-source local checks

After fixing the new fixture's supertype formatting, the final tree passes
all 435 JVM tests and 435 macOS ARM tests, including seven new regressions.
All seven actual Rust oracle cases pass. Detekt has zero findings. Combined
Gradle validation still fails at ktlint: 704 common-main and 31 common-test
findings, all mapped to unchanged baseline 53da4e2 lines. New/changed lines
have no findings. Existing assertions, pins and configuration are unchanged.
Final-source hashes, baseline-line comparison, translator output and red,
oracle and final-check logs remain untracked in build/rust-kotlin-try-chunks.
