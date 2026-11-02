# Zip polling and fused-input parity

## Selection and translator provenance

Futures has 30 graph consumers, Tokio 33 and Itertools 11. All three common
source/test trees were clean when this independent exact-source polling gap
was selected. This repository was clean at 40754c1; e2a8c62 remains the separate
pre-existing build/platform checkpoint, not newly fully validated prior work.
No other repository, Iris session, generator or configuration was changed.

The existing native rust-kotlin executable successfully translated original
`tmp/futures-util/src/stream/stream/zip.rs` into
`build/rust-kotlin-stream-zip/ZipReview.kt` with this repository's
gradle.properties and source root tmp/futures-util/src. Its fused inputs,
non-early-return polls and conjunction of inner termination match direct
inspection of unchanged futures-util 0.3.31. Generated associated types/imports
are not independently compilable. This is manual source-faithful adaptation
using canonical StreamFuse/Poll/Yield, not a generator implementation fix.

## Exact algorithm

Upstream Zip wraps both inputs in Fuse. It polls each input without a queued
item, in left-to-right order, even if the other input has just ended. It then
returns a pair when both queues contain items, otherwise End when either Fuse
has observed End, otherwise Pending. The FusedStream predicate is separately
the conjunction of both Fuse termination states. A raw inner stream's own
is_terminated status does not set the newly created Fuse's observed-end bit.

Kotlin previously returned as soon as an input ended, suppressed all subsequent
polling with a global done bit and used a different raw-input termination
predicate. The repair stores canonical StreamFuse inputs, defers the End
decision until after both possible polls, and uses the upstream predicate.
Queued null payloads retain their separate presence bits. Raw input identities
returned by getRef/intoInner remain unchanged.

The source behavior is deliberately preserved even when Zip returns End but
only one Fuse is terminated: isTerminated can remain false. On a further poll,
an unqueued pending input may still be polled; an already ended input is not
polled again. If the nonended input is already queued, it is not polled either.
These observations concern concrete Zip 0.3.31, not a new promise that arbitrary
streams may be safely repolled after End. No synthetic extra early termination
or queue disposal is introduced to make this behavior seem more conventional.

Six new Kotlin cases cover left End plus queued right, right End plus pending
left and further polls, both End in one poll, initial Fuse status, nullable
buffering across Pending and both Pending. Four fail before repair. Existing
assertions are unchanged. Six actual Rust tests use unchanged local Zip with
scripted input producers and real Context/Waker; inputs reject polls after
End, trace calls and provide size hints. The fixtures never simulate Zip/Fuse.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-stream-zip/cargo-target cargo test --manifest-path verification/stream-zip-oracle/Cargo.toml --offline --locked
```

## Remaining limits

The existing size-hint additions still lack upstream saturating/checked
arithmetic at extreme bounds; the small finite hints in these tests do not
establish full hint parity. Kotlin SizeHint uses Int rather than Rust usize.
No full pointer-width equivalence, Rust move/pin/Send/Sync or deterministic
Drop behavior is claimed. Kotlin accessor receiver reuse/mutability and
missing mutable/pinned accessor APIs remain outside this step. No dependency
pins, build/runtime/publication configuration or repositories changed. Other
platforms, downstream consumers, Swift, CI and publication metadata are
unverified by these local tests.

## Final-source checks

All 428 JVM tests and 428 macOS ARM tests passed, including the six added
regressions. All six actual Rust oracle cases passed. Detekt reports zero
findings. Combined Gradle validation fails at ktlint only: 704 common-main
and 31 common-test findings. All 735 locations map to unchanged baseline
40754c1 lines; no changed production line or new test has a finding. Two
pre-existing branch-spacing findings were removed while adapting those
branches. Existing assertions and dependency/configuration declarations
remain unchanged. Source hashes, diagnostic-line comparison and translator,
red, Rust oracle and final-source logs remain untracked under
build/rust-kotlin-stream-zip.
