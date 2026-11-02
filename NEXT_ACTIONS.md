# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 14/16 (87.5%)
- **Function parity:** 114/195 matched (target 415) — 58.5%
- **Class/type parity:** 35/49 matched (target 96) — 71.4%
- **Combined symbol parity:** 149/244 matched (target 511) — 61.1%
- **Average inline-code cosine:** 0.12 (function body across 14 matched files)
- **Average documentation cosine:** 0.34 (doc text across 14 matched files)
- **Cheat-zeroed Files:** 0
- **Critical Issues:** 14 files with <0.60 function similarity
- **Needs Review:** 0 files with 0.60-0.84 function similarity
- **Excellent:** 0 files with >=0.85 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. bridge.buffer

- **Target:** `bridge.Buffer`
- **Similarity:** 0.09
- **Dependents:** 3
- **Priority Score:** 3061909.0
- **Functions:** 12/17 matched (target 21)
- **Missing functions:** `deref`, `deref_mut`, `to_vec`, `reserve`, `drop`
- **Types:** 1/2 matched (target 4)
- **Missing types:** `Target`

### 2. to_tokens

- **Target:** `ToTokens`
- **Similarity:** 0.05
- **Dependents:** 2
- **Priority Score:** 2283709.5
- **Functions:** 8/36 matched (target 33)
- **Missing functions:** `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`, `to_tokens`
- **Types:** 1/1 matched (target 5)
- **Missing types:** _none_

### 3. bridge.symbol

- **Target:** `bridge.Symbol`
- **Similarity:** 0.09
- **Dependents:** 1
- **Priority Score:** 1041709.1
- **Functions:** 11/15 matched (target 18)
- **Missing functions:** `fmt`, `fmt`, `encode`, `decode`
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_

### 4. bridge.rpc

- **Target:** `bridge.Rpc`
- **Similarity:** 0.02
- **Dependents:** 0
- **Priority Score:** 172609.8
- **Functions:** 5/22 matched (target 56)
- **Missing functions:** `encode`, `decode`, `encode`, `decode`, `encode`, `decode`, `encode`, `decode`, `encode`, `decode`, `encode`, `decode`, `encode`, `decode`, `from`, `encode`, `decode`
- **Types:** 4/4 matched (target 25)
- **Missing types:** _none_

### 5. quote

- **Target:** `Quote`
- **Similarity:** 0.02
- **Dependents:** 0
- **Priority Score:** 153009.8
- **Functions:** 11/21 matched (target 45)
- **Missing functions:** `bitor`, `bitor`, `bitor`, `bitor`, `quote_into_iter`, `quote_into_iter`, `quote_into_iter`, `next`, `to_tokens`, `helper`
- **Types:** 4/9 matched (target 10)
- **Missing types:** `Output`, `RepIteratorExt`, `RepAsIteratorExt`, `Iter`, `Item`

### 6. bridge.client

- **Target:** `bridge.Client`
- **Similarity:** 0.09
- **Dependents:** 0
- **Priority Score:** 103209.1
- **Functions:** 18/25 matched (target 92)
- **Missing functions:** `encode`, `encode`, `decode`, `encode`, `decode`, `fmt`, `with`
- **Types:** 4/7 matched (target 18)
- **Missing types:** `TokenStream`, `Span`, `Bridge`

### 7. bridge.server

- **Target:** `bridge.Server`
- **Similarity:** 0.14
- **Dependents:** 0
- **Priority Score:** 82408.6
- **Functions:** 10/15 matched (target 63)
- **Missing functions:** `encode`, `decode`, `decode`, `encode`, `decode`
- **Types:** 6/9 matched (target 10)
- **Missing types:** `MarkedTokenStream`, `MarkedSpan`, `MarkedSymbol`
- **Lint issues:** 3

### 8. diagnostic

- **Target:** `Diagnostic`
- **Similarity:** 0.18
- **Dependents:** 0
- **Priority Score:** 32008.2
- **Functions:** 13/15 matched (target 33)
- **Missing functions:** `into_spans`, `to_internal`
- **Types:** 4/5 matched (target 7)
- **Missing types:** `Item`

### 9. bridge.fxhash

- **Target:** `bridge.Fxhash`
- **Similarity:** 0.09
- **Dependents:** 0
- **Priority Score:** 11109.1
- **Functions:** 8/9 matched (target 27)
- **Missing functions:** `write_u64`
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_

### 10. bridge.handle

- **Target:** `bridge.Handle`
- **Similarity:** 0.19
- **Dependents:** 0
- **Priority Score:** 11108.1
- **Functions:** 7/7 matched (target 13)
- **Missing functions:** _none_
- **Types:** 3/4 matched
- **Missing types:** `Output`

### 11. bridge.closure

- **Target:** `bridge.Closure`
- **Similarity:** 0.12
- **Dependents:** 0
- **Priority Score:** 10508.8
- **Functions:** 2/3 matched (target 2)
- **Missing functions:** `call`
- **Types:** 2/2 matched (target 4)
- **Missing types:** _none_

### 12. bridge.selfless_reify

- **Target:** `bridge.SelflessReify`
- **Similarity:** 0.04
- **Dependents:** 0
- **Priority Score:** 10209.6
- **Functions:** 1/2 matched (target 1)
- **Missing functions:** `wrapper`
- **Types:** 0/0 matched
- **Missing types:** _none_

### 13. bridge.arena

- **Target:** `bridge.Arena`
- **Similarity:** 0.12
- **Dependents:** 0
- **Priority Score:** 608.8
- **Functions:** 5/5 matched (target 8)
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_

### 14. escape

- **Target:** `Escape`
- **Similarity:** 0.52
- **Dependents:** 0
- **Priority Score:** 404.8
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

## Reexport / Wiring Modules

These files match `reexport_modules` patterns in `.ast_distance_config.json`. They are filtered out of
normal priority and missing-file ladders because they are wiring
modules, not direct logic ports. Consult them for call-site routing;
do not treat them as the next implementation target by default.

### Missing

| Source | Expected target | Deps | Source path | Expected path |
|--------|-----------------|------|-------------|---------------|
| `bridge.mod` | `bridge.Mod` | 0 | `bridge/mod.rs` | `bridge/Mod.kt` |
| `lib` | `Lib` | 0 | `lib.rs` | `Lib.kt` |

