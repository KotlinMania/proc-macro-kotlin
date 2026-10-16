# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 11/16 (68.8%)
- **Function parity:** 77/182 matched (target 332) — 42.3%
- **Class/type parity:** 39/80 matched (target 132) — 48.8%
- **Combined symbol parity:** 116/262 matched (target 464) — 44.3%
- **Average inline-code cosine:** 0.04 (function body across 9 matched files)
- **Average documentation cosine:** 0.29 (doc text across 9 matched files)
- **Cheat-zeroed Files:** 6
- **Critical Issues:** 11 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. to_tokens

- **Target:** `procmacro.ToTokens [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 2
- **Priority Score:** 2000410.0
- **Functions:** 3/3 matched (target 16)
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 5)
- **Missing types:** _none_

### 2. bridge.symbol

- **Target:** `bridge.Symbol`
- **Similarity:** 0.02
- **Dependents:** 1
- **Priority Score:** 1121409.9
- **Functions:** 1/12 matched (target 5)
- **Missing functions:** `new`, `new_ident`, `with`, `invalidate_all`, `is_valid_ascii_ident`, `can_be_raw`, `fmt`, `encode`, `decode`, `get`, `clear`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `Interner`

### 3. bridge.client

- **Target:** `bridge.Client`
- **Similarity:** 0.01
- **Dependents:** 0
- **Priority Score:** 252609.9
- **Functions:** 1/19 matched (target 61)
- **Missing functions:** `drop`, `encode`, `decode`, `clone`, `def_site`, `call_site`, `mixed_site`, `fmt`, `set`, `with`, `maybe_install_panic_hook`, `run_client`, `expand1`, `expand2`, `name`, `custom_derive`, `attr`, `bang`
- **Types:** 0/7 matched (target 10)
- **Missing types:** `HandleCounters`, `TokenStream`, `Span`, `Bridge`, `RestoreOnDrop`, `Client`, `ProcMacro`

### 4. lib

- **Target:** `procmacro.Span [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 218310.0
- **Functions:** 48/66 matched (target 144)
- **Missing functions:** `fmt`, `from_str`, `default`, `tree_to_bridge_tree`, `from`, `push`, `build`, `append_to`, `from_iter`, `extend`, `size_hint`, `count`, `into_iter`, `with_symbol_and_suffix`, `with_stringify_parts`, `get_hashes_str`, `env_var`, `path`
- **Types:** 14/17 matched (target 50)
- **Missing types:** `ConcatTreesHelper`, `ConcatStreamsHelper`, `Item`

### 5. bridge.server

- **Target:** `bridge.Server`
- **Similarity:** 0.04
- **Dependents:** 0
- **Priority Score:** 141809.5
- **Functions:** 1/9 matched (target 12)
- **Missing functions:** `new`, `encode`, `decode`, `drop`, `send`, `recv`, `run_server`, `run`
- **Types:** 3/9 matched (target 4)
- **Missing types:** `HandleStore`, `MarkedTokenStream`, `MarkedSpan`, `MarkedSymbol`, `RunningSameThreadGuard`, `MessagePipe`
- **Lint issues:** 3

### 6. bridge.mod

- **Target:** `bridge.Mod [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 101710.0
- **Functions:** 0/3 matched (target 4)
- **Missing functions:** `mark`, `unmark`, `from_single`
- **Types:** 7/14 matched (target 25)
- **Missing types:** `Methods`, `Mark`, `Marked`, `Unmarked`, `LitKind`, `TokenTree`, `Diagnostic`

### 7. quote

- **Target:** `procmacro.Quote [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 81710.0
- **Functions:** 5/8 matched (target 45)
- **Missing functions:** `bitor`, `to_tokens`, `helper`
- **Types:** 4/9 matched (target 10)
- **Missing types:** `Output`, `RepIteratorExt`, `RepAsIteratorExt`, `Iter`, `Item`

### 8. bridge.rpc

- **Target:** `bridge.Rpc`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 80910.0
- **Functions:** 0/5 matched (target 7)
- **Missing functions:** `encode`, `decode`, `from`, `as_str`, `into_string`
- **Types:** 1/4 matched (target 16)
- **Missing types:** `Encode`, `Decode`, `UnknownPanicMessage`

### 9. bridge.handle

- **Target:** `bridge.Handle`
- **Similarity:** 0.31
- **Dependents:** 0
- **Priority Score:** 30906.9
- **Functions:** 3/5 matched
- **Missing functions:** `new`, `index`
- **Types:** 3/4 matched (target 3)
- **Missing types:** `Output`

### 10. diagnostic

- **Target:** `procmacro.Diagnostic [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 21810.0
- **Functions:** 12/13 matched (target 30)
- **Missing functions:** `to_internal`
- **Types:** 4/5 matched (target 7)
- **Missing types:** `Item`

### 11. escape

- **Target:** `procmacro.Escape [ZERO]`
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

