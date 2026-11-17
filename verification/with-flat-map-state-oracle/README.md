# WithFlatMap drain-state parity

## Selection and attribution

The repository began clean at c614f77. The workspace dependency graph has 56
reverse-dependent repositories for Serde, 30 for Futures, 30 for Rand and 21 for
JSON. This concrete Futures sink expansion gap offers behavioral coverage
without public API redesign or touching adjacent owners. Rand 386aad6 and JSON
f85acc0 are preserved. No previous dirty work is included in this step.
Likewise, c614f77 itself began clean at 09c4126 and included only its Feed repair,
new tests and verification files; it did not include pre-existing dirty work.
The older e2a8c62 checkpoint remains separately attributed.

The existing native rust-kotlin CLI translated original futures-util 0.3.31
sink/with_flat_map.rs into build/rust-kotlin-with-flat-map/WithFlatMapReview.kt,
using this repository's gradle.properties and original source root. Its output
shows the distinct buffered and fresh-item paths, direct pollReady delegation
and startSend assertion. Associated-type, conditional-trait, Pin, Result and
Option lowerings remain unresolved; scratch output is not claimed runnable.
This is a manual exact-source adaptation informed by generated output and
independent upstream comparison, not a generator fix. No root-file guard was
bypassed or original Rust rewritten for translation.

## Exact source contract

Original with_flat_map.rs:51-71 first drains an existing buffer, then pulls the
expansion stream. For a buffered item, readiness Pending or Err retains it;
success takes it before start_send. A freshly yielded item, however, is buffered
only on readiness Pending. Readiness Err drops that local item; start_send Err
also consumes it. Stream Pending or Err returns immediately. EOF clears the
active stream. The adapter polls inner readiness only when an item needs sending,
not when idle or after EOF. poll_ready (:106-108) delegates directly to draining.
start_send (:110-115) asserts no active stream before calling the transform.
Flush and close (:118-126) drain first and delegate only after success.

Kotlin conflated nullable items with an empty buffer, stored fresh items before
readiness (retaining them on readiness error), added an idle readiness poll,
and let startSend overwrite an active expansion stream. The bounded repair uses
the existing canonical Try.Ok presence carrier and follows the original two
paths and assertion. It adds no duplicate Option owner or alternate algorithm.

## Actual Rust and Kotlin regressions

The twelve Rust cases invoke unchanged upstream WithFlatMap through SinkExt.
Fixtures implement only a scripted Sink/Stream and real Context/Waker, never
WithFlatMap itself. Matching Kotlin cases cover idle readiness, non-null drain,
nullable repeated Pending, distinct fresh/buffered readiness error handling,
distinct fresh/buffered send errors, stream error, stream Pending before flush,
buffered close with Pending, overlapping startSend, and a throwing transform.
They compare exact poll/send/stream/flush/close traces and relevant wake counts.
The original Kotlin fails ten cases; fresh-send-error and stream-error controls
pass. No existing assertion or test is changed. Cases that repoll after an
error or caught panic observe this implementation's state; they do not promise
generic failed-sink recovery or panic safety.

Commands, timestamps, final-source hashes, original red failures, first/final
check receipts and translator invocation/hash are recorded in receipt.json.
Raw logs and scratch output remain in build/rust-kotlin-with-flat-map. The first
full check found formatting diagnostics in the changed function. Only its
when-branch braces and whitespace were corrected; the test file is byte-identical
to the red run. Final runtime and static checks were rerun after that correction.

## API and limits

Public signatures are unchanged. Workspace source search for exact Futures
sink.withFlatMap / sink.WithFlatMap imports found only the unchanged SinkTest
and new suite. No caller adaptation or binary compatibility claim is made.
The existing SinkTest assertions are byte-identical to the starting revision.

Rust conditional Stream/FusedStream forwarding, delegated accessors, detailed
Debug output, Pin/Unpin and trait relationships remain outside this repair.
Kotlin references/GC do not reproduce Rust ownership, mutable borrowing,
Drop/destruction, Send/Sync or panic behavior equivalence. Prior usize, threading,
platform and downstream limitations remain. No Swift/other-platform, downstream
build, fresh publication metadata or complete-library parity is claimed.
The test-only package resolves futures-util/core/sink/task 0.3.31 offline; its
generated Cargo.lock remains ignored under existing policy, with its hash
recorded. No production pins, repositories or configuration changed. No remote
CI, push, PR, release, publication, other owner or UI session was involved.

## Final-source validation

All 12 actual Rust oracle cases pass. All 612 JVM and 612 macOS ARM tests pass,
including the new twelve-case suite and unchanged existing SinkTest.withFlatMap.
Detekt reports zero findings. The final combined Gradle invocation exits 1 only
for ktlint: 585 common-main and 31 common-test findings. All 616 locations map
to unchanged c614f77 baseline lines; no authored line has a diagnostic.
Formatting the modified function also resolves some pre-existing diagnostics.
The first full run had 628 total findings and seven locations attributed to
authored lines; those receipts remain preserved, not presented as final gates.
