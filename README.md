# proc-macro-kotlin

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
| Status | published / pre-publish maintenance | scaffolded |

`proc-macro2-kotlin` continues to be the public API surface that
downstream crates (`syn-kotlin`, `quote-kotlin`, the Kotlin ports of
`serde_derive`, `async-trait`, `starlark_derive`, `logos-codegen`, …)
depend on. `proc-macro-kotlin` is the alternative backend wired in
through `proc-macro2-kotlin`'s wrapper layer — never imported directly by
downstream ports.

## Porting plan

The order is: faithful Rust API first, weld in the Kotlin backend
second. Concretely:

1. **Pull the upstream `proc_macro` source into `tmp/`.** Target is
   [`rust-lang/rust:library/proc_macro/src/`](https://github.com/rust-lang/rust/tree/master/library/proc_macro/src),
   shallow-cloned and pinned. The crate's `bridge` submodule (the FFI
   layer that talks to `rustc`'s expansion process) does not port — it
   has no Kotlin analog. Every other public type does.
2. **Stand up the Gradle Multiplatform build.** Same target list as
   sibling `*-kotlin` repos: macOS arm64, Linux x64, mingw-x64, iOS
   arm64 / x64 / simulator-arm64, JS, Wasm-JS, Android. Same Kotlin/JS
   security-hardening template from workspace `CLAUDE.md`.
3. **Resolve the JetBrains KMP-parsing dependency.** Pin Maven
   coordinates for `org.jetbrains.kotlin.kmp.lexer.*` (currently
   `@ApiStatus.Experimental` in the kotlin/kotlin tree). Decide depend vs
   vendor based on artifact availability on every target.
4. **Port the public types bottom-up.** `Delimiter`, `Spacing`,
   `Span`, `LexError`, `Ident`, `Punct`, `Literal`, `Group`, `TokenTree`,
   `TokenStream`, `token_stream::IntoIter`. Each gets a `port-lint:
   source` header pointing at its upstream `.rs` file. Bodies start as
   the most faithful translation possible; backend wiring lands as a
   second pass.
5. **Weld in the Kotlin tokenizer.** `TokenStream::from_str` (`fromString`
   in Kotlin) calls `KotlinLexer` and adapts its `SyntaxElementType`
   output into the Rust-shaped `TokenTree` variants. `Span` carries real
   text offsets. `Group`'s delimiters map `LBRACE`/`RBRACE`/`LPAR`/`RPAR`
   /`LBRACKET`/`RBRACKET` to `Delimiter.Brace`/`Parenthesis`/`Bracket`.
6. **Re-enable `proc-macro2-kotlin`'s wrapper layer.** Restore the
   two-variant `WrapperTokenStream` / `WrapperSpan` / etc. that the
   in-flight `port/refaithful-divergent-translations` branch collapsed,
   with the Compiler arms now delegating here.
7. **Publish to Maven Central** behind `proc-macro2-kotlin 0.2.0`'s
   release. The two ship together.

## Status

Scaffold only. README, LICENSE, `.gitignore`. No source files yet, no
Gradle build yet, no published artifact. The next commit lands the build
template + workspace docs (AGENTS.md / CLAUDE.md / NEXT_ACTIONS.md) and
the upstream `tmp/proc-macro/` fetch script.

## License

Apache 2.0. Upstream `proc_macro` is dual-licensed MIT / Apache 2.0; the
JetBrains Kotlin compiler sources we depend on are Apache 2.0; this repo
takes the intersection.
