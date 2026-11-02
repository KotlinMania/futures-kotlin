# OptionFuture delegation parity

## Provenance and scope

The dependency graph records Futures 30 consumers, Tokio 33 and Itertools 11.
Their common source/test trees were clean at selection. Futures has an
independent confirmed runtime delegation gap, avoiding competing generator
work or an unrelated runtime design. This repository was clean at 2320573.
Prior build/platform checkpoint e2a8c62 remains separate; this step makes no
new full-platform validation claim for it.

The current rust-kotlin.kexe successfully translated original
`tmp/futures-util/src/future/option.rs` into
`build/rust-kotlin-option-future/OptionReview.kt`, with this repository's
gradle.properties and source root `tmp/futures-util/src`. Generated output
preserves direct inner polling and delegated FusedFuture status. Its unresolved
associated types/imports are not independently compilable. This repair is
manual adaptation of exact source behavior using the existing canonical
Future/Poll/FusedFuture owners, not a generator fix or a new Option owner.

## Behavior

Upstream OptionFuture retains Some(inner) and delegates each poll to that
future; it does not turn Some into None after a Ready. FusedFuture termination
is forwarded from a present inner future, and an absent inner is terminated.
Kotlin previously discarded the inner after one Ready and cached termination.
This changed an explicitly repeatable AlwaysReady into an absent future after
its first value. The repair retains the immutable inner reference and delegates
status without a separate completion bit. Pending propagation and the supplied
context are preserved. One-shot Ready termination still forwards unchanged.

Five Kotlin cases cover repeated AlwaysReady values, its nonterminated status,
one-shot Ready status, Pending/context forwarding and repeated absence. Two
failed before repair; existing tests/assertions were not changed. The initial
new Kotlin test compile exposed a mismatched override parameter name, corrected
before establishing runtime red results. The Rust fixture initially used
std::future::Ready where the source FusedFuture bound requires Futures Ready;
that fixture type was corrected without changing the upstream bound.

The five Rust cases use actual unchanged local futures-util 0.3.31 and standard
Context/Waker values. AlwaysReady and Ready are the real upstream producers;
the pending fixture only models a producer, never OptionFuture. Repeated
polling assertions apply to the explicitly repeatable AlwaysReady and absent
OptionFuture, not arbitrary completed futures.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-option-future/cargo-target cargo test --manifest-path verification/option-future-oracle/Cargo.toml --offline --locked
```

## Limits

The existing Kotlin T? output still collapses Some(null) and None. No full
Option representation parity or canonical Option migration is claimed. Kotlin
implements FusedFuture for every OptionFuture, whereas Rust requires its inner
future to implement FusedFuture; existing non-Fused Kotlin inputs report false.
That public bound difference is unchanged. Rust move/pin/Send/Sync, clone bounds,
Default support and deterministic Drop behavior are not established by this
repair. No pins, runtime/build/publication configuration or repositories changed.
Other platforms, downstream builds, Swift, remote CI and fresh publication
metadata remain outside the local validation scope.

## Final-source local validation

After correcting the added test's multiline-expression formatting, all 416 JVM
and 416 macOS ARM tests passed on the final tree. Detekt has zero findings.
The actual Rust oracle passed all five cases. Ktlint remains blocked by 706
common-main and 31 common-test findings: all 737 locations map to unchanged
lines in baseline 2320573. Removing the old completion branch removed two old
style findings; no changed-line or new-test findings remain. Combined Gradle
validation exits unsuccessfully for those baseline ktlint failures. Logs,
source hashes and diagnostic-line comparison remain under
build/rust-kotlin-option-future, untracked.
