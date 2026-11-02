# TryUnfold seed consumption before closure

The repository began clean at 1349070. Established graph reverse-dependent
counts remain Serde 56, Futures 30, Rand 30 and JSON 21. This bounded Futures
state-transfer gap is checked independently from ordinary Unfold. No
pre-existing dirty work or adjacent owner edits are included. All earlier
checkpoints and canonical sink mapper delegation, explicit-callback conversion,
shared takeValue, Fanout cloning and ownership/platform limitations remain
separately preserved.

The existing native rust-kotlin CLI translated original futures-util 0.3.31
stream/try_stream/try_unfold.rs using owning gradle.properties and original
source root into build/rust-kotlin-try-unfold-state/TryUnfoldReview.kt. It emits
state take/clear before closure invocation and the quiet no-future EOF branch.
Its Option/state/Pin/associated-type/Result lowerings remain incomplete; scratch
is not installed or claimed runnable. This is a manual exact-source adaptation
using existing canonical UnfoldState, not a generator fix or root-guard bypass.

## Exact source contract and repair

Actual TryUnfold stores state and future separately. poll_next takes state
before calling the closure. Pending keeps the active future. On Ready it clears
that future before handling the outcome: Ok(Some((item,next))) stores next
state and yields Ok(item), Ok(None) returns EOF, and Err(e) yields one error.
With neither state nor future left, polls return EOF again. A closure panic
therefore consumes the seed and leaves no active future; subsequent polls
return EOF without calling the closure again. A panic while polling an already
installed future instead retains that future.

Kotlin assigned InProgress only after the closure succeeded, leaving Value
available when it threw. The repair adds state = Empty before invoking the
closure. Existing nullable state/item/error tags, pending/error/EOF branches,
public declarations and successful iteration behavior remain unchanged.
No substitute future/type, completion flag or compatibility helper is added.

This is deliberately different from ordinary Unfold: its actual no-future
branch panics, whereas TryUnfold's returns EOF. A side-by-side actual Rust
control and corresponding Kotlin control assert that distinction. Canonical
Fuse and the ordinary Unfold repair are unchanged.

## Independent regressions

Eight Rust tests execute actual unchanged stream::try_unfold and, in the
distinction control, stream::unfold. Scripted fixtures implement only Future,
with real Context/Waker; they never implement either adapter or state transfer.
Cases cover closure panic with non-null seed, closure panic with nullable seed,
closure panic after an item, repeated Pending and EOF, a nullable error yielded
exactly once, nullable item/next seed distinct from EOF, future-poll panic
retaining its active future, and ordinary versus fallible terminal behavior.

Eight matching Kotlin cases run on original code with three failures: each
closure-panic case invokes the closure again instead of returning EOF. The
other five controls pass. The entire new test file is byte-identical between
red and final; all existing tests and assertions are unchanged. Post-panic/
post-completion probes establish these implementation traces, not a general
Future recovery, repoll or exception-safety guarantee.

Raw translator/red/oracle/Gradle logs and scratch output remain under
build/rust-kotlin-try-unfold-state. receipt.json records commands, timestamps,
final hashes and original failures. Test-only dependencies resolve actual
futures-util/core/task 0.3.31 offline; Cargo.lock remains ignored by existing
policy with its hash recorded. No production pin or repository changed.

## Remaining limits

The original Rust TryUnfold implements Stream, not FusedStream. Kotlin's
existing extra FusedStream/isTerminated API remains unchanged; this repair
does not certify that trait/type relationship, and the oracle does not query a
nonexistent Rust FusedStream implementation. Shared takeValue remains nullable/
non-consuming and uncorrected. Rust move/borrow/Pin/Unpin/Drop/Send/Sync and
deterministic destruction are not reproduced by Kotlin references/GC.
Debug, accessor/comment completeness and other APIs remain outside scope.

Public signatures are unchanged; no caller migration is introduced. Existing
TryStream tests, ordinary Unfold, canonical StreamFuse, UnfoldState and the
ordinary Unfold regression suite are byte-identical to baseline. No downstream,
Swift/other-target, fresh publication or complete-library parity is claimed.
No configuration, UI, adjacent owner, remote CI, push, PR, tag, release or
publication action occurred.

## Final-source local checks

All eight actual Rust cases pass. All 674 JVM and 674 macOS ARM tests pass,
including the eight new cases and unchanged existing tests. Detekt reports
zero findings. The combined Gradle invocation exits 1 only at ktlint:
581 common-main and 31 common-test findings. All 612 diagnostic locations
map to unchanged 1349070 baseline lines; no authored line has a finding.
Final receipt hashes are verified after the local checkpoint.
