# SelectAll source parity

## Scope and provenance

The workspace dependency graph records 30 Futures consumers, versus 33 for
Tokio and 11 for Itertools. Futures has a concrete independent polling gap
exposed by bounded translator output, avoiding a new runtime design task.
The repository was clean at 8592f6d before this repair. Existing build/platform
work remains separately checkpointed in e2a8c62, without a full-platform
validation claim. No Iris-owned generator or other library work was changed.

The existing rust-kotlin.kexe successfully translated original
`tmp/futures-util/src/future/select_all.rs` into
`build/rust-kotlin-select-all/SelectAllReview.kt` using this repository's
`gradle.properties`, package io.github.kotlinmania.futures and source root
`tmp/futures-util/src`. Generated `swapRemove` and `mem.take` expose both
existing-source gaps. Generated associated-type aliases and imports are not
independently compilable; this is a manual source-faithful repair informed by
that output, using the existing canonical Future/Poll/TaskContext owners.
No generator repair or fully generated compilation is claimed.

## Behavior and regression evidence

Unchanged futures-util 0.3.31 polls in input order until the first Ready,
removes that winner with Vec::swap_remove, then transfers the remaining Vec
with mem::take. Kotlin previously shifted the list with removeAt and retained
the remaining queue. The repair removes the last element, moves it into the
winner's slot when needed, snapshots the remaining references and clears the
internal list. Returned index and ready value, including a null value, remain
unchanged. There is no public guarantee on the order of remaining futures:
these tests preserve the concrete upstream algorithm, not a new API promise.

Five new Kotlin cases cover first/middle swap removal, transferred queue state,
nullable ready values and an entirely-pending scan. Three fail before repair;
existing assertions remain unchanged. Five Rust cases execute actual local
futures-util, with ordinary scripted producers and real Context/Waker values.
They do not simulate the selection algorithm. Queue transfer is observed with
into_inner after success; no generic permission to repoll completed futures
is asserted.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-select-all/cargo-target cargo test --manifest-path verification/select-all-oracle/Cargo.toml --offline --locked
```

## Limits

Kotlin does not enforce Rust move/pin/Unpin/Send/Sync or deterministic Drop
semantics. This change preserves returned future object identity; it does not
establish general ownership equivalence. The existing Kotlin intoInner API
returns a snapshot without preventing subsequent receiver use, unlike Rust's
consuming receiver. That API limitation, empty-input diagnostic spelling,
other combinators and stream nullable/presence representation are unchanged.
No pins, repositories, runtime configuration or publication settings changed.
Downstream builds, other targets, Swift, remote CI and publication metadata
remain unverified by this bounded local step.

## Final-source local checks

All 411 JVM tests and 411 macOS ARM tests passed, including the five added
regressions. The actual Rust oracle passed all five cases. Detekt reports
zero findings. Combined Gradle validation fails at ktlint only: 708 common-main
and 31 common-test findings. All 739 diagnostic locations map to unchanged
lines in baseline 8592f6d; the added test and changed production lines have
none. No existing test assertions were edited. Final source hashes and the
static-line comparison are in build/rust-kotlin-select-all alongside red,
oracle, translator and final-check logs; build outputs stay untracked.
