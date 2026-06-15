# proc-macro-kotlin


[![GitHub link](https://img.shields.io/badge/GitHub-KotlinMania%2Fproc--macro--kotlin-blue.svg)](https://github.com/KotlinMania/proc-macro-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/proc-macro-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/proc-macro-kotlin)
[![Build status](https://img.shields.io/github/actions/workflow/status/KotlinMania/proc-macro-kotlin/ci.yml?branch=main)](https://github.com/KotlinMania/proc-macro-kotlin/actions)

**Rust's `proc_macro` API. Kotlin's tokenizer underneath.**

The Kotlin Multiplatform port of Rust's compiler-internal
[`proc_macro`](https://doc.rust-lang.org/proc_macro/) crate — the in-tree
crate that `rustc` makes available to procedural macros and that
[`proc_macro2`](https://crates.io/crates/proc-macro2) dispatches to via its
`Compiler` variant. We keep the surface API faithful to the upstream Rust
crate so Kotlin ports of `syn`, `quote`, `serde_derive`, `async-trait`,
`starlark_derive`, `logos-codegen`, and the rest of the proc-macro
ecosystem can consume it without surprise. We back that surface with
JetBrains' multiplatform Kotlin lexer + parser
([`org.jetbrains.kotlin.kmp.lexer.KotlinLexer`, `KtTokens`, `KotlinParser`](https://github.com/JetBrains/kotlin/tree/master/compiler/multiplatform-parsing/common/src/org/jetbrains/kotlin/kmp))
so the tokens carry real spans into real Kotlin source.

Both upstreams are Apache 2.0. This repo is Apache 2.0. The licensing path
is clean for either depending on the JetBrains KMP-parsing artifact or
vendoring the pieces we need.

## Design contract

- **Public API: faithful to upstream `proc_macro`.** Every public type
  (`TokenStream`, `Span`, `Group`, `Delimiter`, `Ident`, `Punct`,
  `Spacing`, `Literal`, `TokenTree`, `LexError`, `token_stream::IntoIter`)
  matches the Rust crate's shape. KDoc translates the upstream `///`
  comments. The translation rules in workspace-root `CLAUDE.md` and this
  repo's own `AGENTS.md` apply (Rust `snake_case` → Kotlin
  `lowerCamelCase`, `Vec<T>` → `List<T>`, lifetimes dropped, etc.) — but
  the API contract upstream callers see is `proc_macro`'s.
- **Implementation: backed by Kotlin's tokenizer.** `TokenStream::new`,
  `TokenStream::from_str`, `Span::call_site`, etc. don't sit on a
  hand-rolled Rust-source lexer (that's `proc-macro2-kotlin`'s Fallback
  job). They sit on `KotlinLexer` + `KtTokens` + the multiplatform
  `SyntaxTreeBuilder` pipeline, producing tokens that carry actual
  Kotlin-source positions.

## Why this exists

`proc-macro2-kotlin` shipped only the Fallback half of the
`Compiler` / `Fallback` split that `proc_macro2`'s `wrapper.rs` defines.
There was no Compiler half because Kotlin doesn't have a compiler-supplied
token-stream crate in the `rustc`-bridged sense — Kotlin's plugin
extension points (FIR `FirDeclarationGenerationExtension`, IR
`IrGenerationExtension`, kapt, KSP) trade in symbols and IR, not tokens.

But Kotlin **does** ship a portable lexer/parser pair at
`compiler/multiplatform-parsing/`. That lexer produces a real token stream
over real Kotlin source. Once we wrap it in the same surface shapes
`proc_macro2` exposes, we have the missing Compiler half — and a lot more
besides.

## What this unlocks

1. **A real Compiler variant for `proc-macro2-kotlin`.** Its `wrapper.rs`
   dispatch layer becomes two-variant in earnest: Fallback keeps doing
   Rust-source tokenization for tests / standalone codegen, Compiler
   delegates here for Kotlin-source-aware work. `Detection.kt`'s
   `insideProcMacro()` gets a non-trivial meaning: "we have a Kotlin lexer
   available on this target."

2. **A Kotlin-emitter substrate for `lalrpop-kotlin`.** `lalrpop-kotlin`
   already reaches Rust-output byte parity. The natural next step is a
   Kotlin emitter on the same parser tables. A `quote!`-style Kotlin
   emitter needs a tokenizer that knows Kotlin keywords, string templates
   (`OPEN_QUOTE` / `CLOSING_QUOTE` / interpolation entries), `?.` / `!!`,
   `val` / `var`, `fun`-modifier forms, etc. — i.e. `KotlinLexer` +
   `KtTokens`. Wrap that in `proc_macro2`-shaped types here and the
   emitter has its tokenizer.

3. **A Rust → Kotlin source-level translation bridge.** Pipeline reads:
   Rust source → `proc-macro2-kotlin` (Fallback, Rust-shaped) →
   `syn-kotlin` AST → transliteration pass → `proc-macro-kotlin`
   (Compiler, Kotlin-shaped) → emitted `.kt` files validated against
   `KotlinLexer`. The kotlinmania porting workflow becomes a library
   pipeline instead of a hand transliteration.

4. **A foundation for a Kotlin parser via `starlark-kotlin`.**
   `starlark-kotlin` ports the Starlark expression language. A Kotlin
   parser expressed as Starlark rules over this repo's token stream
   becomes tractable in a way it wasn't when the token surface didn't
   exist.

## Relationship to `proc-macro2-kotlin`

| Concern | `proc-macro2-kotlin` | `proc-macro-kotlin` |
|---|---|---|
| Upstream Rust crate | `proc-macro2` | `proc_macro` (rustc in-tree) |
| Role in upstream | the standalone fallback + the public API | the compiler-supplied backend |
| Token vocabulary | Rust-shaped | Rust-shaped (same surface) |
| Source text accepted | Rust (via the fallback lexer) | Kotlin (via `KotlinLexer`) |
| Span data | synthetic byte ranges in a process-wide source map | real `KtTokens` syntax-element spans |
| Status | published / pre-publish maintenance | phase 2 in progress (Kotlin lexer next) |

`proc-macro2-kotlin` continues to be the public API surface that
downstream crates (`syn-kotlin`, `quote-kotlin`, the Kotlin ports of
`serde_derive`, `async-trait`, `starlark_derive`, `logos-codegen`, …)
depend on. `proc-macro-kotlin` is the alternative backend wired in
through `proc-macro2-kotlin`'s wrapper layer — never imported directly by
downstream ports.

## Porting plan

The order is: faithful Rust API first, Kotlin tokenizer second, wire
the two together third.

1. **Pull the upstream `proc_macro` source into `tmp/`.** ✅ Done.
   Upstream is
   [`rust-lang/rust:library/proc_macro/src/`](https://github.com/rust-lang/rust/tree/master/library/proc_macro/src).
   The `bridge` submodule does not port (rustc FFI, no Kotlin analog).
2. **Stand up the Gradle Multiplatform build.** ✅ Done. Full target
   list including Swift Export + XCFramework.
3. **Port the public types bottom-up.** ✅ Done. All 10 core types
   (`Delimiter`, `Spacing`, `Span`, `LexError`, `Ident`, `Punct`,
   `Literal`, `Group`, `TokenTree`, `TokenStream`, `IntoIter`) plus
   `Quote.kt`, `ToTokens.kt`, `Diagnostic.kt`, `Escape.kt`, and the
   `rustcore/` helpers.
4. **Vendor the JetBrains `com.intellij.platform.syntax.*`
   infrastructure.** ✅ Done. 102 files, ~9,764 lines. Provides the
   `Lexer` interface, `SyntaxElementType`, `SyntaxTreeBuilderImpl`,
   `MarkerPool`, fastutil collections, and the builder/production
   infrastructure that a Kotlin tokenizer sits on top of.
5. **Vendor the JetBrains KMP lexer.** ✅ Done. JetBrains' own
   `KotlinFlexLexer.kt` (1,723 lines, JFlex-generated) is pure Kotlin
   multiplatform code — zero `java.*` imports, `CharSequence` buffer,
   Kotlin stdlib surrogates only. It implements the `FlexLexer` interface
   we've already vendored and produces `KtTokens.*`-typed output over the
   `SyntaxElementType` infrastructure already in tree. The
   [Kotlin spec ANTLR4 grammars](https://github.com/Kotlin/kotlin-spec/tree/release/grammar/src/main/antlr)
   (`KotlinLexer.g4`, `KotlinParser.g4`, `UnicodeClasses.g4`) go under
   `tmp/kotlin-spec/` as cross-reference. See `PROJECT_PLAN.md` for the
   detailed adapter design.
6. **Wire `KotlinLexer` into `TokenStream.fromString`.** ✅ Done. The Compiler
   variant tokenizes Kotlin source through the new lexer, maps `KtToken`
   variants to `proc_macro`-shaped `TokenTree` variants, produces nested
   `Group` tokens via delimiter matching, and sources `Span` data from
   real byte offsets.
7. **Re-enable `proc-macro2-kotlin`'s wrapper layer.** Restore the
   two-variant `WrapperTokenStream` / `WrapperSpan` / etc. that the
   in-flight `port/refaithful-divergent-translations` branch collapsed,
   with the Compiler arms now delegating here.
8. **Publish to Maven Central** behind `proc-macro2-kotlin 0.2.0`'s
   release. The two ship together.

### Kotlin grammar runtime

The tokenizer path currently uses JetBrains' KMP `KotlinLexer` and
`KtTokens` directly. The grammar-driven path uses the published
`io.github.kotlinmania:antlr4-kotlin:0.1.2` runtime from Maven Central;
the old in-repo `antlr4-runtime/` module was removed and must not be
restored. The published runtime's package is
`io.github.kotlinmania.antlr4.*`, so generated grammar callers are wired
against that namespace rather than the old vendored
`org.antlr.v4.runtime.*` package.

## Status

**Phase 2 is wired.** Rust `proc_macro` API surface is ported
(14,248 lines of Kotlin). JetBrains `com.intellij.platform.syntax.*`
infrastructure, the KMP `KotlinLexer`, and `KtTokens` are present, and
`TokenStream.fromString` tokenizes Kotlin source through that lexer.
The published ANTLR4 Kotlin runtime is available for generated grammar
callers through the Gradle version catalog.

## License

Apache 2.0. Upstream `proc_macro` is dual-licensed MIT / Apache 2.0; the
JetBrains Kotlin compiler sources we depend on are Apache 2.0; this repo
takes the intersection.
