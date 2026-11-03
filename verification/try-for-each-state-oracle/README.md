# TryForEach callback-state and trait parity

Actual source: tmp/futures-util/src/stream/try_stream/try_for_each.rs, Future::poll. `ready!(fut.try_poll(cx))?` returns an error before `this.future.set(None)`; only successful callback completion clears the stored future. Kotlin previously cleared it before inspecting the result, losing that state. Rust also has no FusedFuture implementation for this adaptor, whereas Kotlin exposed one unconditionally.

The bounded repair moves pendingFuture clearing into the success branch and changes TryStreamForEach's implemented interface from FusedFuture to Future. The unsupported done/isTerminated state is removed. Callback signature, concrete adaptor/factory names, Try/Poll/Yield types, stream error behavior, error payloads and canonical Fuse owner remain unchanged. No replacement future/algorithm or compatibility shim is introduced.

Seven Rust runtime groups invoke actual upstream try_for_each and compare complete producer/action creation/poll traces plus independent counters: repeated callback errors; error then Pending then success; error then callback panic; nullable error data after Pending; stream-error continuation; outer versus inner Pending; and canonical future Fuse after error. Kotlin has eight groups: those seven plus a runtime check that the raw adaptor does not advertise FusedFuture. A separate actual Rust compile probe requires that trait and fails specifically with E0277 for TryForEach. Its complete diagnostic and expected-failure metadata are retained; it is not counted as a failing runtime oracle.

Post-Ready polling cases intentionally observe the concrete upstream implementation with deliberately scripted, repollable fixtures. They do not promise portable permission to repoll arbitrary completed futures, provide a retry protocol, or claim recovery behavior for all Future implementations. Canonical fuse() is separately checked for ordinary guarded completion/error behavior. Manual polling fixtures do not establish wake registration, fairness, scheduling or cross-thread behavior.

The Kotlin baseline compiled and ran eight tests: five failed, three controls passed. Original red XML and exact test source/hash remain in build/rust-kotlin-try-for-each-state. All original expectations, outcome/trace/counter assertions remain. After the first passing final run, direct typed-error payload assertions were added in the same Rust/Kotlin cases so null/None cannot be confused with literal text "null" by trace rendering. The first sources, oracle log and runtime/static receipts are preserved; no production changes accompanied this strengthening, and final checks were rerun. Existing tests are unchanged. An initial Python fixture-generation NameError occurred before any Rust/Kotlin execution; its partial helper source/correction record is retained. Translation was not repeated. This was fixture setup, not a source/test failure or a claimed oracle pass.

Before production edits, a bounded workspace exact-API/class/alias search and canonical wildcard-import review was performed. One existing local caller, StreamTryStreamTest.tryFoldAndTryForEach, only polls the returned future and needs no edit; its assertions remain unchanged. No external exact callers or relevant wildcard consumers were found in that scan. New tests call isTerminated only on canonical Fuse. This is source-level evidence, not a typechecked global resolver or downstream build.

API impact is explicit: raw TryStreamForEach no longer satisfies FusedFuture and has no isTerminated method. Source/binary consumers relying on that unsupported extra contract must rebuild/adapt to fuse(); downstream compatibility is unverified. There is no deceptive compatibility implementation.

One original-file translator run exited 0; source/recorded-binary/output hashes and invocation are retained. Emitted pin/ready/tri scaffolding was inspected, not compiled or adopted as proof. Exact Rust source/compiler execution determines error order and trait behavior. No generator edit, rebuild investigation or owner contact occurred; the recorded binary is not attested as the current generator build.

Futures has 30 direct reverse dependents, ahead of HTTP's 16. Baseline ceba470 was clean; previous Futures checkpoints (including e2a8c62 prior-work checkpoint), HTTP 1d607a9 and Serde 9ae88dd remain separate and preserved. Adjacent Tokio and other owners were not edited. No pins, remote or configuration resources changed.

Final checks are full JVM/macOS ARM runtime suites plus static gates. Detekt passes; ktlint remains failing with 611 baseline findings (612 before), none on authored lines. This is not a fully green build. Rust ownership/Drop/Pin/Send/Sync, physical destruction/GC, other platforms and downstream binary compatibility remain unverified.

Commands:

- `cargo test --offline --manifest-path verification/try-for-each-state-oracle/Cargo.toml --target-dir build/rust-kotlin-try-for-each-state/rust-target`
- `cargo check --offline --manifest-path verification/try-for-each-state-oracle/negative-fused/Cargo.toml --target-dir build/rust-kotlin-try-for-each-state/negative-target` (expected E0277)
- `./gradlew jvmTest --tests '*TryForEachStateParityTest*' --console=plain` (runtime red)
- `./gradlew jvmTest macosArm64Test detekt ktlintCheck --continue --console=plain` (final source)
