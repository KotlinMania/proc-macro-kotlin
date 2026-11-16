package io.github.kotlinmania.procmacro.vendored.kotlin.kmp

import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.element.SyntaxTokenTypes
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.infra.NewTestLexer
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.infra.NewTestParser
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.infra.ParseMode
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.infra.TestSyntaxElement
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.infra.checkSyntaxElements
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.infra.toSourceLinesMapping
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.lexer.KDocKnownTag
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.lexer.KDocTokens
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.lexer.KtTokens
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.parser.BinaryOperationPrecedence
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.parser.KDocParseNodes
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.parser.KtNodeTypes
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.tree.LightSyntaxTree
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.tree.dump
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.tree.parseKDocLightSyntaxTree
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.tree.parseKDocLinkLightSyntaxTree
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.tree.parseKotlinLightSyntaxTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KotlinKmpParserTest {
    @Test
    fun testNewTestLexerWithNestedKDocAndMarkdownLinks() {
        val source =
            """
            /**
             * Summary linking to [ sample.pkg.Greeter.greet ] and [List.size].
             *
             * @param name target user
             * @return greeting string
             */
            fun greet(name: String): String = "Hello, ${'$'}name!"
            """.trimIndent()

        val rootToken = NewTestLexer().tokenize(source)
        val stats = rootToken.countSyntaxElements()
        assertFalse(stats.hasErrorElement)
        assertTrue(stats.number > 20L)

        val docToken = rootToken.children.first { it.syntaxElement == KtTokens.DOC_COMMENT }
        assertTrue(docToken.children.any { it.syntaxElement == KDocTokens.START })
        assertTrue(docToken.children.any { it.syntaxElement == KDocTokens.TAG_NAME })
        assertTrue(docToken.children.any { it.syntaxElement == KDocTokens.END })

        val markdownLinks = docToken.children.filter { it.syntaxElement == KDocTokens.MARKDOWN_LINK }
        assertEquals(2, markdownLinks.size)
        assertTrue(markdownLinks[0].children.any { it.syntaxElement == KtTokens.IDENTIFIER })
        assertTrue(markdownLinks[0].children.any { it.syntaxElement == KtTokens.DOT })

        val dump = rootToken.dump(source.toSourceLinesMapping(), source)
        assertTrue(dump.contains("KDoc"))
        assertTrue(dump.contains("KDOC_MARKDOWN_LINK"))
        assertEquals(KDocKnownTag.PARAM, KDocKnownTag.findByTagName("@param"))
        assertEquals(KDocKnownTag.RETURN, KDocKnownTag.findByTagName("return"))
    }

    @Test
    fun testFullParserDeclarationsTypesAndExpressions() {
        val dollar = '$'
        val source =
            """
            @file:Suppress(" Sample ")
            package sample.demo

            import kotlin.collections.List as KtList
            import kotlin.math.*

            /**
             * Documented sealed hierarchy referencing [Outcome.Success].
             * @property label human readable label
             */
            sealed class Outcome<out T : Any>(val label: String) where T : Comparable<@JvmSuppressWildcards T> {
                data class Success<T : Comparable<T>>(val value: T) : Outcome<T>("ok") {
                    constructor(value: T, extra: Int) : this(value) {
                        val combined = extra + 1
                    }
                }

                enum class Status(val code: Int) {
                    READY(1),
                    DONE(2) {
                        override fun toString(): String = "done"
                    };

                    fun isTerminal(): Boolean = this == DONE
                }

                companion object : Runnable by DefaultRunner {
                    const val VERSION: Int = 1
                }
            }

            typealias Predicate<T> = suspend (T) -> Boolean

            fun <T : Comparable<T>> evaluate(items: KtList<T>, fallback: T?): Int
                contract [
                    returns() implies (fallback != null)
                ]
            {
                var acc = 0
                val (first, second) = Pair(1, 2)
                acc += first + second * 3
                val safeLen = fallback?.toString()?.length ?: 0
                for ((idx, item) in items.withIndex()) {
                    when (val candidate = idx + safeLen) {
                        in 0..<10 if item is String && item !in emptyList<Any>() -> acc += candidate
                        is Int -> acc += 1
                        else -> break
                    }
                }
                val interpolated = $dollar$dollar"value = $dollar$dollar{acc + 1} plain $dollar{ignored}"
                try {
                    while (acc < 0) {
                        acc++
                    }
                    do {
                        acc--
                    } while (acc > 1000)
                } catch (err: RuntimeException) {
                    throw err
                } finally {
                    acc += interpolated.length
                }
                val ref = String::length
                val kls = Outcome::class
                return acc + ref("hi") + kls.hashCode()
            }
            """.trimIndent()

        val fullTree = NewTestParser(ParseMode.Full).parse("Demo.kt", source)
        val noKDocTree = NewTestParser(ParseMode.NoKDoc).parse("Demo.kt", source)
        val lazyTree = NewTestParser(ParseMode.NoCollapsableAndKDoc).parse("Demo.kt", source)

        val fullStats = fullTree.countSyntaxElements()
        val noKDocStats = noKDocTree.countSyntaxElements()
        val lazyStats = lazyTree.countSyntaxElements()

        assertFalse(fullStats.hasErrorElement, fullTree.dump(source.toSourceLinesMapping(), source))
        assertFalse(noKDocStats.hasErrorElement)
        assertFalse(lazyStats.hasErrorElement)

        // Full mode expands KDoc subtrees, NoKDoc expands blocks/lambdas without KDoc, Lazy collapses blocks/lambdas
        assertTrue(fullStats.number > noKDocStats.number)
        assertTrue(noKDocStats.number > lazyStats.number)

        val reparsedFull = NewTestParser(ParseMode.Full).parse("Demo.kt", source)
        assertTrue(checkSyntaxElements(fullTree, reparsedFull))

        val collectedNames = mutableSetOf<String>()
        fun collectNodeNames(node: TestSyntaxElement<*>) {
            collectedNames.add(node.name)
            node.children.forEach(::collectNodeNames)
        }
        collectNodeNames(fullTree)

        assertTrue("kotlin.FILE" in collectedNames)
        assertTrue("FILE_ANNOTATION_LIST" in collectedNames)
        assertTrue("PACKAGE_DIRECTIVE" in collectedNames)
        assertTrue("IMPORT_DIRECTIVE" in collectedNames)
        assertTrue("IMPORT_ALIAS" in collectedNames)
        assertTrue("CLASS" in collectedNames)
        assertTrue("PRIMARY_CONSTRUCTOR" in collectedNames)
        assertTrue("SECONDARY_CONSTRUCTOR" in collectedNames)
        assertTrue("ENUM_ENTRY" in collectedNames)
        assertTrue("DELEGATED_SUPER_TYPE_ENTRY" in collectedNames)
        assertTrue("TYPEALIAS" in collectedNames)
        assertTrue("FUNCTION_TYPE" in collectedNames)
        assertTrue("CONTRACT_EFFECT_LIST" in collectedNames)
        assertTrue("DESTRUCTURING_DECLARATION" in collectedNames)
        assertTrue("SAFE_ACCESS_EXPRESSION" in collectedNames)
        assertTrue("WHEN" in collectedNames)
        assertTrue("WHEN_ENTRY_GUARD" in collectedNames)
        assertTrue("STRING_INTERPOLATION_PREFIX" in collectedNames)
        assertTrue("TRY" in collectedNames)
        assertTrue("CATCH" in collectedNames)
        assertTrue("FINALLY" in collectedNames)
        assertTrue("CALLABLE_REFERENCE_EXPRESSION" in collectedNames)
        assertTrue("CLASS_LITERAL_EXPRESSION" in collectedNames)
        assertTrue("KDoc" in collectedNames)
        assertTrue("KDOC_SECTION" in collectedNames)
        assertTrue("KDOC_TAG" in collectedNames)
        assertTrue("KDOC_MARKDOWN_LINK" in collectedNames)
        assertTrue("KDOC_NAME" in collectedNames)
    }

    @Test
    fun testScriptParsingAndSyntaxErrorRecovery() {
        val scriptSource =
            """
            val greeting = "hello from kts"
            println(greeting)
            greeting.length + 40
            """.trimIndent()

        val scriptTree = NewTestParser(ParseMode.Full).parse("build.kts", scriptSource)
        assertFalse(scriptTree.countSyntaxElements().hasErrorElement)
        assertTrue(scriptTree.children.any { it.name == "SCRIPT" })

        val brokenSource =
            """
            fun broken(x: Int {
                val y = 1 +
            }
            """.trimIndent()
        val brokenTree = NewTestParser(ParseMode.NoKDoc).parse("Broken.kt", brokenSource)
        val brokenStats = brokenTree.countSyntaxElements()
        assertTrue(brokenStats.hasErrorElement)
        assertEquals("kotlin.FILE", brokenTree.name)
    }

    @Test
    fun testLightSyntaxTreeNavigationAndOffsets() {
        val source =
            """
            package sample.light

            class Box(val count: Int) {
                fun doubled(): Int = count * 2
            }
            """.trimIndent()

        val tree = parseKotlinLightSyntaxTree(source, isScript = false, isLazy = false)
        assertEquals(tree, tree.lightSourceTreeStructure)
        assertNull(tree.getParent(LightSyntaxTree.NO_NODE))

        val root = tree.getRoot()
        assertNull(tree.getParent(root))
        assertTrue(tree.isComposite(root))
        assertFalse(tree.isToken(root))
        assertEquals(KtNodeTypes.KT_FILE, tree.getType(root))
        assertEquals(0, tree.getStartOffset(root))
        assertEquals(source.length, tree.getEndOffset(root))
        assertTrue(tree.hasChildOfType(root, KtNodeTypes.PACKAGE_DIRECTIVE))
        assertTrue(tree.hasChildOfType(root, KtNodeTypes.CLASS))

        val pkgNode = assertNotNull(tree.findChildByType(root, KtNodeTypes.PACKAGE_DIRECTIVE))
        assertEquals(root, tree.getParent(pkgNode))
        assertTrue(tree.textEquals(pkgNode, "package sample.light"))
        assertEquals("package sample.light", tree.getTextString(pkgNode))

        val classNodes = tree.getChildrenByType(root, KtNodeTypes.CLASS)
        assertEquals(1, classNodes.size)
        val classNode = classNodes.single()
        val classBody = assertNotNull(tree.findChildByType(classNode, KtNodeTypes.CLASS_BODY))
        val funNode = assertNotNull(tree.findChildByType(classBody, KtNodeTypes.FUN))
        assertTrue(tree.getTextString(funNode).contains("fun doubled(): Int = count * 2"))

        val funKeyword = tree.getChildren(funNode).first { tree.isToken(it) && tree.getType(it) == KtTokens.FUN_MODIFIER }
        assertEquals(funNode, tree.getParent(funKeyword))
        assertTrue(tree.textEquals(funKeyword, "fun"))

        val dumped = tree.dump()
        assertTrue(dumped.contains("kotlin.FILE:"))
        assertTrue(dumped.contains("CLASS:"))
        assertTrue(dumped.contains("FUN:"))

        val kdocTree = parseKDocLightSyntaxTree("/** Hello [Box.doubled] @return twice */")
        assertEquals(KtTokens.DOC_COMMENT, kdocTree.getType(kdocTree.getRoot()))
        assertTrue(kdocTree.hasChildOfType(kdocTree.getRoot(), KDocParseNodes.KDOC_SECTION))

        val linkTree = parseKDocLinkLightSyntaxTree("[Box.doubled]")
        assertEquals(KDocTokens.MARKDOWN_LINK, linkTree.getType(linkTree.getRoot()))
        assertTrue(linkTree.hasChildOfType(linkTree.getRoot(), KDocParseNodes.KDOC_NAME))

        val brokenTree = parseKotlinLightSyntaxTree("fun missingParen(x: Int { }", isScript = false, isLazy = false)
        val brokenFun = assertNotNull(brokenTree.findChildByType(brokenTree.getRoot(), KtNodeTypes.FUN))
        val brokenChildrenTypes = brokenTree.getChildren(brokenFun).map { brokenTree.getType(it) }
        assertTrue(SyntaxTokenTypes.ERROR_ELEMENT in brokenChildrenTypes || brokenTree.dump().contains("ERROR_ELEMENT"))

        assertEquals(BinaryOperationPrecedence.AS, BinaryOperationPrecedence.MULTIPLICATIVE.higherPriority)
        assertNull(BinaryOperationPrecedence.AS.higherPriority)
        assertTrue(KtTokens.PLUS in BinaryOperationPrecedence.ADDITIVE.tokens)
    }
}
