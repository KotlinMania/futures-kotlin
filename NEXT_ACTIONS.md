# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 36/305 (11.8%)
- **Function parity:** 123/1888 matched (target 358) — 6.5%
- **Class/type parity:** 45/558 matched (target 100) — 8.1%
- **Combined symbol parity:** 168/2446 matched (target 458) — 6.9%
- **Average inline-code cosine:** 0.34 (function body across 31 matched files)
- **Average documentation cosine:** 0.54 (doc text across 31 matched files)
- **Cheat-zeroed Files:** 6
- **Critical Issues:** 33 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

### 1. future.ready
- **Similarity:** 0.43 (needs 42% improvement)
- **Dependencies:** 84
- **Priority Score:** 84010808.0
- **Functions:** 6/6 matched (target 10)
- **Missing functions:** _none_
- **Types:** 1/2 matched
- **Missing types:** `Output`
- **Symbol Deficit:** 1 (functions: 0, types: 1)
- **Action:** Deep review - likely missing major functionality

### 2. futures-core.future
- **Similarity:** 0.10 (needs 75% improvement)
- **Dependencies:** 65
- **Priority Score:** 65030908.0
- **Functions:** 1/2 matched (target 5)
- **Missing functions:** `is_terminated`
- **Types:** 5/7 matched
- **Missing types:** `Sealed`, `Error`
- **Symbol Deficit:** 3 (functions: 1, types: 2)
- **Action:** Deep review - likely missing major functionality

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

1. **tests.sink** (62 deps)
   - Path: `futures/tests/sink.rs`
   - Essential for 62 other files

2. **tests.stream** (25 deps)
   - Path: `futures/tests/stream.rs`
   - Essential for 25 other files

3. **tests.mpsc** (17 deps)
   - Path: `futures-channel/tests/mpsc.rs`
   - Essential for 17 other files

4. **task.poll** (16 deps)
   - Path: `futures-core/src/task/poll.rs`
   - Essential for 16 other files

5. **tests.oneshot** (11 deps)
   - Path: `futures/tests/oneshot.rs`
   - Essential for 11 other files

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. future.ready

- **Target:** `futures.Ready`
- **Similarity:** 0.43
- **Dependents:** 84
- **Priority Score:** 84010808.0
- **Functions:** 6/6 matched (target 10)
- **Missing functions:** _none_
- **Types:** 1/2 matched
- **Missing types:** `Output`
- **Lint issues:** 1

### 2. futures-core.future

- **Target:** `futures.Future`
- **Similarity:** 0.10
- **Dependents:** 65
- **Priority Score:** 65030908.0
- **Functions:** 1/2 matched (target 5)
- **Missing functions:** `is_terminated`
- **Types:** 5/7 matched
- **Missing types:** `Sealed`, `Error`

### 3. __internal.atomic_waker

- **Target:** `futures.AtomicWaker`
- **Similarity:** 0.34
- **Dependents:** 6
- **Priority Score:** 6030806.5
- **Functions:** 4/6 matched (target 4)
- **Missing functions:** `default`, `fmt`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `AssertSync`

### 4. futures-task.noop_waker

- **Target:** `futures.NoopWaker [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 4
- **Priority Score:** 4050710.0
- **Functions:** 2/6 matched (target 5)
- **Missing functions:** `noop_clone`, `noop`, `noop_raw_waker`, `issue_2091_cross_thread_segfault`
- **Types:** 0/1 matched
- **Missing types:** `SyncRawWaker`
- **Tests:** 0/1 matched

### 5. futures-util.never

- **Target:** `futures.Never`
- **Similarity:** 1.00
- **Dependents:** 3
- **Priority Score:** 3000100.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 6. future.either

- **Target:** `futures.Either [PROVENANCE-FALLBACK]`
- **Similarity:** 0.31
- **Dependents:** 2
- **Priority Score:** 2122406.8
- **Functions:** 11/20 matched (target 24)
- **Missing functions:** `as_pin_ref`, `as_pin_mut`, `poll_read`, `poll_read_vectored`, `poll_write`, `poll_write_vectored`, `poll_seek`, `poll_fill_buf`, `consume`
- **Types:** 1/4 matched
- **Missing types:** `Output`, `Item`, `Error`
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:futures-util/tests/either.rs` vs expected `future/either.rs`
- **Proposed provenance header:** `// port-lint: tests future/either.rs` (current: `// port-lint: tests futures-util/tests/either.rs`)
- **Lint issues:** 1

### 7. futures-util.abortable

- **Target:** `futures.Abortable`
- **Similarity:** 0.49
- **Dependents:** 2
- **Priority Score:** 2051505.0
- **Functions:** 6/9 matched (target 20)
- **Missing functions:** `new`, `fmt`, `try_poll`
- **Types:** 4/6 matched (target 7)
- **Missing types:** `Output`, `Item`

### 8. stream.poll_fn

- **Target:** `futures.PollFnTest [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 2
- **Priority Score:** 2050510.0
- **Functions:** 0/3 matched (target 1)
- **Missing functions:** `fmt`, `poll_fn`, `poll_next`
- **Types:** 0/2 matched (target 1)
- **Missing types:** `PollFn`, `Item`
- **Provenance warning:** port-lint provenance header matched only by basename: `futures-util/src/future/poll_fn.rs` vs expected `stream/poll_fn.rs`
- **Proposed provenance header:** `// port-lint: source stream/poll_fn.rs` (current: `// port-lint: source futures-util/src/future/poll_fn.rs`)
- **Lint issues:** 1

### 9. futures-channel.lock

- **Target:** `futures.Lock`
- **Similarity:** 0.28
- **Dependents:** 2
- **Priority Score:** 2040907.2
- **Functions:** 3/6 matched (target 9)
- **Missing functions:** `deref`, `deref_mut`, `drop`
- **Types:** 2/3 matched
- **Missing types:** `Target`
- **Tests:** 1/1 matched

### 10. futures-util.unfold_state

- **Target:** `futures.UnfoldState`
- **Similarity:** 0.19
- **Dependents:** 2
- **Priority Score:** 2000208.1
- **Functions:** 2/2 matched (target 4)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 4)
- **Missing types:** _none_

### 11. future.lazy

- **Target:** `futures.LazyFuture [PROVENANCE-FALLBACK]`
- **Similarity:** 0.23
- **Dependents:** 1
- **Priority Score:** 1020507.7
- **Functions:** 2/3 matched (target 4)
- **Missing functions:** `lazy`
- **Types:** 1/2 matched
- **Missing types:** `Output`
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:futures-util/tests/lazy.rs` vs expected `future/lazy.rs`
- **Proposed provenance header:** `// port-lint: tests future/lazy.rs` (current: `// port-lint: tests futures-util/tests/lazy.rs`)
- **Lint issues:** 1

### 12. futures-macro.stream_select

- **Target:** `futures.StreamSelect`
- **Similarity:** 0.29
- **Dependents:** 1
- **Priority Score:** 1000107.1
- **Functions:** 1/1 matched (target 10)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_

### 13. mpsc.mod

- **Target:** `mpsc.Mpsc [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 295910.0
- **Functions:** 21/43 matched (target 51)
- **Missing functions:** `fmt`, `new`, `notify`, `poll_ready_nb`, `queue_push_and_signal`, `inc_num_messages`, `is_connected_to`, `ptr`, `do_send_b`, `park`, `poll_unparked`, `hash_receiver`, `do_send_nb`, `len`, `drop`, `next_message`, `unpark_one`, `dec_num_messages`, `set_closed`, `max_senders`, `decode_state`, `encode_state`
- **Types:** 9/16 matched (target 9)
- **Missing types:** `UnboundedSenderInner`, `BoundedSenderInner`, `AssertKinds`, `SendErrorKind`, `State`, `SenderTask`, `Item`

### 14. stream.select_all

- **Target:** `futures.SelectAllTest [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 222210.0
- **Functions:** 0/17 matched (target 1)
- **Missing functions:** `fmt`, `new`, `len`, `is_empty`, `push`, `iter`, `iter_mut`, `clear`, `default`, `poll_next`, `is_terminated`, `select_all`, `from_iter`, `extend`, `into_iter`, `next`, `size_hint`
- **Types:** 0/5 matched (target 1)
- **Missing types:** `SelectAll`, `Item`, `IntoIter`, `Iter`, `IterMut`
- **Provenance warning:** port-lint provenance header matched only by basename: `futures-util/src/future/select_all.rs` vs expected `stream/select_all.rs`
- **Proposed provenance header:** `// port-lint: source stream/select_all.rs` (current: `// port-lint: source futures-util/src/future/select_all.rs`)
- **Lint issues:** 1

### 15. futures-core.stream

- **Target:** `futures.Stream`
- **Similarity:** 0.15
- **Dependents:** 0
- **Priority Score:** 71308.5
- **Functions:** 3/4 matched (target 9)
- **Missing functions:** `is_terminated`
- **Types:** 3/9 matched (target 7)
- **Missing types:** `BoxStream`, `LocalBoxStream`, `Item`, `Sealed`, `Ok`, `Error`

### 16. futures-channel.oneshot

- **Target:** `oneshot.Oneshot [PROVENANCE-FALLBACK]`
- **Similarity:** 0.51
- **Dependents:** 0
- **Priority Score:** 52304.9
- **Functions:** 14/17 matched (target 31)
- **Missing functions:** `new`, `drop`, `fmt`
- **Types:** 4/6 matched
- **Missing types:** `Inner`, `Output`
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:futures-channel/tests/oneshot.rs` vs expected `oneshot.rs`
- **Proposed provenance header:** `// port-lint: tests oneshot.rs` (current: `// port-lint: tests futures-channel/tests/oneshot.rs`)
- **Lint issues:** 1

### 17. futures-macro.join

- **Target:** `futures.JoinTest [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 50510.0
- **Functions:** 0/4 matched
- **Missing functions:** `parse`, `bind_futures`, `join`, `try_join`
- **Types:** 0/1 matched
- **Missing types:** `Join`
- **Provenance warning:** port-lint provenance header matched only by basename: `futures-util/src/future/join.rs` vs expected `join.rs`
- **Proposed provenance header:** `// port-lint: source join.rs` (current: `// port-lint: source futures-util/src/future/join.rs`)
- **Lint issues:** 1

### 18. future.join_all

- **Target:** `futures.JoinAll`
- **Similarity:** 0.37
- **Dependents:** 0
- **Priority Score:** 40806.3
- **Functions:** 3/5 matched (target 6)
- **Missing functions:** `iter_pin_mut`, `fmt`
- **Types:** 1/3 matched (target 2)
- **Missing types:** `JoinAllKind`, `Output`

### 19. future.try_join_all

- **Target:** `futures.TryJoinAll`
- **Similarity:** 0.42
- **Dependents:** 0
- **Priority Score:** 40805.8
- **Functions:** 3/4 matched (target 6)
- **Missing functions:** `fmt`
- **Types:** 1/4 matched (target 2)
- **Missing types:** `FinalState`, `TryJoinAllKind`, `Output`

### 20. future.select

- **Target:** `futures.Select [PROVENANCE-FALLBACK]`
- **Similarity:** 0.24
- **Dependents:** 0
- **Priority Score:** 40607.6
- **Functions:** 2/4 matched (target 7)
- **Missing functions:** `unwrap_option`, `is_terminated`
- **Types:** 0/2 matched (target 1)
- **Missing types:** `Select`, `Output`
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:futures-util/tests/select.rs` vs expected `future/select.rs`
- **Proposed provenance header:** `// port-lint: tests future/select.rs` (current: `// port-lint: tests futures-util/tests/select.rs`)
- **Lint issues:** 1

### 21. future.poll_immediate

- **Target:** `futures.PollImmediate [PROVENANCE-FALLBACK]`
- **Similarity:** 0.25
- **Dependents:** 0
- **Priority Score:** 40607.5
- **Functions:** 2/4 matched
- **Missing functions:** `is_terminated`, `poll_next`
- **Types:** 0/2 matched (target 1)
- **Missing types:** `Output`, `Item`
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:futures-util/tests/poll_immediate.rs` vs expected `future/poll_immediate.rs`
- **Proposed provenance header:** `// port-lint: tests future/poll_immediate.rs` (current: `// port-lint: tests futures-util/tests/poll_immediate.rs`)
- **Lint issues:** 1

### 22. future.always_ready

- **Target:** `futures.AlwaysReady`
- **Similarity:** 0.37
- **Dependents:** 0
- **Priority Score:** 30706.3
- **Functions:** 3/5 matched (target 4)
- **Missing functions:** `fmt`, `clone`
- **Types:** 1/2 matched
- **Missing types:** `Output`

### 23. future.option

- **Target:** `futures.OptionFuture [PROVENANCE-FALLBACK]`
- **Similarity:** 0.28
- **Dependents:** 0
- **Priority Score:** 30507.2
- **Functions:** 2/4 matched (target 5)
- **Missing functions:** `default`, `from`
- **Types:** 0/1 matched (target 2)
- **Missing types:** `Output`
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:futures-util/tests/option.rs` vs expected `future/option.rs`
- **Proposed provenance header:** `// port-lint: tests future/option.rs` (current: `// port-lint: tests futures-util/tests/option.rs`)
- **Lint issues:** 1

### 24. future.try_maybe_done

- **Target:** `futures.TryMaybeDone`
- **Similarity:** 0.39
- **Dependents:** 0
- **Priority Score:** 20706.1
- **Functions:** 4/5 matched (target 10)
- **Missing functions:** `output_mut`
- **Types:** 1/2 matched
- **Missing types:** `Output`

### 25. future.maybe_done

- **Target:** `futures.MaybeDone`
- **Similarity:** 0.41
- **Dependents:** 0
- **Priority Score:** 20705.9
- **Functions:** 4/5 matched (target 10)
- **Missing functions:** `output_mut`
- **Types:** 1/2 matched
- **Missing types:** `Output`

### 26. future.pending

- **Target:** `futures.Pending`
- **Similarity:** 0.52
- **Dependents:** 0
- **Priority Score:** 20604.8
- **Functions:** 3/4 matched (target 3)
- **Missing functions:** `clone`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `Output`

### 27. future.poll_fn

- **Target:** `futures.PollFn`
- **Similarity:** 0.28
- **Dependents:** 0
- **Priority Score:** 20507.2
- **Functions:** 2/3 matched (target 2)
- **Missing functions:** `fmt`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `Output`

### 28. future.try_join

- **Target:** `futures.TryJoin [PROVENANCE-FALLBACK]`
- **Similarity:** 0.14
- **Dependents:** 0
- **Priority Score:** 20408.6
- **Functions:** 2/4 matched (target 9)
- **Missing functions:** `try_join4`, `try_join5`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:futures-util/tests/try_join.rs` vs expected `future/try_join.rs`
- **Proposed provenance header:** `// port-lint: tests future/try_join.rs` (current: `// port-lint: tests futures-util/tests/try_join.rs`)
- **Lint issues:** 1

### 29. futures-sink.lib

- **Target:** `futures.Sink [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10610.0
- **Functions:** 4/4 matched (target 14)
- **Missing functions:** _none_
- **Types:** 1/2 matched (target 4)
- **Missing types:** `Error`

### 30. future.select_all

- **Target:** `futures.SelectAll`
- **Similarity:** 0.64
- **Dependents:** 0
- **Priority Score:** 10603.6
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 1/2 matched (target 1)
- **Missing types:** `Output`

### 31. future.try_select

- **Target:** `futures.TrySelect`
- **Similarity:** 0.73
- **Dependents:** 0
- **Priority Score:** 10602.7
- **Functions:** 2/2 matched (target 5)
- **Missing functions:** _none_
- **Types:** 3/4 matched
- **Missing types:** `Output`

### 32. future.select_ok

- **Target:** `futures.SelectOk`
- **Similarity:** 0.59
- **Dependents:** 0
- **Priority Score:** 10504.1
- **Functions:** 3/3 matched (target 5)
- **Missing functions:** _none_
- **Types:** 1/2 matched
- **Missing types:** `Output`

### 33. futures-util.stream.mod

- **Target:** `futures.StreamCombinators [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10110.0
- **Functions:** 0/1 matched (target 18)
- **Missing functions:** `assert_stream`
- **Types:** 0/0 matched (target 2)
- **Missing types:** _none_

### 34. futures-util.future.mod

- **Target:** `futures.FutureCombinators [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10110.0
- **Functions:** 0/1 matched (target 37)
- **Missing functions:** `assert_future`
- **Types:** 0/0 matched (target 2)
- **Missing types:** _none_

### 35. future.join

- **Target:** `futures.Join`
- **Similarity:** 0.57
- **Dependents:** 0
- **Priority Score:** 404.3
- **Functions:** 4/4 matched (target 12)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 6)
- **Missing types:** _none_

### 36. futures-core.task.mod

- **Target:** `futures.Task [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10.0
- **Functions:** 0/0 matched (target 5)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 4)
- **Missing types:** _none_

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

## Reexport / Wiring Modules

These files match `reexport_modules` patterns in `.ast_distance_config.json`. They are filtered out of
normal priority and missing-file ladders because they are wiring
modules, not direct logic ports. Consult them for call-site routing;
do not treat them as the next implementation target by default.

### Missing

| Source | Expected target | Deps | Source path | Expected path |
|--------|-----------------|------|-------------|---------------|
| `futures-channel.lib` | `futureschannel.src.Lib` | 0 | `futures-channel/src/lib.rs` | `futureschannel/src/Lib.kt` |
| `futures-core.lib` | `futurescore.src.Lib` | 0 | `futures-core/src/lib.rs` | `futurescore/src/Lib.kt` |
| `__internal.mod` | `futurescore.src.task.internal.Mod` | 0 | `futures-core/src/task/__internal/mod.rs` | `futurescore/src/task/internal/Mod.kt` |
| `futures-executor.lib` | `futuresexecutor.src.Lib` | 0 | `futures-executor/src/lib.rs` | `futuresexecutor/src/Lib.kt` |
| `futures-io.lib` | `futuresio.src.Lib` | 0 | `futures-io/src/lib.rs` | `futuresio/src/Lib.kt` |
| `futures-macro.lib` | `futuresmacro.src.Lib` | 0 | `futures-macro/src/lib.rs` | `futuresmacro/src/Lib.kt` |
| `futures-task.lib` | `futurestask.src.Lib` | 0 | `futures-task/src/lib.rs` | `futurestask/src/Lib.kt` |
| `future.mod` | `futurestest.src.future.Mod` | 0 | `futures-test/src/future/mod.rs` | `futurestest/src/future/Mod.kt` |
| `io.mod` | `futurestest.src.io.Mod` | 0 | `futures-test/src/io/mod.rs` | `futurestest/src/io/Mod.kt` |
| `read.mod` | `futurestest.src.io.read.Mod` | 0 | `futures-test/src/io/read/mod.rs` | `futurestest/src/io/read/Mod.kt` |
| `write.mod` | `futurestest.src.io.write.Mod` | 0 | `futures-test/src/io/write/mod.rs` | `futurestest/src/io/write/Mod.kt` |
| `futures-test.lib` | `futurestest.src.Lib` | 0 | `futures-test/src/lib.rs` | `futurestest/src/Lib.kt` |
| `sink.mod` | `futurestest.src.sink.Mod` | 0 | `futures-test/src/sink/mod.rs` | `futurestest/src/sink/Mod.kt` |
| `stream.mod` | `futurestest.src.stream.Mod` | 0 | `futures-test/src/stream/mod.rs` | `futurestest/src/stream/Mod.kt` |
| `task.mod` | `futurestest.src.task.Mod` | 0 | `futures-test/src/task/mod.rs` | `futurestest/src/task/Mod.kt` |
| `async_await.mod` | `futuresutil.src.asyncawait.Mod` | 0 | `futures-util/src/async_await/mod.rs` | `futuresutil/src/asyncawait/Mod.kt` |
| `compat.mod` | `futuresutil.src.compat.Mod` | 0 | `futures-util/src/compat/mod.rs` | `futuresutil/src/compat/Mod.kt` |
| `futures-util.future.future.mod` | `futuresutil.src.future.future.Mod` | 0 | `futures-util/src/future/future/mod.rs` | `futuresutil/src/future/future/Mod.kt` |
| `try_future.mod` | `futuresutil.src.future.tryfuture.Mod` | 0 | `futures-util/src/future/try_future/mod.rs` | `futuresutil/src/future/tryfuture/Mod.kt` |
| `futures-util.io.mod` | `futuresutil.src.io.Mod` | 0 | `futures-util/src/io/mod.rs` | `futuresutil/src/io/Mod.kt` |
| `futures-util.lib` | `futuresutil.src.Lib` | 0 | `futures-util/src/lib.rs` | `futuresutil/src/Lib.kt` |
| `lock.mod` | `futuresutil.src.lock.Mod` | 0 | `futures-util/src/lock/mod.rs` | `futuresutil/src/lock/Mod.kt` |
| `futures-util.sink.mod` | `futuresutil.src.sink.Mod` | 0 | `futures-util/src/sink/mod.rs` | `futuresutil/src/sink/Mod.kt` |
| `futures_unordered.mod` | `futuresutil.src.stream.futuresunordered.Mod` | 0 | `futures-util/src/stream/futures_unordered/mod.rs` | `futuresutil/src/stream/futuresunordered/Mod.kt` |
| `futures-util.stream.stream.mod` | `futuresutil.src.stream.stream.Mod` | 0 | `futures-util/src/stream/stream/mod.rs` | `futuresutil/src/stream/stream/Mod.kt` |
| `try_stream.mod` | `futuresutil.src.stream.trystream.Mod` | 0 | `futures-util/src/stream/try_stream/mod.rs` | `futuresutil/src/stream/trystream/Mod.kt` |
| `futures-util.task.mod` | `futuresutil.src.task.Mod` | 0 | `futures-util/src/task/mod.rs` | `futuresutil/src/task/Mod.kt` |
| `futures.lib` | `futures.src.Lib` | 0 | `futures/src/lib.rs` | `futures/src/Lib.kt` |

