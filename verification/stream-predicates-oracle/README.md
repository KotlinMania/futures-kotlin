# Stream all/any asynchronous predicate parity

## Selection and independent source evidence

Graph counts: Serde 56 consumers, Tokio 33, Futures/Rand 30. Their common
source/test trees were clean at selection. Futures offered two material missing
predicate-future state machines using canonical Future/Poll/Yield without broader
architecture or another owner's files. This tree started clean at 345af11.
Existing build/platform work remains separately checkpointed in e2a8c62, not
newly authored or fully platform-validated by this step. Adjacent Tokio work
is untouched.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/stream/all.rs` and `any.rs` independently, with this
repo's gradle.properties and source root tmp/futures-util/src, into
build/rust-kotlin-stream-predicates. Direct unchanged futures-util 0.3.31 source
comparison confirms each predicate Future<bool>, completion identity and
short-circuit condition. Generated types/imports are not independently
compilable. This is manual source-faithful adaptation, not a generator repair,
coroutine migration or assumption that the methods share identical results.

## Exact algorithms

Both source futures first poll a stored predicate future, clearing it only on
Ready. All terminates false on a false predicate and otherwise reads another
item; input EOF terminates true. Any terminates true on a true predicate and
otherwise reads another item; EOF terminates false. Input Pending does not
create a predicate; predicate Pending retains that same future and prevents
further input polling. Fused termination is done && future.is_none(). Further
polls after completion panic with the source All/Any message.

Kotlin accepted synchronous (T)->Boolean predicates. The repair changes the
constructors/factories/extensions to (T)->Future<Boolean> and stores/drives that
canonical future. Nullable stream items remain Yield.Value(null), separate from
End. Creation panic does not fabricate done; stored-future panic retains its
pending state. The existing forAll alias delegates to the corrected All type;
no new synchronous compatibility overload or helper owner was introduced.
Existing StreamAll/StreamAny naming is unchanged, not claimed to complete the
source concrete/associated-type API.

Eight new Kotlin cases cover Pending/short-circuit/no over-read for each method,
nullable ordered items to EOF, input Pending then empty identity for each,
predicate creation panic in All and returned-future panic in Any. The old API
fails compilation at five future-valued predicate expressions: API-red evidence,
not eight executed runtime failures. Existing assertions and warning policy were
not relaxed. Eight actual Rust oracle cases execute unchanged All/Any with real
Context/Waker, manually released predicate futures and caught source panics.
Fixtures model only input/future producers, never either predicate algorithm.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-stream-predicates/cargo-target cargo test --manifest-path verification/stream-predicates-oracle/Cargo.toml --offline --locked
```

## Caller scope and remaining limits

Eight known local stream call sites are adapted without changing assertions:
six function-reference calls in StreamTest use their two local isEven factories
returning ready(Boolean), and StreamCombinatorsTest's forAll/any callbacks also
return ready(Boolean). Unrelated collection all/any and Tokio's distinct APIs
are untouched. A workspace text search found no explicit core StreamAll,
StreamAny, all, any, forAll or wildcard import candidates; that is not semantic
resolution, downstream source compilation or binary compatibility proof.
External callbacks may require the same source adaptation and remain unbuilt.
Rust FnMut/move/borrow/pin/Send/Sync, generic concrete future/type relationships,
deterministic Drop/cross-thread behavior, Debug and accessor gaps remain limits.
No separate Peekable Option API or broader representation migration is claimed.
Prior Native failure receipts and corrected writer representation remain intact.
No production pins, runtime/build/publication configuration, repositories or
unrelated owners changed. Other targets, Swift, remote CI and publication
metadata remain outside local validation scope.

## Final-source local checks

The actual final tree passes all 524 JVM tests and 524 macOS ARM tests, including
eight added cases and eight adapted local stream calls with unchanged assertions.
All eight actual Rust oracle cases pass; Detekt has zero findings. Combined
Gradle validation fails only at ktlint: 655 common-main and 31 common-test
findings. All 686 diagnostic locations map to unchanged baseline 345af11
lines; no changed source/caller line or added test has a finding. Production
pins/configuration are unchanged. Source hashes, baseline-line comparison and
translator/API-red/oracle/final-check logs remain untracked under
build/rust-kotlin-stream-predicates.
