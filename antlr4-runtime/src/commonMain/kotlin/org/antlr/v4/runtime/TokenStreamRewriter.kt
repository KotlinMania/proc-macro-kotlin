/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import org.antlr.v4.runtime.misc.Interval

/**
 * Useful for rewriting out a buffered input token stream after doing some
 * augmentation or other manipulations on it.
 *
 *
 *
 * You can insert stuff, replace, and delete chunks. Note that the operations
 * are done lazily--only if you convert the buffer to a [String] with
 * [TokenStream.getText]. This is very efficient because you are not
 * moving data around all the time. As the buffer of tokens is converted to
 * strings, the [.getText] method(s) scan the input token stream and
 * check to see if there is an operation at the current index. If so, the
 * operation is done and then normal [String] rendering continues on the
 * buffer. This is like having multiple Turing machine instruction streams
 * (programs) operating on a single input tape. :)
 *
 *
 *
 * This rewriter makes no modifications to the token stream. It does not ask the
 * stream to fill itself up nor does it advance the input cursor. The token
 * stream [TokenStream.index] will return the same value before and
 * after any [.getText] call.
 *
 *
 *
 * The rewriter only works on tokens that you have in the buffer and ignores the
 * current input cursor. If you are buffering tokens on-demand, calling
 * [.getText] halfway through the input will only do rewrites for those
 * tokens in the first half of the file.
 *
 *
 *
 * Since the operations are done lazily at [.getText]-time, operations do
 * not screw up the token index values. That is, an insert operation at token
 * index `i` does not change the index values for tokens
 * `i`+1..n-1.
 *
 *
 *
 * Because operations never actually alter the buffer, you may always get the
 * original token stream back without undoing anything. Since the instructions
 * are queued up, you can easily simulate transactions and roll back any changes
 * if there is an error just by removing instructions. For example,
 *
 * <pre>
 * CharStream input = new ANTLRFileStream("input");
 * TLexer lex = new TLexer(input);
 * CommonTokenStream tokens = new CommonTokenStream(lex);
 * T parser = new T(tokens);
 * TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);
 * parser.startRule();
</pre> *
 *
 *
 *
 * Then in the rules, you can execute (assuming rewriter is visible):
 *
 * <pre>
 * Token t,u;
 * ...
 * rewriter.insertAfter(t, "text to put after t");}
 * rewriter.insertAfter(u, "text after u");}
 * println(rewriter.text);
</pre> *
 *
 *
 *
 * You can also have multiple "instruction streams" and get multiple rewrites
 * from a single pass over the input. Just name the instruction streams and use
 * that name again when printing the buffer. This could be useful for generating
 * a C file and also its header file--all from the same buffer:
 *
 * <pre>
 * rewriter.insertAfter("pass1", t, "text to put after t");}
 * rewriter.insertAfter("pass2", u, "text after u");}
 * println(rewriter.getText("pass1"));
 * println(rewriter.getText("pass2"));
</pre> *
 *
 *
 *
 * If you don't use named rewrite streams, a "default" stream is used as the
 * first example shows.
 */
class TokenStreamRewriter(tokens: TokenStream) {
    // Define the rewrite operation hierarchy
    inner open class RewriteOperation {
        /** What index into rewrites List are we?  */
        var instructionIndex: Int = 0

        /** Token buffer index.  */
        var index: Int
        var text: Any? = null

        protected constructor(index: Int) {
            this.index = index
        }

        protected constructor(index: Int, text: Any?) {
            this.index = index
            this.text = text
        }

        /** Execute the rewrite operation by possibly adding to the buffer.
         * Return the index of the next token to operate on.
         */
        open fun execute(buf: StringBuilder): Int {
            return index
        }
        override fun toString(): String {
            var opName: String = this::class.simpleName ?: "RewriteOperation"
            val dollarIndex: Int = opName.indexOf("$")
            opName = opName.substring(dollarIndex + 1, opName.length)
            return "<" + opName + "@" + tokens.get(index) +
                    ":\"" + text + "\">"
        }
    }

    internal inner open class InsertBeforeOp(index: Int, text: Any?) : RewriteOperation(index, text) {
        override fun execute(buf: StringBuilder): Int {
            buf.append(text)
            val token = tokens.get(index)
            if (token != null && token.type != Token.EOF) {
                buf.append(token.text)
            }
            return index + 1
        }
    }

    /** Distinguish between insert after/before to do the "insert afters"
     * first and then the "insert befores" at same index. Implementation
     * of "insert after" is "insert before index+1".
     */
    internal inner class InsertAfterOp(index: Int, text: Any?) : InsertBeforeOp(index + 1, text)

    /** I'm going to try replacing range from x..y with (y-x)+1 ReplaceOp
     * instructions.
     */
    internal inner class ReplaceOp(from: Int, var lastIndex: Int, text: Any?) : RewriteOperation(from, text) {
        override fun execute(buf: StringBuilder): Int {
            if (text != null) {
                buf.append(text)
            }
            return lastIndex + 1
        }
        public override fun toString(): String {
            if (text == null) {
                return "<DeleteOp@" + tokens.get(index) +
                        ".." + tokens.get(lastIndex) + ">"
            }
            return "<ReplaceOp@" + tokens.get(index) +
                    ".." + tokens.get(lastIndex) + ":\"" + text + "\">"
        }
    }

    /** Our source stream  */
    protected val tokens: TokenStream

    /** You may have multiple, named streams of rewrite operations.
     * I'm calling these things "programs."
     * Maps String (name)  rewrite (List)
     */
    protected val programs: MutableMap<String, MutableList<RewriteOperation?>>

    /** Map String (program name)  Int index  */
    protected val lastRewriteTokenIndexes: MutableMap<String, Int>

    init {
        this.tokens = tokens
        programs = HashMap()
        programs.put(
            DEFAULT_PROGRAM_NAME,
            ArrayList<RewriteOperation?>(PROGRAM_INIT_SIZE)
        )
        lastRewriteTokenIndexes = HashMap()
    }

    val tokenStream: TokenStream
        get() = tokens

    fun rollback(instructionIndex: Int) {
        rollback(DEFAULT_PROGRAM_NAME, instructionIndex)
    }

    /** Rollback the instruction stream for a program so that
     * the indicated instruction (via instructionIndex) is no
     * longer in the stream. UNTESTED!
     */
    fun rollback(programName: String, instructionIndex: Int) {
        val `is` = programs.get(programName)
        if (`is` != null) {
            programs.put(
                programName,
                `is`.subList(MIN_TOKEN_INDEX, instructionIndex).toMutableList()
            )
        }
    }

    /** Reset the program so that no instructions exist  */
    @kotlin.jvm.JvmOverloads
    fun deleteProgram(programName: String = DEFAULT_PROGRAM_NAME) {
        rollback(programName, MIN_TOKEN_INDEX)
    }

    fun insertAfter(t: Token, text: Any?) {
        insertAfter(DEFAULT_PROGRAM_NAME, t, text)
    }

    fun insertAfter(index: Int, text: Any?) {
        insertAfter(DEFAULT_PROGRAM_NAME, index, text)
    }

    fun insertAfter(programName: String, t: Token, text: Any?) {
        insertAfter(programName, t.tokenIndex, text)
    }

    fun insertAfter(programName: String, index: Int, text: Any?) {
        // to insert after, just insert before next index (even if past end)
        val op: RewriteOperation = InsertAfterOp(index, text)
        val rewrites = getProgram(programName)
        op.instructionIndex = rewrites.size
        rewrites.add(op)
    }

    fun insertBefore(t: Token, text: Any?) {
        insertBefore(DEFAULT_PROGRAM_NAME, t, text)
    }

    fun insertBefore(index: Int, text: Any?) {
        insertBefore(DEFAULT_PROGRAM_NAME, index, text)
    }

    fun insertBefore(programName: String, t: Token, text: Any?) {
        insertBefore(programName, t.tokenIndex, text)
    }

    fun insertBefore(programName: String, index: Int, text: Any?) {
        val op: RewriteOperation = InsertBeforeOp(index, text)
        val rewrites = getProgram(programName)
        op.instructionIndex = rewrites.size
        rewrites.add(op)
    }

    fun replace(index: Int, text: Any?) {
        replace(DEFAULT_PROGRAM_NAME, index, index, text)
    }

    fun replace(from: Int, to: Int, text: Any?) {
        replace(DEFAULT_PROGRAM_NAME, from, to, text)
    }

    fun replace(indexT: Token, text: Any?) {
        replace(DEFAULT_PROGRAM_NAME, indexT, indexT, text)
    }

    fun replace(from: Token, to: Token, text: Any?) {
        replace(DEFAULT_PROGRAM_NAME, from, to, text)
    }

    fun replace(programName: String, from: Int, to: Int, text: Any?) {
        require(!(from > to || from < 0 || to < 0 || to >= tokens.size())) { "replace: range invalid: " + from + ".." + to + "(size=" + tokens.size() + ")" }
        val op: RewriteOperation = ReplaceOp(from, to, text)
        val rewrites = getProgram(programName)
        op.instructionIndex = rewrites.size
        rewrites.add(op)
    }

    fun replace(programName: String, from: Token, to: Token, text: Any?) {
        replace(
            programName,
            from.tokenIndex,
            to.tokenIndex,
            text
        )
    }

    fun delete(index: Int) {
        delete(DEFAULT_PROGRAM_NAME, index, index)
    }

    fun delete(from: Int, to: Int) {
        delete(DEFAULT_PROGRAM_NAME, from, to)
    }

    fun delete(indexT: Token) {
        delete(DEFAULT_PROGRAM_NAME, indexT, indexT)
    }

    fun delete(from: Token, to: Token) {
        delete(DEFAULT_PROGRAM_NAME, from, to)
    }

    fun delete(programName: String, from: Int, to: Int) {
        replace(programName, from, to, null)
    }

    fun delete(programName: String, from: Token, to: Token) {
        replace(programName, from, to, null)
    }

    val lastRewriteTokenIndex: Int
        get() = getLastRewriteTokenIndex(DEFAULT_PROGRAM_NAME)

    protected fun getLastRewriteTokenIndex(programName: String): Int {
        val I: Int? = lastRewriteTokenIndexes.get(programName)
        if (I == null) {
            return -1
        }
        return I
    }

    protected fun setLastRewriteTokenIndex(programName: String, i: Int) {
        lastRewriteTokenIndexes[programName] = i
    }

    protected fun getProgram(name: String): MutableList<RewriteOperation?> {
        var `is` = programs.get(name)
        if (`is` == null) {
            `is` = initializeProgram(name)
        }
        return `is`
    }

    private fun initializeProgram(name: String): MutableList<RewriteOperation?> {
        val `is`: MutableList<RewriteOperation?> =
            ArrayList<RewriteOperation?>(org.antlr.v4.runtime.TokenStreamRewriter.Companion.PROGRAM_INIT_SIZE)
        programs[name] = `is`
        return `is`
    }

    val text: String
        get() = getText(
            DEFAULT_PROGRAM_NAME,
            Interval.of(0, tokens.size() - 1)
        )

    fun getText(programName: String): String {
        return getText(programName, Interval.of(0, tokens.size() - 1))
    }

    fun getText(interval: Interval): String {
        return getText(DEFAULT_PROGRAM_NAME, interval)
    }

    fun getText(programName: String, interval: Interval): String {
        val rewrites = programs.get(programName)
        var start: Int = interval.a
        var stop: Int = interval.b

        // ensure start/end are in range
        if (stop > tokens.size() - 1) stop = tokens.size() - 1
        if (start < 0) start = 0

        if (rewrites == null || rewrites.isEmpty()) {
            return tokens.getText(interval) ?: ""
        }
        val buf: StringBuilder = StringBuilder()

        // First, optimize instruction stream
        val indexToOp: MutableMap<Int, RewriteOperation> = reduceToSingleOperationPerIndex(rewrites)

        // Walk buffer, executing instructions and emitting tokens
        var i = start
        while (i <= stop && i < tokens.size()) {
            val op = indexToOp.get(i)
            indexToOp.remove(i) // remove so any left have index size-1
            val t: Token = tokens.get(i)!!
            if (op == null) {
                // no operation at that index, just dump token
                if (t.type != Token.EOF) buf.append(t.text)
                i++ // move to next token
            } else {
                i = op.execute(buf) // execute operation and skip
            }
        }

        // include stuff after end if it's last index in buffer
        // So, if they did an insertAfter(lastValidIndex, "foo"), include
        // foo if end==lastValidIndex.
        if (stop == tokens.size() - 1) {
            // Scan any remaining operations after last token
            // should be included (they will be inserts).
            for (op in indexToOp.values) {
                if (op.index >= tokens.size() - 1) buf.append(op.text)
            }
        }
        return buf.toString()
    }

    /** We need to combine operations and report invalid operations (like
     * overlapping replaces that are not completed nested). Inserts to
     * same index need to be combined etc...  Here are the cases:
     *
     * I.i.u I.j.v								leave alone, nonoverlapping
     * I.i.u I.i.v								combine: Iivu
     *
     * R.i-j.u R.x-y.v	| i-j in x-y			delete first R
     * R.i-j.u R.i-j.v							delete first R
     * R.i-j.u R.x-y.v	| x-y in i-j			ERROR
     * R.i-j.u R.x-y.v	| boundaries overlap	ERROR
     *
     * Delete special case of replace (text==null):
     * D.i-j.u D.x-y.v	| boundaries overlap	combine to max(min)..max(right)
     *
     * I.i.u R.x-y.v | i in (x+1)-y			delete I (since insert before
     * we're not deleting i)
     * I.i.u R.x-y.v | i not in (x+1)-y		leave alone, nonoverlapping
     * R.x-y.v I.i.u | i in x-y				ERROR
     * R.x-y.v I.x.u 							R.x-y.uv (combine, delete I)
     * R.x-y.v I.i.u | i not in x-y			leave alone, nonoverlapping
     *
     * I.i.u = insert u before op @ index i
     * R.x-y.u = replace x-y indexed tokens with u
     *
     * First we need to examine replaces. For any replace op:
     *
     * 1. wipe out any insertions before op within that range.
     * 2. Drop any replace op before that is contained completely within
     * that range.
     * 3. Throw exception upon boundary overlap with any previous replace.
     *
     * Then we can deal with inserts:
     *
     * 1. for any inserts to same index, combine even if not adjacent.
     * 2. for any prior replace with same left boundary, combine this
     * insert with replace and delete this replace.
     * 3. throw exception if index in same range as previous replace
     *
     * Don't actually delete; make op null in list. Easier to walk list.
     * Later we can throw as we add to index  op map.
     *
     * Note that I.2 R.2-2 will wipe out I.2 even though, technically, the
     * inserted stuff would be before the replace range. But, if you
     * add tokens in front of a method body '{' and then delete the method
     * body, I think the stuff before the '{' you added should disappear too.
     *
     * Return a map from token index to operation.
     */
    protected fun reduceToSingleOperationPerIndex(rewrites: MutableList<RewriteOperation?>): MutableMap<Int, RewriteOperation> {
        // WALK REPLACES
        for (i in 0..<rewrites.size) {
            val op = rewrites.get(i)
            if (op == null) continue
            if (op !is ReplaceOp) continue
            val rop = op
            // Wipe prior inserts within range
            val inserts: List<InsertBeforeOp> =
                getKindOfOps<InsertBeforeOp>(rewrites, i)
            for (iop in inserts) {
                if (iop.index == rop.index) {
                    rewrites[iop.instructionIndex] = null
                    rop.text = iop.text.toString() + (if (rop.text != null) rop.text.toString() else "")
                } else if (iop.index > rop.index && iop.index <= rop.lastIndex) {
                    rewrites[iop.instructionIndex] = null
                }
            }
            // Drop any prior replaces contained within
            val prevReplaces: List<ReplaceOp> =
                getKindOfOps<ReplaceOp>(rewrites, i)
            for (prevRop in prevReplaces) {
                if (prevRop.index >= rop.index && prevRop.lastIndex <= rop.lastIndex) {
                    rewrites[prevRop.instructionIndex] = null
                    continue
                }
                val disjoint =
                    prevRop.lastIndex < rop.index || prevRop.index > rop.lastIndex
                if (prevRop.text == null && rop.text == null && !disjoint) {
                    rewrites[prevRop.instructionIndex] = null
                    rop.index = minOf(prevRop.index, rop.index)
                    rop.lastIndex = maxOf(prevRop.lastIndex, rop.lastIndex)
                    println("new rop " + rop)
                } else require(disjoint) { "replace op boundaries of " + rop + " overlap with previous " + prevRop }
            }
        }
        // WALK INSERTS
        for (i in 0..<rewrites.size) {
            val op = rewrites[i]
            if (op == null) continue
            if (op !is InsertBeforeOp) continue
            val iop = op
            val prevInserts: List<InsertBeforeOp> =
                getKindOfOps<InsertBeforeOp>(rewrites, i)
            for (prevIop in prevInserts) {
                if (prevIop.index == iop.index) {
                    if (prevIop is InsertAfterOp) {
                        iop.text = catOpText(prevIop.text, iop.text)
                        rewrites[prevIop.instructionIndex] = null
                    } else if (prevIop is InsertBeforeOp) {
                        iop.text = catOpText(iop.text, prevIop.text)
                        rewrites[prevIop.instructionIndex] = null
                    }
                }
            }
            val prevReplaces: List<ReplaceOp> =
                getKindOfOps<ReplaceOp>(rewrites, i)
            for (rop in prevReplaces) {
                if (iop.index == rop.index) {
                    rop.text = catOpText(iop.text, rop.text)
                    rewrites[i] = null
                    continue
                }
                require(!(iop.index >= rop.index && iop.index <= rop.lastIndex)) { "insert op " + iop + " within boundaries of previous " + rop }
            }
        }
        val m: MutableMap<Int, RewriteOperation> = HashMap()
        for (i in 0..<rewrites.size) {
            val op = rewrites[i]
            if (op == null) continue
            if (m[op.index] != null) {
                throw Error("should only be one op per index")
            }
            m[op.index] = op
        }
        return m
    }

    protected fun catOpText(a: Any?, b: Any?): String? {
        var x: String? = ""
        var y: String? = ""
        if (a != null) x = a.toString()
        if (b != null) y = b.toString()
        return x + y
    }

    /** Get all operations before an index of a particular kind  */
    protected inline fun <reified T : RewriteOperation> getKindOfOps(
        rewrites: List<RewriteOperation?>,
        before: Int
    ): List<T> {
        val ops = ArrayList<T>()
        for (i in 0 until minOf(before, rewrites.size)) {
            val op = rewrites[i] ?: continue
            if (op is T) {
                ops.add(op)
            }
        }
        return ops
    }

    companion object {
        val DEFAULT_PROGRAM_NAME: String = "default"
        const val PROGRAM_INIT_SIZE: Int = 100
        const val MIN_TOKEN_INDEX: Int = 0
    }
}
