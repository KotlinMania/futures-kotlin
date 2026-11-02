# Stream Fold asynchronous operation and presence parity

## Selection and translator evidence

Graph counts: Serde 56 consumers, Tokio 33, Futures/Rand 30. Their common
source/test trees were clean at selection. Futures offered a material missing
operation-future capability using canonical Future/Poll/Yield, without broader
architecture or another owner's work. This tree started clean at 6123fba.
Existing build/platform work remains separately checkpointed in e2a8c62, not
newly authored or fully platform-validated by this step.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/stream/fold.rs` with this repo's gradle.properties
and source root tmp/futures-util/src into
build/rust-kotlin-stream-fold/FoldReview.kt. Direct unchanged futures-util
0.3.31 comparison confirms FnMut(Accum,Item)->Future<Output=Accum>, separate
accumulator/future Option states and the future-first poll loop. Generated
unresolved types/imports are not independently compilable. This is manual
faithful adaptation, not a generator fix or coroutine/Flow substitute.

## Exact API and state transitions

While an operation future exists, Rust polls it and stores its Ready output as
a present accumulator, then clears that future. Otherwise, while an accumulator
exists, it polls the stream. Input Pending leaves the accumulator present.
After Ready it takes the accumulator before either calling the operation for
an item or returning it at EOF. With neither accumulator nor future it panics
on further polling. Fused termination is exactly both states absent.

Kotlin incorrectly accepted a synchronous (Accum,Item)->Accum operation. The
repair changes constructor/factory/extension to return Future<Accum>, stores
that canonical pending future and follows the source loop. The accumulator
uses an explicit hasAccum flag separate from nullable data, so a legitimate
null initial/result value stays present. The public constructor now accepts R
rather than incorrectly widening every accumulator to R?; the parameter name
remains accum. Existing unchecked generic extraction is guarded by that presence.

Operation-creation failure occurs after taking the accumulator and before
storing a future, so Fold is terminated. Failure during a stored future's poll
keeps that future present, so Fold is not terminated. Those distinct observed
states are preserved; no synthetic restoration or post-panic retry contract is
introduced. The existing Kotlin RuntimeException completion spelling remains;
Rust uses a panic rather than Kotlin exceptions.

Six new Kotlin cases cover Pending operation and next-accumulator feeding,
nullable initial/output accumulators, creation failure, stored-future failure,
input Pending preserving state, and empty nullable initial result/completion.
The old synchronous API rejects four future-valued operations at compile time:
this is API-red evidence, not six runtime failures. No warning flags or test
assertions were relaxed. Six actual Rust oracle cases run unchanged local Fold
with real Context/Waker, manually released operation futures and caught source
panics. Producers model only input/future events, never the Fold algorithm.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-stream-fold/cargo-target cargo test --manifest-path verification/stream-fold-oracle/Cargo.toml --offline --locked
```

## Caller scope and limitations

Three known local stream-fold call sites were adapted: one in
StreamCombinatorsTest and two in StreamTest. They return canonical ready(computed)
without changing any existing assertion. Poll.fold, Either.fold, Yield.fold and
SinkOutcome.fold are different APIs and were untouched. A workspace text search
found no explicit core Fold/fold or wildcard imports outside the defining
package; it is not semantic resolution or downstream compilation proof.
Downstream callbacks may need the same adaptation and remain unbuilt; no
compatibility overload, duplicate future type or synchronous wrapper was added.
Rust FnMut/type relationships, move/borrow/pin/Send/Sync, deterministic Drop,
callback borrow restrictions and cross-thread equivalence remain limitations.
Generic null acceptance now follows its tagged state, without claiming a broad
Option API migration elsewhere. Public Debug/concrete-type/associated-type gaps
remain outside this step. Prior Native failure receipts and corrected writer
representation remain untouched. No production pins, runtime/build/publication
configuration, repositories or unrelated owners changed. Other targets, Swift,
remote CI and publication metadata remain outside local checks.

## Final-source local checks

The actual final tree passes all 510 JVM tests and 510 macOS ARM tests, including
six added cases and three adapted local stream callers with unchanged assertions.
All six actual Rust oracle cases pass; Detekt has zero findings. Combined Gradle
validation fails only at ktlint: 670 common-main and 31 common-test
findings. All 701 diagnostic locations map to unchanged baseline 6123fba
lines; no changed source/caller line or new test has a finding. Production
pins/configuration remain unchanged. Source hashes, baseline-line comparison
and translator/API-red/oracle/final-check logs remain untracked under
build/rust-kotlin-stream-fold.
