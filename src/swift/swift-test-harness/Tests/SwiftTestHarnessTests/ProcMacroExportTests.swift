import Testing
import ProcMacro

// Smoke and parity tests for the Kotlin -> Swift Export -> SPM -> swift test pipeline.
//
// The import below and successful compilation prove three layers of the pipeline:
//
//   1. `embedSwiftExportForXcode` produced `ProcMacro.swiftmodule/`
//      and the supporting KotlinRuntimeSupport / ExportedKotlinPackages /
//      KotlinRuntime swiftmodule bundles. If any of them were missing,
//      `import ProcMacro` above would fail at compile time.
//
//   2. The static archive `libProcMacro.a` (produced by the
//      `linkSwiftExportBinaryDebugStaticMacosArm64` and
//      `mergeMacosDebugSwiftExportLibraries` tasks) supplied every
//      `__root____*` and `KotlinError`-related symbol the Swift modules
//      reference. If the archive were missing or empty, this test
//      executable would fail to link with "undefined symbols for
//      architecture arm64".
//
//   3. The Kotlin `swiftExport { moduleName = "ProcMacro" }` and
//      `flattenPackage = "io.github.kotlinmania.procmacro"` configuration in
//      build.gradle.kts produced a module name that's both syntactically
//      valid as a Swift identifier and reachable from this Package.swift
//      via the `ProcMacroLibrary` product.
//
// The test methods then exercise the exported API through Swift with the
// same values covered by the Kotlin common tests. This keeps the Swift ABI
// honest instead of only proving that the module can be imported.
@Suite struct ProcMacroExportTests {
    @Test func testSwiftModuleLoads() {
        #expect(Bool(true), "ProcMacro swift module imported cleanly")
    }

    @Test func testEnumShapesMatchKotlinSurface() {
        #expect(Delimiter.allCases.count == 4)
        #expect(Delimiter.PARENTHESIS != Delimiter.BRACE)
        #expect(Delimiter(rawValue: 0) == .PARENTHESIS)
        #expect(Delimiter("BRACKET") == .BRACKET)

        #expect(Spacing.allCases.count == 2)
        #expect(Spacing.JOINT != Spacing.ALONE)
        #expect(Spacing(rawValue: 1) == .ALONE)

        #expect(Level.allCases.count == 4)
        #expect(Level("ERROR") == .ERROR)
        #expect(Level(rawValue: 3) == .HELP)
    }

    @Test func testSpanSentinelsMatchKotlinBehavior() {
        let callSite = Span.Companion.shared.callSite()
        let mixedSite = Span.Companion.shared.mixedSite()
        let defSite = Span.Companion.shared.defSite()

        #expect(!callSite.eq(other: mixedSite))
        #expect(!callSite.eq(other: defSite))
        #expect(!mixedSite.eq(other: defSite))
        #expect(callSite.localFile() == nil)
        #expect(callSite.file() == "<token stream>")
        #expect(callSite.line() == 0)
        #expect(callSite.column() == 0)
        #expect(callSite.eq(other: callSite.resolvedAt(other: defSite)))

        let saved = mixedSite.saveSpan()
        #expect(Span.Companion.shared.recoverProcMacroSpan(id: saved).eq(other: mixedSite))
    }

    @Test func testIdentifierAndPunctBehaviorMatchesKotlin() {
        let ident = Ident.Companion.shared.new(string: "hello", span: Span.Companion.shared.callSite())
        #expect(ident.toString() == "hello")

        let raw = Ident.Companion.shared.newRaw(string: "fn", span: Span.Companion.shared.callSite())
        #expect(raw.toString() == "r#fn")

        ident.setSpan(span: Span.Companion.shared.mixedSite())
        #expect(ident.span().eq(other: Span.Companion.shared.mixedSite()))

        let punct = Punct.Companion.shared.new(ch: utf16("+"), spacing: .JOINT)
        #expect(punct.asChar() == utf16("+"))
        #expect(punct.spacing() == .JOINT)
        #expect(punct.eq(rhs: utf16("+")))
        #expect(!punct.eq(rhs: utf16("-")))
        #expect(punct.toString() == "+")
        #expect(punct.span().eq(other: Span.Companion.shared.callSite()))
    }

    @Test func testLiteralRenderingMatchesKotlin() {
        #expect(Literal.Companion.shared.string(string: "hello").toString() == "\"hello\"")
        #expect(Literal.Companion.shared.string(string: #"a\b"#).toString() == #""a\\b""#)
        #expect(Literal.Companion.shared.string(string: #"a"b"#).toString() == #""a\"b""#)
        #expect(Literal.Companion.shared.string(string: "a\nb").toString() == #""a\nb""#)
        #expect(Literal.Companion.shared.string(string: "café").toString() == "\"café\"")
        #expect(Literal.Companion.shared.string(string: "it's").toString() == "\"it's\"")

        #expect(Literal.Companion.shared.character(ch: utf16("a")).toString() == "'a'")
        #expect(Literal.Companion.shared.character(ch: utf16("'")).toString() == "'\\''")
        #expect(Literal.Companion.shared.character(ch: utf16("\"")).toString() == "'\"'")

        #expect(Literal.Companion.shared.u8Suffixed(n: 42).toString() == "42u8")
        #expect(Literal.Companion.shared.i32Unsuffixed(n: -7).toString() == "-7")
        #expect(Literal.Companion.shared.f64Unsuffixed(n: 1.0).toString() == "1.0")
    }

    @Test func testTokenStreamAndGroupBehaviorMatchesKotlin() {
        let empty = TokenStream.Companion.shared.new()
        #expect(empty.isEmpty())
        #expect(empty.toString() == "")

        let identTree = TokenTree.Ident(value: Ident.Companion.shared.new(string: "hello", span: Span.Companion.shared.callSite()))
        let one = TokenStream.Companion.shared.fromTokenTree(tree: identTree)
        #expect(!one.isEmpty())
        #expect(one.toString() == "hello")

        let parsed = TokenStream.Companion.shared.fromString(src: "fun main() { 1 + 2 }")
        #expect(parsed.isSuccess)
        #expect(!parsed.isFailure)
        #expect(parsed.getOrThrow().toString() == "fun main () { 1 + 2 }")

        let parseError = TokenStream.Companion.shared.fromString(src: "(")
        #expect(!parseError.isSuccess)
        #expect(parseError.isFailure)
        #expect(parseError.errorOrNull() != nil)

        let group = Group.Companion.shared.new(delimiter: .BRACE, stream: one)
        #expect(group.delimiter() == .BRACE)
        #expect(group.stream().toString() == "hello")
        #expect(group.toString() == "{ hello }")
        #expect(group.span().eq(other: Span.Companion.shared.callSite()))
    }

    private func utf16(_ scalar: String) -> UInt16 {
        let units = Array(scalar.utf16)
        #expect(units.count == 1)
        return units[0]
    }
}
