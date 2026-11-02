# TakeWhile asynchronous predicate and completion parity

## Selection and provenance

Futures has 30 graph consumers, alongside Rand 30 (Tokio 33, Serde 56).
The repository started clean at 34b6f89. This is a material missing future/item
state machine and EOF behavior using canonical Future/Poll/Yield; no architecture
redesign or another owner's files is needed. Prior build/platform checkpoint
e2a8c62 remains separate and is not fully revalidated by this step.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/stream/take_while.rs` from futures-util 0.3.31 using
this repository's gradle.properties/source root into
build/rust-kotlin-take-while/TakeWhileReview.kt. The scratch output exposes the
pending fields and source transitions but unresolved imports, associated types,
projection/Pin and loop-expression lowering prevent a runnable-output claim.
This is manual exact-source adaptation informed by a translator diff, not a
transpiler repair. Existing source comments are retained.

## Source contract

Upstream creates and stores the predicate future before caching its item. On
Pending it retains both and does not read ahead. On Ready it takes the item,
clears the future and yields the item if true. False sets done_taking and
returns None, including on subsequent polls without reading the input. Input
EOF alone does not set done_taking. Termination is done_taking OR no pending
item AND terminated input. The upper hint adds the pending item with checked
addition, unless done_taking when both bounds are zero.

Kotlin previously used (T)->Boolean and latched completion on input EOF.
Constructors/factories/extensions now accept (T)->Future<Boolean> and preserve
the source state transitions. An existing Yield.Value tag retains a present
null without a duplicate Option owner. No synchronous compatibility helper or
synthetic Fuse was introduced.

Seven Kotlin regressions and seven actual upstream Rust oracle cases cover
pending nullable items, hints/no read-ahead, false-predicate latching,
termination with exhausted input, predicate creation versus polling failure,
input Pending/End, and non-fused input EOF followed by another item. The latter
uses a Stream-only producer, not a dishonest FusedStream fixture. The old Kotlin
API fails compilation at five future-valued predicates: this is API-red evidence,
not seven observed runtime failures. No existing assertion or warning policy
was relaxed. Rust tests execute the unchanged upstream TakeWhile, with real
Context/Waker and predicate futures; fixtures model producers only.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-take-while/cargo-target cargo test --manifest-path verification/take-while-oracle/Cargo.toml --offline --locked
```

## Limits

The callback signature is a source/binary compatibility change; downstream
compilation is unverified. Rust borrowed input, move/pin/Drop, conditional
FusedStream bounds, concrete generic future relationships, Send/Sync and
cross-thread behavior are not fully represented. Existing TakeWhile<T> and raw
getRef/intoInner receiver-reuse/accessor limitations remain. Checked hint
arithmetic follows the source algorithm in Kotlin's existing Int domain, not
full usize. Debug/Sink forwarding and other source gaps remain outside scope.
Swift, other targets and fresh publication metadata are unverified. No pins,
build/runtime/publication settings, repositories or adjacent owners changed.
Prior Native writer failure evidence is retained. No pre-existing dirty work
was included; earlier checkpoints remain separately attributed.

## Exact-API caller review

Read-only workspace inspection included hidden/ignored Kotlin source and
excluded build/tmp/tools/node_modules and tool-cache directories. There are
344 files referencing the futures namespace in eight repositories: 305 here,
39 external (async-io 2, allocative 1, async-stream 5, tokio-test 7,
opentelemetry 2, async-channel 2, tokio-stream 20). No external explicit
TakeWhile/takeWhile import, core futures wildcard import or fully qualified
TakeWhile/takeWhile candidate was found. The only external same-name matches
are Tokio StreamExt.takeWhile and its imported
io.github.kotlinmania.tokiostream.streamext.TakeWhile, a different API/owner.
Adjacent Tokio dirty build/platform work is preserved without staging/editing.

The only existing local exact-API call is skipAndTakeWhile in
StreamCombinatorsTest: its predicate now returns ready(Boolean), with the
asserted result list [3, 4] unchanged. Seven new calls exercise this exact API;
TryStream.tryTakeWhile is a distinct unchanged method. No external files or
assertions were edited. This is source-text impact evidence, not semantic
resolution, a downstream build campaign or proof of compatibility. The
structured search receipt remains under build/rust-kotlin-take-while.

## Final-source validation

All 537 JVM tests and 537 macOS ARM tests pass, including seven new cases and
the adapted existing caller. All seven actual Rust oracle cases pass. Detekt
has zero findings. The combined local Gradle command fails only at ktlint:
648 common-main plus 31 common-test findings. Every diagnostic maps to an
unchanged baseline 34b6f89 line; none is on an authored line. After correcting
test formatting, Kotlin runtime/static checks were rerun on the actual final
source. Final source hashes and baseline-line comparison receipts remain under
build/rust-kotlin-take-while, with translator/API-red/oracle/Gradle logs.

## Producer-fixture correction discovered during SkipWhile review

The Rust input producer's size_hint accidentally used Iterator::take_while
when adapting the previous fixture. It now counts all queued value events with
Iterator::filter, matching the Kotlin producer. No assertion or adapter code
changed. All seven actual upstream TakeWhile cases pass again with the corrected
fixture. The rerun log is build/rust-kotlin-skip-while/take-while-fixture-recheck.log.
Earlier Kotlin runtime results are unchanged; this correction does not change
production Kotlin. Historical source-hash receipts refer to the original Rust
fixture; this newer oracle verification supersedes that fixture receipt only.

## Closing the after-Pending producer-hint coverage gap

A separate test-only addition after 91276c9 now scripts Value(1), Pending,
Value(2), End. It checks the producer hint (2, Some(2)) initially, then
(1, Some(1)) before and after the Pending poll, and (0, Some(0)) after the
second value. The actual Rust TakeWhile upper hints are checked through the
same sequence, with lower bound zero and exactly four input polls. The
corresponding Kotlin test checks the same producer/adapter hints, yielded
values, Pending and final End. All pre-existing assertions are unchanged.

The original seven successful cases did not cover a value after Pending at
a hint query. An isolated negative control under
build/rust-kotlin-take-while-producer-hint restores only the former producer
Iterator::take_while call in a copy of the current Rust test source. It links
the already-built, unchanged futures-util library. The original seven tests
still pass; the newly added case fails immediately: actual (1, Some(1)) versus
expected (2, Some(2)), exit 101. This is deliberately broken fixture evidence,
not the real oracle run or a modified upstream algorithm. Its source, compile
command/receipt and failure log are retained separately. The corrected real
oracle runs all eight cases successfully with the same assertions.

Original seven-case logs remain unchanged in build/rust-kotlin-take-while and
the corrected seven-case rerun remains in build/rust-kotlin-skip-while. New
oracle, negative-control and final Kotlin/static logs are stored under
build/rust-kotlin-take-while-producer-hint. No production source, dependency,
API signature or existing assertion changed in this test-only step.

Final checks for this test-only addition: all eight Rust oracle cases pass,
545 JVM tests and 545 macOS ARM tests pass, and Detekt has zero findings.
Ktlint remains blocked by 645 common-main plus 31 common-test findings, all
mapped to unchanged baseline 91276c9 lines; none is on an authored line.
The test formatting was corrected before final runtime/static checks were
rerun. Source hashes and static comparison are in the new receipt directory.
Read-only comparisons confirm the original seven Rust/Kotlin test bodies,
production TakeWhile and upstream source are unchanged; the previous corrected
seven-case execution log still matches its recorded SHA-256. This closes only
the explicit after-Pending fixture contract, not broader parity limitations.
