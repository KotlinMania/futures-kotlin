# SinkMapErr one-use error mapper

The repository started clean at 9affec7. Graph comparison remains Serde 56
reverse dependents, Futures 30, Rand 30 and JSON 21. This bounded Futures
contract gap affects all four sink operations. No pre-existing dirty work or
adjacent owner edits are included. Prior checkpoints and shared takeValue,
With identity-only error conversion, Fanout clone/send, ownership and platform
limitations remain separately preserved.

The existing native rust-kotlin CLI translated original futures-util 0.3.31
sink/map_err.rs using owning gradle.properties and original source root into
build/rust-kotlin-map-err-once/MapErrReview.kt. It emits takeF() with take/expect,
matching direct source comparison. Its associated-type/Pin/Result/Option and
conditional-trait lowerings remain incomplete; scratch is neither installed
nor claimed runnable. This is a manual exact-source correction using the
existing function type, not a generator change or root-guard bypass.

## Source contract and repair

Original map_err.rs:19-20 stores Some(f); :25-26 takes that slot and expects
"polled MapErr after completion" if empty. The bound is FnOnce (:30-34). Each
of readiness/send/flush/close (:37-50) first calls the underlying sink and only
takes/invokes the mapper if that operation returns Err. Pending and success do
not consume it. Consumption precedes user callback execution, so even a mapper
panic leaves the slot empty. This is not a global done guard: after consumption,
underlying success/Pending still pass through; another underlying error reaches
the empty mapper check after that underlying operation has already run.

Kotlin retained a reusable function and invoked it for every error. It now
holds a nullable private mapper slot, takes/clears it before the first callback,
and uses the exact original missing-mapper message. The slot tags function
presence, independent of nullable error input or mapped output. Constructors,
factory and extension signatures remain unchanged. No duplicate Option owner
or alternate error algorithm is added.

## Existing-test correction and independent proof

The old SinkCombinatorsTest.testSinkMapErr used one failing sink/adapter for
four successive errors and expected four mapper invocations. Actual Rust
rejects every second-error operation combination. The fixture now creates four
fresh failing sinks and four fresh adapters, one per existing operation.
All four expected mapped error values and operation arguments are retained;
only the latter three assertion receivers change to their respective adapters.
No expectation is removed or loosened. This removes an invalid reuse assumption,
rather than hiding mapper reuse behind a getter or replacing the test result.

Eight Rust tests execute actual unchanged SinkMapErr via SinkExt. Four tests
exercise all sixteen first-operation/second-operation pairs: the first maps
once, then the second underlying error must panic with the exact original
completion message and no second mapper invocation. The other cases cover
nullable input/output, mapper panic, Pending/success preserving the mapper,
and success/Pending still delegating after consumption. These are twenty
scenarios across eight test functions. Scripted fixtures implement only Sink,
never SinkMapErr or take_f; real Context/Waker trace order and wake counts are
checked.

The new matching Kotlin suite runs on original code with seven failures and
one passing Pending/success control. Each failing matrix stops on its first
violating pair; no claim is made that all sixteen pairs ran red. All pairs
run in the final green suite. The entire new test file is byte-identical
between red and final; the existing fixture correction is separately audited.
These post-error/panic probes observe upstream behavior, not a general promise
that arbitrary failed sinks can recover.

## Limits and provenance

SinkErrInto still has its own duplicate reusable mapper instead of delegating
to canonical SinkMapErr, and its explicit conversion callback does not model
Rust Into completely. It is unchanged and remains a known follow-up gap.
Conditional Stream/FusedStream/Clone, delegated accessors and Debug coverage
also remain incomplete. Kotlin cannot enforce Rust FnOnce/move/borrow/Pin/
Unpin/Drop/Send/Sync relationships merely through this private one-use slot.

No public signature migration is introduced. Existing single-error/channel
callers in SinkTest remain unchanged. No downstream, Swift/other-platform or
publication validation is claimed. No production pin, repository, configuration,
UI, remote CI, push, PR, tag, release, publication or adjacent owner changed.
Test-only Cargo dependencies resolve futures-util/core/sink/task 0.3.31
offline; Cargo.lock remains ignored by existing policy, with its hash recorded.
Exact commands, timestamps and source hashes are in receipt.json; raw CLI/
red/oracle/full-check logs remain in build/rust-kotlin-map-err-once.

## Final-source local checks

All eight actual Rust tests pass (twenty scenarios). All 649 JVM and 649 macOS
ARM tests pass, including the eight new tests, the corrected four-adapter
fixture, and unchanged SinkTest callers. Detekt reports zero findings.
The final combined Gradle command exits 1 only at ktlint: 581 common-main and
31 common-test findings. All 612 locations map to unchanged 9affec7 baseline
lines; no authored line has a finding. The first full run had two branch-style
diagnostics in the changed when expression, including one on a textually
unchanged branch affected by its new multiline sibling. Those receipts remain
preserved. Only branch braces/whitespace were corrected before rerunning
final-source runtime and static checks; no assertion was changed to fix style.
