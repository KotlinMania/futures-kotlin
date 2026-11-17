# Stream filter asynchronous predicate parity

Futures has 30 dependency-graph consumers (Serde 56, Tokio 33, Rand 30).
The common source/test trees considered were clean at selection. This Futures
step started clean at 7d6c04c. Prior build/platform work remains separately
checkpointed in e2a8c62; adjacent Tokio and other owners' work is untouched.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/stream/filter.rs` (futures-util 0.3.31), using this
repository's gradle.properties and source root tmp/futures-util/src, into
build/rust-kotlin-stream-filter/FilterReview.kt. Direct original-source comparison
established the pending-future/item state machine and checked upper hint. The
scratch translation has unresolved associated types/imports and is not claimed
runnable. This repair is a manual source-faithful adaptation informed by that
diff, not a generator fix.

Upstream stores the predicate future before the item. It polls that future
before reading more input, retains both on Pending, clears the future on Ready,
yields the stored item on true and discards it on false. Creation panic consumes
the input without caching it; a polling panic retains both pending fields.
Termination requires no pending future and terminated input. The upper hint
includes the pending item through checked addition. No synthetic Fuse is added.
Kotlin now uses (T)->Future<Boolean> and retains the existing Yield.Value tag,
which distinguishes a present null item from absence without a new Option owner.

Six Kotlin cases cover retained/rejected pending null items, no read-ahead,
upper hints, termination while input is exhausted, creation/polling failures and
input Pending then End. The old API rejects four future-valued predicates at
compilation: API-red, not executed runtime failures. Six actual Rust cases run
the unchanged upstream Filter with actual Context/Waker and released predicate
futures, including caught panic behavior. Fixtures model only producers, not
Filter's algorithm. No existing assertion or warning policy was relaxed.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-stream-filter/cargo-target cargo test --manifest-path verification/stream-filter-oracle/Cargo.toml --offline --locked
```

## Exact-API caller review

A read-only workspace search included hidden/ignored Kotlin source, excluding
build/tmp/tools/node_modules and tool-cache directories. It found 343 files
referencing the futures namespace in eight repositories: 304 here and 39 outside
(async-io 2, allocative 1, async-stream 5, tokio-test 7, opentelemetry 2,
async-channel 2, tokio-stream 20). No external exact Filter/filter import,
futures wildcard import or fully qualified Filter/filter call was found.
The only external same-name candidates are Tokio StreamExt.filter and its own
io.github.kotlinmania.tokiostream.streamext.Filter: a distinct API/owner.
No external files were edited or downstream builds launched.

Both existing local exact-API calls were adapted: mapAndFilter in
StreamCombinatorsTest and flatMap in StreamTest now return ready(Boolean).
Their result assertions remain identical. Six new calls exercise the new API.
This text-level review is not semantic resolution or downstream source/binary
compatibility proof. External callers may need the callback adaptation.

## Remaining limits

Rust borrowed predicate input, move/pin/Drop guarantees, conditional FusedStream
trait implementation, concrete generic future relationships, Send/Sync and
cross-thread behavior are not represented completely. Existing Kotlin Filter<T>
shape and raw getRef/intoInner receiver-reuse limitations remain. Checked hint
addition follows the algorithm in the existing Int count domain, not full usize.
Debug/Sink forwarding and other source API gaps are not claimed complete.
No dependency pins, repositories or build/runtime/publication settings changed.
Swift, other platforms and downstream publication metadata are unverified.
Initial Native writer-failure evidence from earlier work remains intact.

## Final-source local validation

All 530 JVM and 530 macOS ARM tests pass, including six new regressions and
both adapted local callers with unchanged assertions. All six actual Rust
oracle cases pass. Detekt has zero findings. The combined Gradle command exits
unsuccessfully only for ktlint: 651 common-main plus 31 common-test findings,
all mapped to unchanged baseline 7d6c04c lines. No authored line has a finding.
Initial formatting diagnostics were corrected and all local Kotlin checks rerun
on the actual final source. Final hash/static-comparison receipts and CLI,
API-red, oracle and Gradle logs are retained under build/rust-kotlin-stream-filter.

Read-only status confirmed adjacent tokio-stream contains substantial dirty
build/template/platform work and its own Filter API; it was not staged or edited.
This step adds no prior dirty-work checkpoint because this repository started
clean. Earlier e2a8c62 remains separately attributed, not revalidated in full.
