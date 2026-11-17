# Skip EOF counter parity

## Source comparison and repair

Exact source: `tmp/futures-util/src/stream/stream/skip.rs`. In its `poll_next` loop, a yielded item decrements `remaining`; source EOF returns `Ready(None)` without altering `remaining`. Kotlin had an extra `remaining = 0` assignment in that EOF branch. This step removes that assignment only. It preserves the decrement, Pending behavior, ordinary finite/zero skip behavior, size-hint implementation, signatures, overloads, accessors, dependency pins and existing tests. No algorithm replacement is involved.

A single bounded translation of the original Rust file succeeded (11 items), into `build/rust-kotlin-skip-eof-state/SkipReview.kt`. Exact command, original-file/binary/output hashes and exit status are in translator-attempt.json. The emitted Skip loop also returns at EOF without clearing the count. Scratch output is structural comparison evidence, not compiled adoption or proof that the recorded binary is Iris's current generator build. The one-line consumer repair is manual, based on both direct upstream source and that diff. No generator edit/investigation/rebuild, source extraction or guard bypass occurred.

Graph priority was checked against `workspace_dep_graph.json`: Futures has 30 distinct reverse dependents (Serde 56, Tokio 33, Rand 30, JSON 21, HTTP 16). This bounded confirmed gap uses a clean, owned Futures checkout rather than adjacent concurrent work. Baseline d37db64 was clean; no pre-existing dirty work or other owner's edits are included. Earlier TryConcat/fuse/rebuild caveats remain unchanged.

## Oracle and regressions

Run actual upstream Rust with:

`cargo test --offline --manifest-path verification/skip-eof-state-oracle/Cargo.toml --target-dir build/rust-kotlin-skip-eof-state/rust-target`

Nine test groups compare typed yielded values versus End/Pending, independent producer poll counts and size hints after every observed poll. Cases cover zero count, exhausted count, a short finite source, count retained across EOF, repeated EOF, Pending while still skipping, nullable yielded items and unknown upper bounds. The ninth checks canonical Fuse stops polling the producer after End. Rust uses the actual local upstream `.skip()` adapter; the fixture implements only its scripted producer. Kotlin uses canonical Stream/Poll/Yield/SizeHint and `.skip()`/`.fuse()`, with the same explicit expectations. No new production representation was invented.

The scripted producer counts remaining queued value events, including values after an explicit End, for its known-hint profiles; unknown-hint profiles report `(0, None/null)`. This intentionally observes a concrete nonfused implementation, not ordinary finite-stream semantics. Poll counters are independent of output expectations. Nullable values are distinct from End in both languages. All nine Rust groups passed before the repair. JVM initially had nine tests: five failed and four passed. Red XML, source and logs are retained. Existing assertions and fixtures were not relaxed to obtain a pass. Final-source platform/static results and hashes are in receipt.json and the build evidence folder.

## Contract and compatibility limits

The upstream Stream trait explicitly makes no requirements on calls to `poll_next` after `Ready(None)`; such calls may panic or block. This test does not introduce a portable promise to resume arbitrary terminated streams. It observes exact Skip state and behavior for the actual scripted upstream source, including the immediately queried size hint. Canonical Fuse is the supported way to stop further source polling; its control case remains repeatably End. Do not claim all post-End sources behave like this fixture.

There is no API/signature change or caller adaptation in this step. The existing StreamCombinatorsTest skipAndTakeWhile caller remains unchanged, retaining expected `[3, 4]`. No broad downstream build or binary compatibility proof was attempted. The existing Kotlin unconditional FusedStream marker cannot express Rust's conditional `St: FusedStream` bound; this step does not resolve that trait-model limitation. Kotlin uses signed Long/Int counts and Int-sized hints rather than full usize; negative counts, full pointer-width range, Sink forwarding, pinning/exclusive borrowing, ownership/destruction, other platforms and downstream behavior are not validated here.

The global ktlint gate remains failing if baseline findings remain; passing runtime tests are not a claim of clean static gates. No remote CI, pushes, PRs, tags, publication, settings changes or agent contact occurred.
