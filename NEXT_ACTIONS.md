# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 28/181 (15.5%)
- **Function parity:** 70/1030 matched (target 285) — 6.8%
- **Class/type parity:** 20/357 matched (target 74) — 5.6%
- **Combined symbol parity:** 90/1387 matched (target 359) — 6.5%
- **Average inline-code cosine:** 0.38 (function body across 23 matched files)
- **Average documentation cosine:** 0.57 (doc text across 23 matched files)
- **Cheat-zeroed Files:** 5
- **Critical Issues:** 25 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

### 1. future.ready
- **Similarity:** 0.43 (needs 42% improvement)
- **Dependencies:** 82
- **Priority Score:** 82010808.0
- **Functions:** 6/6 matched (target 10)
- **Missing functions:** _none_
- **Types:** 1/2 matched
- **Missing types:** `Output`
- **Symbol Deficit:** 1 (functions: 0, types: 1)
- **Action:** Deep review - likely missing major functionality

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

1. **io.sink** (54 deps)
   - Path: `io/sink.rs`
   - Essential for 54 other files

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. future.ready

- **Target:** `futures.Ready [PROVENANCE-FALLBACK]`
- **Similarity:** 0.43
- **Dependents:** 82
- **Priority Score:** 82010808.0
- **Functions:** 6/6 matched (target 10)
- **Missing functions:** _none_
- **Types:** 1/2 matched
- **Missing types:** `Output`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/ready.rs` vs expected `future/ready.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/ready.rs` vs expected `future/ready.rs`
- **Proposed provenance header:** `// port-lint: source future/ready.rs` (current: `// port-lint: source futures-util/src/future/ready.rs`)
- **Proposed provenance header:** `// port-lint: source future/ready.rs` (current: `// port-lint: source futures-util/src/future/ready.rs`)
- **Lint issues:** 3

### 2. future.either

- **Target:** `futures.Either [PROVENANCE-FALLBACK]`
- **Similarity:** 0.31
- **Dependents:** 2
- **Priority Score:** 2122406.8
- **Functions:** 11/20 matched (target 24)
- **Missing functions:** `as_pin_ref`, `as_pin_mut`, `poll_read`, `poll_read_vectored`, `poll_write`, `poll_write_vectored`, `poll_seek`, `poll_fill_buf`, `consume`
- **Types:** 1/4 matched
- **Missing types:** `Output`, `Item`, `Error`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/either.rs` vs expected `future/either.rs`
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:futures-util/tests/either.rs` vs expected `future/either.rs`
- **Proposed provenance header:** `// port-lint: source future/either.rs` (current: `// port-lint: source futures-util/src/future/either.rs`)
- **Proposed provenance header:** `// port-lint: tests future/either.rs` (current: `// port-lint: tests futures-util/tests/either.rs`)
- **Lint issues:** 2

### 3. abortable

- **Target:** `futures.Abortable [PROVENANCE-FALLBACK]`
- **Similarity:** 0.49
- **Dependents:** 2
- **Priority Score:** 2051505.0
- **Functions:** 6/9 matched (target 20)
- **Missing functions:** `new`, `fmt`, `try_poll`
- **Types:** 4/6 matched (target 7)
- **Missing types:** `Output`, `Item`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/abortable.rs` vs expected `abortable.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `tests:futures-util/src/abortable.rs` vs expected `abortable.rs`
- **Proposed provenance header:** `// port-lint: source abortable.rs` (current: `// port-lint: source futures-util/src/abortable.rs`)
- **Proposed provenance header:** `// port-lint: tests abortable.rs` (current: `// port-lint: tests futures-util/src/abortable.rs`)
- **Lint issues:** 2

### 4. unfold_state

- **Target:** `futures.UnfoldState [PROVENANCE-FALLBACK]`
- **Similarity:** 0.19
- **Dependents:** 2
- **Priority Score:** 2000208.1
- **Functions:** 2/2 matched (target 4)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 4)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/unfold_state.rs` vs expected `unfold_state.rs`
- **Proposed provenance header:** `// port-lint: source unfold_state.rs` (current: `// port-lint: source futures-util/src/unfold_state.rs`)
- **Lint issues:** 1

### 5. never

- **Target:** `futures.Never [PROVENANCE-FALLBACK]`
- **Similarity:** 1.00
- **Dependents:** 2
- **Priority Score:** 2000100.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/never.rs` vs expected `never.rs`
- **Proposed provenance header:** `// port-lint: source never.rs` (current: `// port-lint: source futures-util/src/never.rs`)
- **Lint issues:** 1

### 6. stream.select_all

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

### 7. stream.poll_fn

- **Target:** `futures.PollFnTest [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 50510.0
- **Functions:** 0/3 matched (target 1)
- **Missing functions:** `fmt`, `poll_fn`, `poll_next`
- **Types:** 0/2 matched (target 1)
- **Missing types:** `PollFn`, `Item`
- **Provenance warning:** port-lint provenance header matched only by basename: `futures-util/src/future/poll_fn.rs` vs expected `stream/poll_fn.rs`
- **Proposed provenance header:** `// port-lint: source stream/poll_fn.rs` (current: `// port-lint: source futures-util/src/future/poll_fn.rs`)
- **Lint issues:** 1

### 8. future.join_all

- **Target:** `futures.JoinAll [PROVENANCE-FALLBACK]`
- **Similarity:** 0.37
- **Dependents:** 0
- **Priority Score:** 40806.3
- **Functions:** 3/5 matched (target 6)
- **Missing functions:** `iter_pin_mut`, `fmt`
- **Types:** 1/3 matched (target 2)
- **Missing types:** `JoinAllKind`, `Output`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/join_all.rs` vs expected `future/join_all.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/join_all.rs` vs expected `future/join_all.rs`
- **Proposed provenance header:** `// port-lint: source future/join_all.rs` (current: `// port-lint: source futures-util/src/future/join_all.rs`)
- **Proposed provenance header:** `// port-lint: source future/join_all.rs` (current: `// port-lint: source futures-util/src/future/join_all.rs`)
- **Lint issues:** 2

### 9. future.try_join_all

- **Target:** `futures.TryJoinAll [PROVENANCE-FALLBACK]`
- **Similarity:** 0.42
- **Dependents:** 0
- **Priority Score:** 40805.8
- **Functions:** 3/4 matched (target 6)
- **Missing functions:** `fmt`
- **Types:** 1/4 matched (target 2)
- **Missing types:** `FinalState`, `TryJoinAllKind`, `Output`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/try_join_all.rs` vs expected `future/try_join_all.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/try_join_all.rs` vs expected `future/try_join_all.rs`
- **Proposed provenance header:** `// port-lint: source future/try_join_all.rs` (current: `// port-lint: source futures-util/src/future/try_join_all.rs`)
- **Proposed provenance header:** `// port-lint: source future/try_join_all.rs` (current: `// port-lint: source futures-util/src/future/try_join_all.rs`)
- **Lint issues:** 2

### 10. future.select

- **Target:** `futures.Select [PROVENANCE-FALLBACK]`
- **Similarity:** 0.24
- **Dependents:** 0
- **Priority Score:** 40607.6
- **Functions:** 2/4 matched (target 7)
- **Missing functions:** `unwrap_option`, `is_terminated`
- **Types:** 0/2 matched (target 1)
- **Missing types:** `Select`, `Output`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/select.rs` vs expected `future/select.rs`
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:futures-util/tests/select.rs` vs expected `future/select.rs`
- **Proposed provenance header:** `// port-lint: source future/select.rs` (current: `// port-lint: source futures-util/src/future/select.rs`)
- **Proposed provenance header:** `// port-lint: tests future/select.rs` (current: `// port-lint: tests futures-util/tests/select.rs`)
- **Lint issues:** 2

### 11. future.poll_immediate

- **Target:** `futures.PollImmediate [PROVENANCE-FALLBACK]`
- **Similarity:** 0.25
- **Dependents:** 0
- **Priority Score:** 40607.5
- **Functions:** 2/4 matched
- **Missing functions:** `is_terminated`, `poll_next`
- **Types:** 0/2 matched (target 1)
- **Missing types:** `Output`, `Item`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/poll_immediate.rs` vs expected `future/poll_immediate.rs`
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:futures-util/tests/poll_immediate.rs` vs expected `future/poll_immediate.rs`
- **Proposed provenance header:** `// port-lint: source future/poll_immediate.rs` (current: `// port-lint: source futures-util/src/future/poll_immediate.rs`)
- **Proposed provenance header:** `// port-lint: tests future/poll_immediate.rs` (current: `// port-lint: tests futures-util/tests/poll_immediate.rs`)
- **Lint issues:** 2

### 12. future.always_ready

- **Target:** `futures.AlwaysReady [PROVENANCE-FALLBACK]`
- **Similarity:** 0.37
- **Dependents:** 0
- **Priority Score:** 30706.3
- **Functions:** 3/5 matched (target 4)
- **Missing functions:** `fmt`, `clone`
- **Types:** 1/2 matched
- **Missing types:** `Output`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/always_ready.rs` vs expected `future/always_ready.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/always_ready.rs` vs expected `future/always_ready.rs`
- **Proposed provenance header:** `// port-lint: source future/always_ready.rs` (current: `// port-lint: source futures-util/src/future/always_ready.rs`)
- **Proposed provenance header:** `// port-lint: source future/always_ready.rs` (current: `// port-lint: source futures-util/src/future/always_ready.rs`)
- **Lint issues:** 2

### 13. future.option

- **Target:** `futures.OptionFuture [PROVENANCE-FALLBACK]`
- **Similarity:** 0.28
- **Dependents:** 0
- **Priority Score:** 30507.2
- **Functions:** 2/4 matched (target 5)
- **Missing functions:** `default`, `from`
- **Types:** 0/1 matched (target 2)
- **Missing types:** `Output`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/option.rs` vs expected `future/option.rs`
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:futures-util/tests/option.rs` vs expected `future/option.rs`
- **Proposed provenance header:** `// port-lint: source future/option.rs` (current: `// port-lint: source futures-util/src/future/option.rs`)
- **Proposed provenance header:** `// port-lint: tests future/option.rs` (current: `// port-lint: tests futures-util/tests/option.rs`)
- **Lint issues:** 2

### 14. async_await.mod

- **Target:** `mpsc.Mpsc [STUB] [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 30310.0
- **Functions:** 0/3 matched (target 51)
- **Missing functions:** `assert_unpin`, `assert_fused_future`, `assert_fused_stream`
- **Types:** 0/0 matched (target 9)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only by basename: `futures-channel/src/mpsc/mod.rs` vs expected `async_await/mod.rs`
- **Proposed provenance header:** `// port-lint: source async_await/mod.rs` (current: `// port-lint: source futures-channel/src/mpsc/mod.rs`)
- **Lint issues:** 1

### 15. future.try_maybe_done

- **Target:** `futures.TryMaybeDone [PROVENANCE-FALLBACK]`
- **Similarity:** 0.39
- **Dependents:** 0
- **Priority Score:** 20706.1
- **Functions:** 4/5 matched (target 10)
- **Missing functions:** `output_mut`
- **Types:** 1/2 matched
- **Missing types:** `Output`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/try_maybe_done.rs` vs expected `future/try_maybe_done.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/try_maybe_done.rs` vs expected `future/try_maybe_done.rs`
- **Proposed provenance header:** `// port-lint: source future/try_maybe_done.rs` (current: `// port-lint: source futures-util/src/future/try_maybe_done.rs`)
- **Proposed provenance header:** `// port-lint: source future/try_maybe_done.rs` (current: `// port-lint: source futures-util/src/future/try_maybe_done.rs`)
- **Lint issues:** 2

### 16. future.maybe_done

- **Target:** `futures.MaybeDone [PROVENANCE-FALLBACK]`
- **Similarity:** 0.41
- **Dependents:** 0
- **Priority Score:** 20705.9
- **Functions:** 4/5 matched (target 10)
- **Missing functions:** `output_mut`
- **Types:** 1/2 matched
- **Missing types:** `Output`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/maybe_done.rs` vs expected `future/maybe_done.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/maybe_done.rs` vs expected `future/maybe_done.rs`
- **Proposed provenance header:** `// port-lint: source future/maybe_done.rs` (current: `// port-lint: source futures-util/src/future/maybe_done.rs`)
- **Proposed provenance header:** `// port-lint: source future/maybe_done.rs` (current: `// port-lint: source futures-util/src/future/maybe_done.rs`)
- **Lint issues:** 2

### 17. future.pending

- **Target:** `futures.Pending [PROVENANCE-FALLBACK]`
- **Similarity:** 0.52
- **Dependents:** 0
- **Priority Score:** 20604.8
- **Functions:** 3/4 matched (target 3)
- **Missing functions:** `clone`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `Output`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/pending.rs` vs expected `future/pending.rs`
- **Proposed provenance header:** `// port-lint: source future/pending.rs` (current: `// port-lint: source futures-util/src/future/pending.rs`)
- **Lint issues:** 1

### 18. future.lazy

- **Target:** `futures.LazyFuture [PROVENANCE-FALLBACK]`
- **Similarity:** 0.23
- **Dependents:** 0
- **Priority Score:** 20507.7
- **Functions:** 2/3 matched (target 4)
- **Missing functions:** `lazy`
- **Types:** 1/2 matched
- **Missing types:** `Output`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/lazy.rs` vs expected `future/lazy.rs`
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:futures-util/tests/lazy.rs` vs expected `future/lazy.rs`
- **Proposed provenance header:** `// port-lint: source future/lazy.rs` (current: `// port-lint: source futures-util/src/future/lazy.rs`)
- **Proposed provenance header:** `// port-lint: tests future/lazy.rs` (current: `// port-lint: tests futures-util/tests/lazy.rs`)
- **Lint issues:** 2

### 19. future.poll_fn

- **Target:** `futures.PollFn [PROVENANCE-FALLBACK]`
- **Similarity:** 0.28
- **Dependents:** 0
- **Priority Score:** 20507.2
- **Functions:** 2/3 matched (target 2)
- **Missing functions:** `fmt`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `Output`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/poll_fn.rs` vs expected `future/poll_fn.rs`
- **Proposed provenance header:** `// port-lint: source future/poll_fn.rs` (current: `// port-lint: source futures-util/src/future/poll_fn.rs`)
- **Lint issues:** 1

### 20. future.try_join

- **Target:** `futures.TryJoin [PROVENANCE-FALLBACK]`
- **Similarity:** 0.14
- **Dependents:** 0
- **Priority Score:** 20408.6
- **Functions:** 2/4 matched (target 9)
- **Missing functions:** `try_join4`, `try_join5`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/try_join.rs` vs expected `future/try_join.rs`
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:futures-util/tests/try_join.rs` vs expected `future/try_join.rs`
- **Proposed provenance header:** `// port-lint: source future/try_join.rs` (current: `// port-lint: source futures-util/src/future/try_join.rs`)
- **Proposed provenance header:** `// port-lint: tests future/try_join.rs` (current: `// port-lint: tests futures-util/tests/try_join.rs`)
- **Lint issues:** 2

### 21. future.select_all

- **Target:** `futures.SelectAll [PROVENANCE-FALLBACK]`
- **Similarity:** 0.64
- **Dependents:** 0
- **Priority Score:** 10603.6
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 1/2 matched (target 1)
- **Missing types:** `Output`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/select_all.rs` vs expected `future/select_all.rs`
- **Proposed provenance header:** `// port-lint: source future/select_all.rs` (current: `// port-lint: source futures-util/src/future/select_all.rs`)
- **Lint issues:** 1

### 22. future.try_select

- **Target:** `futures.TrySelect [PROVENANCE-FALLBACK]`
- **Similarity:** 0.73
- **Dependents:** 0
- **Priority Score:** 10602.7
- **Functions:** 2/2 matched (target 5)
- **Missing functions:** _none_
- **Types:** 3/4 matched
- **Missing types:** `Output`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/try_select.rs` vs expected `future/try_select.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/try_select.rs` vs expected `future/try_select.rs`
- **Proposed provenance header:** `// port-lint: source future/try_select.rs` (current: `// port-lint: source futures-util/src/future/try_select.rs`)
- **Proposed provenance header:** `// port-lint: source future/try_select.rs` (current: `// port-lint: source futures-util/src/future/try_select.rs`)
- **Lint issues:** 2

### 23. future.select_ok

- **Target:** `futures.SelectOk [PROVENANCE-FALLBACK]`
- **Similarity:** 0.59
- **Dependents:** 0
- **Priority Score:** 10504.1
- **Functions:** 3/3 matched (target 5)
- **Missing functions:** _none_
- **Types:** 1/2 matched
- **Missing types:** `Output`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/select_ok.rs` vs expected `future/select_ok.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/select_ok.rs` vs expected `future/select_ok.rs`
- **Proposed provenance header:** `// port-lint: source future/select_ok.rs` (current: `// port-lint: source futures-util/src/future/select_ok.rs`)
- **Proposed provenance header:** `// port-lint: source future/select_ok.rs` (current: `// port-lint: source futures-util/src/future/select_ok.rs`)
- **Lint issues:** 2

### 24. stream.mod

- **Target:** `futures.StreamCombinators [STUB] [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10110.0
- **Functions:** 0/1 matched (target 18)
- **Missing functions:** `assert_stream`
- **Types:** 0/0 matched (target 2)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/stream/mod.rs` vs expected `stream/mod.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `tests:futures-util/src/stream/mod.rs` vs expected `stream/mod.rs`
- **Proposed provenance header:** `// port-lint: source stream/mod.rs` (current: `// port-lint: source futures-util/src/stream/mod.rs`)
- **Proposed provenance header:** `// port-lint: tests stream/mod.rs` (current: `// port-lint: tests futures-util/src/stream/mod.rs`)
- **Lint issues:** 2

### 25. future.mod

- **Target:** `futures.FutureCombinators [STUB] [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10110.0
- **Functions:** 0/1 matched (target 37)
- **Missing functions:** `assert_future`
- **Types:** 0/0 matched (target 2)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/mod.rs` vs expected `future/mod.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `tests:futures-util/src/future/mod.rs` vs expected `future/mod.rs`
- **Proposed provenance header:** `// port-lint: source future/mod.rs` (current: `// port-lint: source futures-util/src/future/mod.rs`)
- **Proposed provenance header:** `// port-lint: tests future/mod.rs` (current: `// port-lint: tests futures-util/src/future/mod.rs`)
- **Lint issues:** 2

### 26. future.join

- **Target:** `futures.Join [PROVENANCE-FALLBACK]`
- **Similarity:** 0.57
- **Dependents:** 0
- **Priority Score:** 404.3
- **Functions:** 4/4 matched (target 16)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 7)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/join.rs` vs expected `future/join.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-util/src/future/join.rs` vs expected `future/join.rs`
- **Proposed provenance header:** `// port-lint: source future/join.rs` (current: `// port-lint: source futures-util/src/future/join.rs`)
- **Proposed provenance header:** `// port-lint: source future/join.rs` (current: `// port-lint: source futures-util/src/future/join.rs`)
- **Lint issues:** 2

### 27. lib

- **Target:** `futures.Sink [STUB] [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10.0
- **Functions:** 0/0 matched (target 14)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 4)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-sink/src/lib.rs` vs expected `lib.rs`
- **Proposed provenance header:** `// port-lint: source lib.rs` (current: `// port-lint: source futures-sink/src/lib.rs`)
- **Lint issues:** 1

### 28. task.mod

- **Target:** `futures.Task [STUB] [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10.0
- **Functions:** 0/0 matched (target 5)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 4)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-core/src/task/mod.rs` vs expected `task/mod.rs`
- **Proposed provenance header:** `// port-lint: source task/mod.rs` (current: `// port-lint: source futures-core/src/task/mod.rs`)
- **Lint issues:** 1

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
| `compat.mod` | `compat.Mod` | 0 | `compat/mod.rs` | `compat/Mod.kt` |
| `future.future.mod` | `future.future.Mod` | 0 | `future/future/mod.rs` | `future/future/Mod.kt` |
| `try_future.mod` | `future.tryfuture.Mod` | 0 | `future/try_future/mod.rs` | `future/tryfuture/Mod.kt` |
| `io.mod` | `io.Mod` | 0 | `io/mod.rs` | `io/Mod.kt` |
| `lock.mod` | `lock.Mod` | 0 | `lock/mod.rs` | `lock/Mod.kt` |
| `sink.mod` | `sink.Mod` | 0 | `sink/mod.rs` | `sink/Mod.kt` |
| `futures_unordered.mod` | `stream.futuresunordered.Mod` | 0 | `stream/futures_unordered/mod.rs` | `stream/futuresunordered/Mod.kt` |
| `stream.stream.mod` | `stream.stream.Mod` | 0 | `stream/stream/mod.rs` | `stream/stream/Mod.kt` |
| `try_stream.mod` | `stream.trystream.Mod` | 0 | `stream/try_stream/mod.rs` | `stream/trystream/Mod.kt` |

