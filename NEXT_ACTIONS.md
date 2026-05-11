# NEXT_ACTIONS — proc-macro-kotlin

Stage: **scaffold**. No source files, no published artifact, no consumers
wired up.

## Immediate next steps

1. **Fetch upstream `proc_macro` source into `tmp/proc-macro/`.**
   Run `tools/fetch-rust-source.sh`. Pin the rustc revision and record it
   in this file once the script lands. Confirm `tmp/proc-macro/src/lib.rs`
   and friends are present.

2. **Pin the JetBrains KMP-parsing artifact coordinates.** Verify which
   Maven artifact actually publishes
   `org.jetbrains.kotlin.kmp.lexer.KotlinLexer`,
   `org.jetbrains.kotlin.kmp.lexer.KtTokens`, and
   `org.jetbrains.kotlin.kmp.parser.KotlinParser` across all KMP targets
   this repo claims (macOS arm64, Linux x64, mingw-x64, iOS arm64 / x64 /
   simulator-arm64, JS, Wasm-JS, Android). The classes are gated
   `@ApiStatus.Experimental` in the kotlin/kotlin tree under
   `compiler/multiplatform-parsing/`. If no published artifact ships all
   targets, decide between vendoring the relevant files (Apache 2.0
   permits this) and reducing the target set.

3. **First commits land the `tmp/` source + the build template.** Build
   should compile (no Kotlin source yet, so the only gate is
   `./gradlew tasks` succeeding) before any porting begins.

## Porting backlog (bottom-up)

Order is from-leaves-up. Each entry maps to a single upstream `.rs` file
under `tmp/proc-macro/src/`.

1. `bridge/diagnostic.rs` — minimal; only the bits public to `Diagnostic`.
   (Most of `bridge/` does NOT port; see CLAUDE.md.)
2. `lib.rs` — module-level docs + the public type re-exports.
3. `Delimiter`, `Spacing` — pure enums, trivial.
4. `Span` — `call_site()`, `mixed_site()`, `def_site()`, `byte_range()`,
   `start()`, `end()`, `join()`, `file()`, `local_file()`, `source_text()`,
   `eq()`. Backend wiring lands second pass.
5. `LexError` — error span plus `Display` / `Debug`.
6. `Ident` — `new()`, `new_raw()`, `span()`, `set_span()`. Identifier
   validation per Unicode Standard Annex 31 (XID_Start/XID_Continue).
7. `Punct` — `new()`, `as_char()`, `spacing()`, `span()`, `set_span()`.
8. `Literal` — every suffixed/unsuffixed factory the upstream exposes
   (`u8_suffixed`, `i32_suffixed`, `f64_unsuffixed`, `string()`,
   `character()`, `byte_character()`, `byte_string()`, `c_string()`, etc.).
9. `Group` — `new()`, `delimiter()`, `stream()`, `span()`, `span_open()`,
   `span_close()`, `set_span()`.
10. `TokenTree` — the four-variant sealed-class enum + `span()` /
    `set_span()`.
11. `TokenStream` — `new()`, `from_str()`, `is_empty()`, the `Display` /
    `Debug` / `From<TokenTree>` / `FromIterator` / `Extend` impls.
12. `token_stream::IntoIter` — `Iterator<TokenTree>`.

After every entry, run `./gradlew compileKotlinMacosArm64` and the
relevant test gate. After every group of entries that change a public
type's surface, refresh this file and any downstream consumers'
`RUST_CALLERS.md` notes if they exist.

## Kotlin-backend integration milestones

Once the surface lands with stub-or-fallback bodies:

A. **`TokenStream.fromString(...)` → `KotlinLexer`.** First real backend
   wiring. Feed the input string to `KotlinLexer.start(...)`, advance via
   `getTokenType()` / `getTokenStart()` / `getTokenEnd()` / `advance()`,
   map each `SyntaxElementType` from `KtTokens` to the appropriate
   `proc_macro`-shaped `TokenTree` variant. Open issues this surfaces:
   - Kotlin string templates (`OPEN_QUOTE`, `LONG_TEMPLATE_ENTRY_START`,
     interpolation) have no direct `proc_macro` analog — decide on a
     representation in CLAUDE.md before committing.
   - Kotlin operators like `?.` / `!!` / `?:` / `..` either decompose into
     single-`Punct` tokens with `Spacing::Joint` chains (matching how
     `proc_macro2` represents Rust operators) or become bespoke variants.
   - Comments (`EOL_COMMENT_ID`, `BLOCK_COMMENT_ID`, `DOC_COMMENT_ID`)
     and whitespace are filtered before reaching the consumer in upstream
     `proc_macro`. Mirror that filtering at the boundary.

B. **`Span` byte ranges sourced from `KtTokens`.** Replace the synthetic
   process-wide source map with real text offsets from the lexer.

C. **`Group` parsing via `KotlinParser`.** Required for `from_str` to
   produce nested groups rather than flat punct streams.

D. **Round-trip via `KotlinLexer` after `Display::fmt`.** Validate that
   `TokenStream.toString().toLexedTokenStream() == TokenStream`.

## Re-enabling `proc-macro2-kotlin`'s wrapper layer

Cross-repo task. After milestones A-C land:

- Restore the two-variant `WrapperTokenStream` / `WrapperSpan` etc. that
  the in-flight `port/refaithful-divergent-translations` branch on
  `proc-macro2-kotlin` collapsed. The Compiler arms delegate to this
  repo.
- Detection (`Detection.kt`) gets a non-trivial `insideProcMacro()`
  meaning: "the JetBrains KMP-parsing artifact is loaded on this
  target." Currently hardcoded to false.
- Publish `proc-macro-kotlin 0.1.0` and `proc-macro2-kotlin 0.2.0`
  together.

## Open questions

- What happens for downstream callers that need Rust-source lexing (the
  current `proc-macro2-kotlin` fallback)? Confirm they keep using the
  Fallback variant via `proc-macro2-kotlin`'s wrapper. No Rust-source
  parsing lives in `proc-macro-kotlin`.
- Should `Literal` factories that take Rust-specific suffixes (`u128`,
  `isize`) emit Kotlin equivalents or error? Likely emit, since downstream
  Kotlin ports of Rust derives generate Rust-suffixed tokens that
  eventually round-trip back to Rust source via `lalrpop-kotlin`.
- Hygiene: `Span::resolved_at()` / `Span::located_at()` have no `KotlinLexer`
  equivalent. Stub to identity (mirror `proc-macro2-kotlin`'s fallback
  behavior) or model resolution context independently?
