# Sink Unfold tagged-state transfer parity

The repository began clean at a5dfcde. This bounded Futures gap was selected
after graph comparison (Serde 56 reverse dependents, Futures 30, Rand 30, JSON
21) and direct source review. No pre-existing dirty work or adjacent owner edits
are included. Earlier checkpoints, the With caller audit and its identity-only
error-conversion limitation remain separately preserved.

The existing native rust-kotlin CLI translated original futures-util 0.3.31
sink/unfold.rs with the owning gradle.properties and original source root into
build/rust-kotlin-unfold-state/UnfoldReview.kt. Generated code calls takeValue
and tests null, exposing the representation mismatch. Associated-type/Pin/state
lowering remains incomplete; scratch is not installed or claimed runnable.
This repair adapts original source manually using the existing canonical
UnfoldState tags; it is not a generator fix or a substitute state type.

## Exact source behavior

Original sink/unfold.rs:58-65 calls UnfoldState.take_value before invoking the
callback. Original unfold_state.rs:30-37 replaces Value with Empty and returns
Some(value), including a value that is itself None. Consequently a nullable
initial/returned state is valid, and callback panic leaves no value available
for a second invocation. On a successful callback, the returned future becomes
the new state. Pending retains that future; Ok restores Value; Err sets Empty
(sink/unfold.rs:68-83). Readiness and close delegate to flush.

The Kotlin sink used nullable state.takeValue() and rejected valid null states.
That shared helper is a non-consuming nullable lookup, so callback failure also
left the old Value available. The sink now checks the Value tag, reads its
payload (which may be null), empties its own slot, then invokes the callback.
This directly implements the caller-owned state replacement required by Rust.
No shared helper, signature, carrier type or compatibility overload changes.

The public shared UnfoldState.takeValue()/valueOrNull() helpers remain nullable,
and takeValue still does not consume state. This step does not repair or claim
parity for that API. No remaining commonMain call to takeValue was found after
the sink repair. A broader shared representation/API redesign is outside scope.

## Regressions and validation

Eight Rust cases execute actual unchanged upstream sink::unfold and state
implementation with scripted futures and real Context/Waker. They cover null
initial state, null returned state, callback panic consuming state, repeated
Pending, null error emptying state, non-null accumulation, overlapping input
preserving the active future, and close draining without repolling a completed
future. The fixture never implements Unfold or take_value.

The matching Kotlin suite runs eight cases against the original code: the two
nullable-state cases and callback-panic case fail; five controls pass. The new
suite is byte-identical between red and final runs, and existing SinkTest is
unchanged. Panic/error reuse probes observe the actual implementation's state;
they do not establish a general failed-sink recovery or panic-safety guarantee.

All eight actual Rust cases pass offline. All 632 JVM and 632 macOS ARM tests
pass, including the eight new cases and existing sink unfold coverage. Detekt
reports zero findings. The combined Gradle command exits 1 only for ktlint:
581 common-main plus 31 common-test findings. All 612 diagnostic locations map
to unchanged a5dfcde baseline lines; no authored line has a finding.

receipt.json records exact commands, final hashes, test timestamps and original
red failures. Raw logs and scratch output remain in build/rust-kotlin-unfold-state.
The generated test-only Cargo.lock remains ignored under existing policy,
with its hash recorded; resolved futures-util/core/sink/task versions are
0.3.31. No production pin or repository/configuration changes were made.

## Remaining limits

Rust ownership, mutable borrow, Pin/Unpin, deterministic Drop and Send/Sync
relationships are not reproduced by Kotlin references/GC. Shared helper APIs,
stream unfolds, Debug/comment coverage and broader traits are not certified
by this sink repair. The pre-existing Kotlin panic message is retained; exact
cross-language panic text/type equivalence is not claimed. Public signatures
are unchanged, so no caller migration is introduced. No downstream build,
Swift/other-target or fresh publication validation is claimed. No remote CI,
push, PR, tag, release, publication, UI or other owner was touched.
