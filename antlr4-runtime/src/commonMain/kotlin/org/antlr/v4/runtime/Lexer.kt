/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import org.antlr.v4.runtime.atn.LexerATNSimulator
import org.antlr.v4.runtime.misc.IntStack
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.misc.Pair


/** A lexer is recognizer that draws input symbols from a character stream.
 * lexer grammars result in a subclass of this object. A Lexer object
 * uses simplified match() and error recovery mechanisms in the interest
 * of speed.
 */
abstract class Lexer : Recognizer<Int, LexerATNSimulator>, TokenSource {
    var _input: CharStream? = null
    protected var _tokenFactorySourcePair: Pair<TokenSource?, CharStream?>? = null

    /** How to create token objects  */
    protected var _factory: TokenFactory<*> = CommonTokenFactory.DEFAULT

    /** The goal of all lexer rules/methods is to create a token object.
     * This is an instance variable as multiple rules may collaborate to
     * create a single token.  nextToken will return this object after
     * matching lexer rule(s).  If you subclass to allow multiple token
     * emissions, then set this to the last token to be matched or
     * something nonnull so that the auto token emit mechanism will not
     * emit another token.
     */
    var _token: Token? = null

    /** What character index in the stream did the current token start at?
     * Needed, for example, to get the text for current token.  Set at
     * the start of nextToken.
     */
    var _tokenStartCharIndex: Int = -1

    /** The line on which the first character of the token resides  */
    var _tokenStartLine: Int = 0

    /** The character position of first character within the line  */
    var _tokenStartCharPositionInLine: Int = 0

    /** Once we see EOF on char stream, next token will be EOF.
     * If you have DONE : EOF ; then you see DONE EOF.
     */
    var _hitEOF: Boolean = false

    /** The channel number for the current token  */
    var channel: Int = 0

    /** The token type for the current token  */
    var type: Int = 0

    val _modeStack: IntStack = IntStack()
    var _mode: Int = org.antlr.v4.runtime.Lexer.Companion.DEFAULT_MODE

    /** You can set the text for the current token to override what is in
     * the input char buffer.  Use setText() or can set this instance var.
     */
    var _text: String? = null

    constructor()

    constructor(input: CharStream?) {
        this._input = input
        this._tokenFactorySourcePair = Pair<TokenSource?, CharStream?>(this, input)
    }

    override fun reset() {
        // wack Lexer state variables
        if (_input != null) {
            _input.seek(0) // rewind the input
        }
        _token = null
        this.type = Token.INVALID_TYPE
        this.channel = Token.DEFAULT_CHANNEL
        _tokenStartCharIndex = -1
        _tokenStartCharPositionInLine = -1
        _tokenStartLine = -1
        _text = null

        _hitEOF = false
        _mode = org.antlr.v4.runtime.Lexer.Companion.DEFAULT_MODE
        _modeStack.clear()

        interpreter!!.reset()
    }

    /** Return a token from this source; i.e., match a token on the char
     * stream.
     */
    fun nextToken(): Token {
        checkNotNull(_input) { "nextToken requires a non-null input stream." }

        // Mark start location in char stream so unbuffered streams are
        // guaranteed at least have text of current token
        val tokenStartMarker: Int = _input.mark()
        try {
            outer@ while (true) {
                if (_hitEOF) {
                    emitEOF()
                    return _token
                }

                _token = null
                this.channel = Token.DEFAULT_CHANNEL
                _tokenStartCharIndex = _input.index()
                _tokenStartCharPositionInLine = interpreter!!.charPositionInLine
                _tokenStartLine = interpreter!!.line
                _text = null
                do {
                    this.type = Token.INVALID_TYPE
                    //				println("nextToken line "+tokenStartLine+" at "+((char)input.LA(1))+
//								   " in mode "+mode+
//								   " at index "+input.index());
                    var ttype: Int
                    try {
                        ttype = interpreter!!.match(_input, _mode)
                    } catch (e: LexerNoViableAltException) {
                        notifyListeners(e) // report error
                        recover(e)
                        ttype = org.antlr.v4.runtime.Lexer.Companion.SKIP
                    }
                    if (_input.LA(1) === IntStream.EOF) {
                        _hitEOF = true
                    }
                    if (this.type == Token.INVALID_TYPE) this.type = ttype
                    if (this.type == org.antlr.v4.runtime.Lexer.Companion.SKIP) {
                        continue@outer
                    }
                } while (this.type == org.antlr.v4.runtime.Lexer.Companion.MORE)
                if (_token == null) emit()
                return _token
            }
        } finally {
            // make sure we release marker after match or
            // unbuffered char stream will keep buffering
            _input.release(tokenStartMarker)
        }
    }

    /** Instruct the lexer to skip creating a token for current lexer rule
     * and look for another token.  nextToken() knows to keep looking when
     * a lexer rule finishes with token set to SKIP_TOKEN.  Recall that
     * if token==null at end of any token rule, it creates one for you
     * and emits it.
     */
    fun skip() {
        this.type = org.antlr.v4.runtime.Lexer.Companion.SKIP
    }

    fun more() {
        this.type = org.antlr.v4.runtime.Lexer.Companion.MORE
    }

    fun mode(m: Int) {
        _mode = m
    }

    fun pushMode(m: Int) {
        if (LexerATNSimulator.debug) println("pushMode " + m)
        _modeStack.push(_mode)
        mode(m)
    }

    fun popMode(): Int {
        if (_modeStack.isEmpty) throw NoSuchElementException("mode stack is empty")
        if (LexerATNSimulator.debug) println("popMode back to " + _modeStack.peek())
        mode(_modeStack.pop())
        return _mode
    }
    fun setTokenFactory(factory: TokenFactory<*>) {
        this._factory = factory
    }
    val tokenFactory: TokenFactory<out Token?>
        get() = _factory

    /** Set the char stream and reset the lexer  */
    fun setInputStream(input: IntStream?) {
        this._input = null
        this._tokenFactorySourcePair = Pair<TokenSource?, CharStream?>(this, _input)
        reset()
        this._input = input as CharStream?
        this._tokenFactorySourcePair = Pair<TokenSource?, CharStream?>(this, _input)
    }
    val sourceName: String
        get() = _input.sourceName
    val inputStream: CharStream?
        get() = _input

    /** By default does not support multiple emits per nextToken invocation
     * for efficiency reasons.  Subclass and override this method, nextToken,
     * and getToken (to push tokens into a list and pull from that list
     * rather than a single variable as this implementation does).
     */
    fun emit(token: Token?) {
        //println("emit "+token);
        this._token = token
    }

    /** The standard method called to automatically emit a token at the
     * outermost lexical rule.  The token object should point into the
     * char buffer start..stop.  If there is a text override in 'text',
     * use that to set the token's text.  Override this method to emit
     * custom Token objects or provide a new factory.
     */
    fun emit(): Token? {
        val t: Token? = _factory.create(
            _tokenFactorySourcePair,
            this.type, _text,
            this.channel, _tokenStartCharIndex, this.charIndex - 1,
            _tokenStartLine, _tokenStartCharPositionInLine
        )
        emit(t)
        return t
    }

    fun emitEOF(): Token? {
        val cpos = this.charPositionInLine
        val line = this.line
        val eof: Token? = _factory.create(
            _tokenFactorySourcePair, Token.EOF, null, Token.DEFAULT_CHANNEL, _input.index(), _input.index() - 1,
            line, cpos
        )
        emit(eof)
        return eof
    }
    var line: Int
        get() = interpreter!!.line
        set(line) {
            interpreter!!.setLine(line)
        }
    var charPositionInLine: Int
        get() = interpreter!!.charPositionInLine
        set(charPositionInLine) {
            interpreter!!.setCharPositionInLine(charPositionInLine)
        }

    val charIndex: Int
        /** What is the index of the current character of lookahead?  */
        get() = _input.index()

    var text: String?
        /** Return the text matched so far for the current token or any
         * text override.
         */
        get() {
            if (_text != null) {
                return _text
            }
            return interpreter!!.getText(_input)
        }
        /** Set the complete text of this token; it wipes any previous
         * changes to the text.
         */
        set(text) {
            this._text = text
        }

    val token: Token?
        /** Override if emitting multiple tokens.  */
        get() = _token

    fun setToken(_token: Token?) {
        this._token = _token
    }

    val channelNames: Array<String?>?
        get() = null

    val modeNames: Array<String?>?
        get() = null

    @get:Deprecated
    val tokenNames: Array<String?>?
        /** Used to print out token names like ID during debugging and
         * error reporting.  The generated parsers implement a method
         * that overrides this to point to their String[] tokenNames.
         */
        get() = null

    val allTokens: List<out Token>
        /** Return a list of all Token objects in input char stream.
         * Forces load of all tokens. Does not include EOF token.
         */
        get() {
            val tokens: MutableList<Token> = ArrayList()
            var t: Token = nextToken()
            while (t.type !== Token.EOF) {
                tokens.add(t)
                t = nextToken()
            }
            return tokens
        }

    fun recover(e: LexerNoViableAltException?) {
        if (_input.LA(1) !== IntStream.EOF) {
            // skip a char and try again
            interpreter!!.consume(_input)
        }
    }

    fun notifyListeners(e: LexerNoViableAltException?) {
        val text: String = _input.getText(Interval.of(_tokenStartCharIndex, _input.index()))
        val msg = "token recognition error at: '" + getErrorDisplay(text) + "'"

        val listener: ANTLRErrorListener = getErrorListenerDispatch()
        listener.syntaxError(this, null, _tokenStartLine, _tokenStartCharPositionInLine, msg, e)
    }

    fun getErrorDisplay(s: String): String {
        val buf: StringBuilder = StringBuilder()
        for (c in s.toCharArray()) {
            buf.append(getErrorDisplay(c.code))
        }
        return buf.toString()
    }

    fun getErrorDisplay(c: Int): String? {
        var s: String? = c.toChar().toString()
        when (c) {
            Token.EOF -> s = "<EOF>"
            '\n' -> s = "\\n"
            '\t' -> s = "\\t"
            '\r' -> s = "\\r"
        }
        return s
    }

    fun getCharErrorDisplay(c: Int): String? {
        val s = getErrorDisplay(c)
        return "'" + s + "'"
    }

    /** Lexers can normally match any char in it's vocabulary after matching
     * a token, so do the easy thing and just kill a character and hope
     * it all works out.  You can instead use the rule invocation stack
     * to do sophisticated error recovery if you are in a fragment rule.
     */
    fun recover(re: RecognitionException?) {
        //println("consuming char "+(char)input.LA(1)+" during recovery");
        //re.printStackTrace();
        // TODO: Do we lose character or line position information?
        _input.consume()
    }

    companion object {
        const val DEFAULT_MODE: Int = 0
        val MORE: Int = -2
        val SKIP: Int = -3

        val DEFAULT_TOKEN_CHANNEL: Int = Token.DEFAULT_CHANNEL
        val HIDDEN: Int = Token.HIDDEN_CHANNEL
        const val MIN_CHAR_VALUE: Int = 0x0000
        const val MAX_CHAR_VALUE: Int = 0x10FFFF
    }
}
