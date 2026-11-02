# ForEachConcurrent input/action interleaving parity

## Selection and provenance

The repository started clean at 0a552a3. Futures has 30 dependency-graph
consumers (Rand 30, Tokio 33, Serde 56). Independent original-source comparison
confirmed a material scheduling, backpressure and callback-panic ordering gap
in this infallible adapter, using the existing canonical FuturesUnordered owner.
It is not assumed to have TryForEachConcurrent's error paths. Adjacent owners
are untouched; no previous dirty work is included. Prior build/platform
checkpoint e2a8c62 and all previous oracle/native failure evidence remain
separately attributed and preserved.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/stream/stream/for_each_concurrent.rs` from futures-util
0.3.31 using this repository's gradle.properties and original source root.
Scratch output/logs are in build/rust-kotlin-for-each-concurrent. Unresolved
associated types/imports and projection/Pin/NonZero lowering prevent a runnable
scratch-output claim. The repair is manual exact-source adaptation informed by
translator output and direct original source, not a generator fix or redesign.

## Exact source algorithm

The source holds an optional input stream and a FuturesUnordered action queue.
Zero is ignored as an unlimited limit, like None. Each loop iteration reads at
most one input event if below the limit, then polls one queue result. An input
item or a completed action counts as progress; EOF alone does not. EOF removes
the input. Completion/termination requires absent input and an empty queue;
no progress returns Pending. Creating a later action therefore happens only
after the earlier iteration has polled its queue, including when the callback
panics. Callback/input panics are not caught or normalized by the adapter.

Kotlin previously filled an entire batch before polling any actions, scanned
all in-flight actions on unrelated polls, treated EOF as progress, rejected
zero and retained the input behind a done flag. The repair stores optional
input, reuses canonical FuturesUnordered and follows the exact source loop and
progress order. Negative Int limits remain rejected because source usize has
no negative values. Public constructor/extension signatures and existing
callers/assertions are unchanged; no new helper owner or compatibility shortcut
is introduced. The canonical queue implementation itself is unchanged.

Six new Kotlin cases cover zero-as-unlimited, wake-only action polling,
one-slot backpressure, immediate action/input interleaving, callback panic after
the earlier action was polled, and repeated Pending input without extra action
polls or input reads. All six fail at runtime against the old implementation.
The Gradle red filter also matches the six existing TryForEachConcurrent parity
cases: those six pass, giving 12 total and six failures. XML-derived receipts
isolate the new six-case suite; logs remain under build/rust-kotlin-for-each-concurrent.
No assertion or warning rule was weakened.

Six matching Rust cases execute actual upstream ForEachConcurrent and canonical
FuturesUnordered with real Context/Waker, manually released gates, scripted
input and caught callback panic. Producers never implement the adapter or queue
algorithm. Tests assert exact callback/input/action-poll counts, traces and
completion status for these bounded single-thread schedules.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-for-each-concurrent/cargo-target cargo test --manifest-path verification/for-each-concurrent-oracle/Cargo.toml --offline --locked
```

## Remaining limits

These cases do not establish all canonical queue fairness/budgeting, wake-race
or cross-thread scheduling equivalence. Kotlin GC/reference ownership does not
reproduce deterministic Rust Drop, borrow/move/pin/Send/Sync guarantees. Limits
and counts remain Int rather than usize. Generic concrete-future relationships,
Debug and other API gaps remain outside scope. No downstream source/binary,
Swift/other-target or freshly published metadata validation is claimed.
No pins, repositories or runtime/build/publication settings changed, and no
other owner or UI session was touched. Earlier checkpoints, original/corrected
oracle receipts and Native regression evidence remain intact.

## Final-source local checks

All 576 JVM and 576 macOS ARM tests pass, including six new cases and the
unchanged StreamCombinatorsTest.forEachConcurrentAndBuffered caller. All six
actual upstream Rust cases pass. Detekt has zero findings. The combined Gradle
command fails only at ktlint: 608 common-main and 31 common-test findings.
All 639 diagnostics map to unchanged baseline 0a552a3 lines; no authored line
has a finding. Final source hashes include the reused, unchanged canonical
FuturesUnordered owner. Hash/static-comparison receipts and CLI/red/oracle/
Gradle logs remain under build/rust-kotlin-for-each-concurrent. This step adds
no prior-work checkpoint and no unrelated dirty files; all earlier commits
and representation/validation limits remain separate.
