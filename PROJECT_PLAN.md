# Project Plan — proc-macro-kotlin

Stage: **Phase 2c complete. Phase 3 (publish + wire into proc-macro2-kotlin) is the next action.**

---

## The critical path: what blocks serde_derive

`serde` is rank #1 in the workspace dependency graph (101 direct, 187 transitive dependents). Its core is 82% ported. But `serde_derive` — the `#[derive(Serialize, Deserialize)]` code generator — is the crate that actually makes serde useful, and it is a **proc-macro** that cannot be transliterated like a library crate.

**The blocking chain:**

```
proc-macro-kotlin published (v0.1.0)
  └─→ proc-macro2-kotlin Compiler variant wired (v0.2.0)
       └─→ serde_derive can be ported as straight transliteration
            └─→ 101 downstream crates unblocked
```

**What serde_derive actually imports** (from `tmp/serde/serde_derive/src/lib.rs`):

```rust
use proc_macro::TokenStream;        // ← needs proc-macro-kotlin
use proc_macro2::{Ident, Span};      // ✓ already in proc-macro2-kotlin
use quote::{ToTokens, TokenStreamExt as _};  // ✓ already in quote-kotlin v0.1.1
use syn::parse_macro_input;         // ✓ already in syn-kotlin v0.1.7
use syn::DeriveInput;               // ✓ already in syn-kotlin
```

Every downstream crate in serde_derive's body (`de.rs`, `ser.rs`, `bound.rs`, `internals/*.rs`) uses `proc_macro2::TokenStream`, `quote::quote`, `quote::quote_spanned`, and `syn::*` — all already published on Maven Central. The only missing surface is `proc_macro::TokenStream` at the entry point, which is what this repo provides.

**serde_derive port status:**

| Rust file | Lines | Kotlin port | Lines | Status |
|---|---|---|---|---|
| `internals/attr.rs` | 1,818 | `Attr.kt` | 1,370 | Done |
| `internals/check.rs` | 477 | `Check.kt` | 533 | Done |
| `internals/ast.rs` | — | `Ast.kt` | 276 | Done |
| `internals/case.rs` | 200 | `Case.kt` | 225 | Done |
| `pretend.rs` | 188 | `Pretend.kt` | 197 | Done |
| `internals/name.rs` | 113 | `Name.kt` | 117 | Done |
| `fragment.rs` | — | `Fragment.kt` | 91 | Done |
| `internals/ctxt.rs` | 67 | `Ctxt.kt` | 83 | Done |
| `deprecated.rs` | — | `Deprecated.kt` | 61 | Done |
| `internals/symbol.rs` | 71 | `Symbol.kt` | 57 | Done |
| `this.rs` | 32 | `This.kt` | 40 | Done |
| `dummy.rs` | — | `Dummy.kt` | 40 | Done |
| `internals/respan.rs` | 16 | `Respan.kt` | 34 | Done |
| `internals/mod.rs` | 28 | `Mod.kt` | 15 | Done |
| **`lib.rs`** | **127** | — | — | **Not started — needs `proc_macro::TokenStream`** |
| **`de.rs`** | **976** | — | — | **Not started — heavy quote! codegen** |
| **`ser.rs`** | **1,369** | — | — | **Not started — heavy quote! codegen** |
| **`bound.rs`** | **425** | — | — | **Not started** |
| **`internals/receiver.rs`** | **293** | — | — | **Not started** |

Ported: 3,139 lines (internals + scaffolding). Remaining: 3,190 lines (code generators + entry point). The internals are done; the code generators are what need the proc-macro pipeline.

---

## What's done

### Phase 1 — Rust `proc_macro` API surface (complete)

All 10 core types ported from `tmp/proc-macro/` with `port-lint: source` headers:

| Type | Lines | Status |
|---|---|---|
| `Delimiter` | 37 | Complete |
| `Spacing` | 39 | Complete |
| `LexError` | 14 | Complete |
| `Ident` | 125 | Complete (XID_Start / XID_Continue validation) |
| `Punct` | 82 | Complete |
| `Literal` | 414 | Complete (all suffixed/unsuffixed factories) |
| `Group` | 104 | Complete |
| `TokenTree` | 78 | Complete (four-variant sealed class) |
| `Span` | 237 | Complete (span data accessors backed by synthetic source map) |
| `TokenStream` | 205 | Complete (KotlinLexer-backed `fromString`) |
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

9 files, ~2,386 lines from the JetBrains Kotlin compiler's
`multiplatform-parsing` module. `KotlinLexer.kt` wraps `KotlinFlexLexer.kt`
(1,689 lines, JFlex-generated, pure Kotlin Multiplatform). `KtTokens.kt`
(465 lines) defines the full token vocabulary.

### Phase 2c — KotlinLexer wired into TokenStream (complete)

`KtTokenAdapter` converts JetBrains `SyntaxElementType` tokens into
`proc_macro`-shaped `TokenTree` variants:

- Whitespace and comments are filtered
- String template token runs collapse into atomic `Literal`s
- Multi-char operators decompose into `Punct` chains with correct spacing
- Delimiter pairs nest into `Group` with `TokenStream`
- Kotlin keywords map to `Ident`
- `Literal` has `fromKotlinString/Char/Integer/Float` factory methods

19 integration tests passing (macosArm64Test).

---

## Next action: Phase 3 — Publish and wire into proc-macro2-kotlin

This is the action that unblocks serde_derive. It's a two-repo coordinated release.

### Step 3.1 — Publish proc-macro-kotlin v0.1.0

- Bump `version` in `build.gradle.kts` to `0.1.0`
- Update README install snippets
- `gh release create v0.1.0` to fire the `release[released]` publish workflow
- Verify artifact appears on Maven Central

### Step 3.2 — Add proc-macro-kotlin as a dependency of proc-macro2-kotlin

```kotlin
commonMain.dependencies {
    implementation("io.github.kotlinmania:proc-macro-kotlin:0.1.0")
}
```

### Step 3.3 — Wire Detection.insideProcMacro() to return true

`Detection.kt` currently always returns `false` (Fallback mode). The Kotlin
lexer is pure Kotlin Multiplatform — it's always available on every target.
Change `initialize()` to store `works = 2` (Compiler mode), and remove the
dead-code path that pretends the Compiler half doesn't exist.

### Step 3.4 — Restore the two-variant wrapper layer

`Wrapper.kt` is currently a stub (with a `port-lint: ignore` that must be
stripped per AGENTS.md — no such directive exists). Restore it as a real
dispatch layer:

- `WrapperTokenStream` delegates to `FallbackTokenStream` when
  `insideProcMacro() == false`, delegates to `proc_macro.TokenStream`
  when `insideProcMacro() == true`
- Same pattern for `WrapperSpan`, `WrapperGroup`, `WrapperIdent`,
  `WrapperLiteral`, `WrapperLexError`
- Since `insideProcMacro()` is always `true` now, the Compiler path is
  the hot path. Fallback remains available via `forceFallback()`

### Step 3.5 — Publish proc-macro2-kotlin v0.2.0

- Bump version to `0.2.0`
- Update `libs.versions.toml` references in downstream repos
- `gh release create v0.2.0`

### Open questions for Phase 3

- **Kotlin string templates.** `OPEN_QUOTE` / `CLOSING_QUOTE` /
  interpolation entries have no `proc_macro` analog. Options:
  a. Synthetic `Group` with a custom `Delimiter` variant.
  b. Sequence of `Punct` + `Literal` tokens matching how a Rust
     `format_args!` expansion would look.
  c. A dedicated `TokenTree` variant (breaks the four-variant sealed
     class contract — only if a and b are unworkable).
- **Kotlin multi-char operators.** `?.`, `!!`, `?:`, `..`, `::`, `=>`,
  `&&`, `||` — decompose into single-character `Punct` with
  `Spacing.Joint` chains. This is already handled in `KtTokenAdapter`.
- **`Span.resolvedAt()` / `Span.locatedAt()`.** Rust hygiene resolution
  has no Kotlin analog. Stub to identity; model resolution context
  independently if real hygiene becomes necessary.

---

## Phase 4 — Kotlin parser

With the lexer + `KtTokenAdapter` + `TokenStream` working, a parser is
the natural next piece. Three paths are available, in order of maturity:

### Path A: JetBrains KMP recursive-descent parser (fastest to land)

The full parser is already vendored in `tmp/kmp-parser/` (10,509 lines
across 28 files) and also available in the Kotlin compiler source at
`kotlin.coroutines-cpp/tmp/kotlin/compiler/multiplatform-parsing/`.
Key files: `KotlinParsing.kt` (2,909 lines),
`KotlinExpressionParsing.kt` (1,874 lines).

The compiler's `SemanticWhitespaceAwareSyntaxBuilderImpl` handles
complex token joining at the parser level: `QUEST`+`DOT` →
`SAFE_ACCESS`, `QUEST`+`COLON` → `ELVIS`, `EXCL`+`EXCL` → `EXCLEXCL`.
Our `KtTokenAdapter` decomposes these at the adapter level instead,
which is simpler for our use case.

The compiler also has a `LightTree2Fir` converter pipeline:
`source code → KotlinLightParser.buildLightTree() →
FlyweightCapableTreeStructure → LightTreeRawFirDeclarationBuilder → FIR`.
This shows the full production path from tokens to compiler IR.

This is the fastest path to a working Kotlin parser because it's
already Kotlin Multiplatform code — same shape as the lexer we already
vendored. Wire it behind a `proc_macro`-shaped surface and it produces
AST nodes over real Kotlin source.

### Path B: ANTLR4 with Kotlin runtime (grammar-driven)

**Major discovery:** The ANTLR4 `dev` branch has already converted the
entire runtime to Kotlin. The `atn/` package (69 files, 10,557 lines)
is entirely `.kt` — no Java at all. The full runtime is 141 Kotlin files
totaling 23,577 lines, with only 33 remaining `.java` files (2,927 lines)
in the `tree/` subpackage (parse tree visitor/walker infrastructure).

The `kotlin-spec` grammars in `tmp/kotlin-spec/` provide
`KotlinLexer.g4` and `KotlinParser.g4` — authoritative ANTLR4 grammar
specs for the Kotlin language, maintained by JetBrains.

**ANTLR4 runtime status (from `dev` branch):**

| Package | Kotlin files | Lines | Java deps | KMP adaptation |
|---|---|---|---|---|
| `atn/` | 69 | 10,557 | `java.util`, `java.security` (1 usage) | Easy — swap `java.util` for Kotlin stdlib |
| `dfa/` | 3 | ~480 | `java.util` only | Easy |
| `misc/` | 18 | ~3,177 | `java.util`, `java.io` | Easy — `java.io` isolated to `InterpreterDataReader` |
| Top-level runtime | 45 | ~9,556 | `java.io`, `java.nio`, `java.util` | Medium — I/O isolated to `CharStreams`, `ANTLRInputStream` |
| `tree/` | 0 (Java) | 2,927 | `java.util` only | Easy — batch-translate last 33 Java files |
| `tree/pattern/` | 0 (Java) | 1,342 | `java.util` only | Easy |
| `tree/xpath/` | 0 (Java) | 653 | `java.util` only | Easy |

**KMP adaptation work for `atn/` (the highest-value package):**

The `java.util` imports in `atn/` are nearly all direct Kotlin stdlib
swaps. The two non-trivial ones are `IdentityHashMap` (2-3 files) and
`BitSet` (2-3 files), both of which can be implemented in pure Kotlin.

The only `java.security` usage is `AccessController.doPrivileged` in
`ParserATNSimulator.kt` — a minor optimization guard that can be removed
without changing behavior.

The only `java.io` usage in `atn/` is `InvalidClassException` in
`ATNConfig.kt` — swap for a custom exception class.

**`km-io` replaces `java.io` and `java.nio` for KMP I/O.**

The kotlinmania workspace already has `km-io` (v0.1.5, Maven Central), a
fork of `kotlinx-io`/Okio that provides `Source`, `Sink`, `Buffer`,
`FileSystem`, `Path`, and `ByteString` across **every** KMP target:

JVM, JS, wasmJs, wasmWasi, Android, iOS (x64/arm64/sim), tvOS
(x64/arm64/sim), watchOS (arm32/arm64/x64/sim/device), Android Native
(arm32/arm64/x64/x86), Linux (x64/arm64), macOS (x64/arm64),
Windows (mingwX64).

For the ANTLR4 runtime's I/O layer (`CharStreams`, `ANTLRInputStream`,
`ANTLRFileStream`), `km-io`'s `Source`/`RawSource` replaces
`java.io.InputStream`/`Reader`, and `km-io`'s `Buffer` replaces
`java.nio.CharBuffer`/`ByteBuffer`. No new I/O abstractions needed.

**`libc-kotlin` and `klang` cover C-level primitives.**

`libc-kotlin` (1,944 lines, POSIX bindings) and `klang` (21,942 lines,
pure Kotlin C-semantics library) provide `BitSet`-level bit manipulation,
`GlobalHeap` memory model, and `CString`/`CLib` primitives that cover the
low-level patterns sometimes found in parser runtimes. If `BitSet` or
`IdentityHashMap` needs a KMP-native implementation, `klang`'s bitwise
infrastructure (`BitPrimitives`, `BitTwiddle`) provides the foundation.

### Path C: lalrpop-kotlin + logos-kotlin (fully Kotlin-native)

The kotlinmania workspace has its own LR(1) parser generator
(`lalrpop-kotlin`, 173 Kotlin files, v0.1.7) and a working hand-written
lexer pattern from `starlark-syntax-kotlin` (50 Kotlin files, v0.1.1).
`logos-kotlin` (15 Kotlin files) provides a DFA-based lexer generator.

The fully Kotlin-native path:

1. Write a Kotlin token vocabulary as a `Logos`-style enum, generate
   a DFA at build time via `logos-kotlin`
2. Translate `KotlinParser.g4` into a `.lalrpop` grammar
3. Generate LR(1) parse tables via `lalrpop-kotlin`
4. No JVM, no C, no ANTLR4, no JFlex in the build pipeline

This is the Phase 5 endpoint — the most principled path but the most
work. `lalrpop-kotlin` and `logos-kotlin` are not yet published to
Maven Central.

### Decision tree

```
Need a parser now?
  ├─ Yes → Path A (JetBrains KMP parser, already Kotlin MP code)
  └─ Need grammar-driven correctness guarantees?
       ├─ Yes → Path B (ANTLR4, KMP-adapt the already-Kotlin runtime)
       └─ Need fully Kotlin-native toolchain?
            └─ Yes → Path C (lalrpop + logos, most work, most principled)
```

---

## Phase 5 — Kotlin-native JFlex (separate repo)

Batch-translate the JetBrains JFlex fork from Java to Kotlin and publish
it as a standalone build-time tool. Not a library dependency of this
repo — a separate project that produces the same `.kt` lexer output
that JetBrains' JVM-based JFlex produces, but runs natively as a
Kotlin application.

### Scope

1. **Translate the core generator** (~14.5K Java lines, excluding Unicode
   data tables and GUI). IntelliJ IDEA batch-convert handles the bulk.
   Manual cleanup: `java.io.Reader` → `km-io` `Source`,
   `java_cup.runtime.Symbol` → Kotlin equivalent, `System.exit` →
   exception-based flow, `java.util.*` → Kotlin stdlib.
2. **Translate the Kotlin-specific files** (2,472 Java lines).
   These are the highest-value targets — they already emit valid
   Kotlin syntax.
3. **Port the skeleton template.** `idea-flex-kotlin.skeleton` (302
   lines) is already nearly Kotlin.
4. **Regenerate Unicode data tables** using the `ucd_generator` that
   ships with JFlex, targeting Kotlin output.
5. **Test against existing `.flex` specs.** Run the Kotlin-native JFlex
   on `Kotlin.flex` and diff the output against the existing
   `KotlinFlexLexer.kt`. Byte-identical output is the success criterion.
6. **Publish as a separate `jflex-kotlin` repo** under kotlinmania.

### Why this matters beyond this repo

- Any kotlinmania project that needs a lexer can write a `.flex` spec
  and get a correct Kotlin lexer without a JVM dependency.
- Combined with `lalrpop-kotlin`, the workspace has a complete
  Kotlin-native toolchain: `.flex` → JFlex → Kotlin lexer,
  `.lalrpop` → lalrpop → LR(1) parser tables.
- No JVM dependency in the build pipeline for any kotlinmania repo.

---

## Patterns from the Kotlin compiler source

The Kotlin compiler source at
`kotlin.coroutines-cpp/tmp/kotlin/compiler/` reveals several patterns
relevant to this project:

### Complex token joining (SemanticWhitespaceAwareSyntaxBuilderImpl)

The KMP parser uses a `SemanticWhitespaceAwareSyntaxBuilderImpl` that
wraps `SyntaxTreeBuilder` and intercepts `tokenType`, `advanceLexer()`,
`tokenText`, and `lookAhead()` to transparently join compound tokens:

| Raw tokens | Joined token | Joining rule |
|---|---|---|
| `QUEST` + `DOT` | `SAFE_ACCESS` (`?.`) | `?` followed by `.` |
| `QUEST` + `COLON` | `ELVIS` (`?:`) | `?` followed by `:` |
| `EXCL` + `EXCL` | `EXCLEXCL` (`!!`) | `!` followed by `!` |

When joining is enabled and a complex token is detected,
`advanceLexer()` creates a `mark()`, advances twice, and calls
`mark.collapse(tokenType)` to merge the two tokens into one.

Our `KtTokenAdapter` takes the opposite approach: it receives the
already-joined token and decomposes it back into `Punct` chains.
Both approaches are correct; the decomposition approach is simpler
for our use case since `proc_macro` tokens are always single characters.

### Light tree → FIR pipeline

The compiler's `LightTree2Fir` class shows the production pipeline:

1. `KtSourceFile` + source code → `KotlinLightParser.buildLightTree()`
2. `FlyweightCapableTreeStructure<LighterASTNode>` (the light tree)
3. `LightTreeRawFirDeclarationBuilder.convertFile()` → FIR

The light tree is a flyweight AST — no PSI, no JVM dependencies in the
KMP version. `LightTreeRawFirDeclarationBuilder` (2,929 lines) and
`LightTreeRawFirExpressionBuilder` (1,719 lines) walk the light tree
and emit FIR nodes. This pattern is directly applicable to our
proc-macro pipeline: `KotlinLexer` → light tree → proc_macro types.

### Two parser implementations in the compiler

The Kotlin compiler has two distinct parser implementations:

1. **KMP parser** (`multiplatform-parsing/common/`) — Pure Kotlin
   Multiplatform, uses `SyntaxTreeBuilder` from `com.intellij.platform.syntax`.
   This is what we've vendored and what works without JVM dependencies.

2. **PSI parser** (`psi/parser/`) — JVM-based, uses `PsiBuilder` from
   IntelliJ Platform. Mixed Java + Kotlin. Uses the JVM `KotlinLexer`
   (`org.jetbrains.kotlin.lexer.KotlinLexer`, a different class from
   the KMP one). This path has more features (PSI tree, IntelliJ
   integration) but requires the JVM.

For `proc-macro-kotlin`, the KMP parser is the right choice. It's pure
Kotlin Multiplatform, produces the same tokens, and has no JVM
dependencies.

---

## The kotlinmania infrastructure ecosystem

These repos exist under `/Volumes/stuff/Projects/kotlinmania/` and provide
the KMP building blocks for the proc-macro pipeline and the ANTLR4
KMP adaptation.

### Proc-macro pipeline

| Repo | Published | Role |
|---|---|---|
| `proc-macro2-kotlin` | v0.1.1 (Maven Central) | Public API + Fallback lexer |
| `syn-kotlin` | v0.1.7 (Maven Central) | Rust AST |
| `quote-kotlin` | v0.1.1 (Maven Central) | TokenStream emission |
| `proc-macro-kotlin` | **not yet** | Compiler-variant backend (this repo) |

### Lexer/parser toolchain

| Repo | Published | Role |
|---|---|---|
| `lalrpop-kotlin` | v0.1.7 (local) | LR(1) parser generator |
| `logos-kotlin` | v0.1.0 (local) | DFA-based lexer generator |
| `starlark-syntax-kotlin` | v0.1.1 (Maven Central) | Hand-written lexer reference |
| `tree-sitter-kotlin` | local | tree-sitter C FFI grammar |

### KMP I/O and systems infrastructure

| Repo | Published | Role |
|---|---|---|
| `km-io` | v0.1.5 (Maven Central) | KMP I/O: `Source`, `Sink`, `Buffer`, `FileSystem`, `Path` — replaces `java.io`/`java.nio` |
| `libc-kotlin` | not yet | POSIX bindings — covers `unistd`, `pthread`, `types` |
| `klang` | local | Pure Kotlin C-semantics: `GlobalHeap`, `KMalloc`, `CString`, `BitPrimitives` |
| `starlarkmap-kotlin` | v0.1.2 | Hash collections: `Equivalent`, `FxHasher64`, `UnorderedMap`, `OrderedMap`, `SmallMap`, `VecMap` |
| `indexmap-kotlin` | not yet | `IndexMap`/`IndexSet` — insertion-order-preserving hash map |
| `btree-kotlin` | not yet | `BTreeMap`/`BTreeSet` — ported from Rust std collections |

`km-io` ships **every** KMP target: JVM, JS, wasmJs, wasmWasi, Android,
iOS, tvOS, watchOS, Android Native, Linux, macOS, Windows. It is the
`java.io`/`java.nio` replacement for any KMP adaptation of JVM code.

`klang` provides bit-level primitives (`BitPrimitives`, `BitTwiddle`,
`PackOps`) that can implement `BitSet` and `IdentityHashMap` in pure
Kotlin if needed for the ANTLR4 KMP adaptation.

`starlarkmap-kotlin` is the key missing piece for the ANTLR4 KMP
adaptation. It provides `Equivalent` (the `equivalent` crate trait
that `hashbrown` depends on), `FxHasher64` (the `fxhash`/`rustc-hash`
hasher), and `UnorderedMap`/`UnorderedSet` (`hashbrown`-shaped maps).
ANTLR4's `IdentityHashMap` usage maps directly to an `Equivalent`-based
lookup — define an `Equivalent` that compares by object identity
(`===`) and use `UnorderedMap`. The `BitSet` replacement can use `klang`'s
bit primitives or `starlarkmap`'s `SmallSet`/`OrderedSet` as compact
bit-backed structures.

### Vendored reference sources (all in `tmp/`, gitignored)

| Path | Language | Lines | Source | Purpose |
|---|---|---|---|---|
| `tmp/antlr4/` | Kotlin + Java (tree/) | 23,577 kt + 2,927 java | [antlr/antlr4](https://github.com/antlr/antlr4) `dev` branch | ANTLR4 runtime — **already Kotlin!** KMP-adapt for Kotlin-native parser |
| `tmp/jflex/` | Java | 14,484 | JetBrains/intellij-deps-jflex | JFlex code generator — batch-translate for Kotlin-native JFlex |
| `tmp/jflex-skeleton/` | Kotlin-ish | 302 | JetBrains/intellij-community | `idea-flex-kotlin.skeleton` template |
| `tmp/kotlin-spec/` | ANTLR4 grammar | — | [Kotlin/kotlin-spec](https://github.com/Kotlin/kotlin-spec/tree/release/grammar/src/main/antlr) | `KotlinLexer.g4`, `KotlinParser.g4`, `UnicodeClasses.g4` |
| `tmp/kmp-parser/` | Kotlin | 10,509 | JetBrains KMP parser | Full recursive-descent parser for reference |
| `tmp/proc-macro/` | Rust | — | [rust-lang/rust](https://github.com/rust-lang/rust/tree/master/library/proc_macro/src) | Upstream Rust proc_macro source |

**The ANTLR4 discovery changes the calculus.** The runtime is already
Kotlin — we don't need a Java→Kotlin batch translation step. We need a
KMP adaptation step: swap `java.util.*` for Kotlin stdlib, replace
`java.io`/`java.nio` with `km-io`, and remove `AccessController`.
The `atn/` package (10,557 lines) is the highest-value target and the
cleanest to adapt.

---

## Parallel work tracks

Two people can work in parallel without blocking each other:

### Track 1: Phase 3 — publish + wire

1. Publish `proc-macro-kotlin v0.1.0`
2. Wire into `proc-macro2-kotlin` (Detection, Wrapper, TokenStream dispatch)
3. Publish `proc-macro2-kotlin v0.2.0`
4. Verify serde-kotlin can depend on the new versions

### Track 2: KMP adaptation + Java → Kotlin batch translations

**ANTLR4 `atn/` KMP adaptation (highest parser value, 10,557 lines Kotlin):**
Already Kotlin — just need to swap JVM deps for Kotlin Multiplatform equivalents:

| Java class | Kotlin replacement | kotlinmania alternative |
|---|---|---|
| `ArrayList` | `mutableListOf()` / `ArrayList()` | Direct stdlib swap |
| `HashMap` | `mutableMapOf()` / `HashMap()` | Direct stdlib swap |
| `HashSet` | `mutableSetOf()` / `HashSet()` | Direct stdlib swap |
| `LinkedHashMap` | `linkedMapOf()` | Direct stdlib swap |
| `IdentityHashMap` | `Equivalent`-based `UnorderedMap` | `starlarkmap-kotlin` `Equivalent` + `UnorderedMap` |
| `BitSet` | Custom impl or `klang` `BitPrimitives` | `klang` + `starlarkmap` `SmallSet` |
| `Arrays` | Kotlin stdlib `sort`, etc. | Direct stdlib swap |
| `Collections` | Kotlin stdlib equivalents | Direct stdlib swap |
| `AtomicInteger` | `kotlin.concurrent.atomics.AtomicInt` | Direct stdlib swap |
| `AccessController` | Remove (security optimization) | No replacement needed |
| `InvalidClassException` | Custom exception class | Custom |
| `Locale` | `kotlin.text` lowercase/uppercase | Direct stdlib swap |
| `Objects.hash` | `kotlin.hashCode()` combiner | Direct stdlib swap |

I/O adaptation for the top-level runtime (`CharStreams`, `ANTLRInputStream`,
`ANTLRFileStream`, `CodePointCharStream`, `UnbufferedCharStream`):
`java.io.InputStream` → `km-io` `RawSource`, `java.io.Reader` → `km-io`
`Source`, `java.nio.CharBuffer` → `km-io` `Buffer`, `java.nio.ByteBuffer`
→ `km-io` `Buffer`.

The 33 remaining `.java` files in `tree/` (2,927 lines) can also be
batch-translated in IntelliJ — they use only `java.util.*`.

**JFlex Kotlin-specific emitters (highest value, 2,466 lines Java):**
- `KotlinEmitter.java` (1,455 lines)
- `KotlinAbstractLexScan.java` (481 lines)
- `KotlinCountEmitter.java` (180 lines)
- `KotlinPackEmitter.java` (192 lines)
- `KotlinHiLowEmitter.java` (94 lines)
- `KotlinHiCountEmitter.java` (64 lines)

**JFlex base emitters (2,280 lines Java):**
- `Emitter.java` (1,466), `IEmitter.java` (62), `LexGenerator.java` (158),
  `Emitters.java` (83), `CountEmitter.java` (163), `PackEmitter.java` (204),
  `HiCountEmitter.java` (63), `HiLowEmitter.java` (81)

**Manual cleanup after JFlex batch translation:**
- `java.io.Reader` → `km-io` `Source`
- `System.exit` → exception-based flow
- `java.util.*` → Kotlin stdlib
- `java_cup.runtime.Symbol` → Kotlin equivalent
- `@JvmStatic` / `companion object` restructuring
PLAN_EOF

---

## JFlex ecosystem discovery — the Kotlin emitter chain

The Kotlin compiler's own `multiplatform-parsing/build.gradle.kts` reveals
the exact toolchain JetBrains uses to generate `KotlinFlexLexer.kt`:

```
Kotlin.flex ──→ jflex.Main --output-mode kotlin
              --skel idea-flex-kotlin.skeleton
              ──→ KotlinFlexLexer.kt
```

**JFlex already has a production Kotlin target.** The `--output-mode kotlin`
flag and the Kotlin-specific emitters are what JetBrains itself uses to
produce the lexer that ships in the Kotlin compiler. Our `tmp/jflex/`
contains this codebase, already partially ported to Kotlin:

### JFlex core: already in Kotlin (12,991 lines)

| File | Lines | Role |
|---|---|---|
| `generator/KotlinEmitter.kt` | 1,469 | **The Kotlin output emitter** — generates `.kt` lexer files |
| `generator/Emitter.kt` | 1,477 | Base emitter (Java target) |
| `core/NFA.kt` | 929 | NFA construction from regex |
| `dfa/DFA.kt` | 901 | DFA generation and minimization |
| `core/RegExp.kt` | 752 | Regular expression parser |
| `core/KotlinAbstractLexScan.kt` | 442 | Kotlin-mode `.flex` scanner |
| `core/AbstractLexScan.kt` | 435 | Base `.flex` scanner |
| `logging/Out.kt` | 426 | Logging |
| `state/StateSet.kt` | 418 | State set management |
| + 46 more Kotlin files | ~7,842 | Supporting infrastructure |

### JFlex core: remaining Java (2,145 lines non-data, 611K Unicode data)

Only two non-Unicode Java files remain: `Main.java` (408 lines) and
`CMapBlock.java` (45 lines). The Unicode data tables (19 files, 611K
lines) are auto-generated lookup tables — they can stay as-is or be
converted mechanically; they carry no logic.

### CUP2 LALR(1)/LR(1) parser generator: fully in Kotlin

`tmp/jflex/third_party/edu/tum/cup2/` is a complete LALR(1)/LR(1)
parser generator, already fully ported to Kotlin. It provides:

- `LR0Generator`, `LR1Generator`, `LALR1Generator`, `LALR1SCCGenerator`,
  `LALR1ParallelGenerator`, `LLkGenerator` — the full generator hierarchy
- `Automaton`, `AutomatonFactory`, `LR0AutomatonFactory`,
  `LR1AutomatonFactory`, `LALR1AutomatonFactory` — automaton construction
- `Grammar`, `Production`, `NonTerminal`, `Terminal`, `Symbol` — grammar model
- `LRParser`, `LLkParser` — runtime parsers
- `LRActionTable`, `LRGoToTable`, `LRParsingTable` — parse tables

This is the LALR(1) generator the workspace needs — it complements
`lalrpop-kotlin` (which is LR(1) via PEG-inspired table generation).

### Google third_party: Bazel stubs only

`tmp/jflex/third_party/com/google/` contains only Bazel `BUILD.bazel`
files for Guava, FindBugs, Flogger, Truth, and AutoValue — no source
code. These are Maven dependency declarations in Bazel format. They are
not vendored Google code and need no Kotlin translation.

### Kotlin compiler source: confirming the KMP architecture

The Kotlin compiler at `kotlin.coroutines-cpp/tmp/kotlin/` confirms:

1. **`compiler/multiplatform-parsing/`** (30 files) IS the KMP
   lexer/parser — this is the same code we have in `tmp/kmp-parser/`.
   JetBrains uses JFlex with `--output-mode kotlin` + a custom
   `idea-flex-kotlin.skeleton` to generate the `.kt` lexer files.

2. **`compiler/psi/psi-api/`** (235 files, 153 Java) is the JVM-only
   PSI layer — token interfaces (`KtToken.java`, `KtTokens.java`),
   AST node types, visitors, stubs. This is IntelliJ-specific and
   not needed for KMP. The KMP equivalents live in our vendored
   `com.intellij.platform.syntax.*` infrastructure.

3. **`compiler/psi/parser/`** has only `buildLexer.xml` — the Ant
   build script that shows the JFlex invocation for the JVM lexer
   (uses `idea-flex.skeleton`, not `idea-flex-kotlin.skeleton`).

**The JFlex Kotlin emitter chain means we can generate new Kotlin
lexers, not just consume pre-generated ones.** If we need a lexer
for a new token vocabulary, we write a `.flex` spec and run JFlex
with `--output-mode kotlin` — the same toolchain JetBrains uses.

---

## Updated parallel work tracks

### Track 1: Phase 3 — publish + wire (unblocks serde_derive)

1. ~~Publish `proc-macro-kotlin v0.1.0`~~ ✓ Done (v0.1.1 now)
2. Wire into `proc-macro2-kotlin` (Detection, Wrapper, TokenStream dispatch)
3. Publish `proc-macro2-kotlin v0.2.0`
4. Verify serde-kotlin can depend on the new versions

### Track 2: ANTLR4 KMP adaptation (23,577 lines Kotlin, 2,927 lines Java)

The runtime is already Kotlin. KMP adaptation replaces JVM deps:

| Java class | KMP replacement | kotlinmania package |
|---|---|---|
| `ArrayList`, `HashMap`, `HashSet` | Kotlin stdlib | Direct |
| `IdentityHashMap` | `Equivalent`-based `UnorderedMap` | `starlarkmap-kotlin` |
| `BitSet` | `klang` bit primitives | `klang` |
| `java.io.*`, `java.nio.*` | `km-io` | `km-io` v0.1.5 |

The 33 `.java` files in `tree/` (2,927 lines) batch-convert in IntelliJ.

### Track 3: JFlex-in-Kotlin (native Kotlin lexer generator)

Only `Main.java` (408 lines) and `CMapBlock.java` (45 lines) remain
in Java. Port those, KMP-adapt the Unicode data tables, and we have a
self-hosting Kotlin lexer generator — the same tool that JetBrains uses
to produce `KotlinFlexLexer.kt`.

### Track 4: CUP2 LALR(1) generator (already Kotlin)

Fully ported. Needs KMP adaptation (remove JVM `Reflection`, `XMLWriter`)
and a Gradle build. Complements `lalrpop-kotlin` for LR parsing.

---

## ANTLR4 KMP adaptation: complete Java dependency audit

Every `java.*` import in the 141 `.kt` files of the ANTL4 runtime
(`tmp/antlr4/runtime/Java/src/`) has been cataloged. There are 55
distinct `java.*` imports. The 33 `.java` files in `tree/` use only
`java.util.*` plus two `java.io.*` imports in `xpath/XPath.java`.

### Direct stdlib swaps (41 imports, mechanical replacement)

| Java import | Uses | Kotlin replacement |
|---|---|---|
| `java.util.ArrayList` | 20 | `ArrayList` / `mutableListOf()` |
| `java.util.Arrays` | 16 | `kotlin.collections.*` array extensions |
| `java.util.Collections` | 7 | `kotlin.collections.*` |
| `java.util.HashMap` | 8 | `HashMap` / `mutableMapOf()` |
| `java.util.HashSet` | 6 | `HashSet` / `mutableSetOf()` |
| `java.util.LinkedHashMap` | 4 | `LinkedHashMap` / `linkedMapOf()` |
| `java.util.Locale` | 6 | `kotlin.text` lowercase/uppercase |
| `java.util.NoSuchElementException` | 1 | `NoSuchElementException` |
| `java.util.Comparator` | 1 | `Comparator` |
| `java.util.Deque` | 1 | `ArrayDeque` |
| `java.util.ArrayDeque` | 1 | `ArrayDeque` |
| `java.util.LinkedList` | 1 | `ArrayDeque` |
| `java.util.LinkedHashSet` | 1 | `LinkedHashSet` / `linkedSetOf()` |
| `java.util.Objects` | 1 | `kotlin.*` |
| `java.util.EmptyStackException` | 1 | `NoSuchElementException` or custom |
| `java.io.IOException` | 6 | `IOException` |
| `java.io.Serializable` | 2 | `Serializable` or drop |
| `java.lang.annotation.*` | 3 | Kotlin annotations |
| `java.util.concurrent.atomic.AtomicInteger` | 1 | `kotlin.concurrent.atomics` |
| `java.util.concurrent.CopyOnWriteArrayList` | 1 | thread-safe list |
| `java.util.concurrent.CancellationException` | 1 | `CancellationException` |
| `java.util.WeakHashMap` | 1 | `WeakReference`-based map |

### kotlinmania replacements (2 imports, specific packages)

| Java import | Uses | kotlinmania package | Replacement |
|---|---|---|---|
| `java.util.BitSet` | 10 | Already in-tree: `com.intellij.platform.syntax.impl.util.BitSet` + `MutableBitSet` | JetBrains' own `LongArray`-backed `BitSet` is already vendored in `src/commonMain/`. Use directly or extract to shared package. |
| `java.util.IdentityHashMap` | 1 | `starlarkmap-kotlin` | `Equivalent`-based `UnorderedMap` with identity comparison: `Equivalent<PredictionContext> { this === it }` |

### km-io replacements (17 imports, I/O and NIO)

| Java import | Uses | km-io replacement |
|---|---|---|
| `java.io.InputStream` | 3 | `km-io` `RawSource` |
| `java.io.Reader` | 3 | `km-io` `Source` |
| `java.io.InputStreamReader` | 3 | `km-io` decoding source |
| `java.io.BufferedReader` | 1 | `km-io` buffered source |
| `java.io.BufferedWriter` | 1 | `km-io` buffered sink |
| `java.io.File` | 1 | `km-io` or `okio` `Path` |
| `java.io.FileReader` | 1 | `km-io` file source |
| `java.io.FileWriter` | 1 | `km-io` file sink |
| `java.io.FileInputStream` | 1 | `km-io` file source |
| `java.io.FileOutputStream` | 1 | `km-io` file sink |
| `java.io.InvalidClassException` | 1 | Custom `InvalidClassException` |
| `java.io.PrintStream` | 1 | `kotlin.io` / `print()` |
| `java.io.OutputStreamWriter` | 1 | `km-io` writer sink |
| `java.nio.ByteBuffer` | 2 | `km-io` `Buffer` |
| `java.nio.CharBuffer` | 2 | `km-io` `Buffer` |
| `java.nio.IntBuffer` | 1 | `km-io` `Buffer` or custom |
| `java.nio.charset.*` | 7 | Kotlin stdlib charset handling |
| `java.nio.channels.*` | 2 | `km-io` channels |
| `java.nio.file.*` | 3 | `km-io` or `okio` `Path` |

### Remove entirely (2 imports)

| Java import | Uses | Replacement |
|---|---|---|
| `java.security.AccessController` + `PrivilegedAction` | 1 | Replace `AccessController.doPrivileged { System.getenv(name) }` with plain `System.getenv(name)` wrapped in try/catch |
| `java.awt.font.FontRenderContext` | 1 | GUI-only, not needed for KMP runtime |

### Other (2 imports)

| Java import | Uses | Replacement |
|---|---|---|
| `java.lang.reflect.Method` | 1 | `kotlin.reflect.KFunction` or JVM-only `expect/actual` |
| `java.text.SimpleDateFormat` + `java.util.Date` | 2 | `kotlinx-datetime` or custom format |

### Key architectural discovery: JetBrains BitSet is already in-tree

The `com.intellij.platform.syntax.impl.util` package (already vendored
in our `src/commonMain/`) contains a `LongArray`-backed `BitSet` and
`MutableBitSet` — the same design pattern ANTLR4 needs. The ANTLR4
`BitSet` usage is limited to:
- `ATNConfigSet.kt`: `conflictingAlts: BitSet?` and `alts: BitSet`
- `PredictionMode.kt`: several `BitSet`-returning helper methods
- `LL1Analyzer.kt`, `ParserATNSimulator.kt`, `ProfilingATNSimulator.kt`: `BitSet` for looksets

These are all membership-testing bit sets over small integer ranges.
The JetBrains `BitSet(IntList)` constructor (from a list of set bits)
and `contains(Int)` method provide exactly the API needed. The
`MutableBitSet` provides `add(Int)` and `remove(Int)` for mutation.

**No new `BitSet` implementation is needed.** The JetBrains one is
already KMP-compatible and already in our source tree.

### The `tree/` Java files: clean batch-convert targets

All 33 `.java` files in `tree/` use only:
- `java.util.ArrayList`, `java.util.Arrays`, `java.util.Collections`,
  `java.util.List`, `java.util.Map` (standard collection types)
- `java.io.IOException`, `java.io.StringReader` (in `XPath.java` only)
- ANTLR4's own `org.antlr.v4.runtime.*` types (already Kotlin)

These are mechanical conversions — JetBrains' batch converter handles
them with near-zero manual cleanup. The `xpath/XPathLexer.java` is an
ANTLR-generated lexer; once the runtime is KMP, it can be regenerated
in Kotlin using the ANTLR4 tool with the same approach used for the
Kotlin spec grammars.

---

## ANTLR4 `tree/` package: fully converted to Kotlin

The 33 Java files in `runtime/Java/src/org/antlr/v4/runtime/tree/`
have been batch-converted to Kotlin. The ANTLR4 runtime is now
**174 `.kt` files, 0 `.java` files** (26,326 lines total).

The only remaining Java file in the entire ANTLR4 checkout is
`tool/src/org/antlr/v4/unicode/UnicodeDataTemplateController.java`
(435 lines) — a build-time code generator, not runtime code.

### Remaining `java.*` imports in the converted `tree/` files

Only 19 `java.*` imports across the 33 new `.kt` files:

| Java import | Uses | KMP replacement |
|---|---|---|
| `java.util.ArrayList` | 8 | Direct stdlib swap |
| `java.util.Collections` | 3 | Direct stdlib swap |
| `java.util.Arrays` | 2 | Direct stdlib swap |
| `java.util.LinkedHashSet` | 1 | Direct stdlib swap |
| `java.util.Deque` | 1 | `ArrayDeque` |
| `java.util.ArrayDeque` | 1 | Direct |
| `java.util.IdentityHashMap` | 1 | `starlarkmap-kotlin` `Equivalent`-based `UnorderedMap` |
| `java.io.StringReader` | 1 | `km-io` or drop (XPath-specific) |
| `java.io.IOException` | 1 | `IOException` |

These are all straightforward KMP swaps — no bytecode dependencies, no
native method calls, no class loading tricks.

### Updated ANTLR4 status

| Component | Files | Lines | Status |
|---|---|---|---|
| Runtime (all `.kt`) | 174 | 26,326 | Fully Kotlin, needs KMP dep swap |
| Tool (1 `.java` + 231 `.kt`) | 232 | 24,025 + 435 java | 1 build-time Java file, rest Kotlin |
| Test suite | ~80 `.kt` | — | Kotlin, test infrastructure |

---

## KMP adaptation progress

### Completed mechanical swaps (ANTLR4 runtime)

Removed `java.util.*` imports that have direct Kotlin stdlib equivalents:
- `ArrayList` (20 uses) — Kotlin stdlib
- `HashMap` (8 uses) — Kotlin stdlib
- `HashSet` (6 uses) — Kotlin stdlib
- `LinkedHashMap` (4 uses) — Kotlin stdlib
- `LinkedHashSet` (1 use) — Kotlin stdlib
- `ArrayDeque` (1 use) — Kotlin stdlib
- `NoSuchElementException` (1 use) — Kotlin stdlib
- `Comparator` (1 use) — Kotlin stdlib
- `IOException` (7 uses) — Kotlin stdlib
- `Serializable` (2 uses) — Kotlin stdlib
- `Locale` (6 uses) — replaced with `java.util.Locale.US` in format calls
- `java.lang.annotation.*` (3 uses) — Kotlin annotations

Structural changes:
- `Deque<T>` → `ArrayDeque<T>` (ParserInterpreter, IterativeParseTreeWalker)
- `LinkedList<T>` → `ArrayDeque<T>` (FlexibleHashMap)
- `EmptyStackException` → `NoSuchElementException` (Lexer)
- `Objects.equals()` → `kotlin.Objects.equals()` (ATNConfig)
- `AccessController.doPrivileged { System.getenv() }` → `try { System.getenv() } catch (_: SecurityException) { null }` (ParserATNSimulator)

### Remaining java.* imports in ANTLR4 runtime (51 total)

**Needs semantic replacement (not mechanical):**
| Import | Uses | Strategy |
|---|---|---|
| `java.util.Arrays` | 18 | Per-site: `copyOf` → `array.copyOf()`, `equals` → `contentEquals()`, `toString` → `contentToString()`, `asList` → `listOf()`, `fill` → `array.fill()`, `sort` → `array.sort()`, `binarySearch` → `array.binarySearch()` |
| `java.util.Collections` | 10 | Per-site: `emptyList()` → `emptyList()`, `singletonList()` → `listOf()`, `unmodifiableMap/List` → wrapper or `Collections.unmodifiableMap` via `expect/actual`, `reverse` → `reversed()`, `max/min` → `maxOf/minOf` |
| `java.util.BitSet` | 10 | Replace with JetBrains `LongArray`-backed `BitSet`/`MutableBitSet` already in `src/commonMain/` |
| `java.util.IdentityHashMap` | 2 | Replace with `starlarkmap-kotlin` `Equivalent`-based `UnorderedMap` |

**I/O (needs km-io):**
| Import | Uses |
|---|---|
| `java.io.File/FileInputStream/FileOutputStream/FileReader/FileWriter` | 7 |
| `java.io.InputStream/InputStreamReader/Reader` | 9 |
| `java.io.BufferedReader/BufferedWriter/OutputStreamWriter/PrintStream` | 4 |
| `java.io.InvalidClassException/StringReader` | 2 |
| `java.nio.ByteBuffer/CharBuffer/IntBuffer` | 5 |
| `java.nio.charset.*` | 7 |
| `java.nio.channels.*` | 2 |
| `java.nio.file.*` | 3 |

**Low-priority / can defer:**
| Import | Uses | Strategy |
|---|---|---|
| `java.util.concurrent.atomic.AtomicInteger` | 1 | `kotlin.concurrent.atomics` or `expect/actual` |
| `java.util.concurrent.CopyOnWriteArrayList` | 1 | `expect/actual` or custom |
| `java.util.concurrent.CancellationException` | 1 | `kotlinx.coroutines.CancellationException` |
| `java.util.WeakHashMap` | 1 | `WeakReference`-based map or `expect/actual` |
| `java.util.Date` + `java.text.SimpleDateFormat` | 2 | `kotlinx-datetime` or custom |
| `java.lang.reflect.Method` | 1 | `expect/actual` (JVM only) |

### JFlex remaining Java

| File | Lines | Role |
|---|---|---|
| `Main.java` | 408 | Entry point — straightforward conversion |
| `core/unicode/*.java` (7 files) | 2,145 | Unicode character class handling |
| `third_party/edu/tum/cup2/spec/*.java` (2 files) | 1,717 | CUP2 test specs only |

The JFlex core (57 `.kt` files, 12,991 lines) is already Kotlin. The
`cup-interpreter` example is now fully Kotlin (19 `.kt`, 1 `.java` test).

### CUP2 status

The LALR(1)/LR(1) generator core is **fully Kotlin** (100+ `.kt` files).
Only 2 `.java` files remain — both test specification classes in
`spec/`, not part of the generator itself.
