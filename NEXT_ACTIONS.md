# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 6/7 (85.7%)
- **Function parity:** 8/12 matched (target 143) — 66.7%
- **Class/type parity:** 9/18 matched (target 36) — 50.0%
- **Combined symbol parity:** 17/30 matched (target 179) — 56.7%
- **Average inline-code cosine:** 0.19 (function body across 3 matched files)
- **Average documentation cosine:** 0.54 (doc text across 3 matched files)
- **Cheat-zeroed Files:** 3
- **Critical Issues:** 6 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. __internal.atomic_waker

- **Target:** `futures.AtomicWaker [PROVENANCE-FALLBACK]`
- **Similarity:** 0.34
- **Dependents:** 1
- **Priority Score:** 1030806.6
- **Functions:** 4/6 matched (target 4)
- **Missing functions:** `default`, `fmt`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `AssertSync`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-core/src/task/__internal/atomic_waker.rs` vs expected `task/__internal/atomic_waker.rs`
- **Proposed provenance header:** `// port-lint: source task/__internal/atomic_waker.rs` (current: `// port-lint: source futures-core/src/task/__internal/atomic_waker.rs`)
- **Lint issues:** 1

### 2. stream

- **Target:** `futures.Stream [PROVENANCE-FALLBACK]`
- **Similarity:** 0.15
- **Dependents:** 0
- **Priority Score:** 71308.5
- **Functions:** 3/4 matched (target 9)
- **Missing functions:** `is_terminated`
- **Types:** 3/9 matched (target 7)
- **Missing types:** `BoxStream`, `LocalBoxStream`, `Item`, `Sealed`, `Ok`, `Error`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-core/src/stream.rs` vs expected `stream.rs`
- **Proposed provenance header:** `// port-lint: source stream.rs` (current: `// port-lint: source futures-core/src/stream.rs`)
- **Lint issues:** 1

### 3. future

- **Target:** `futures.Future [PROVENANCE-FALLBACK]`
- **Similarity:** 0.10
- **Dependents:** 0
- **Priority Score:** 30909.0
- **Functions:** 1/2 matched (target 5)
- **Missing functions:** `is_terminated`
- **Types:** 5/7 matched
- **Missing types:** `Sealed`, `Error`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `futures-core/src/future.rs` vs expected `future.rs`
- **Proposed provenance header:** `// port-lint: source future.rs` (current: `// port-lint: source futures-core/src/future.rs`)
- **Lint issues:** 1

### 4. lib

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

### 5. task.mod

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

### 6. __internal.mod

- **Target:** `futures.FutureCombinators [STUB] [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10.0
- **Functions:** 0/0 matched (target 106)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 13)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only by basename: `futures-util/src/future/mod.rs` vs expected `task/__internal/mod.rs`
- **Provenance warning:** port-lint provenance header matched only by basename: `futures-util/src/stream/mod.rs` vs expected `task/__internal/mod.rs`
- **Provenance warning:** port-lint provenance header matched only by basename: `futures-channel/src/mpsc/mod.rs` vs expected `task/__internal/mod.rs`
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:futures-util/src/future/mod.rs` vs expected `task/__internal/mod.rs`
- **Provenance warning:** port-lint provenance header matched only by basename: `tests:futures-util/src/stream/mod.rs` vs expected `task/__internal/mod.rs`
- **Proposed provenance header:** `// port-lint: source task/__internal/mod.rs` (current: `// port-lint: source futures-util/src/future/mod.rs`)
- **Proposed provenance header:** `// port-lint: source task/__internal/mod.rs` (current: `// port-lint: source futures-util/src/stream/mod.rs`)
- **Proposed provenance header:** `// port-lint: source task/__internal/mod.rs` (current: `// port-lint: source futures-channel/src/mpsc/mod.rs`)
- **Proposed provenance header:** `// port-lint: tests task/__internal/mod.rs` (current: `// port-lint: tests futures-util/src/future/mod.rs`)
- **Proposed provenance header:** `// port-lint: tests task/__internal/mod.rs` (current: `// port-lint: tests futures-util/src/stream/mod.rs`)
- **Lint issues:** 5

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

