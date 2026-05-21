# Vendored upstream sources

This repo vendors a subset of Kotlin source from the public
[`JetBrains/intellij-community`](https://github.com/JetBrains/intellij-community)
repository at commit
[`8d942fa8acf2f583ba08ebb11c0ce53eb8dca088`](https://github.com/JetBrains/intellij-community/tree/8d942fa8acf2f583ba08ebb11c0ce53eb8dca088).

The upstream is published under the Apache License 2.0; modification is
permitted by that license. Per-file `// Copyright 2000-2026 JetBrains s.r.o.
and contributors. Use of this source code is governed by the Apache 2.0
license.` headers are preserved unchanged.

## Vendored modules

| Upstream path | Local path | Purpose |
|---|---|---|
| `platform/syntax/syntax-api/src/com/intellij/platform/syntax/` | `src/commonMain/kotlin/com/intellij/platform/syntax/` | `SyntaxElementType`, `Lexer`, `SyntaxTreeBuilder`, plus the impl/builder, lexer, parser, tree subdirectories |
| `platform/syntax/syntax-util/src/com/intellij/platform/syntax/util/` | `src/commonMain/kotlin/com/intellij/platform/syntax/util/` | `FlexAdapter`, `FlexLexer`, `MergingLexerAdapter`, `SyntaxTreeBuilderAdapter`, and related lexer/parser helpers |
| `platform/syntax/syntax-extensions/src/com/intellij/platform/syntax/extensions/` | `src/commonMain/kotlin/com/intellij/platform/syntax/extensions/` | `ExtensionPointKey`, `ExtensionSupport`, in-process extension registry |
| `platform/util/multiplatform/src/com/intellij/util/` | `src/commonMain/kotlin/com/intellij/util/` | `BitUtil`, `ThreadLocalKmp`, `JavaVersionShim`, `ThreeState`, plus the `fastutil/` and `containers/` packages |
| `platform/util/multiplatform/src/com/intellij/openapi/util/` | `src/commonMain/kotlin/com/intellij/openapi/util/` | (filtered) — i18n annotation declarations (`Nls`, `NlsContext`) excluded |
| `platform/util/base/multiplatform/src/com/intellij/util/text/` | `src/commonMain/kotlin/com/intellij/util/text/` | `CharArrayUtilKmp`, `CharSequenceSubSequence`, `CharArrayCharSequence`, `Matcher`, related char-sequence helpers |
| `platform/util/base/multiplatform/src/com/intellij/openapi/util/text/` | `src/commonMain/kotlin/com/intellij/openapi/util/text/` | `CharSequenceWithStringHash`, `LineTokenizer`, `StringUtilKmp`, `StringsKmp` |
| `fleet/util/multiplatform/srcCommonMain/fleet/util/multiplatform/` | `src/commonMain/kotlin/fleet/util/multiplatform/` | `linkToActual()` + `@Actual` annotation (compile-time multiplatform shim) |

## Modifications applied during vendoring

Upstream files were copied with their original `// Copyright …` license
headers intact. The following changes were applied to satisfy this repo's
Kotlin Multiplatform target matrix and the per-repo CLAUDE.md rules:

1. **Stripped `org.jetbrains.annotations.*` imports and their annotation
   usages** (`@Nls`, `@NonNls`, `@Contract`, `@ApiStatus.*`, `@TestOnly`,
   `@PropertyKey`, `@MagicConstant`, `@Range`). The artifact
   `org.jetbrains:annotations` is JVM-only and would not resolve for
   Apple / Linux / Wasm-WASI / Android Native targets. These annotations
   are informational; removing them does not affect behavior.
2. **Removed the `@Deprecated("This API is temporary multiplatform shim …",
   level = DeprecationLevel.WARNING)` annotations** from upstream's
   `fastutil/ints/*` types. Those notes are upstream's reminder to
   themselves to swap the shim once Kotlin stdlib gains primitive-int
   collections; consumers (including the rest of the vendored tree) need
   them today, so the warnings would otherwise apply to every call site.
3. **Excluded i18n annotation declarations** `NlsContext.kt` and
   `NlsContexts.kt` (would have required `org.jetbrains:annotations` to
   compile and contributed nothing the lexer needs).
4. **Excluded the `syntax-util` runtime/, cancellation/, language/, and
   log/ subpackages**. These bring in Grammar-Kit runtime helpers and the
   logger / cancellation extension subsystems that the lexer doesn't use.

No upstream behavior was modified. No method signatures were changed; no
implementation logic was rewritten. The only edits are removals of
JVM-only annotation references and the temporary-shim self-deprecation
markers.

The repo-level `allWarningsAsErrors` flag is set to `false` while the
vendored set lives in `commonMain`. Upstream's parameter-name mismatches
on overrides and its deliberate unchecked casts are intentional in their
codebase. A follow-up may move the vendored set into its own Gradle
sub-source-set so the rest of the project can re-enable `-Werror`
without forcing refactors of vendored bodies. See the inline comment on
the `compilerOptions` block in `build.gradle.kts`.

## Why these sources, not the published artifact

The Kotlin compiler's own `compiler/multiplatform-parsing` module imports
the same `com.intellij.platform.syntax.*` types from
`org.jetbrains:syntax-api:0.3.340`, which is published only at
JetBrains' own Maven repos (not Maven Central). Inspecting its Gradle
metadata (`syntax-api-0.3.340.module`) shows publications for **JVM and
Wasm-JS only** — 2 of this repo's 21 configured KMP targets. The
artifact path therefore can't satisfy the full target matrix; vendoring
the source from `JetBrains/intellij-community` is the path that lets
every target link.

## Refreshing the vendor

When upstream advances, re-sparse-clone at the new sha:

```bash
mkdir -p tmp/intellij-syntax-checkout && cd tmp/intellij-syntax-checkout
git init -q && git remote add origin https://github.com/JetBrains/intellij-community.git
git config core.sparseCheckout true
cat > .git/info/sparse-checkout <<'EOF'
platform/syntax/
platform/util/multiplatform/
platform/util/base/multiplatform/
fleet/util/multiplatform/
EOF
git fetch --depth=1 origin master
git checkout master
```

Then re-apply the diff: drop `org.jetbrains.annotations.*` imports and
annotation usages, drop the temporary-shim `@Deprecated` block on the
`fastutil/ints/*` types, drop the listed excluded files. Re-run
`./gradlew build` to verify all targets still link.
