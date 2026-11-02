# ForEach asynchronous callback source parity

## Selection and translation evidence

Graph counts: Serde 56 consumers, Tokio 33, Futures/Rand 30. Their common
source/test trees were clean at selection. Futures offered a material missing
callback-future capability using canonical Future/Poll/Yield, without coroutine
redesign or another owner's files. This tree started clean at c8b5373. Existing
build/platform work remains separately checkpointed in e2a8c62, not newly
authored or fully platform-validated by this step.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/stream/for_each.rs` using this repository's
gradle.properties and source root tmp/futures-util/src into
build/rust-kotlin-for-each/ForEachReview.kt. Direct unchanged futures-util 0.3.31
comparison confirms FnMut(Item)->Future<Output=()>, the stored Option<Future>,
and the callback-future-first poll loop. Generated unresolved types/imports
are not independently compilable. This is a manual faithful source adaptation,
not a generator fix or coroutine/Flow substitution.

## Exact API and state machine

Rust obtains an item, creates and stores its callback future, and polls that
future until Ready before polling the input again. Pending retains the same
future and does not call the callback again. A callback future's panic is not
discarded. For a FusedStream input, termination is pending_future.is_none() and
input.is_terminated(), not a separate completion bit. No synthetic Fuse or
post-completion guard is added to change underlying stream polling behavior.

Kotlin incorrectly accepted (T)->Unit and invoked only that synchronous action.
A returned Future could be discarded rather than driven. The repair changes
ForEach's constructor/factory/extension callback to (T)->Future<Unit>, stores
the pending canonical future, and follows the source loop and Fused predicate.
The known repository caller in StreamCombinatorsTest now returns ready(Unit)
after appending its item. Every existing assertion is unchanged; no synchronous
compatibility overload, duplicate helper owner or callback future simulation
was added. The public API correction is intentional and callers must return
a real Future<Unit>.

Six added Kotlin cases cover blocking further input on Pending action, retaining
that action across polls, callback-future exception propagation, initial fused
termination, distinct input/action Pending states, nullable items with immediate
futures and Pending followed by End without a callback. The old API fails the
focused compile under existing -Werror because future-valued variable expressions
are unused in a Unit callback. This is API/compiler-gate red evidence, not six
executed runtime failures. Initial expected-list nullable inference was corrected
in the new fixture first. No warning suppression or assertion relaxation was
used to force a runtime red run. Six actual Rust oracle cases execute unchanged
ForEach with real callback futures, Context/Waker, manual release and caught
panic. Fixtures model only producers, never the ForEach algorithm.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-for-each/cargo-target cargo test --manifest-path verification/for-each-oracle/Cargo.toml --offline --locked
```

## Caller and representation limits

Repository source/test compilation checks local callers; the one known stream
forEach caller was repaired. A workspace Kotlin text search found no explicit
imports of io.github.kotlinmania.futures.ForEach/forEach, excluding build/tmp/
tools/node_modules/Gradle/git trees. This does not resolve wildcard imports or
receiver types, prove no downstream callers, or validate external binaries.
Downstream source may need the same callback adaptation and is not built here.
Rust's FusedFuture impl requires FusedStream; Kotlin's existing extra interface
is retained for all input types and reports false for a non-Fused input. Static
trait bounds, FnMut/type relationships and Rust move/borrow/pin/Send/Sync,
deterministic Drop/cross-thread equivalence remain limitations. Existing public
Debug/associated-type omissions are not completed by this step. Prior Native
flush-result evidence and correction are preserved unchanged. No pins,
runtime/build/publication configuration, repositories or unrelated owners were
changed. Other platforms, Swift, remote CI and publication metadata remain
outside local validation scope.

## Final-source local checks

After sorting the new test imports, the actual final tree compiles with the
corrected API and passes all 504 JVM tests and 504 macOS ARM tests, including
six added cases and the adapted local caller's unchanged assertions. All six
actual Rust oracle cases pass; Detekt has zero findings. Combined Gradle
validation fails only at ktlint: 673 common-main and 31 common-test
findings. All 704 diagnostic locations map to unchanged baseline c8b5373
lines; no changed source/caller line or new test has a finding. Production
pins/configuration remain unchanged. Source hashes, baseline-line comparison
and translator/API-red/oracle/final-check logs remain untracked under
build/rust-kotlin-for-each.
