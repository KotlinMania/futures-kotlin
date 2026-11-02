# Feed item-presence and readiness-order parity

The repository began clean at 09c4126. Futures has 30 dependency-graph consumers;
the directly adjacent Feed/Send null-loss gap was selected as a material repair.
Previous checkpoint e2a8c62 and all earlier authored repairs remain separately
attributed. This step includes no pre-existing dirty work or other owner's edits.

A bounded rust-kotlin translation of original
tmp/futures-util/src/sink/feed.rs produced
build/rust-kotlin-feed/FeedReview.kt. Its readiness-first/take/expect/send order
matches the original Rust. Its nullable Option lowering still cannot distinguish
a present nullable item from absence; Pin/associated-type/import lowering is
also unresolved. This is a manual exact-source adaptation informed by generated
output and direct upstream comparison, not a generator fix or runnable generated
output claim. No mod.rs/lib.rs guard was bypassed.

## Source contract and repair

Actual futures-util 0.3.31 feed.rs:19-20 stores Some(item), :27-28 checks that
presence, and :35-41 polls sink readiness before taking the item and invoking
start_send. Pending, readiness error or readiness panic leaves the item present.
Taking precedes start_send, so its error consumes the item. Feed never flushes or
closes. After consumption, another poll still consults readiness first: Pending
or error is returned before the missing-item panic on readiness success.

Kotlin's Item? private slot conflated a null payload with an empty slot and
returned success without polling or sending. It also skipped readiness on
subsequent polls. The slot now uses the already-owned Try.Ok<Item> carrier,
matching existing nullable-item state representations in this library. Readiness
is polled before checking/taking the slot; startSend receives the wrapped value.
No new Option owner, casts, algorithms, API migration or completion flag is added.
Public declarations and Send.kt are unchanged. Send's nullable integration now
feeds before flushing, rather than silently skipping its item.

Ten scripted Rust cases exercise actual unchanged upstream Feed/Send through
SinkExt, with real Context/Waker. The producer implements only Sink, never the
adapter. They cover nullable ready/pending/error/panic paths, non-null control,
post-consumption readiness order, and nullable Send flushing. Kotlin has ten
matching cases, including source-derived private item-presence observations.
The original Kotlin implementation fails nine; the non-null control passes.
Existing tests and assertions are unchanged. Fixtures do not rewrite expected
results or execute a copied Feed algorithm.

Repoll-after-completion/error and panic cases are observations of this upstream
implementation, not a general Future reuse or failed-sink recovery guarantee.
Rust Option<i32> payloads represent the nullable Kotlin item; Rust Option<&str>
errors distinguish an actual null error from successful completion.

## Validation and limits

All 10 actual Rust cases pass offline. All 600 JVM and 600 macOS ARM tests pass,
including the new ten-case suite. Detekt reports zero findings. The combined
Gradle command exits 1 for existing ktlint failures: 596 common-main and 31
common-test findings, all on unchanged baseline lines, none on authored lines.
The original red log, translator output and final runtime/static logs are in
build/rust-kotlin-feed; receipt.json records hashes, suite timestamps, counts,
commands and baseline attribution. The Cargo.lock is generated and ignored under
existing repository policy; its hash is recorded, with futures-util/core/sink/
task resolved at 0.3.31. No production pin or repository setting changed.

Kotlin references/GC do not reproduce mutable borrow lifetimes, Pin/Unpin,
move/Drop destruction or Send/Sync bounds. Existing broader concurrency, usize,
accessor and trait limitations remain. No downstream source/binary, Swift,
other-platform or publication validation is claimed. No other owner, UI session,
remote service, configuration or publication was changed.
