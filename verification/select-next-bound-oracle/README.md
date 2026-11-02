# SelectNextSome requires a fused stream

The repository began clean at 89f84e6. Graph comparison remains Serde 56 reverse
dependents, Futures 30, Rand 30 and JSON 21. Bounded review found Scan's borrowed
mutable-state/async callback representation and TakeUntil's typed nullable
result API require broader canonical representation work; neither was changed
or approximated. This step corrects a concrete SelectNextSome type-contract
omission instead. No pre-existing dirty work is included.

The existing native rust-kotlin CLI translated original futures-util 0.3.31
stream/stream/select_next_some.rs with owning gradle.properties and original
source root into build/rust-kotlin-select-next-bound/SelectNextSomeReview.kt.
Direct source bounds and original StreamExt::select_next_some require
FusedStream + Unpin. Scratch associated-type/borrow/Pin/import lowering remains
incomplete; it is not installed or claimed runnable. This is a manual source
type-bound correction, not a generator fix.

## Contract and repair

Rust SelectNextSome can query termination because its stream must implement
FusedStream. It rejects polling after termination before touching the stream.
Pending propagates. A valid item (including a nullable payload) is returned
without an artificial completed flag. EOF causes one wake and Pending so select
can observe termination. The original debug build also asserts a producer
reporting EOF has become terminated.

Kotlin accepted any Stream, used a dynamic FusedStream cast and substituted
false when the trait was missing. Plain streams therefore compiled without the
termination capability required by Rust. The constructor and internal factory
now require the existing canonical FusedStream<T>; the public extension receiver
is also FusedStream<T>. isTerminated delegates directly. Polling code, error
message and wake/order behavior are unchanged. No implicit Fuse wrapper or
substitute termination flag is inserted.

## Compile contract evidence

The Rust negative fixture rejected/non_fused.rs implements only Stream and
calls select_next_some. cargo check --bin reject-non-fused rejects it with
E0277 for missing FusedStream. This is an expected negative check, not a passing
build. Cargo.toml keeps it separate from cargo test --lib.

The Kotlin rejected/SelectNextSomePlainStreamCompileProbe.kt contains two
plain-Stream calls: the extension and direct constructor. Before the repair,
that exact file compiled alongside four passing valid-stream runtime controls.
After the repair, the same bytes fail compileTestKotlinJvm with receiver type
mismatch and expected FusedStream<Int> argument mismatch. No claim is made of a
runtime red test: this defect is rejection of an invalid type.

The probe is moved intact from normal commonTest sources into rejected/ after
both compile observations; it remains preserved as an intentionally invalid
fixture, rather than breaking normal test compilation or being deleted.
Original logs, fixture hashes and exit codes are recorded. The four valid-stream
tests remain byte-identical between baseline and final and also execute actual
unchanged Rust SelectNextSome with real Context/Waker.

## Caller impact

The exact-API source review covers workspace *-kotlin repositories' Kotlin,
Java and Swift files, including aliases, root wildcard imports, bare constructor/
method names, fully qualified references and the JVM facade name. Generated
build output, vendored tmp and dependency caches are excluded. Existing use is
five call sites in StreamSelectNextSomeTest, all on FuturesUnordered<Int>.
That canonical class implements FusedStream<T>, so all five compile unchanged.
No existing assertion or caller is adapted. New runtime cases use a scripted
FusedStream. Intentionally invalid probes are not counted as valid consumers.
No external affected source owner was identified within this search scope.

This narrows public source types and changes JVM constructor/extension receiver
descriptors from Stream to FusedStream. Consumers require rebuild and possibly
declared-type adaptation; downstream binary/source compatibility is not claimed.
No cross-project edits or broad downstream builds are performed.

## Remaining limits

Rust Unpin/borrow/Pin/move/Drop/Send/Sync relationships remain unrepresented.
The debug-only check for an invalid fused producer that reports EOF without
termination is not reproduced here; no debug/release equivalence for malformed
producers is claimed. Debug/accessor/comment completeness and other traits
remain outside scope. Prior extra TryUnfold FusedStream, shared takeValue,
explicit-callback Into/From, With distinct-error and Fanout clone/send gaps
remain explicit. Ordinary Unfold, TryUnfold and canonical Fuse terminal
distinctions are preserved.

No production pins, repositories/configuration, UI, adjacent owner, remote CI,
push, PR, tag, release or publication changed. No Swift/other-target builds or
fresh publication validation is claimed. Test-only Cargo dependencies resolve
actual futures-util/core/task 0.3.31 offline; Cargo.lock remains ignored under
existing policy with its hash recorded. Raw logs and scratch output remain in
build/rust-kotlin-select-next-bound; receipt.json records final evidence.

## Final-source local checks

Four actual Rust runtime cases pass. The deliberately invalid Rust probe
fails as expected with exit 101. Kotlin's exact plain-stream probe compiles on
the old API and fails with exit 1 on the corrected API. It is preserved under
rejected/ and is not part of normal Gradle source sets; this is recorded
compile-probe evidence, not an automatic negative Gradle test gate.

All 678 JVM and 678 macOS ARM tests pass, including four unchanged new controls
and all five existing valid call sites. Detekt reports zero findings. The
combined Gradle command exits 1 only for ktlint: 581 common-main plus 31
common-test findings, all on unchanged 89f84e6 baseline lines, none authored.
Current javap output verifies the FusedStream JVM parameter descriptors.
Final receipt hashes are verified after the local checkpoint.
