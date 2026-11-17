# Fanout first-error polling order

The repository began clean at 7bd0e75. Graph comparison remains Serde 56 reverse
dependents, Futures 30, Rand 30 and JSON 21. This bounded Futures gap affects
readiness and flush/close side effects after an error. No pre-existing dirty
work is included. Prior checkpoints, the With caller audit and identity-only
error conversion, and the shared takeValue/ownership/platform limitations are
preserved separately.

The existing native rust-kotlin CLI translated original futures-util 0.3.31
sink/fanout.rs, with owning gradle.properties and original source root, into
build/rust-kotlin-fanout-order/FanoutReview.kt. Its output returns the first
error before the second poll, matching direct source comparison. Associated
types/Pin/Result/Clone lowerings remain incomplete; scratch output is neither
installed nor claimed runnable. This is a manual exact-source correction
informed by translator output, not a generator fix or root-guard bypass.

## Source contract and repair

Actual Fanout.poll_ready, poll_flush and poll_close apply ? to sink1's
Poll<Result> before polling sink2. Err returns immediately and the second sink
must not be polled. Pending is different: it does not short-circuit this code,
so sink2 must still be polled. A second-sink error takes priority over first-sink
Pending. Both ready succeed; otherwise the result is Pending. When both would
error, the first error wins without executing the second operation.

Kotlin previously polled both sinks and only then checked errors, executing
unwanted readiness/flush/close side effects after the first error. In each of
three methods, the existing first-error check now precedes the second poll.
No new algorithm, completion flag, signature or compatibility helper is added.
All other production code, including startSend, is unchanged.

## Regression evidence

Nine Rust tests execute actual unchanged upstream Fanout with scripted Sink
implementations and real Context/Waker, never a copied Fanout implementation.
For each operation, tests cover first Err(Some("left")) while the second would
error, and first Err(None) while the second would be Pending. They assert the
exact trace, error payload and wake count. Three further six-row matrices cover
Ready/Ready, Ready/Pending, Pending/Ready, Pending/Pending, Pending/Err and
Ready/Err. There are 24 scenarios across nine Rust test functions.

The nine matching Kotlin tests run on the original code: six first-error
tests fail on the unexpected second poll; all three control matrices pass,
including all eighteen control rows. The only post-red change to the new suite is enum constructor whitespace/line
wrapping required by ktlint; every assertion is unchanged. The original fixture
and first full-check receipts are preserved. Existing SinkTest is unchanged. Tests do not
repoll a failed sink or imply any failed-sink recovery contract.

receipt.json records exact commands, final-source hashes, timestamps and
original red failures; raw CLI/red/oracle/Gradle logs remain under
build/rust-kotlin-fanout-order. Cargo.lock is generated and ignored under
existing repository policy, with its hash recorded; test-only dependencies
resolve futures-util/core/sink/task 0.3.31 offline. No production pins,
repositories or runtime/build settings changed.

## Scope limits

Rust Item: Clone and cloning the first outgoing payload are not represented
by the existing Kotlin startSend implementation. This repair leaves that
known limitation explicit and does not certify send/cloning parity. Delegated
accessor gaps and Rust borrow/move/Pin/Unpin/Drop/Send/Sync semantics likewise
remain outside this poll-order repair. Public declarations are unchanged;
no caller migration is introduced. No downstream, Swift/other-platform or
fresh publication validation is claimed. No remote CI, push, PR, release,
publication, configuration, UI or adjacent owner was touched.

## Final-source local checks

All nine actual Rust tests pass (24 scenarios). All 641 JVM and 641 macOS ARM
tests pass, including the nine new tests and unchanged existing SinkTest.
Detekt reports zero findings. The final combined Gradle command exits 1 only
for ktlint: 581 common-main and 31 common-test findings, all mapped to unchanged
7bd0e75 baseline lines, none on authored lines. The first full check had two
new enum-signature formatting findings; its receipts are preserved. Runtime
and static checks were rerun after correcting that formatting.
