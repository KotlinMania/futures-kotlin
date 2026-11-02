# SkipWhile predicate future and state parity

## Selection and provenance

Futures has 30 graph consumers, alongside Rand 30 (Tokio 33, Serde 56).
This bounded material omission uses canonical Future/Poll/Yield without an
architecture redesign. The repository started clean at 5a79b4a; prior WIP
build/platform checkpoint e2a8c62 remains separately attributed and is not fully
revalidated here. Adjacent owners' dirty work is untouched.

The existing native rust-kotlin CLI successfully translated the original
`tmp/futures-util/src/stream/stream/skip_while.rs` from futures-util 0.3.31 with
this repository's gradle.properties and original source root. Scratch output
build/rust-kotlin-skip-while/SkipWhileReview.kt shows the future/item fields
and transitions, but unresolved imports, associated types, projection/Pin and
loop-expression lowering prevent a runnable-output claim. The repair is manual
source-faithful adaptation informed by that output and exact original source,
not a generator repair. Existing comments remain unchanged.

## Exact state transitions

Upstream stores the predicate future before the item, then polls that future
before reading more input. Pending retains both fields. Ready takes the item
and clears the future: true discards the item and repeats; false sets
 done_skipping and yields the retained item. Subsequent polls directly delegate
to the input without further predicate calls. Input EOF alone does not set
 done_skipping, so a non-fused input resuming after EOF still runs the predicate.
Termination requires no pending item and terminated input. Before skipping ends,
the upper hint adds the pending item with checked addition and the lower bound
is zero; afterwards both bounds delegate to the input.

Kotlin had only a synchronous predicate and incorrectly disabled skipping on
input EOF. Its callback now returns Future<Boolean>, and its existing
Yield.Value tag retains a present null without inventing another Option owner.
Seven Kotlin regressions and seven actual upstream Rust cases cover pending
true/false nullable items, no read-ahead, direct delegation, hints, termination,
creation versus polling failure, Pending/End, and non-fused EOF resumption.
The latter uses a Stream-only producer, not a dishonest FusedStream fixture.
The old API rejects five future-valued callbacks at compilation: API-red,
not seven executed runtime failures. No assertion or warning rule was relaxed.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-skip-while/cargo-target cargo test --manifest-path verification/skip-while-oracle/Cargo.toml --offline --locked
```

## Oracle fixture correction

The first new oracle run failed three cases because adapting the fixture had
accidentally renamed its Iterator::filter count to Iterator::skip_while. That
producer count was restored without changing assertions or upstream source;
all seven cases then passed. The initial failure log is preserved under
build/rust-kotlin-skip-while/initial-oracle-fixture-failure.log. The prior
TakeWhile fixture had the same rename error (Iterator::take_while); it was
corrected and all seven cases passed again, separately checkpointed in 44f4f98.
No production Kotlin changed in that correction. The TakeWhile rerun is logged
under build/rust-kotlin-skip-while/take-while-fixture-recheck.log.

## Exact-API caller impact

The read-only workspace review included hidden/ignored Kotlin source, excluding
build/tmp/tools/node_modules and tool-cache directories. It found 345 files with
futures namespace references in eight repositories: 306 here and 39 external
(async-io 2, allocative 1, async-stream 5, tokio-test 7, opentelemetry 2,
async-channel 2, tokio-stream 20). No external exact SkipWhile/skipWhile import,
core futures wildcard import or fully qualified SkipWhile/skipWhile candidate
was found. The only same-name external matches are Tokio StreamExt.skipWhile
and its own io.github.kotlinmania.tokiostream.streamext.SkipWhile, a distinct
API/owner. No external source was changed or downstream build launched.

The sole existing local exact call, skipWhileAndEnumerate in
StreamCombinatorsTest, now returns ready(Boolean); its result assertions are
unchanged. Seven new calls exercise this API; trySkipWhile is a distinct API.
Text-level impact evidence is not semantic resolution or downstream source/
binary compatibility proof. External callers may require adaptation.

## Remaining limits

Rust borrowed input, ownership/move/pin/Drop, conditional FusedStream bounds,
concrete future generic relationships and Send/Sync/cross-thread behavior are
not fully represented. Existing SkipWhile<T> and getRef/intoInner receiver-reuse
and accessor limitations remain. Checked addition uses the existing Kotlin Int
count domain, not full usize. Debug/Sink forwarding is outside this repair.
No synchronous compatibility shortcut, duplicate canonical type or synthetic
Fuse was introduced. No pins, repositories, runtime/build/publication settings
or adjacent owners changed. Swift, other platforms and fresh publication
metadata remain unverified. Earlier Native writer failure evidence is retained.

## Final-source validation

All 544 JVM and 544 macOS ARM tests pass, including seven new cases and the
adapted existing caller. Seven actual upstream SkipWhile cases and all seven
corrected TakeWhile cases pass. Detekt has zero findings. The combined Gradle
command fails only at ktlint: 645 common-main and 31 common-test findings.
All 676 diagnostics map to unchanged baseline 44f4f98 lines; no authored line
has a finding. Final source-hash/static-comparison receipts and translation,
API-red, oracle and Kotlin check logs are retained in build/rust-kotlin-skip-while.
Prior checkpoints remain separate; this repair includes no pre-existing dirty
work. The fixture correction is explicitly separate in commit 44f4f98.
