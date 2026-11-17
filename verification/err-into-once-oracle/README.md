# SinkErrInto canonical error-mapper delegation

The repository began clean at 9abbf2f. The established graph comparison remains
Serde 56 reverse dependents, Futures 30, Rand 30 and JSON 21. This bounded
Futures gap follows directly from the confirmed SinkMapErr one-use contract.
No pre-existing dirty work or adjacent owner edits are included. All earlier
checkpoints, including the source-justified four-fresh-adapter fixture from
9abbf2f, remain separately preserved and unchanged.

The existing native rust-kotlin CLI translated original futures-util 0.3.31
sink/err_into.rs, using owning gradle.properties and original source root,
into build/rust-kotlin-err-into-once/ErrIntoReview.kt. It emits construction
through SinkExt.sinkMapErr(sink, Into::into), matching original source. Pin,
associated-type/Into/Result and conditional-trait lowering remain unresolved;
scratch is not installed or claimed runnable. This is a manual source-faithful
adaptation using the existing canonical owner, not a generator fix.

## Source contract and repair

Original err_into.rs stores SinkMapErr, constructs it through sink_map_err
with Into::into, delegates sink operations and reaches the original sink
through delegated accessors. Original map_err.rs:25-26 takes its mapper before
invocation; a second error panics with "polled MapErr after completion".
Pending/success leave the mapper available; mapper panic still consumes it.
Later Pending/success can still delegate after consumption; another error
checks the consumed slot only after calling the underlying sink.

Kotlin duplicated mapping and retained a reusable callback inside SinkErrInto,
bypassing canonical SinkMapErr. It now stores one SinkMapErr owner and forwards
readiness/send/flush/close directly to it. getRef reaches the original sink.
The existing Kotlin display is preserved using that original sink; Rust Debug
text parity is not claimed. Public constructor, factory and extension callback
types remain unchanged. No duplicate function slot or Option owner is added.

## Independent regressions

Nine Rust tests execute actual unchanged SinkExt::sink_err_into::<Converted>().
A test SourceError implements a real From<SourceError> for a distinct Converted
error type, recording the conversion call. Fixtures implement only Sink and
that conversion; they do not call sink_map_err themselves or implement either
adapter. The actual upstream SinkErrInto -> SinkMapErr path is exercised.

The first four tests cover all sixteen first/second operation pairs, ensuring
one conversion and exact completion panic text. Further cases cover nullable
error input/output, conversion panic, Pending/success preserving conversion,
success/Pending after consumption, and get_ref reaching original backing state.
There are twenty-one scenarios across nine Rust test functions.

Nine matching Kotlin tests use the existing explicit conversion callback API.
The getter case additionally checks Kotlin object identity and existing display
as binding-specific properties, not Rust ownership/Debug equivalence. The
original Kotlin runs nine cases with seven failures; Pending/success and getter
controls pass. Each failing matrix stops on its first violating pair; all
sixteen pairs run in the final green suite. The entire new Kotlin test file is
byte-identical between red and final. Existing SinkTest, SinkCombinatorsTest
(including every expected value and the earlier fixture correction) and the
canonical MapErr.kt are byte-identical to 9abbf2f.

Error/panic probes observe actual upstream adapter behavior; they do not imply
a general failed-sink recovery guarantee. No existing assertion is relaxed or
replaced. The earlier fresh-adapter fixture remains justified by the original
FnOnce semantics; this step makes no further setup changes to it.

## Limits and evidence

Kotlin still exposes an explicit conversion callback, while Rust's API selects
Into through the type system. This repair fixes canonical delegation/one-use
behavior but does not claim complete Into/From trait or static FnOnce/move
representation. Conditional Stream/FusedStream, delegated accessor completeness,
Debug, borrow/Pin/Unpin/Drop/Send/Sync and other ownership/platform gaps remain.
Prior shared takeValue, With distinct-error conversion and Fanout clone/send
limitations are unchanged. Public signatures do not change, so no caller
migration is introduced. No downstream builds, Swift/other-platform or fresh
publication validation is claimed.

Raw CLI/red/oracle/Gradle logs and scratch output remain in
build/rust-kotlin-err-into-once; receipt.json records commands, final hashes,
timestamps and original failures. Test-only dependencies resolve actual
futures-util/core/sink/task 0.3.31 offline. Cargo.lock remains ignored under
existing repository policy, with its hash recorded. No production pin,
repository/configuration, UI, remote CI, push, PR, tag, release, publication or
adjacent owner changed.

## Final-source local checks

All nine actual Rust tests pass (twenty-one scenarios), using the real
SinkErrInto/Into/From path. All 658 JVM and 658 macOS ARM tests pass, including
the nine new cases and unchanged existing callers/fixture. Detekt has zero
findings. The combined Gradle command exits 1 only for ktlint: 581 common-main
and 31 common-test findings. All 612 locations map to unchanged 9abbf2f baseline
lines; no authored line has a diagnostic. Final receipt hashes are verified
after the local checkpoint.
