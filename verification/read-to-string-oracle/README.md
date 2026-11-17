# ReadToString finalization parity

## Selection and source provenance

Graph comparison records Serde 56 consumers, Tokio 33, Futures 30 and Rand 30.
Their common source/test trees were clean at selection. Futures offered a
confirmed independent partial-read/error data-loss gap without shared-runtime
or generator design changes. This tree started clean at 41a1ef4. Pre-existing
build/platform changes remain separately checkpointed in e2a8c62, not newly
authored or fully validated across platforms by this step.

The existing native rust-kotlin CLI successfully translated original
`tmp/futures-util/src/io/read_to_string.rs` using this repository's
gradle.properties, package io.github.kotlinmania.futures.io and source root
tmp/futures-util/src into build/rust-kotlin-read-to-string/ReadToStringReview.kt.
Direct unchanged futures-util 0.3.31 source comparison confirms mem::take at
construction, finalization after read_to_end on both Ok and Err, strict UTF-8
validation, and ret.and_then error priority. Generated types/imports are not
independently compilable. This is an authored faithful adaptation of that
bounded contract, not a generator change or a complete read_to_end port.

## Exact behavior

Rust moves the original String bytes into the future at construction, leaving
the destination empty. On terminal EOF or I/O error, valid accumulated UTF-8
is restored into the destination, and the original read result is returned.
Only newly read bytes count toward success. Invalid accumulated UTF-8 leaves
the moved destination empty. It changes a successful EOF result into InvalidData
but never replaces an original I/O error. There is no ReadToString Drop
restoration: abandoning a Pending read leaves the destination empty as well.
This differs from the separate ReadLine implementation and was verified from
the actual ReadToString rather than inferred from other adapters.

Kotlin previously accumulated only new bytes, appended only at successful EOF,
returned I/O errors before restoring valid partial data and retained the old
destination on invalid UTF-8 or abandonment. The repair moves the existing
text into its byte buffer and clears the destination, finalizes both error and
EOF through one private helper, restores valid accumulated text and clears the
transferred byte list. Strict decoder failure preserves an original IoError
object; only that specific decoding exception is mapped to InvalidData.

Seven Kotlin regressions cover valid partial error restoration, invalid EOF,
invalid bytes with original read error, split supplementary UTF-8 across Pending,
abandonment, multibyte original prefix counts and immediate EOF restoration.
Four fail before repair. Existing tests/assertions are unchanged. Seven actual
Rust cases run local unchanged futures-util with its io feature, AsyncReadExt
and real Context/Waker values. Scripted readers only supply data/errors/Pending;
they do not simulate read_to_string. Rust cancellation inspects the destination
after the borrowed future leaves scope. The Kotlin counterpart abandons its
local future reference; it does not claim Rust borrow enforcement or equivalent
GC/Drop timing. Its destination was already cleared during construction.

```sh
CARGO_TARGET_DIR=build/rust-kotlin-read-to-string/cargo-target cargo test --manifest-path verification/read-to-string-oracle/Cargo.toml --offline --locked
```

## Remaining limits

Tests use valid original text: Rust String is valid UTF-8, while Kotlin
StringBuilder can contain isolated surrogates. That Kotlin-only input domain
is not assigned Rust equivalence here. External destination mutation while a
future owns it is possible in Kotlin and forbidden by Rust's borrow; no borrowed
receiver enforcement is claimed. Existing fixed 8192-byte read request buffering
is unchanged and does not mirror upstream Vec capacity/initialization strategy.
Int byte-count limits, malformed reader counts, repeated completed-future polls,
other I/O helpers and placeholder API omissions are outside this repair. No
full ownership/pin/Send/Sync or deterministic allocation/Drop claim is made.
Only the oracle enables the existing io feature; no production dependency pin,
runtime/build/publication configuration or repository changed. Other platforms,
downstream consumers, Swift, remote CI and publication metadata remain outside
local validation scope.

## Final-source local checks

After correcting touched branch/new fixture formatting, the actual final tree
passes all 462 JVM tests and 462 macOS ARM tests, including seven new cases.
All seven actual Rust oracle cases pass; Detekt reports zero findings. Combined
Gradle validation fails at ktlint: 683 common-main and 31 common-test
findings. All 714 diagnostic locations map to unchanged baseline 41a1ef4 lines;
no changed production line or new test has a finding. Existing assertions and
production pins/configuration are unchanged. Source hashes, baseline-line
comparison and translator/red/oracle/final-check logs remain untracked under
build/rust-kotlin-read-to-string.
