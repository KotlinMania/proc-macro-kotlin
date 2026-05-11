# NEXT_ACTIONS — proc-macro-kotlin

Stage: **scaffold**. Build template loads, no source files yet.

## Phase 1 — Port the Rust code so the shape is like the Rust code

The team manually places upstream Rust source under `tmp/proc-macro/`
(gitignored). Upstream is
[`rust-lang/rust:library/proc_macro/src/`](https://github.com/rust-lang/rust/tree/master/library/proc_macro/src).
Workspace porting discipline from `AGENTS.md` applies: port-lint
`// port-lint: source <path>.rs` headers, one Rust file → one Kotlin
file, comments-are-content, no stubs, no `@Suppress`.

Porting backlog, bottom-up:

1. `Delimiter`, `Spacing` — pure enums.
2. `Span` — `call_site()`, `mixed_site()`, `def_site()`, `byte_range()`,
   `start()`, `end()`, `join()`, `file()`, `local_file()`, `source_text()`,
   `eq()`. Implementations land as stubs that compile but throw on the
   span-data accessors; phase 2 fills them in.
3. `LexError` — span plus `Display` / `Debug`.
4. `Ident` — `new()`, `new_raw()`, `span()`, `set_span()`. XID_Start /
   XID_Continue identifier validation per Unicode Annex 31.
5. `Punct` — `new()`, `as_char()`, `spacing()`, `span()`, `set_span()`.
6. `Literal` — every suffixed/unsuffixed factory upstream exposes
   (`u8_suffixed`, `i32_suffixed`, `f64_unsuffixed`, `string()`,
   `character()`, `byte_character()`, `byte_string()`, `c_string()`, etc.).
7. `Group` — `new()`, `delimiter()`, `stream()`, `span()`, `span_open()`,
   `span_close()`, `set_span()`.
8. `TokenTree` — four-variant sealed class + `span()` / `set_span()`.
9. `TokenStream` — `new()`, `from_str()`, `is_empty()`, `Display` /
   `Debug` / `From<TokenTree>` / `FromIterator` / `Extend` impls.
10. `token_stream::IntoIter` — `Iterator<TokenTree>`.

The `bridge` submodule does not port. Each phase-1 commit ends with
`./gradlew compileKotlinMacosArm64` green.

## Phase 2 — Add the Kotlin pieces to make a real tokenizer

Vendor JetBrains' multiplatform Kotlin lexer + parser into the tracked
tree under their upstream package paths (preserve `com.intellij.platform.syntax.*`
and `org.jetbrains.kotlin.kmp.*`). Vendor discipline from `AGENTS.md`
"Phase 2" applies: `// Vendored from <repo> @ <sha>` provenance comment,
drop `@JvmStatic`, drop `java.*` / `javax.*` imports, no refactoring while
vendoring.

Vendoring order, leaves first:

1. **`com.intellij.platform.syntax` core types** — `SyntaxElementType`,
   `SyntaxElementTypeSet`, `SyntaxTokenTypes`, `Lexer`, `LexerPosition`,
   plus the `lexer/` and `parser/` sub-packages they need. From
   `JetBrains/intellij-community/platform/syntax/syntax-api/src/`.
2. **`com.intellij.platform.syntax.util`** — `FlexAdapter`,
   `FlexLexer`, `MergingLexerAdapter`. From `syntax-util/src/`.
3. **`com.intellij.platform.syntax.impl.fastutil`** — `Int2IntOpenHashMap`
   and its dependencies. From `syntax-util/src/`.
4. **`org.jetbrains.kotlin.kmp.utils`** — `Stack`, `StringUtil`,
   `SyntaxElementTypesWithIds`. From
   `JetBrains/kotlin/compiler/multiplatform-parsing/common/src/`.
5. **`org.jetbrains.kotlin.kmp.lexer`** — `KtTokens`, `KotlinLexer`,
   `KDocLexer`, `KDocTokens`, `KDocKnownTag`. Plus the JFlex-generated
   `KotlinFlexLexer` and `KDocFlexLexer` from `common/src/gen/`.
6. **`org.jetbrains.kotlin.kmp.parser`** — `AbstractParser`,
   `KotlinParser`, `KtNodeTypes`, and the `parser/utils/` helpers.

Each step compiles before the next starts. `./gradlew test` (macOS arm64
+ jsNode + wasmJsNode) green at every step.

## Phase 3 — Wire phase-1 types to phase-2 lexer

Now the `proc_macro`-shaped types from phase 1 stop being stubs:

A. `TokenStream.fromString(...)` calls `KotlinLexer.start(...)`, advances
   via `getTokenType()` / `getTokenStart()` / `getTokenEnd()` / `advance()`,
   maps each `SyntaxElementType` from `KtTokens` to the appropriate
   `TokenTree` variant. Comments and whitespace are filtered before
   reaching the consumer, matching upstream `proc_macro`.
B. `Span` byte ranges sourced from real `KtTokens` offsets, not from
   process-wide synthetic source maps.
C. `Group` parsing via `KotlinParser` so `from_str` produces nested groups
   instead of flat punct streams.
D. Round-trip validation: `TokenStream.toString().toLexedTokenStream() == TokenStream`.

Open questions that surface during phase 3 and need documenting:

- Kotlin string templates (`OPEN_QUOTE`, `LONG_TEMPLATE_ENTRY_START`,
  interpolation) — Rust `proc_macro` has no analog. Decide on a
  representation (synthetic `Group` with a custom `Delimiter`, or a
  sequence of `Punct` + `Literal`).
- Kotlin operators (`?.`, `!!`, `?:`, `..`, `::`) — decompose into
  single-character `Punct` with `Spacing::Joint` chains matching how
  `proc_macro` represents multi-char operators, or model as bespoke
  variants.
- `Span::resolved_at()` / `Span::located_at()` — Rust hygiene resolution
  has no `KotlinLexer` equivalent. Stub to identity, or model resolution
  context independently.

## Phase 4 — Wire into proc-macro2-kotlin

Cross-repo task. Restore the two-variant wrapper layer that
`proc-macro2-kotlin`'s `port/refaithful-divergent-translations` branch
collapsed, with the `Compiler` arms delegating to this repo's types.
`Detection.kt`'s `insideProcMacro()` gets a non-trivial meaning: "the
JetBrains KMP-parsing artifact is loaded on this target."

Publish `proc-macro-kotlin 0.1.0` and `proc-macro2-kotlin 0.2.0`
together.
