import XCTest
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
final class ProcMacroExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "ProcMacro swift module imported cleanly")
    }

    func testEnumShapesMatchKotlinSurface() throws {
        XCTAssertEqual(Delimiter.allCases.count, 4)
        XCTAssertNotEqual(Delimiter.PARENTHESIS, Delimiter.BRACE)
        XCTAssertEqual(Delimiter(rawValue: 0), .PARENTHESIS)
        XCTAssertEqual(Delimiter("BRACKET"), .BRACKET)

        XCTAssertEqual(Spacing.allCases.count, 2)
        XCTAssertNotEqual(Spacing.JOINT, Spacing.ALONE)
        XCTAssertEqual(Spacing(rawValue: 1), .ALONE)

        XCTAssertEqual(Level.allCases.count, 4)
        XCTAssertEqual(Level("ERROR"), .ERROR)
        XCTAssertEqual(Level(rawValue: 3), .HELP)
    }

    func testSpanSentinelsMatchKotlinBehavior() throws {
        let callSite = Span.Companion.shared.callSite()
        let mixedSite = Span.Companion.shared.mixedSite()
        let defSite = Span.Companion.shared.defSite()

        XCTAssertFalse(callSite.eq(other: mixedSite))
        XCTAssertFalse(callSite.eq(other: defSite))
        XCTAssertFalse(mixedSite.eq(other: defSite))
        XCTAssertNil(callSite.localFile())
        XCTAssertEqual(callSite.file(), "<token stream>")
        XCTAssertEqual(callSite.line(), 0)
        XCTAssertEqual(callSite.column(), 0)
        XCTAssertTrue(callSite.eq(other: callSite.resolvedAt(other: defSite)))

        let saved = mixedSite.saveSpan()
        XCTAssertTrue(Span.Companion.shared.recoverProcMacroSpan(id: saved).eq(other: mixedSite))
    }

    func testIdentifierAndPunctBehaviorMatchesKotlin() throws {
        let ident = Ident.Companion.shared.new(string: "hello", span: Span.Companion.shared.callSite())
        XCTAssertEqual(ident.toString(), "hello")

        let raw = Ident.Companion.shared.newRaw(string: "fn", span: Span.Companion.shared.callSite())
        XCTAssertEqual(raw.toString(), "r#fn")

        ident.setSpan(span: Span.Companion.shared.mixedSite())
        XCTAssertTrue(ident.span().eq(other: Span.Companion.shared.mixedSite()))

        let punct = Punct.Companion.shared.new(ch: utf16("+"), spacing: .JOINT)
        XCTAssertEqual(punct.asChar(), utf16("+"))
        XCTAssertEqual(punct.spacing(), .JOINT)
        XCTAssertTrue(punct.eq(rhs: utf16("+")))
        XCTAssertFalse(punct.eq(rhs: utf16("-")))
        XCTAssertEqual(punct.toString(), "+")
        XCTAssertTrue(punct.span().eq(other: Span.Companion.shared.callSite()))
    }

    func testLiteralRenderingMatchesKotlin() throws {
        XCTAssertEqual(Literal.Companion.shared.string(string: "hello").toString(), "\"hello\"")
        XCTAssertEqual(Literal.Companion.shared.string(string: #"a\b"#).toString(), #""a\\b""#)
        XCTAssertEqual(Literal.Companion.shared.string(string: #"a"b"#).toString(), #""a\"b""#)
        XCTAssertEqual(Literal.Companion.shared.string(string: "a\nb").toString(), #""a\nb""#)
        XCTAssertEqual(Literal.Companion.shared.string(string: "café").toString(), "\"café\"")
        XCTAssertEqual(Literal.Companion.shared.string(string: "it's").toString(), "\"it's\"")

        XCTAssertEqual(Literal.Companion.shared.character(ch: utf16("a")).toString(), "'a'")
        XCTAssertEqual(Literal.Companion.shared.character(ch: utf16("'")).toString(), "'\\''")
        XCTAssertEqual(Literal.Companion.shared.character(ch: utf16("\"")).toString(), "'\"'")

        XCTAssertEqual(Literal.Companion.shared.u8Suffixed(n: 42).toString(), "42u8")
        XCTAssertEqual(Literal.Companion.shared.i32Unsuffixed(n: -7).toString(), "-7")
        XCTAssertEqual(Literal.Companion.shared.f64Unsuffixed(n: 1.0).toString(), "1.0")
    }

    func testTokenStreamAndGroupBehaviorMatchesKotlin() throws {
        let empty = TokenStream.Companion.shared.new()
        XCTAssertTrue(empty.isEmpty())
        XCTAssertEqual(empty.toString(), "")

        let identTree = TokenTree.Ident(value: Ident.Companion.shared.new(string: "hello", span: Span.Companion.shared.callSite()))
        let one = TokenStream.Companion.shared.fromTokenTree(tree: identTree)
        XCTAssertFalse(one.isEmpty())
        XCTAssertEqual(one.toString(), "hello")

        let parsed = TokenStream.Companion.shared.fromString(src: "fun main() { 1 + 2 }")
        XCTAssertTrue(parsed.isSuccess)
        XCTAssertFalse(parsed.isFailure)
        XCTAssertEqual(parsed.getOrThrow().toString(), "fun main () { 1 + 2 }")

        let parseError = TokenStream.Companion.shared.fromString(src: "(")
        XCTAssertFalse(parseError.isSuccess)
        XCTAssertTrue(parseError.isFailure)
        XCTAssertNotNil(parseError.errorOrNull())

        let group = Group.Companion.shared.new(delimiter: .BRACE, stream: one)
        XCTAssertEqual(group.delimiter(), .BRACE)
        XCTAssertEqual(group.stream().toString(), "hello")
        XCTAssertEqual(group.toString(), "{ hello }")
        XCTAssertTrue(group.span().eq(other: Span.Companion.shared.callSite()))
    }

    private func utf16(_ scalar: String) -> UInt16 {
        let units = Array(scalar.utf16)
        XCTAssertEqual(units.count, 1)
        return units[0]
    }
}
