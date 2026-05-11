# DESIGN — proc-macro-kotlin

Companion to `README.md`, `AGENTS.md`, `CLAUDE.md`, and `NEXT_ACTIONS.md`.
Tracks the architecture decisions that span multiple files so reviewers
don't have to reconstruct them from commits.

## Two-phase architecture

The repo is implementing a Kotlin Multiplatform library whose public API is
shaped after Rust's compiler-internal
[`proc_macro`](https://doc.rust-lang.org/proc_macro/) crate. The
implementation is delivered in phases:

| Phase | Scope | Source | Status |
|---|---|---|---|
| 1 | Port the Rust API shape | `rust-lang/rust:library/proc_macro/src/` (placed in local `tmp/proc-macro/`) | this PR |
| 2 | Vendor JetBrains' multiplatform Kotlin lexer + parser | `JetBrains/intellij-community:platform/syntax/` and `JetBrains/kotlin:compiler/multiplatform-parsing/` | next |
| 3 | Wire phase-1 types to phase-2 lexer | this repo | after 2 |
| 4 | Re-enable proc-macro2-kotlin's wrapper Compiler variant | `proc-macro2-kotlin` | after 3 |

Phase 1 lands a faithful Kotlin Multiplatform translation of `proc_macro`'s
public surface. Internal storage is the most boring shape that compiles —
Kotlin data classes carrying the actual token content. Where upstream uses
opaque `bridge::client::*` handles (the FFI channel to rustc), this repo
uses simple inline storage that downstream phases will swap for
`KotlinLexer`-backed data without changing the public surface.

## File-level porting decisions

### `lib.rs` does NOT port as `Lib.kt`

Memory rule (workspace `CLAUDE.md`): "Never port lib.rs as Lib.kt. Split
each top-level item in lib.rs into its own Kotlin file." Applied here:
upstream's 1667-line `lib.rs` decomposes into:

| Upstream item | Kotlin file |
|---|---|
| `mod bridge` | does not port (rustc FFI, no Kotlin analog) |
| `mod diagnostic` | `Diagnostic.kt` (deferred — `proc_macro_diagnostic` is unstable) |
| `mod escape` | `Escape.kt` (deferred — internal helpers, ported when `Literal` needs them) |
| `mod to_tokens` | `ToTokens.kt` (deferred — `proc_macro_totokens` is unstable) |
| `mod quote` | `Quote.kt` (deferred — `proc_macro_quote` is unstable + compiler builtin) |
| `mod tracked` | `Tracked.kt` (deferred — `proc_macro_tracked_*` is unstable) |
| `enum ConversionErrorKind` | `ConversionErrorKind.kt` |
| `fn is_available` | `IsAvailable.kt` |
| `struct TokenStream` | `TokenStream.kt` |
| `struct LexError` | `LexError.kt` |
| `struct ExpandError` | `ExpandError.kt` |
| `struct Span` | `Span.kt` |
| `enum TokenTree` | `TokenTree.kt` |
| `struct Group` | `Group.kt` |
| `enum Delimiter` | `Delimiter.kt` |
| `struct Punct` | `Punct.kt` |
| `enum Spacing` | `Spacing.kt` |
| `struct Ident` | `Ident.kt` |
| `struct Literal` | `Literal.kt` |
| `mod token_stream` (containing `IntoIter`) | `tokenstream/IntoIter.kt` |

Phase 1 ports the stable types. Unstable / `#[unstable(...)]`-gated items
(diagnostic, expand_expr, ToTokens, quote, tracked, several Literal value
accessors and span methods) port in a later phase or alongside the
specific stable type that needs them.

### `bridge/` does NOT port

The `bridge` submodule (`bridge::client`, `bridge::server`, `bridge::TokenTree`,
`bridge::Group`, `bridge::Punct`, `bridge::Ident`, `bridge::Literal`,
`bridge::LitKind`, `bridge::DelimSpan`, `bridge::Symbol`, `bridge::arena`,
`bridge::buffer`, `bridge::closure`, `bridge::fxhash`, `bridge::handle`,
`bridge::rpc`, `bridge::selfless_reify`) is the FFI-style channel between
rustc and a proc-macro process. It has no Kotlin Multiplatform analog —
Kotlin doesn't have rustc on the other side of the wire. The Kotlin port
replaces every `bridge::client::X` with a regular Kotlin reference to the
internal data shape.

### Internal storage shape

Each public type wraps an `internal` storage class declared in the same
file. The storage class carries the actual content. Phase 1's storage is
the simplest representation that compiles; phase 3 swaps it for
`KotlinLexer`-backed forms without changing the public surface.

Concretely:

| Public type | Internal storage |
|---|---|
| `TokenStream` | `internal class TokenStreamData(val trees: List<TokenTree>)` |
| `Span` | `internal class SpanData` — sealed: `CallSite` / `MixedSite` / `DefSite` / `Synthetic(byteRange: IntRange)` (phase 3 adds `Lexed(...)`) |
| `Group` | `internal data class GroupData(val delimiter: Delimiter, val stream: TokenStream, val delimSpan: DelimSpanData)` |
| `Ident` | `internal data class IdentData(val sym: String, val isRaw: Boolean, val span: Span)` |
| `Punct` | `internal data class PunctData(val ch: Char, val joint: Boolean, val span: Span)` |
| `Literal` | `internal data class LiteralData(val kind: LitKind, val symbol: String, val suffix: String?, val span: Span)` |
| `LexError` | `internal val message: String` |
| `ExpandError` | unit data |
| `LitKind` (mirrors `bridge::LitKind`) | `internal sealed class LitKind` with variants for `Byte`, `Char`, `Str`, `StrRaw(n)`, `ByteStr`, `ByteStrRaw(n)`, `CStr`, `CStrRaw(n)`, `Integer`, `Float`, `ErrWithGuar` |

`Span`'s sealed hierarchy is the most interesting internal type. Upstream
`Span` is one opaque handle whose `def_site()` / `call_site()` /
`mixed_site()` flavors are distinguished only by hygiene rules baked into
rustc. Without rustc, the three sentinels need explicit storage. Phase 3
adds a `Lexed` variant carrying a `KtTokens`-backed byte range plus token
type.

### Naming and conventions

Workspace defaults from `AGENTS.md`:

- Snake_case Rust function names → camelCase: `call_site` → `callSite`,
  `from_str` → `fromString`, `is_empty` → `isEmpty`, `set_span` → `setSpan`.
- `pub fn` → `fun` (Kotlin's `public` is the default).
- Operators on a public type port as members or static factories: Rust
  `FromStr for TokenStream` → Kotlin `TokenStream.companion.fromString()`.
- Rust `From<X> for Y` impls port as static factory or convenience
  constructor on `Y`, not as Kotlin `operator` overloads.

### Visibility

Upstream marks everything stable/unstable via attributes. Phase 1 ports
only stable items by default; unstable items get a Kotlin marker
annotation (`@PrintIfNeeded` or similar) only if they need to be reachable
from another file in this phase. Most unstable items are deferred to later
phases.

### Side-effect-free API for now

Phase 1 implementations are deterministic and side-effect-free except
where the type itself models effects (e.g. `LexError` carrying its message
string). No global state, no thread locals, no atomics. Phase 3 may add a
process-wide `KtTokens`-backed source map; that decision lives in
`NEXT_ACTIONS.md` phase 3.

### Equality and hashing

Upstream `proc_macro` types are mostly not `PartialEq`. The exceptions:

- `Delimiter`, `Spacing` — `#[derive(Copy, Clone, Debug, PartialEq, Eq)]`
- `ConversionErrorKind` — `#[derive(Debug, PartialEq, Eq)]`
- `Punct: PartialEq<char>` and `char: PartialEq<Punct>` — equality against
  the underlying character

The Kotlin port preserves this restraint. Enums get `equals` / `hashCode`
for free; data classes get them; other types do NOT override `equals` /
`hashCode`. `Punct == Char` operator on a Kotlin class becomes a member
function `punct.eq(ch: Char)` to avoid Kotlin's operator-overload
restrictions.

### `Display`, `Debug`, `toString`

Upstream `Display::fmt` becomes Kotlin `toString()`. Upstream
`Debug::fmt` doesn't have a clean Kotlin analog — we port it as
`debugString(): String` for now, leaving `toString()` for the Display
shape. Phase 3 may revisit if downstream consumers need both at once.

## Phase 2 preview — vendoring

Phase 2 adds `src/commonMain/kotlin/com/intellij/platform/syntax/...` and
`src/commonMain/kotlin/org/jetbrains/kotlin/kmp/...` directories carrying
vendored upstream Kotlin source. Each vendored file gets a provenance
header per `AGENTS.md` "Phase 2 — vendoring JetBrains Kotlin lexer +
parser". Phase 1 files don't reference those packages yet — phase 3
introduces the references when wiring `TokenStream.fromString` to
`KotlinLexer`.

## Open questions for phase 3

Captured in `NEXT_ACTIONS.md`, summary:

1. **Kotlin string templates** — Rust `proc_macro` has no analog of
   `OPEN_QUOTE` / `LONG_TEMPLATE_ENTRY_START`. Decide: synthetic `Group`
   with a custom `Delimiter`, or sequence of `Punct` + `Literal`.
2. **Multi-character Kotlin operators** — `?.`, `!!`, `?:`, `..`, `::`
   decompose into `Punct(Joint)` chains the way Rust `proc_macro2`
   handles `+=` and `&&`.
3. **Comments and whitespace filtering** — upstream `proc_macro` doesn't
   surface them. Mirror that at the `KotlinLexer` adapter boundary.
4. **`Span::resolved_at` / `Span::located_at` hygiene** — Rust hygiene
   resolution has no `KotlinLexer` analog. Stub to identity, or model
   resolution context independently.

## Out of scope (for now)

- `quote!` macro implementation (compiler-builtin in Rust; Kotlin would
  need to be a builder API or a Kotlin compiler plugin)
- `Diagnostic` API (unstable, deferred until downstream consumers ask)
- `ToTokens` trait (unstable, deferred — sibling `quote-kotlin` repo
  already has a `ToTokens` interface; reconciliation is a phase-3+ task)
- `tracked` module (unstable, requires bridge-style env-var tracking)
- `is_available()` semantics beyond "always true in Kotlin" — phase 3 may
  refine to "true if KotlinLexer is loaded"
