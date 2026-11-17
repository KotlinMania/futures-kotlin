# TryFold nullable presence and terminal-state parity

## Selection and translator evidence

Graph counts: Serde 56 consumers, Tokio 33, Futures/Rand 30. Their common
source/test trees were clean at selection. Futures offered a material nullable
accumulator liveness and failure-state gap without changing a public API or
another owner's work. This tree started clean at 31c49b7. Existing build/platform
work remains separately checkpointed in e2a8c62, not newly authored or fully
platform-validated by this step. Adjacent Tokio work is untouched.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/try_stream/try_fold.rs` with this repo's
gradle.properties and source root tmp/futures-util/src into
build/rust-kotlin-try-fold/TryFoldReview.kt. Direct unchanged futures-util
0.3.31 comparison independently confirms tagged accumulator/future presence,
take-before-operation, error transitions and completed-poll panic. Generated
unresolved types/imports are not independently compilable. This is manual
faithful adaptation using canonical Future/Poll/Try/Yield, not a generator fix,
synchronous substitute or new Option owner.

## Exact state transitions

A null accumulator is legitimate data, not absence. Rust stores Some(initial)
and Some(operation_success), even when that payload is itself None. While an
operation future exists, TryFold polls it first; Ready clears that future and
Ok stores a present accumulator, while Err terminates. Otherwise input Pending
leaves the accumulator present; an input Ready takes it before operation
creation, input Err, or EOF. With neither state present, further polls panic
with "Fold polled after completion". Fused termination uses those same tags.

Kotlin used accum==null as absence, so null initial/success payloads could
report termination and remain Pending indefinitely. It also retained the old
accumulator while calling the operation and returned Pending after completion.
The repair adds a separate hasAccum tag, clears data/tag before operation
creation, and follows the source transitions. Generic nullable extraction uses
a local unchecked cast guarded by that private presence invariant, as in the
existing Fold adaptation; it does not require a non-null payload or a duplicate
Option type. Original input/operation error payloads stay tagged in Try and
remain generic non-Throwable E. Public signatures and callers are unchanged.

Creation failure leaves accumulator and future absent, hence terminated.
Failure inside the stored future leaves that future present, hence not
terminated. These are source-observed states, not a promise to recover or safely
repoll arbitrary panicking futures. Null EOF success is Ready(Try.Ok(null)),
which remains distinct from Pending and Try.Err(null).

Six new Kotlin cases cover empty null initial state, null operation success
feeding a later nullable operation error, separate input/action Pending with
null initial state, nullable input error/completion, creation failure and
stored-future failure. Five fail at runtime before repair; stored-future failure
is the passing control. Existing assertions were unchanged. Six actual Rust
oracle cases execute unchanged local TryFold with nested Option/Result payloads,
real Context/Waker, manually released futures and caught source panics. Fixtures
supply producer events only, never TryFold's algorithm.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-try-fold/cargo-target cargo test --manifest-path verification/try-fold-oracle/Cargo.toml --offline --locked
```

## Limits

No Rust FnMut, move/borrow/pin/Send/Sync, deterministic Drop or cross-thread
identity is claimed from Kotlin lambda/reference state. Generic erasure and
public concrete/associated type and Debug gaps remain. This fixes private
presence in TryFold, not the separate Peekable public accepted-null/absence
limitation. Prior Native writer failure receipts and corrected representation
are retained unchanged. No production pins, public callback signatures,
runtime/build/publication configuration, repositories or unrelated owners were
changed. Other targets, downstream source/binary consumers, Swift, remote CI
and publication metadata remain outside local validation scope.

## Final-source local checks

After formatting the new fixture branches, the actual final tree passes all
516 JVM tests and 516 macOS ARM tests, including six added cases and prior
Native regressions. All six actual Rust oracle cases pass; Detekt has zero
findings. Combined Gradle validation fails only at ktlint: 661 common-main
and 31 common-test findings. All 692 locations map to unchanged
baseline 31c49b7 lines; no changed source line or new test has a finding.
Existing assertions, public signatures and production pins/configuration are
unchanged. Source hashes, baseline-line comparison and translator/red/oracle/
final-check logs remain untracked under build/rust-kotlin-try-fold.
