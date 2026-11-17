# SelectOk polling parity

The graph records thirty consumers for futures-kotlin versus eleven for
itertools-kotlin. Common source/tests were clean. Commit e2a8c62 separately
checkpoints the 42 pre-existing dirty status entries (110 actual build/platform
files); it is not authored SelectOk repair work and has no new full-platform
validation claim. Log's dirty common-source lane was left untouched.

## Tool/source comparison

The current native rust-kotlin executable successfully translated the original
futures-util 0.3.31 future/select_ok.rs with this repo's gradle.properties into
build/rust-kotlin-select-ok/SelectOkReview.kt. No previous stalled input was
retried. The output preserves the outer rescan loop and mem.take on success,
which expose gaps in the existing Kotlin implementation. Generated associated
types/imports and Kotlin Result error narrowing still need adaptation; the
scratch output is not claimed independently compilable. The authored repair
preserves the owning Future/Poll/Try types and generic E, with no duplicate
Output alias or helper owner introduced. No generator change is included.

## Exact behavior

Upstream restarts its find_map scan from the first remaining future after
every removed error. Kotlin resumed at the removed index, skipping earlier
pending futures. An earlier future that returns Pending once (and wakes its
task) can become ready on the rescan and win before a later already-ready
success. The repair resets the index to zero after an error unless the queue
is empty, exactly matching the source loop. A successful result also clears
the internal queue after taking a snapshot of its remaining futures, matching
mem.take and retaining the original future references in the returned list.

Five new Kotlin tests cover earlier-success priority after error, an earlier
future that remains pending on the rescan, completed queue transfer, tagged
nullable success/error values and an entirely-pending control scan. Three
failed before repair. Five actual Rust tests execute unchanged local
futures-util with scripted Future+Unpin inputs and real standard Context/Waker
values. These fixtures model producer readiness; they do not implement or
simulate SelectOk itself. Pending fixtures wake their task before returning.
The oracle uses no coroutine/executor replacement.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-select-ok/cargo-target cargo test --manifest-path verification/select-ok-oracle/Cargo.toml --offline --locked
```

The completed-queue test observes concrete SelectOk 0.3.31's empty-queue Pending
behavior on a further poll, solely to detect retained/transferred futures.
It does not promise arbitrary completed Future implementations may be safely
repolled under their own contracts. Rust move/pin/Send/Sync enforcement and
deterministic Drop timing are not claimed equivalent to Kotlin references.
Empty-input diagnostic spelling, Ready's separate context access and other
combinators/stream Option representation are outside this bounded repair.

Existing Kotlin assertions and dependency pins remain unchanged. No runtime
or publication configuration was changed. Other platforms, downstream builds,
Swift, CI and publication metadata remain outside the local validation scope.

## Final-source validation

The repaired source passed all 406 JVM tests and all 406 macOS ARM tests,
including the five new regressions, plus Detekt (zero findings). The actual
Rust oracle passed all five tests. Combined Gradle validation still exits
unsuccessfully because ktlint reports 708 common-main and 31 common-test
findings. All 739 locations map to unchanged lines in baseline e2a8c62; the
new test and the two added production lines have no findings. The existing
SelectOk branch-spacing diagnostics remain. No existing assertions were
modified. Receipts, final source hashes and the line comparison are under
build/rust-kotlin-select-ok; build output remains untracked.
