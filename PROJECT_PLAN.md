# Project Plan — proc-macro-kotlin

Stage: **phase 2c complete — Rust API ported, JetBrains infrastructure vendored, KotlinLexer wired into TokenStream.fromString().

## What's done

### Phase 1 — Rust `proc_macro` API surface (complete)

All 10 core types ported from `tmp/proc-macro/` with `port-lint: source` headers:

| Type | Lines | Status |
|---|---|---|
| `Delimiter` | 37 | Complete |
| `Spacing` | 39 | Complete |
| `Span` | 237 | Complete (span data accessors backed by synthetic source map) |
| `LexError` | 14 | Complete |
| `Ident` | 125 | Complete (XID_Start / XID_Continue validation) |
| `Punct` | 82 | Complete |
| `Literal` | 414 | Complete (all suffixed/unsuffixed factories) |
| `Group` | 104 | Complete |
| `TokenTree` | 78 | Complete (four-variant sealed class) |
| `TokenStream` | 205 | Complete (Fallback-mode `fromString` via Rust-source tokenizer) |
| `IntoIter` | 16 | Complete |

Supporting files: `Quote.kt` (1,086 lines — the `quote!` quasiquoter),
`ToTokens.kt` (237 lines), `Escape.kt` (69), `Diagnostic.kt` (218),
`IsAvailable.kt` (20), `ConversionErrorKind.kt` (50), `ExpandError.kt` (9),
`rustcore/CharEscape.kt` (116), `rustcore/Unescape.kt` (131),
`rustcore/Utf8Chunks.kt` (150).

### Phase 2a — JetBrains `com.intellij.platform.syntax.*` vendored (complete)

102 files, ~9,764 lines vendored from
`JetBrains/intellij-community:platform/syntax/` and related packages.
Includes: `SyntaxElementType`, `SyntaxElementTypeSet`, `SyntaxTreeBuilderImpl`,
`Lexer`, `TokenList`, `MarkerPool`, `Int2IntOpenHashMap` (fastutil),
`CharArrayUtilKmp`, `StringUtilKmp`, `FlexAdapter`, `FlexLexer`,
and the full builder/production infrastructure.

### Phase 2b — JetBrains KMP lexer vendored (complete)

9 files, ~2,386 lines vendored from the JetBrains Kotlin compiler's
`multiplatform-parsing` module:

| File | Lines | Role |
|---|---|---|
| `KotlinLexer.kt` | 11 | `FlexAdapter` wrapper around `KotlinFlexLexer` |
| `KotlinFlexLexer.kt` | 1,689 | JFlex-generated Kotlin-language tokenizer |
| `KtTokens.kt` | 465 | Full token vocabulary (keywords, operators, literals) |
| `KDocTokens.kt` | 41 | KDoc comment token types |
| `KDocKnownTag.kt` | 47 | Known KDoc tag names |
| `SyntaxElementTypesWithIds.kt` | 37 | ID allocation base class |
| `Stack.kt` | 29 | Simple stack utility |
| `StringUtil.kt` | 42 | String utilities |
| `ApiStatus.kt` | 20 | Stub annotations (`@Experimental`, etc.) |

### Phase 2c — KotlinLexer wired into TokenStream (complete)

`KtTokenAdapter` converts JetBrains `SyntaxElementType` tokens into
`proc_macro`-shaped `TokenTree` variants:

- Whitespace and comments are filtered
- String template token runs collapse into atomic `Literal`s
- Multi-char operators (`->`, `==`, `::`, etc.) decompose into `Punct`
  chains with correct `JOINT`/`ALONE` spacing
- Delimiter pairs (`()`, `{}`, `[]`) nest into `Group` with `TokenStream`
- Kotlin keywords map to `Ident` (proc_macro treats keywords as idents)
- `Literal` gains `fromKotlinString/Char/Integer/Float` factory methods

`TokenStream.fromString()` now calls `KtTokenAdapter.tokenize(lexer, src)`
instead of throwing. All macosArm64Test targets pass.

### What has NOT landed yet

- **No `KotlinParser`.** No Kotlin-syntax parser exists here yet.
- **No Compiler-variant wiring into `proc-macro2-kotlin`.** `TokenStream`
  currently only operates in Fallback mode (Rust-source tokenization via
  `proc-macro2-kotlin`'s standalone lexer). The Compiler path that delegates
  to a Kotlin-source tokenizer is unwired.

---

## Reference sources

### 0. JetBrains JFlex fork with Kotlin output mode (code generator)

Path: `tmp/jflex/` (JetBrains/intellij-deps-jflex, branch `intellij/1.10.15`)

JetBrains forked JFlex and added `--output-mode kotlin` support. When this
flag is passed, JFlex generates `.kt` output instead of `.java`. The fork
includes:

| File | Lines | Description |
|---|---|---|
| `KotlinEmitter.java` | 1,455 | Kotlin code generator — mirrors `Emitter.java` but emits Kotlin syntax |
| `KotlinCountEmitter.java` | 180 | Table compression emitter for Kotlin output |
| `KotlinHiCountEmitter.java` | 64 | High-count table emitter variant |
| `KotlinHiLowEmitter.java` | 94 | Hi/low table emitter variant |
| `KotlinPackEmitter.java` | 192 | Packed table emitter variant |
| `KotlinAbstractLexScan.java` | 481 | Kotlin-variant lexer scanner — uses `kotlinx.io` instead of `java.io` |
| `OutputMode.java` | 6 | `enum OutputMode { JAVA, KOTLIN }` |

Total Kotlin-specific additions: **2,472 lines** of Java.
Total core JFlex (excluding Unicode data tables and GUI): **14,484 lines** of Java.

The skeleton template that controls the generated `.kt` output shape is at
`tmp/jflex-skeleton/idea-flex-kotlin.skeleton` (302 lines), vendored from
`JetBrains/intellij-community:tools/lexer/idea-flex-kotlin.skeleton`.

**Batch-translate path.** The JFlex core is ~14.5K lines of Java. JetBrains'
IntelliJ IDEA can batch-convert Java → Kotlin with high fidelity. The
Kotlin-specific files (`KotlinEmitter`, `KotlinCountEmitter`, etc.) are
the most interesting targets — they're the code that already knows how to
emit valid Kotlin. Once translated to Kotlin, JFlex becomes a native
Kotlin code generator that can produce `.kt` lexers from `.flex` specs
without any JVM dependency at all. This would be a standalone tool, not
a library dependency — it runs at build time, not at runtime.

The Unicode data tables (`jflex/core/unicode/data/Unicode_*.java`, ~580K
lines total) are auto-generated lookup tables. They can be either
excluded from the batch-translate and regenerated, or translated as bulk
data.

**Why this matters beyond proc-macro-kotlin.** A Kotlin-native JFlex
means any kotlinmania repo that needs a lexer can write a `.flex` spec
and get a correct, multiplatform Kotlin lexer generated automatically —
not just the Kotlin language tokenizer, but any language. The `lalrpop-kotlin`
parser generator + Kotlin-native JFlex lexer generator gives the workspace
a complete Kotlin-native toolchain for building parsers.

Two independent specifications define what a correct Kotlin tokenizer must
produce. We keep both under `tmp/` for reference during porting.

### 1. JetBrains KMP-parsing source (compiler tree)

Path: `kotlin.coroutines-cpp/tmp/kotlin/compiler/multiplatform-parsing/`

This is JetBrains' own multiplatform Kotlin lexer + parser, extracted from
the Kotlin compiler repository. It consists of:

| File | Lines | Description |
|---|---|---|
| `Kotlin.flex` | 391 | JFlex lexer specification — the source of truth for the compiler's tokenizer |
| `KotlinFlexLexer.kt` | 1,723 | JFlex-generated output — pure Kotlin, no `java.*` imports, `CharSequence` buffer |
| `KtTokens.kt` | 410 | Token vocabulary — `SyntaxElementType` constants with integer IDs |
| `KotlinLexer.kt` | 11 | Thin wrapper: `class KotlinLexer : FlexAdapter(KotlinFlexLexer())` |
| `KDocTokens.kt` | 38 | KDoc token types |
| `KDocLexer.kt` | 39 | KDoc lexer wrapper |
| `KDocKnownTag.kt` | 45 | Known KDoc tag definitions |
| `KotlinParser.kt` | 42 | Parser entry point (delegates to `KotlinParsing`) |
| `KtNodeTypes.kt` | 321 | AST node type definitions |
| `KotlinParsing.kt` | 2,909 | Main parser logic |
| `KotlinExpressionParsing.kt` | 1,874 | Expression parsing |
| `AbstractKotlinParsing.kt` | 349 | Shared parser utilities |
| `AbstractParser.kt` | 28 | Parser base class |
| `BinaryOperationPrecedence.kt` | 66 | Operator precedence table |
| `SemanticWhitespaceAwareSyntaxBuilders.kt` | 229 | Whitespace-aware builder |
| `KotlinWhitespaceAndCommentsBinders.kt` | 131 | Comment/whitespace attachment |
| `TokenStreamPatterns.kt` | 100 | Token stream patterns |
| `Stack.kt` | 26 | Utility stack |
| `StringUtil.kt` | 39 | String utilities |
| `SyntaxElementTypesWithIds.kt` | 34 | ID-bearing element type base |

**Critical finding:** `KotlinFlexLexer.kt` is already pure Kotlin
multiplatform code. JFlex generated it from `Kotlin.flex`, but the
output uses `CharSequence` as the buffer type (not `java.nio.CharBuffer`),
has zero `java.*` imports, and the only `java.lang.Character` reference
is a comment — the actual `codePointAt` implementation is a Kotlin
extension function using `Char.isHighSurrogate()` / `Char.isLowSurrogate()`
/ `Char.toCodePoint()`, all of which are available in Kotlin common.

### 2. Kotlin spec ANTLR4 grammars (language specification)

Path: `tmp/kotlin-spec/`

The official Kotlin language specification's grammar, maintained at
`Kotlin/kotlin-spec` on GitHub. These `.g4` files define the same
language from a specification standpoint.

| File | Lines | Description |
|---|---|---|
| `KotlinLexer.g4` | 529 | ANTLR4 lexical grammar: token definitions, mode stack, operator decomposition |
| `KotlinParser.g4` | 928 | ANTLR4 syntax grammar |
| `UnicodeClasses.g4` | 1,648 | Unicode character classes for identifier rules |
| `KotlinLexer.tokens` | 3,420 | Token vocabulary index |
| `UnicodeClasses.tokens` | 133 | Unicode class token index |

### How the two relate

The JFlex `.flex` and the ANTLR4 `.g4` define the same token set with
different naming conventions and different approaches to
whitespace-sensitivity:

| Aspect | JFlex (`.flex`) | ANTLR4 (`.g4`) |
|---|---|---|
| Keyword naming | `PACKAGE_KEYWORD`, `FUN_MODIFIER` | `PACKAGE`, `FUN` |
| Delimiter naming | `LPAR`/`RPAR`, `LBRACE`/`RBRACE` | `LPAREN`/`RPAREN`, `LCURL`/`RCURL` |
| Whitespace-sensitive `?` | Single `QUEST` token | `QUEST_WS` / `QUEST_NO_WS` |
| Whitespace-sensitive `@` | Single `AT` token | `AT_NO_WS` / `AT_POST_WS` / `AT_PRE_WS` / `AT_BOTH_WS` |
| Whitespace-sensitive `!` | `EXCL`, no ws distinction | `EXCL_WS` / `EXCL_NO_WS` |
| Newline handling | Folded into `WHITE_SPACE` / `DANGLING_NEWLINE` | `NL` as a distinct token |
| `null`/`true`/`false` | `NULL_KEYWORD`, `TRUE_KEYWORD`, `FALSE_KEYWORD` | `NullLiteral`, `BooleanLiteral` |
| Integer literals | Single `INTEGER_LITERAL` | `IntegerLiteral`, `LongLiteral`, `HexLiteral`, `BinLiteral`, `UnsignedLiteral` |
| String interpolation | `INTERPOLATION_PREFIX`, `OPEN_QUOTE`, `CLOSING_QUOTE`, `REGULAR_STRING_PART`, `ESCAPE_SEQUENCE`, `SHORT_TEMPLATE_ENTRY_START`, `LONG_TEMPLATE_ENTRY_START`/`END` | `QUOTE_OPEN`, `TRIPLE_QUOTE_OPEN`, string modes in grammar |
| `Inside` mode | Handled by parser, not lexer | `Inside_*` token aliases for parenthesized context |
| `super@`, `this@` etc. | `SUPER_KEYWORD` + `AT` | `SUPER_AT`, `THIS_AT` variants |
| Field identifiers | `FIELD_IDENTIFIER` (`$ident`) | No separate token |
| `$` in identifiers | Accepted by `IDENTIFIER` pattern | Excluded from `Identifier` |

Both specifications are 1:1 mappable to the same `proc_macro`-shaped
output. The JFlex version is the implementation we can directly vendor;
the ANTLR4 version is the specification we cross-check against.

---

## The path forward

### Revised approach: vendor the JetBrains KMP lexer, don't hand-write from scratch

The original plan was to hand-write a `KotlinLexer` from the ANTLR4 `.g4`
specification. This is still a valid fallback, but the discovery that
JetBrains' own `KotlinFlexLexer.kt` is already pure Kotlin multiplatform
code (zero `java.*` imports, `CharSequence` buffer, Kotlin stdlib
surrogates only) changes the calculus. We can vendor the existing
generated lexer directly instead of reimplementing it.

Advantages:
- The lexer is already correct — it's what the Kotlin compiler uses.
- The `KtTokens` token vocabulary is already aligned with the
  `com.intellij.platform.syntax` infrastructure we've vendored.
- `FlexAdapter` + `FlexLexer` interfaces are already in our vendored
  tree and are the exact interfaces `KotlinFlexLexer` implements.
- No risk of divergence between our hand-written lexer and the spec.

The ANTLR4 `.g4` files remain valuable as a cross-reference for the
token vocabulary and for understanding the mode-switching rules at a
specification level.

### Phase 2b — Vendor the JetBrains KMP lexer

Vendoring order, leaves first (each step compiles before the next):

1. **`org.jetbrains.kotlin.kmp.utils`** — `Stack.kt` (26 lines),
   `StringUtil.kt` (39 lines), `SyntaxElementTypesWithIds.kt` (34 lines).
   These are small utility files that `KtTokens` depends on.

2. **`org.jetbrains.kotlin.kmp.lexer.KtTokens`** — `KtTokens.kt`
   (410 lines). The token vocabulary. Depends on `SyntaxElementTypesWithIds`
   from step 1 and `KDocTokens` from step 3.

3. **`org.jetbrains.kotlin.kmp.lexer.KDocTokens`** — `KDocTokens.kt`
   (38 lines), `KDocKnownTag.kt` (45 lines). Small, no external deps
   beyond `SyntaxElementType`.

4. **`org.jetbrains.kotlin.kmp.lexer.KotlinFlexLexer`** —
   `KotlinFlexLexer.kt` (1,723 lines). The generated JFlex output.
   Pure Kotlin. Depends on `FlexLexer` (already vendored), `KtTokens`,
   `SyntaxTokenTypes`. The `codePointAt` extension function at the
   bottom of the file is pure Kotlin using `Char.isHighSurrogate()`
   etc. — no JVM dependency.

5. **`org.jetbrains.kotlin.kmp.lexer.KotlinLexer`** — `KotlinLexer.kt`
   (11 lines). The thin `FlexAdapter` wrapper.
   `class KotlinLexer : FlexAdapter(KotlinFlexLexer())`. Depends on
   `FlexAdapter` (already vendored) and `KotlinFlexLexer`.

6. **`org.jetbrains.kotlin.kmp.lexer.KDocFlexLexer` + `KDocLexer`** —
   `KDocFlexLexer.kt` + `KDocLexer.kt`. Optional, for KDoc support.

At this point `KotlinLexer` is usable. `KotlinLexer().start(source)`
produces `KtTokens.*`-typed `SyntaxElementType` tokens with real byte
offsets. The `FlexAdapter` base class provides `getTokenStart()`,
`getTokenEnd()`, `advance()`, `getState()`.

Each vendored file gets the standard provenance comment:
```kotlin
// Vendored from JetBrains/kotlin @ <sha> compiler/multiplatform-parsing/common/src/...
```

### Phase 2c — Adapter: `KtTokens` → `proc_macro` `TokenTree`

A thin adapter layer converts `KotlinLexer` output into `proc_macro`-shaped
`TokenStream`:

| `KtTokens` token | `proc_macro` shape |
|---|---|
| `IDENTIFIER` | `TokenTree.Ident` |
| `INTEGER_LITERAL`, `FLOAT_LITERAL`, `CHARACTER_LITERAL` | `TokenTree.Literal` |
| `NULL_KEYWORD`, `TRUE_KEYWORD`, `FALSE_KEYWORD` | `TokenTree.Ident` (keywords are idents in `proc_macro`) |
| Hard keywords (`PACKAGE_KEYWORD`, `IF_KEYWORD`, etc.) | `TokenTree.Ident` (same — `proc_macro` doesn't distinguish keywords from idents) |
| Soft keywords / modifiers | `TokenTree.Ident` |
| `DOT`, `COMMA`, `COLON`, `SEMICOLON`, `HASH` | `TokenTree.Punct` (single char, `Spacing.Alone`) |
| `ARROW` (`->`), `RANGE` (`..`), `COLONCOLON` (`::`), `CONJ` (`&&`), `DISJ` (`||`), `DOUBLE_ARROW` (`=>`), etc. | `TokenTree.Punct` chains (`Spacing.Joint` then `Spacing.Alone`) |
| `LPAR`...`RPAR` | `TokenTree.Group(Delimiter.Parenthesis, ...)` |
| `LBRACE`...`RBRACE` | `TokenTree.Group(Delimiter.Brace, ...)` |
| `LBRACKET`...`RBRACKET` | `TokenTree.Group(Delimiter.Bracket, ...)` |
| `WHITE_SPACE`, `EOL_COMMENT`, `BLOCK_COMMENT`, `DOC_COMMENT` | Filtered (not in output `TokenStream`) |
| `OPEN_QUOTE`, `CLOSING_QUOTE`, `REGULAR_STRING_PART`, `ESCAPE_SEQUENCE`, `SHORT_TEMPLATE_ENTRY_START`, `LONG_TEMPLATE_ENTRY_START`/`END`, `INTERPOLATION_PREFIX` | String template representation decision (see open questions) |

The adapter wraps `KotlinLexer` in a function:
```kotlin
fun TokenStream.Companion.fromKotlinSource(source: String): TokenStream
```
that runs the lexer, filters whitespace/comments, matches delimiters
for `Group` nesting, and produces a `TokenStream`.

### Phase 2d — Group nesting (delimiter matching)

`TokenStream.fromString` in upstream `proc_macro` produces *nested*
`Group` tokens — the delimiter matching happens at lex time. Our adapter
needs a delimiter-stack to pair `LPAR`/`RPAR`, `LBRACE`/`RBRACE`,
`LBRACKET`/`RBRACKET` and produce `Group` wrappers. This does not
require a full parser — just a bracket-matching pass over the flat
token stream.

### Phase 2e — Full Kotlin parser (optional, separate from lexer)

A full Kotlin parser (expression structure, statement structure) is a
separate future task. Two possible paths:

1. **Vendor JetBrains' `KotlinParser`** — the `KotlinParsing.kt`
   (2,909 lines) + `KotlinExpressionParsing.kt` (1,874 lines) +
   supporting files (~4,000 additional lines) from the same KMP-parsing
   source. These files are pure Kotlin and use the `SyntaxTreeBuilder`
   API we've already vendored. This gives us a production-quality Kotlin
   parser with zero custom code.
2. **Write a `lalrpop` grammar for Kotlin** derived from `KotlinParser.g4`
   and generate LR(1) parse tables via `lalrpop-kotlin`. This adds a
   `KotlinWrite` emitter to `lalrpop-kotlin` (the Kotlin emitter the
   README already calls out as the natural next step) and produces
   parse tables that any kotlinmania consumer can use.

Either path depends on the lexer existing first. The lexer is the
prerequisite for everything downstream. The parser is optional for
`proc-macro-kotlin`'s immediate purpose (tokenizing Kotlin source
into `proc_macro`-shaped tokens), but necessary for full syntax
validation and for the `lalrpop-kotlin` Kotlin-emitter use case.

### Phase 3 — Wire phases 1 + 2 into a real Compiler variant

Now the `proc_macro`-shaped types stop operating in Fallback mode:

A. `TokenStream.fromString(source)` detects whether the input is Rust
   or Kotlin source (or is told explicitly). Kotlin-source input routes
   through `KotlinLexer` → adapter → `TokenStream`. Rust-source
   input keeps the existing Fallback path.
B. `Span` byte ranges sourced from real `KotlinLexer` offsets, not from
   the process-wide synthetic source map.
C. `Group` produced by the delimiter-matching pass, so `fromString`
   yields nested `Group` tokens instead of flat `Punct` sequences.
D. Round-trip validation:
   `TokenStream.toString().let { TokenStream.fromString(it) } == TokenStream`.

#### Open questions for phase 3

- **Kotlin string templates.** `OPEN_QUOTE` / `CLOSING_QUOTE` /
   interpolation entries have no `proc_macro` analog. Options:
  a. Synthetic `Group` with a custom `Delimiter` variant.
  b. Sequence of `Punct` + `Literal` tokens matching how a Rust
     `format_args!` expansion would look.
  c. A dedicated `TokenTree` variant (breaks the four-variant sealed
     class contract — only if a and b are unworkable).
- **Kotlin multi-char operators.** `?.`, `!!`, `?:`, `..`, `::`, `=>`,
   `&&`, `||` — decompose into single-character `Punct` with
   `Spacing.Joint` chains, matching how `proc_macro` represents
   multi-char operators. This is faithful to the upstream model.
  Note: the JFlex lexer already splits some of these. `QUEST` is a
  single token in `KtTokens`; the adapter must decide whether `?`
  preceding `.` becomes `Punct('?')` + `Punct('.')` or `Punct('?')`
  with lookahead. The `proc_macro` convention is single chars only.
- **Whitespace-sensitive tokens.** The ANTLR4 spec defines `QUEST_WS`
  vs `QUEST_NO_WS`, `EXCL_WS` vs `EXCL_NO_WS`, `AT_*_WS` variants.
  The JFlex lexer collapses these into single tokens (`QUEST`, `EXCL`,
  `AT`). For `proc_macro` output, the whitespace-sensitivity doesn't
  matter — `Punct` tokens don't carry whitespace context. The adapter
  can safely ignore the distinction.
- **`Span.resolvedAt()` / `Span.locatedAt()`.** Rust hygiene resolution
   has no Kotlin analog. Stub to identity for now; model resolution
   context independently if real hygiene becomes necessary.

### Phase 4 — Wire into `proc-macro2-kotlin`

Cross-repo task. Restore the two-variant wrapper layer that
`proc-macro2-kotlin`'s `port/refaithful-divergent-translations` branch
collapsed, with the `Compiler` arms delegating to this repo's types.
`Detection.kt`'s `insideProcMacro()` gets a non-trivial meaning: "the
Kotlin lexer is available on this target."

Publish `proc-macro-kotlin 0.1.0` and `proc-macro2-kotlin 0.2.0`
together.

### Phase 5 — Kotlin-native JFlex (future, separate repo)

Batch-translate the JetBrains JFlex fork from Java to Kotlin and publish
it as a standalone build-time tool. This is not a library dependency of
`proc-macro-kotlin` — it's a separate project that produces the same
`.kt` lexer output that JetBrains' JVM-based JFlex produces, but runs
natively as a Kotlin application.

Scope:
1. **Translate the core generator** (~14.5K Java lines, excluding Unicode
   data tables and GUI). IntelliJ IDEA's Java → Kotlin converter handles
   the bulk. Manual cleanup for: `java.io.Reader` → `kotlinx.io`,
   `java_cup.runtime.Symbol` → Kotlin equivalent, `System.exit` →
   exception-based flow, `java.util.*` collections → Kotlin stdlib.
2. **Translate the Kotlin-specific files** (2,472 Java lines). These
   are the highest-value targets — they already emit valid Kotlin syntax.
3. **Port the skeleton template.** `idea-flex-kotlin.skeleton` (302
   lines) is already nearly Kotlin — it's a template with JFlex
   placeholders. The batch-translate handles the remaining `@JvmStatic`
   annotations and `companion object` structure.
4. **Regenerate Unicode data tables** using the `ucd_generator` that
   ships with JFlex, targeting Kotlin output. Or exclude the tables
   and generate them as a build step.
5. **Test against existing `.flex` specs.** Run the Kotlin-native JFlex
   on `Kotlin.flex` and `KDoc.flex` and diff the output against the
   existing `KotlinFlexLexer.kt` / `KDocFlexLexer.kt`. Byte-identical
   output is the success criterion.
6. **Publish as a separate `jflex-kotlin` repo** under kotlinmania.
   This is a build tool, not a library — it runs in Gradle build scripts
   or as a CLI command, not at application runtime.

Why this is valuable beyond this repo:
- Any kotlinmania project that needs a lexer (including future language
  ports) can write a `.flex` spec and get a correct Kotlin lexer.
- Combined with `lalrpop-kotlin` (LR(1) parser generator), the workspace
  has a complete Kotlin-native toolchain: `.flex` → JFlex → Kotlin lexer,
  `.lalrpop` → lalrpop → LR(1) parser tables.
- No JVM dependency in the build pipeline for any kotlinmania repo.

---

## Why this matters for serde-kotlin (and everything downstream)

The entire kotlinmania workspace has a dependency chain bottleneck at
`serde_derive`. Serde is the #1 porting priority (101 direct dependents,
187 transitive), and `serde_core` is 82% ported — but `serde_derive`
(the `#[derive(Serialize, Deserialize)]` code generator) has zero Kotlin
source because it is a Rust **proc-macro** that emits Rust token streams.
You cannot "just transliterate" a proc-macro the way you transliterate a
library crate.

The proc-macro-kotlin pipeline makes `serde_derive` a straight
transliteration job:

```
Rust source ──→ proc-macro2-kotlin (Fallback, Rust-shaped tokens)
                    │
                    ├──→ syn-kotlin (Rust AST)
                    │        │
                    │        └──→ serde_derive-kotlin (derive logic)
                    │                 │
                    │                 └──→ quote-kotlin (TokenStream emission)
                    │                      │
                    │                      └──→ proc-macro-kotlin (Compiler variant)
                    │                           │
                    │                           └──→ KotlinLexer validates output as Kotlin
                    │
                    └──→ proc-macro2-kotlin dispatches:
                         Fallback → Rust-source tokenization
                         Compiler → Kotlin-source tokenization (this repo)
```

Every downstream kotlinmania crate that uses `#[derive(Serialize,
Deserialize)]` — and there are over 100 of them — depends on this
pipeline existing. The Kotlin lexer is the single prerequisite that
unlocks it.

### The ecosystem that already exists

| Crate | Repo | Published | kt lines | Role in pipeline |
|---|---|---|---|---|
| `proc-macro2` | `proc-macro2-kotlin` | v0.1.1 | 4,003 | Public API + Fallback lexer |
| `syn` | `syn-kotlin` | v0.1.7 | 7,688 | Rust AST |
| `quote` | `quote-kotlin` | v0.1.1 | 330 | TokenStream emission |
| `proc_macro` | `proc-macro-kotlin` | not yet | 14,248 | Compiler-variant backend |
| `lalrpop` | `lalrpop-kotlin` | v0.1.6 | 71,406 | LR(1) parser generator |
| `starlark-syntax` | `starlark-syntax-kotlin` | v0.1.1 | 22,718 | Hand-written lexer reference |

The stack is one vendor pass (KMP lexer ~2,400 lines) + one adapter
layer + one wrapper-branch PR away from being a working end-to-end
pipeline. Every piece publishes except this repo.
