# TryTakeWhile retained error/item parity

## Selection and provenance

Futures has 30 dependency-graph consumers (Rand 30, Tokio 33, Serde 56). This
repository started clean at ff276ac. Independent exact source review found a
material private-state gap in nullable hints, error retention and creation/poll
panic ordering; the repair needs no public API or broad representation change.
Adjacent owners are untouched. Prior build/platform checkpoint e2a8c62 is
separate and not fully platform-validated here; all prior oracle correction
and after-Pending evidence is preserved.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/try_stream/try_take_while.rs` from futures-util
0.3.31 with this repository's gradle.properties and original source root into
build/rust-kotlin-try-take-while/TryTakeWhileReview.kt. Unresolved associated
types/imports and projection/Pin/loop lowering mean the scratch output is not
claimed independently compilable. This is manual exact-source adaptation
informed by the translator output and direct upstream review, not a generator
repair or an assumed copy of TrySkipWhile semantics.

## Exact source state

Upstream stores a new predicate future before caching its item. When that
future returns Ready, it clears the future and propagates an Err before taking
the stored item. Predicate Err therefore retains the item; later input Err or
EOF also retains it. An Ok result takes the item: true yields it, false sets
 done_taking and returns End without reading further input. Done yields End
on later calls and reports zero hint. Before done, termination requires no
pending item and terminated input, not absence of pending future. The upper
hint adds the pending-item count with checked addition.

Kotlin previously discarded the item on predicate Err, treated null pending
items as absent for hints, tested termination using the future, cached items
before a throwing predicate, and saturated upper overflow. The repair retains
the existing canonical Try.Ok<T> tag privately, preserves the exact source
state/order and uses checked addition in the existing Kotlin Int count domain.
No duplicate Option owner, synchronous helper or artificial cleanup is added.
All public signatures, existing callers and existing assertions remain
unchanged; this makes no downstream compatibility claim.

Seven Kotlin cases cover pending true/null yield followed by another item,
predicate Err retained through EOF, creation panic, future polling panic,
input Err after predicate Err, false stopping without read-ahead, and upper
hint overflow while an item is pending. The old implementation executes all
seven cases and fails six at runtime; the false-stop control passes. Logs and
XML-derived failure details are retained under build/rust-kotlin-try-take-while
as red.log and red-failures.json. No assertion or warning rule was weakened.

Seven matching cases execute actual unchanged upstream TryTakeWhile using
real Context/Waker and predicate futures. Producer fixtures do not implement
the adapter algorithm. Their size hints count all queued value/error events
using Iterator::filter. The overflow case supplies a valid loose maximum upper
bound, then queries the pending-item addition: Rust tests usize::MAX and Kotlin
Int.MAX_VALUE, each producing an unknown upper bound after checked overflow.
This validates the respective domain boundary, not full usize-width equivalence.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-try-take-while/cargo-target cargo test --manifest-path verification/try-take-while-oracle/Cargo.toml --offline --locked
```

## Remaining limits

Rust borrow/move/pin/Drop, conditional FusedStream bounds, concrete future type
relationships and Send/Sync/cross-thread behavior remain incompletely represented.
Kotlin retains the existing TryStreamTakeWhile<T,E> naming/shape and raw
getRef/intoInner receiver-reuse/accessor limits. Counts still use Int rather
than usize; the boundary tests do not remove that width difference. Debug/Sink
forwarding and other adapters are outside scope. No pins, repositories or
runtime/build/publication settings changed; no foreign dirty work is included.
Swift, other platforms, downstream builds and fresh publication metadata are
unverified. Existing Native failure and original/corrected oracle evidence
remain intact.

## Final-source validation

All 558 JVM tests and 558 macOS ARM tests pass, including the seven new cases
and unchanged StreamTryStreamTest.tryTakeWhileAfterErr caller. All seven actual
upstream Rust cases pass. Detekt has zero findings. The combined Gradle command
fails only at ktlint: 627 common-main and 31 common-test findings. All 658
locations map to unchanged baseline ff276ac lines; no authored line has a
finding. Final source hashes/static comparison and CLI/red/oracle/Gradle logs
are retained under build/rust-kotlin-try-take-while. No earlier dirty work was
included or attributed as new repair coverage. Other platform/downstream and
ownership limitations above remain explicit.
