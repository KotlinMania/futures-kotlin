# Buffered concurrent progress and ordered output parity

## Selection and translator evidence

The graph records Futures 30 consumers, Tokio 33 and Itertools 11. All three
common source/test trees were clean at selection. Futures offered a confirmed
material concurrent-progress omission using an existing canonical queue,
avoiding new runtime design or work in Iris-owned files. This tree started
clean at 6b15e55. Prior build/platform changes remain separately checkpointed
in e2a8c62, not reattributed as this repair or fully validated across platforms.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/stream/buffered.rs` with this repo's
gradle.properties and source root tmp/futures-util/src into
build/rust-kotlin-buffered/BufferedReview.kt. The output retains Fuse,
FuturesOrdered, zero-capacity construction, queue polling, and checked upper
size-hint addition. Generated associated types/imports are not independently
compilable. The authored repair uses existing canonical StreamFuse and
FuturesOrdered owners, with no duplicate queue, custom scheduling algorithm,
coroutine migration or generator change.

## Exact behavior

Unchanged futures-util 0.3.31 fills an ordered in-progress queue up to capacity,
then polls that queue. The queue drives later ready-to-run futures even if an
earlier future is still Pending, buffering their outputs until earlier results
can be yielded. Completed out-of-order outputs still occupy queue capacity.
Kotlin instead stored a list and polled only its first future, so later futures
could not progress while that first future waited. The repair restores the
source delegation rather than adding another local scheduling implementation.

The input is fused, so terminal hints are zero without consulting an ended
raw input. An empty ordered queue is not enough to end Buffered while its input
can still supply futures. Fused termination depends on the input's observed
End and the canonical queue's termination. Source capacity zero is allowed and
keeps returning Pending without polling the input; Kotlin had rejected zero.
Negative capacity remains rejected because Rust's usize input cannot express it.
Lower size bounds saturate and upper bounds use checked addition in the
existing Kotlin Int domain. The API comment now correctly describes input
order, not completion order.

Six new Kotlin cases cover later progress plus ordered results, queued-output
capacity accounting, nullable first output, zero capacity, outer Pending then
new work and fused terminal size hints. Five fail before repair; the outer
Pending control passes. Existing assertions are unchanged. Six actual Rust
oracle tests run unchanged local futures-util, not a simulation. Producers
are ordinary manually released futures that save their actual polling Waker,
never self-wake while blocked, and reject post-completion polls. Input streams
trace pulls and reject post-End polling/hint queries. Rust uses its real
FuturesOrdered and FuturesUnordered; Kotlin uses its existing corresponding
owners. Tests require the observed source call order for these bounded cases,
not arbitrary scheduling fairness guarantees.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-buffered/cargo-target cargo test --manifest-path verification/buffered-oracle/Cargo.toml --offline --locked
```

## Limits

No full Rust pin/move/Send/Sync or deterministic allocation/Drop equivalence is
claimed. This step does not audit every canonical queue's wake/lock/iteration
contract; existing FuturesOrdered reference-retention and iterator behavior
remain outside scope. TryBuffered and other buffer adapters are unchanged,
including any similar omissions. Missing raw/mutable/pinned accessors and Sink
forwarding remain outside this step. Int bounds cannot represent full usize;
these cases do not exhaust extreme hint arithmetic. No pins, repositories,
runtime/build/publication configuration or unrelated owners changed. Other
platforms, downstream consumers, Swift, remote CI and publication metadata
remain unverified by these local checks.

## Final-source local checks

After correcting an added when-branch formatting finding, the actual final
source passes all 441 JVM tests and 441 macOS ARM tests, including six new
regressions. All six Rust oracle cases pass and Detekt reports zero findings.
Combined Gradle validation still fails for ktlint: 702 common-main and 31
common-test findings. All 733 diagnostic locations map to unchanged baseline
6b15e55 lines; no changed source line or new test has a finding. Two old
branch-spacing findings disappeared with the replaced branches. Existing
assertions and dependency/configuration declarations remain unchanged. Final
hashes, diagnostic-line comparison and translator/red/oracle/final-check logs
remain untracked under build/rust-kotlin-buffered.
