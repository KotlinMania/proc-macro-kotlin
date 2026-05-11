# Agent guide - proc-macro-kotlin

This file is the quick-reference operating contract for proc-macro-kotlin. The longer
project story lives in `CLAUDE.md`, `README.md`, and `NEXT_ACTIONS.md`. Read
those before editing. This guide captures the workspace-wide porting discipline
that must not drift: Kotlin stays Kotlin, source comments stay Kotlin-facing,
and required port inventory is done with `ast_distance` when the repo ships it.

## What this repo is

proc-macro-kotlin is a Kotlin Multiplatform port of Rust's compiler-internal
[`proc_macro`](https://doc.rust-lang.org/proc_macro/) crate — the in-tree crate
that `rustc` makes available to procedural macros and that
[`proc_macro2`](https://crates.io/crates/proc-macro2) dispatches to via its
`Compiler` variant. The public Kotlin API mirrors `proc_macro`'s shape so
that proc-macro2-kotlin (and through it the Kotlin ports of `syn`, `quote`,
`serde_derive`, `async-trait`, `starlark_derive`, `logos-codegen`, etc.) can
consume this library as the realized Compiler variant.

The work happens in two phases, in order:

1. **Port the Rust code so the shape is like the Rust code.** Standard
   kotlinmania Rust→Kotlin parity port of
   [`rust-lang/rust:library/proc_macro/src/`](https://github.com/rust-lang/rust/tree/master/library/proc_macro/src),
   following every workspace porting rule below — translator's mindset,
   one Rust file to one Kotlin file, port-lint headers, comments-are-content,
   and so on. The team places the Rust source manually under `tmp/proc-macro/`
   (gitignored) for reference. The `bridge` submodule does not port; it
   is the rustc-side FFI channel and has no Kotlin analog.
2. **Add the Kotlin pieces to make a real tokenizer.** Vendor the
   JetBrains multiplatform Kotlin lexer + parser from
   [`JetBrains/intellij-community:platform/syntax/`](https://github.com/JetBrains/intellij-community/tree/master/platform/syntax)
   and
   [`JetBrains/kotlin:compiler/multiplatform-parsing/`](https://github.com/JetBrains/kotlin/tree/master/compiler/multiplatform-parsing)
   into this repo's tracked tree, apply mechanical adjustments (drop
   `@JvmStatic`, repackage if needed, note upstream sha), and wire the
   `proc_macro`-shaped types from phase 1 to delegate into the vendored
   lexer for actual tokenization. Both upstream Kotlin sources are
   Apache 2.0, matching this repo.

Phase 1 is faithful Rust→Kotlin transliteration. Phase 2 is faithful Kotlin
vendoring with documented mechanical adjustments. The translator-discipline
rules below apply to phase 1; the vendor-discipline rules later in this file
apply to phase 2.

No JVM-only dependencies, no `java.*` / `javax.*`, no shortcuts through
established JVM libraries, and no replacing a Cargo dependency with an
unrelated Kotlin library when a `*-kotlin` sibling port exists or should
exist. The intentional exception is the JetBrains multiplatform syntax
library we vendor in phase 2.

## Project phase

Check the repo before choosing a workflow.

- **If `tools/ast_distance/` exists:** the repo is still in parity/porting
  mode. Drift measurement is required, not optional. Use the repo's
  `tools/ast_distance` binary/script to identify missing files, missing
  functions, provenance/header drift, and cheat-detection failures before
  choosing work and again at file or phase boundaries. Do not chase similarity
  scores in the middle of translating a half-read file, and never Rustify
  Kotlin to appease the tool.
- **If `tools/ast_distance/` does not exist:** the repo has matured past the
  structural-port phase and is optimizing for idiomatic Kotlin. Work like a
  Kotlin maintainer: preserve behavior and public API intent, improve Kotlin
  shape when appropriate, and use the repo's tests/docs as the gate. Do not
  reintroduce Rust-shaped code or comments.

## Required workflow in parity mode

1. Read `CLAUDE.md`, `README.md`, this file, and any repo-local status files.
2. Confirm the upstream Rust source is present under the `tmp/` path named by
   `CLAUDE.md` or `.ast_distance_config.json`. Fetch it using the repo's helper
   if needed. Never edit it.
3. If `tools/ast_distance/` exists, run the repo's `ast_distance --deep`
   workflow before picking work. Use it as the required inventory for unported
   files/functions and provenance drift.
4. Pick bottom-up work: dependencies before consumers, leaves before roots.
5. Read the whole upstream `.rs` file before typing. If the file is too large,
   split the turn into "read" and "write"; never start from a half-read file.
6. Keep the mapping one Rust file -> one Kotlin file unless the upstream file is
   pure `mod.rs` re-export glue covered by the `mod.rs` rules below.
7. Translate top-to-bottom in upstream order. Preserve declaration order.
8. Translate comments and docs as content. See "Source comments and KDoc."
9. Leave hard files visible; do not fill holes with stubs.
10. After a file lands, run the relevant compile/test gate and, when available,
    `ast_distance` again.

## Required workflow in mature Kotlin mode

1. Read the repo-local docs and tests first.
2. Make idiomatic Kotlin changes that preserve behavior and public API intent.
3. Remove stale Rust-shaped scaffolding when it is no longer part of the repo's
   Kotlin design.
4. Keep comments Kotlin-facing. Historical Rust notes belong in docs, not source
   comments, unless the repo explicitly keeps a provenance ledger.
5. Run the repo's normal Gradle/test gates.

## Source comments and KDoc

Comments are content. They are part of the port, not decoration.

- Preserve upstream module docs, KDoc-equivalent sections, inline notes, safety
  notes, panic/error docs, and upstream TODO/FIXME items by translating them.
- **No Rust in comments:** KDoc and `//` comments must describe the Kotlin API
  in Kotlin terms. Translate Rust syntax inside comments to Kotlin equivalents:
  `Vec<T>` -> `List<T>`, `Option<&str>` -> `String?`, `Self::foo()` -> `foo()`,
  `snake_case` function names -> `lowerCamelCase`, Rust lifetimes disappear,
  `cfg(test)` / `#[derive(...)]` become prose when relevant.
- **Do not Rustify Kotlin:** this is a translation direction, not a renaming
  scheme. Never rename Kotlin files, packages, functions, locals, parameters,
  or identifiers to `snake_case` to match upstream. Kotlin source stays Kotlin.
- **No porting narratives in source:** do not add comments explaining Kotlin
  workarounds, "Rust vs Kotlin" rationale, ast_distance strategy, or translation
  decisions. Put those in `CLAUDE.md`, `NEXT_ACTIONS.md`, commit messages, or
  review notes.
- Source comments should be upstream comments translated into Kotlin-facing API
  names/signatures, plus required provenance/license headers and required
  migration ledgers such as the `mod.rs` ledger below.
- If `ast_distance` zeros a file because Rust syntax leaked into Kotlin source
  code or comments, treat that as a literal instruction to make the Kotlin
  source Kotlin-native.

## Provenance headers

In parity mode, every Kotlin file translated from a Rust source file starts with
the repo's `port-lint` source header before the package line:

```kotlin
// port-lint: source <path-relative-to-upstream-root>
package <repo package>
```

Use the path convention from `CLAUDE.md` or `.ast_distance_config.json`. Do not
invent absolute upstream paths. If a repo requires an attribution line after the
`port-lint` header, preserve it exactly.

## Naming

The translation direction is always Rust -> Kotlin.

| Thing | Kotlin form |
|---|---|
| Files and types | `PascalCase` |
| Functions, properties, parameters, locals | `lowerCamelCase` |
| Interfaces | `PascalCase`, no `I` prefix |
| `const val`, enum entries, true constants | `SCREAMING_SNAKE_CASE` allowed |
| Type parameters | `T`, `K`, `V`, or meaningful `PascalCase` when clearer |
| Packages | lowercase, no underscores, no camelCase |

Examples:

| Rust source | Kotlin port |
|---|---|
| `fn first_key_value` | `fun firstKeyValue` |
| `let len_underflow` | `val lenUnderflow` |
| `const FOO_BAR: usize = 5` | `const val FOO_BAR: Int = 5` |
| `src/foo_bar.rs` | `FooBar.kt` |

## Rust -> Kotlin mapping defaults

Repo-local rules in `CLAUDE.md` may narrow these, but do not invent new shapes
without documenting the rule in project docs.

| Rust | Kotlin |
|---|---|
| `Option<T>` | `T?` |
| `Result<T, E>` | `Result<T>` when `E` is not modeled; sealed result or exception when error data/behavior matters |
| `Vec<T>` | `MutableList<T>` / `List<T>` |
| `HashMap<K, V>` | `MutableMap<K, V>` / `Map<K, V>` |
| `HashSet<T>` | `MutableSet<T>` / `Set<T>` |
| `BTreeMap<K, V>` | `BTreeMap`/sorted map from `btree-kotlin`; do not use JVM-only `TreeMap` in common code |
| `&T`, `&mut T` | regular Kotlin reference; mutate through the owning object |
| `*const T`, `*mut T`, `NonNull<T>` | regular Kotlin reference unless native interop is explicitly required |
| `Box<T>` | bare `T` |
| `Rc<T>`, `Arc<T>` | bare `T` reference unless shared concurrency semantics must be modeled |
| `Cell<T>`, `RefCell<T>` | `var`; use multiplatform atomics only for real shared mutation |
| `PhantomData<T>` | drop field; encode variance with `in` / `out` when needed |
| `MaybeUninit<T>` | nullable slot/array plus local invariant, or a typed initialization helper |
| `ManuallyDrop<T>` | omit unless drop side effects are observable |
| `mem::replace`, `take_mut` | read old value, compute new value, assign back; return side result explicitly |
| `ptr::read`, `ptr::write` | direct field/slot access |
| `ptr::drop_in_place` | omit unless observable drop behavior is being modeled |
| `mem::transmute` | verified cast or explicit conversion; no shim that hides the invariant |
| `dyn Trait` | interface reference |
| trait | `interface` |
| trait default method with `where` | extension function carrying its own bound |
| class/impl generic bound such as `K: Ord` | `Comparable` bound or `Comparator<in K>` field/dispatch helper |
| struct with fields | `data class` when value semantics fit; otherwise `class` |
| enum with payload variants | `sealed class` / `sealed interface` |
| `pub fn foo()` | `fun foo()`; public is default |
| `pub(crate)` / `pub(super)` | `internal` |
| private `fn foo()` | `private fun foo()` |
| `let` / `let mut` | `val` / `var` |
| `match` | `when` |
| `if let Some(v) = x` | nullable handling such as `x?.let { v -> ... }` |
| `?` operator | explicit early return, `Result` transform, or throw according to the mapped error shape |
| `unsafe { ... }` | regular Kotlin code; keep only upstream comments translated to Kotlin-facing terms |
| `proc-macro` derive | explicit Kotlin codegen/runtime API; never silently elide behavior |
| `pub type X = Y` | `typealias X = Y` only when upstream really defines a type alias and it is not a re-export bridge |
| `impl Iterator for X` | class implementing `Iterator<T>` or `MutableIterator<T>` as appropriate |

## Required `mod.rs` / re-export workflow

Pure upstream `mod.rs` re-export glue must not become a Kotlin central-alias API.
This rule is required.

When an upstream Rust `mod.rs` only re-exports something that actually lives
elsewhere, such as `pub use <crate-path>::<Name>;`, often under a different
name:

1. Identify the original symbol's fully qualified upstream path and the exported
   name.
2. Search dependent Kotlin callers across the kotlinmania monorepo. A caller is
   a Kotlin file in another `*-kotlin` repo with a `tmp/` source tree and a
   Cargo.toml depending on this crate's Rust counterpart. Search for direct
   imports, wildcard imports plus body usage, and fully qualified references.
3. Rewrite callers to reference the original/defining Kotlin symbol directly.
   If the call site must keep the old spelling, use Kotlin import aliasing:
   `import <defining.package.Symbol> as <Name>`.
4. Never bridge a pure re-export with a Kotlin `typealias` at a root or
   re-export package.
5. Keep `Mod.kt` or the equivalent package file as a tracking ledger when the
   upstream file carries module docs or re-export history. It should contain the
   translated upstream module-level comments and literal quoted `pub use` lines,
   for example `// pub use crate::lib::result::Result;`.
6. Each time a caller is migrated off the re-export, append that caller's
   absolute path under a `// Callers migrated:` ledger. Append; do not delete
   migration history.
7. Once all callers are migrated, remove any temporary bridge alias. The ledger
   file remains as the record of the migration.

If a `mod.rs` contains real implementation rather than pure re-export glue,
translate the implementation into the appropriate Kotlin file/package shape
named by repo docs.

## Trait defaults with `where` clauses

Rust traits often put stricter bounds on a default method than on the trait:

```rust
pub trait RangeBounds<T> {
    fn start_bound(&self) -> Bound<&T>;
    fn end_bound(&self) -> Bound<&T>;

    fn is_empty(&self) -> bool
    where
        T: PartialOrd,
    { /* default body */ }
}
```

Do not tighten the whole Kotlin interface to `T : Comparable<T>`. Do not make
the method abstract just to satisfy Kotlin. Do not use runtime comparable casts
that turn Rust compile-time bounds into Kotlin runtime crashes.

Translate the default to an extension function whose own type parameter carries
the bound:

```kotlin
interface RangeBounds<T> {
    fun startBound(): Bound<T>
    fun endBound(): Bound<T>
}

fun <T : Comparable<T>> RangeBounds<T>.isEmpty(): Boolean {
    // translated default body
}
```

Concrete implementations that specialize the default provide a same-named
member function without `override`; Kotlin member resolution mirrors Rust's
per-impl specialization of a default method.

When both comparator-aware and natural-order paths are needed, put the heavy
logic in an unbounded overload that takes a comparator explicitly, then add a
bounded one-line natural-order overload:

```kotlin
internal fun <K, Q> search(key: Q, compare: (K, Q) -> Int): Hit {
    // heavy lifting
}

internal fun <K, Q : Comparable<Q>> search(key: Q): Hit where K : Comparable<Q> =
    search(key) { stored, query -> stored.compareTo(query) }
```

## Other recurring porting patterns

- `Ordering::{Less, Equal, Greater}` maps to Kotlin comparator `Int` convention:
  negative, zero, positive. Do not introduce an `Ordering` enum unless repo docs
  require it.
- Rust `Iterator::next() -> Option<T>` maps to Kotlin `hasNext()` plus `next()`.
  Cache the next value once; do not advance twice.
- `ExactSizeIterator` has no Kotlin equivalent. Pass the known size explicitly
  when an algorithm needs it.
- `FusedIterator` is implicit: after `hasNext()` becomes false, keep it false.
- Rust `Peekable<I>` maps to a small private one-element lookahead adapter.
- Sum enums used only to tag iterator side/source can map to nullable slots plus
  an explicit discriminator enum; the discriminator is the source of truth.
- Same-name Rust impl methods that differ only by typestate marker often erase
  to the same Kotlin/JVM signature. Use typed routers and distinct Kotlin names
  for erased collisions; do not use `@JvmName`, JVM imports, unchecked casts, or
  fake typealiases to force the Rust layout.
- Most Rust `Drop` impls disappear under Kotlin GC. If upstream tests observe
  drop/clone side effects, model them deterministically with narrow internal
  hooks and prove the behavior with ported tests. Do not rely on GC timing.
- Rust iterator `Clone` often has no Kotlin equivalent. Omit cloneability unless
  behavior requires it; represent `Debug` as `toString()` when useful.
- Rust trait specialization (`default fn`) has no direct Kotlin equivalent.
  Prefer explicit dispatch helpers or documented runtime type checks at the
  narrow call site.
- Compile-time-incomplete files are acceptable only in early parity phases when
  they contain no fake stubs and the missing dependencies are tracked. Missing
  symbols are better than placeholder classes that conflict with the real port.

## Dependencies

Approved common dependencies, when the repo already uses or needs them:

- `kotlinx-coroutines-core`
- `kotlinx-serialization-core`
- `kotlinx-serialization-json`
- `kotlinx-collections-immutable`
- `kotlinx-datetime`
- `kotlinx-io`
- `com.ionspin.kotlin:bignum` only when numeric behavior requires it
- `io.github.kotlinmania:*-kotlin` sibling ports for Rust transitive deps

Add a dependency only when stdlib plus approved siblings cannot reproduce the
behavior, and only after confirming it publishes artifacts for every target this
repo ships. If the Rust crate has no KMP equivalent, port that crate instead of
leaving a TODO or using a JVM-only shortcut.

## Forbidden

- Rust syntax leaking into Kotlin code or comments.
- Rustifying Kotlin names, files, packages, or API shape to improve similarity.
- `@Suppress(...)` unless a repo-local doc already records a narrow, reviewed
  invariant that Kotlin cannot encode. New suppressions require discussion.
- `TODO()`, `error("not implemented")`, empty shells, fake implementations, or
  placeholder bodies.
- Re-export `typealias` bridges for upstream `mod.rs` glue.
- `import kotlin.jvm.*`, `java.*`, or `javax.*` from shared/common source.
- JVM-only annotations such as `@JvmName`, `@JvmStatic`, `@JvmField`, or
  `@JvmOverloads` in common code.
- Repo-wide source rewrites with global `sed`/`perl`/`find -exec`. Source edits
  are task-scoped and reviewed.
- Bulk-editing source comments. Comment changes are intentional translation
  work and must be reviewed as such.
- Subagent-driven `.kt` edits. Translation happens in the main loop so mistakes
  are visible immediately.

## Tests and gates

Use the repo's documented Gradle tasks. Common gates include:

```bash
./gradlew test
./gradlew macosArm64Test
./gradlew linuxX64Test
./gradlew jsNodeTest
./gradlew wasmJsNodeTest
```

In parity repos with `tools/ast_distance/`, also run the repo's deep scan, for
example:

```bash
./tools/ast_distance/ast_distance --deep <upstream-root> rust <kotlin-source-root> kotlin
```

The exact paths come from `.ast_distance_config.json`, `CLAUDE.md`, or existing
repo scripts. Use this scan as a progress dashboard for missing files/functions,
header drift, and cheat detection. A file is not done merely because a
similarity score looks good; it is done when the behavior is ported and the
relevant tests pass.

Port tests too. Rust `#[test]` becomes Kotlin `@Test`. Test utilities needed by
ported upstream tests belong in `src/commonTest`, not `commonMain`, unless the
upstream behavior is truly public runtime behavior.

## Scope and commits

- More than about five source files in one change is usually too much; stop and
  ask unless the user explicitly requested a mechanical sweep.
- Commit at file or coherent phase boundaries.
- Commit messages are clear and human: no AI branding, no "Generated with"
  footers, no robot attribution, no `Co-Authored-By` lines unless the human asks.

## Phase 2 — vendoring JetBrains Kotlin lexer + parser

This phase is not Rust→Kotlin translation; it is Kotlin→Kotlin vendoring.
Different discipline applies.

Upstream sources to vendor from:

- [`JetBrains/intellij-community:platform/syntax/`](https://github.com/JetBrains/intellij-community/tree/master/platform/syntax)
  — the IntelliJ Platform multiplatform syntax engine. Apache 2.0. Already
  Kotlin Multiplatform with `src/` (commonMain) plus `srcJvm/`, `srcJs/`,
  `srcWasmJs/`, `srcNativeMain/` per-target source sets. The submodules
  used downstream are at least `syntax-api` and `syntax-util`; pull
  others (`syntax-extensions`, `syntax-tree`) as transitive needs surface.
- [`JetBrains/kotlin:compiler/multiplatform-parsing/`](https://github.com/JetBrains/kotlin/tree/master/compiler/multiplatform-parsing)
  — the Kotlin language's lexer + parser, built on top of the above.
  Apache 2.0. Includes the JFlex-generated `KotlinFlexLexer.kt` (~1700
  lines) and `KDocFlexLexer.kt`, plus `KotlinLexer`, `KtTokens`,
  `KotlinParser`, `AbstractParser`, `KotlinParsing`, `KtNodeTypes`, and
  the `parser/utils/` helpers.

Vendoring recipe:

1. **Identify the upstream file, repo, path, and commit sha.** Record all
   four in the commit message.
2. **Copy the file in verbatim.** Preserve the upstream's package path
   (`com.intellij.platform.syntax.*` stays as-is; `org.jetbrains.kotlin.kmp.*`
   stays as-is) so future upstream diffs apply cleanly. The Kotlin sources
   live under `src/commonMain/kotlin/com/intellij/platform/syntax/...` and
   `src/commonMain/kotlin/org/jetbrains/kotlin/kmp/...` for that reason.
3. **Apply mechanical adjustments only:**
   - Drop `@JvmStatic` annotations and `import kotlin.jvm.JvmStatic`
     (forbidden in common KMP code per the Forbidden list above).
   - Drop `import java.*` / `import javax.*` (forbidden). If the file
     depends on something JVM-only, move it to a platform-specific
     source set rather than rewriting it in commonMain.
   - Replace transitive deps that don't ship for our KMP target set
     with KMP-friendly equivalents only when strictly necessary.
4. **Add a file-level provenance comment at the top, before the package
   declaration:**
   ```kotlin
   // Vendored from <repo> @ <sha>
   //   <path-relative-to-repo>
   // Apache 2.0
   ```
   This is the phase-2 analog of the `port-lint: source` header used in
   phase 1. `ast_distance` doesn't gate phase-2 files; the provenance
   comment is for human review and future-upstream-diff tooling.
5. **Do not "improve" the upstream code while vendoring.** Bring it in
   modulo the mechanical adjustments. Optimizations and refactors come
   later, as named follow-on commits.
6. **Vendoring brings in transitive deps too.** If a vendored file
   imports `com.intellij.platform.syntax.util.lexer.FlexAdapter`, vendor
   `FlexAdapter` in the same pass. No placeholders.
7. **Don't mix phase-1 and phase-2 commits.** A commit is either a
   Rust→Kotlin port (`port-lint: source` header, kotlinmania porting
   discipline) or a Kotlin vendor (`Vendored from` header, vendor
   discipline). One commit shouldn't carry both.

## When unsure

Read upstream again. Read the repo-local `CLAUDE.md` again. If a construct is
not covered here, add the rule to project docs with the translation you chose.
The goal is not to make Kotlin look like Rust; the goal is to preserve behavior
while moving steadily toward Kotlin that Kotlin developers can maintain.
