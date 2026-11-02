# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 11/16 (68.8%)
- **Function parity:** 105/308 matched (target 353) — 34.1%
- **Class/type parity:** 39/80 matched (target 137) — 48.8%
- **Combined symbol parity:** 144/388 matched (target 490) — 37.1%
- **Average inline-code cosine:** 0.01 (function body across 9 matched files)
- **Average documentation cosine:** 0.34 (doc text across 9 matched files)
- **Cheat-zeroed Files:** 6
- **Critical Issues:** 11 files with <0.60 function similarity
- **Needs Review:** 0 files with 0.60-0.84 function similarity
- **Excellent:** 0 files with >=0.85 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. to_tokens

- **Target:** `ToTokens [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 2
- **Priority Score:** 2273710.0
- **Functions:** 9/36 matched (target 32)
- **Missing functions:** `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`
- **Types:** 1/1 matched (target 6)
- **Missing types:** _none_

### 2. bridge.symbol

- **Target:** `bridge.Symbol`
- **Similarity:** 0.00
- **Dependents:** 1
- **Priority Score:** 1151710.0
- **Functions:** 1/15 matched (target 5)
- **Missing functions:** `new`, `new_ident`, `with`, `invalidate_all`, `is_valid_ascii_ident`, `can_be_raw`, `fmt`, `fmt`, `encode`, `decode`, `encode`, `decode`, `get`, `clear`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `Interner`

### 3. lib

- **Target:** `Span [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 502310.0
- **Functions:** 60/106 matched (target 146)
- **Missing functions:** `fmt`, `fmt`, `from_str`, `fmt`, `fmt`, `default`, `tree_to_bridge_tree`, `from`, `push`, `build`, `append_to`, `push`, `build`, `append_to`, `from_iter`, `from_iter`, `extend`, `extend`, `size_hint`, `count`, `into_iter`, `fmt`, `fmt`, `from`, `from`, `from`, `from`, `fmt`, `fmt`, `fmt`, `new`, `fmt`, `fmt`, `eq`, `new`, `fmt`, `fmt`, `new`, `with_symbol_and_suffix`, `with_stringify_parts`, `get_hashes_str`, `from_str`, `fmt`, `fmt`, `env_var`, `path`
- **Types:** 14/17 matched (target 50)
- **Missing types:** `ConcatTreesHelper`, `ConcatStreamsHelper`, `Item`

### 4. bridge.client

- **Target:** `bridge.Client`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 313210.0
- **Functions:** 1/25 matched (target 62)
- **Missing functions:** `drop`, `encode`, `encode`, `decode`, `encode`, `decode`, `clone`, `def_site`, `call_site`, `mixed_site`, `fmt`, `set`, `drop`, `with`, `with`, `clone`, `maybe_install_panic_hook`, `run_client`, `expand1`, `expand2`, `name`, `custom_derive`, `attr`, `bang`
- **Types:** 0/7 matched (target 10)
- **Missing types:** `HandleCounters`, `TokenStream`, `Span`, `Bridge`, `RestoreOnDrop`, `Client`, `ProcMacro`

### 5. bridge.rpc

- **Target:** `bridge.Rpc`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 232610.0
- **Functions:** 2/22 matched (target 9)
- **Missing functions:** `encode`, `decode`, `encode`, `decode`, `encode`, `decode`, `encode`, `decode`, `encode`, `decode`, `encode`, `decode`, `encode`, `decode`, `encode`, `decode`, `from`, `from`, `encode`, `decode`
- **Types:** 1/4 matched (target 19)
- **Missing types:** `Encode`, `Decode`, `UnknownPanicMessage`

### 6. bridge.server

- **Target:** `bridge.Server`
- **Similarity:** 0.01
- **Dependents:** 0
- **Priority Score:** 202409.9
- **Functions:** 1/15 matched (target 12)
- **Missing functions:** `new`, `encode`, `decode`, `decode`, `encode`, `decode`, `new`, `drop`, `new`, `send`, `recv`, `run_server`, `run`, `run`
- **Types:** 3/9 matched (target 4)
- **Missing types:** `HandleStore`, `MarkedTokenStream`, `MarkedSpan`, `MarkedSymbol`, `RunningSameThreadGuard`, `MessagePipe`
- **Lint issues:** 3

### 7. quote

- **Target:** `Quote [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 153010.0
- **Functions:** 11/21 matched (target 45)
- **Missing functions:** `bitor`, `bitor`, `bitor`, `bitor`, `quote_into_iter`, `quote_into_iter`, `quote_into_iter`, `next`, `to_tokens`, `helper`
- **Types:** 4/9 matched (target 10)
- **Missing types:** `Output`, `RepIteratorExt`, `RepAsIteratorExt`, `Iter`, `Item`

### 8. bridge.mod

- **Target:** `bridge.BridgeTypes [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 142110.0
- **Functions:** 0/7 matched (target 4)
- **Missing functions:** `mark`, `unmark`, `mark`, `unmark`, `mark`, `unmark`, `from_single`
- **Types:** 7/14 matched (target 26)
- **Missing types:** `Methods`, `Mark`, `Marked`, `Unmarked`, `LitKind`, `TokenTree`, `Diagnostic`

### 9. bridge.handle

- **Target:** `bridge.Handle`
- **Similarity:** 0.04
- **Dependents:** 0
- **Priority Score:** 41109.6
- **Functions:** 4/7 matched (target 5)
- **Missing functions:** `new`, `index`, `new`
- **Types:** 3/4 matched (target 3)
- **Missing types:** `Output`

### 10. diagnostic

- **Target:** `Diagnostic [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 32010.0
- **Functions:** 13/15 matched (target 30)
- **Missing functions:** `into_spans`, `to_internal`
- **Types:** 4/5 matched (target 7)
- **Missing types:** `Item`

### 11. escape

- **Target:** `Escape [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 410.0
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

