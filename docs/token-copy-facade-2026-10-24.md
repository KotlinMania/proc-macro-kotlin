# Lossless token-copy facade

`bridge.cloneTokenTree` exposes the existing private `TokenTree.deepClone`
implementation already used by `BridgeMethods.tsClone`. The implementation is
unchanged: it retains identifier spelling/rawness, punctuation spacing, literal
kind/symbol/suffix, source spans, separate group delimiter spans, and recursively
copied nested tokens. No token is reparsed or converted to another backend.

This small public entry point is an intentional Kotlin integration adaptation:
proc-macro2-kotlin can now use the existing bridge copy implementation across the
module boundary. It does not change the lexer or open classes for inheritance.
The source behavior being restored is Rust token Clone isolation; mutation of a
returned token must not change its source buffer.

Two new common tests cover leaf data and nested/None-delimited groups with
different opening, closing and entire spans. Local `jvmTest macosArm64Test jvmJar`
passed: 248 JVM tests and 247 macOS tests, with no failures. Other targets are not
validated by these runs. Evidence is retained in
`rust-kotlin/tmp/token-copy-repair-tkw9djkt/proc-macro-facade-tests.log` and the
repair validation receipt.

All 81 pre-existing dirty/untracked files were inventoried before the change and
are excluded from the repair commit. No configuration, dependency pin, workflow,
publication or remote operation is part of this repair.
