# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 2/181 (1.1%)
- **Function parity:** 0/1035 matched (target 19) — 0.0%
- **Class/type parity:** 0/357 matched (target 8) — 0.0%
- **Combined symbol parity:** 0/1392 matched (target 27) — 0.0%
- **Average inline-code cosine:** 0.00 (function body across 0 matched files)
- **Average documentation cosine:** 0.00 (doc text across 0 matched files)
- **Cheat-zeroed Files:** 2
- **Critical Issues:** 2 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

1. **future.ready** (82 deps)
   - Path: `future/ready.rs`
   - Essential for 82 other files

2. **io.sink** (54 deps)
   - Path: `io/sink.rs`
   - Essential for 54 other files

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. lib

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

### 2. task.mod

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
| `async_await.mod` | `asyncawait.Mod` | 0 | `async_await/mod.rs` | `asyncawait/Mod.kt` |
| `compat.mod` | `compat.Mod` | 0 | `compat/mod.rs` | `compat/Mod.kt` |
| `future.future.mod` | `future.future.Mod` | 0 | `future/future/mod.rs` | `future/future/Mod.kt` |
| `future.mod` | `future.Mod` | 0 | `future/mod.rs` | `future/Mod.kt` |
| `try_future.mod` | `future.tryfuture.Mod` | 0 | `future/try_future/mod.rs` | `future/tryfuture/Mod.kt` |
| `io.mod` | `io.Mod` | 0 | `io/mod.rs` | `io/Mod.kt` |
| `lock.mod` | `lock.Mod` | 0 | `lock/mod.rs` | `lock/Mod.kt` |
| `sink.mod` | `sink.Mod` | 0 | `sink/mod.rs` | `sink/Mod.kt` |
| `futures_unordered.mod` | `stream.futuresunordered.Mod` | 0 | `stream/futures_unordered/mod.rs` | `stream/futuresunordered/Mod.kt` |
| `stream.mod` | `stream.Mod` | 0 | `stream/mod.rs` | `stream/Mod.kt` |
| `stream.stream.mod` | `stream.stream.Mod` | 0 | `stream/stream/mod.rs` | `stream/stream/Mod.kt` |
| `try_stream.mod` | `stream.trystream.Mod` | 0 | `stream/try_stream/mod.rs` | `stream/trystream/Mod.kt` |

