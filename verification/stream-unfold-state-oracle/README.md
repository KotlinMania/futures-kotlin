# Ordinary stream Unfold seed and terminal state

The repository started clean at 61725c4. Established graph reverse-dependent
counts remain Serde 56, Futures 30, Rand 30 and JSON 21. This bounded Futures
gap concerns the ordinary stream Unfold state machine. No pre-existing dirty
work or adjacent owner edits are included. Prior checkpoints and canonical
SinkErrInto/SinkMapErr delegation, explicit-callback conversion limits, shared
takeValue and ownership/platform limitations are preserved separately.

The existing native rust-kotlin CLI translated original futures-util 0.3.31
stream/unfold.rs, with owning gradle.properties and original source root,
into build/rust-kotlin-stream-unfold-state/StreamUnfoldReview.kt. The output
calls takeValue and emits the original terminal panic; its Option/state/Pin/
associated-type lowering remains incomplete. Scratch is not installed or
claimed runnable. This is a manual adaptation of exact original source using
canonical UnfoldState, not a generator fix or root-file guard bypass.

## Exact behavior and repair

Original stream/unfold.rs takes its seed through unfold_state.rs.take_value,
which replaces Value with Empty before invoking the closure. Successful
closure construction installs Future. Thus a closure panic consumes the seed
and leaves Empty; a panic while polling an already installed future instead
retains that future. Pending also retains it. A yielded item stores next_state
as Value; EOF stores Empty. is_terminated reflects Empty. poll_next with no
active future panics with the original Unfold terminal message, including
after it previously returned EOF.

There is an upstream prose inconsistency: introductory documentation suggests
future calls after termination return None, while the actual poll_next branch
panics. Tests execute the unchanged original implementation and establish the
latter behavior. We do not silently rewrite Rust or replace its behavior with
the prose description. FusedStream.is_terminated is an observation here, not
a guarantee that this adapter's next poll returns EOF. Actual canonical Fuse
does provide stable repeated EOF and is tested independently.

Kotlin previously assigned InProgress only after the closure succeeded,
retaining Value when it threw, and quietly returned End from an empty state.
The bounded repair empties the slot before closure execution and uses the
original terminal panic when there is no active future. It introduces no
substitute type, future, global done flag or API change. Nullable payload/state
tags and the existing successful iteration algorithm remain intact.

TryUnfold has different original terminal behavior and is deliberately
unchanged. Its adjacent callback/state/type-relationship gaps are not certified
by this repair. Shared UnfoldState.takeValue remains nullable/non-consuming;
the caller-owned tag adaptation does not claim that helper is corrected.

## Actual regressions

Eight Rust cases execute unchanged upstream stream::unfold and canonical Fuse
through Stream/StreamExt with real Context/Waker. Scripted fixtures implement
only Future, never Unfold or state extraction. Cases cover immediate EOF repoll,
a value followed by EOF repoll, closure panic before any item, closure panic
after a value, repeated Pending with nullable item and next seed, panic while
polling an installed future, canonical Fuse stabilizing EOF, and ordinary
non-null iteration. Exact trace order, termination observations, wake counts
and panic boundaries are asserted.

Eight matching Kotlin cases run on original code with five failures and three
passing controls. Nullable iteration succeeds on old code; its containing
case fails specifically at the post-EOF panic assertion. The future-panic,
canonical Fuse and normal-sequence controls pass. All existing assertions and
the entire new suite are unchanged between red and final. Probes after panic
or completion observe upstream behavior and do not establish a generic Future
panic-recovery or reuse guarantee. The production panic text is copied exactly;
fixture assertions check its stable prefix, not complete panic type equivalence.

Raw translator/red/oracle/Gradle logs and scratch output remain under
build/rust-kotlin-stream-unfold-state; receipt.json records exact commands,
timestamps, hashes and original failures. Test-only dependencies resolve
futures-util/core/task 0.3.31 offline. Cargo.lock remains ignored under existing
policy, with its hash recorded. No production pin or repository changed.

## Limits

Public declarations are unchanged. Existing StreamUnfoldTest, canonical
StreamFuse, TryStreamUnfold and UnfoldState are byte-identical to the baseline.
Kotlin references/GC do not reproduce Rust move/borrow/Pin/Unpin/Drop/Send/Sync
relationships or deterministic resource destruction. Debug, accessor/comment
completeness and other trait APIs remain outside scope. No downstream build,
Swift/other-target, fresh publication or complete-library parity is claimed.
No configuration, UI, adjacent owner, remote CI, push, PR, tag, release or
publication action occurred.

## Final-source local checks

All eight actual Rust cases pass. All 666 JVM and 666 macOS ARM tests pass,
including the eight new cases and unchanged existing Unfold tests. Detekt
reports zero findings. The combined Gradle invocation exits 1 only at ktlint:
581 common-main plus 31 common-test findings. All 612 diagnostic locations
map to unchanged 61725c4 baseline lines; no authored line has a diagnostic.
Final receipt hashes are verified after the local checkpoint.
