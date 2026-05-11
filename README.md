# proc-macro-kotlin
## Description
proc-macro-kotlin models Rust-shaped tokens for Kotlin ports of Rust macro ecosystems.
Kotlin compiler plugin APIs (FIR/IR/kapt/KSP) are symbol/IR-based, not token-stream based, so there is no plugin-boundary Compiler variant to target.
Kotlin source tokenization (for example via KotlinLexer) is a separate concern for Kotlin-source parsing libraries, not for proc-macro-kotlin.
