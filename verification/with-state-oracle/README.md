# With fallible transformation and reservation-order parity

The repository started clean at 90730e5. Graph reverse-dependent counts were
Serde 56, Futures 30, Rand 30 and JSON 21. This material Futures gap follows a
concrete original-source/generated-output comparison; no pre-existing dirty
work or adjacent owner's work is included. Prior 90730e5/c614f77 repairs and
e2a8c62 dirty-work checkpoint remain separately attributed. Rand 386aad6 and JSON
f85acc0 are preserved.

## Original source and repair

A bounded native rust-kotlin CLI invocation translated original futures-util
0.3.31 sink/with.rs into build/rust-kotlin-with-state/WithReview.kt, using owning
gradle.properties and the original source root. The generated output shows
Future<Result<Item>>, the direct startSend after transformation, and the active
state assertion. It still has unresolved associated-type/From/Pin/conditional
trait and error lowering. Scratch is not installed or claimed runnable; this
is a manual source-faithful adaptation using canonical Try, not a generator
repair. No source guard was bypassed.

Actual with.rs:87-96 polls a fallible future. Pending or future Err leaves state
present. A successful future clears state before start_send, without polling
inner readiness again: that reservation was obtained before the input was
accepted. Outer poll_ready (:109-112) drains then polls readiness for the next
input. Flush/close (:123-132) drain then delegate. start_send (:115-120) checks
that no future is active before invoking the transform.

Kotlin incorrectly exposed Future<Item>, hiding fallible transform results,
and cleared the future before rechecking inner readiness. If that extra check
returned Pending, the already transformed item was discarded. It also allowed
overwriting an active future. The callback now returns Future<Try<Item, E>>;
completePending preserves future Err and directly starts sending successful
values after clearing state. startSend asserts empty state first. No cached
replacement future, lossy compatibility overload or substituted algorithm is
added. Null item and null error remain distinct through canonical Try.

## Caller impact

Constructor, companion new and extension with callback types change. The
existing Kotlin API still uses a shared sink/transform E (the identity error
conversion subset of Rust); Rust's distinct E: From<Si::Error> relationship
remains unrepresented, with no fabricated conversion.

Read-only workspace Kotlin/Java/Swift source review of exact imports, fully
qualified names, wildcard sink imports and WithKt found the API only in local
test files. Three existing call sites in two files require adaptation:
SinkTest.withAsMap, SinkTest.withPropagatesPollReady and
SinkCombinatorsTest.testWithSink. Each wraps the original successful value in
Try.Ok; all original assertions, including channel backpressure checks, are
unchanged. The new suite also calls the corrected API. Unrelated Kotlin-source
runWithKtorService / testWrongScriptWithKtSource substring matches from the
initial wider search are not this API. No other owner's files changed.

Consumers must adapt successful callbacks and rebuild. Generic callback erasure
does not establish binary compatibility: old compiled callbacks return the
wrong payload shape. No downstream build campaign or compatibility claim is
made.

## Regression history

Twelve Rust cases run actual unchanged upstream With through SinkExt, with
scripted Sink and transformation Future plus real Context/Waker; no copied
With implementation. They cover idle readiness, reservation/send order, flush
without another reservation, nullable item, repeated Future Pending, overlapping
input, consumed future on send error, retained future on transform error, null
transform error before close, next-reservation error, flush error and Pending
close without repolling a completed future.

First, seven Kotlin state cases used the original unfallible callback API:
six fail at runtime; the idle readiness control passes. The same seven assertions
are preserved when their producers are adapted to return Try.Ok. Five error/
terminal cases were added to make twelve. That faithful typed callback fails
compilation against the original API (expected Future<Int?>), so there is no
claim that all twelve ran red. The first post-repair full check also failed
test compilation because a third old callback was initially missed; its
wrapper was then corrected. No runtime pass is attributed to that failed run.
All receipts and original fixture snapshots remain preserved in build.

receipt.json records the final commands, test timestamps, source hashes and
original failure receipts. Raw translator/red/compile/full-check logs remain
under build/rust-kotlin-with-state. The test-only package resolves futures-util,
core, sink and task 0.3.31 offline. Cargo.lock stays ignored by existing policy,
with its hash recorded; no production pins or repositories changed.

## Remaining limits

Conditional Stream forwarding, Clone/accessor/Debug coverage and distinct
error conversion remain outside this repair. Kotlin reference ownership/GC
does not reproduce Rust borrow lifetimes, move/Pin/Unpin/Drop, Send/Sync or
deterministic destruction. Error/panic or post-completion probes observe the
tested implementation; they do not establish general failed-sink recovery.
No complete-library, downstream, Swift/other-platform or publication parity is
claimed. Remote CI/push/PR/release/publication/configuration holds are preserved.

## Final-source local checks

All twelve actual upstream Rust cases pass. All 624 JVM and 624 macOS ARM tests
pass, including twelve new cases and the three adapted existing caller sites.
Detekt reports zero findings. The combined Gradle command exits 1 only at
ktlint: 581 common-main plus 31 common-test findings. All 612 diagnostic
locations map to unchanged 90730e5 baseline lines; no authored line has a finding.
Modified-function formatting also clears some pre-existing diagnostics.
Final receipt hashes are verified after the local checkpoint.
