# Next Actions - futures-kotlin

Based on AST analysis and port progress:

## Summary
- **Production Definitions:** 597/1363 (43.8%)
- **Test Definitions:** 7/17 (41.2%)
- **Extra Real Symbols (Kotlin-only):** 327 real + 0 stubs
- **Multiplatform Test Suite:** 100% passing across JVM, JS (Node), WasmJS (Node), WasmWASI (Node)

## Ported Components
- `PollFn.kt` (source `futures-util/src/future/poll_fn.rs`): `PollFn`, `pollFn`
- `Join.kt` (source `futures-util/src/future/join.rs`): `Join`, `Join3`, `Join4`, `Join5`, `Tuple4`, `Tuple5`, `join`, `join3`, `join4`, `join5`
- `JoinAll.kt` (source `futures-util/src/future/join_all.rs`): `JoinAll`, `joinAll`
- `TryJoinAll.kt` (source `futures-util/src/future/try_join_all.rs`): `TryJoinAll`, `tryJoinAll`
- `SelectAll.kt` (source `futures-util/src/future/select_all.rs`): `SelectAll`, `selectAll`
- `SelectOk.kt` (source `futures-util/src/future/select_ok.rs`): `SelectOk`, `selectOk`
- `TrySelect.kt` (source `futures-util/src/future/try_select.rs`): `TrySelect`, `trySelect`
- `MaybeDone.kt` (source `futures-util/src/future/maybe_done.rs`): `MaybeDone`, `maybeDone`
- `TryMaybeDone.kt` (source `futures-util/src/future/try_maybe_done.rs`): `TryMaybeDone`, `tryMaybeDone`
- `Ready.kt` (source `futures-util/src/future/ready.rs`): `Ready`, `ready`, `ok`, `err`
- `Pending.kt` (source `futures-util/src/future/pending.rs`): `Pending`, `pending`
- `AlwaysReady.kt` (source `futures-util/src/future/always_ready.rs`): `AlwaysReady`, `alwaysReady`
- `Abortable.kt` (source `futures-util/src/abortable.rs`): `Abortable`, `AbortHandle`, `AbortRegistration`, `Aborted`, `abortable`
- `Never.kt` (source `futures-util/src/never.rs`): `typealias Never = Nothing`
- `UnfoldState.kt` (source `futures-util/src/unfold_state.rs`): `UnfoldState`
