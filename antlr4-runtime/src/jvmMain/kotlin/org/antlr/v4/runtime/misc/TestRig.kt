/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc
import java.lang.reflect.Method

/** A proxy for the real org.antlr.v4.gui.TestRig that we moved to tool
 * artifact from runtime.
 *
 * @since 4.5.1
 */
@Deprecated("")
object TestRig {
    fun main(args: Array<String?>?) {
        try {
            val testRigClass: Class<*> = Class.forName("org.antlr.v4.gui.TestRig")
            println("Warning: TestRig moved to org.antlr.v4.gui.TestRig; calling automatically")
            try {
                val mainMethod: Method = testRigClass.getMethod("main", Array<String>::class.java)
                mainMethod.invoke(null, args as Any?)
            } catch (nsme: Exception) {
                println("Problems calling org.antlr.v4.gui.TestRig.main(args)")
            }
        } catch (cnfe: ClassNotFoundException) {
            println("Use of TestRig now requires the use of the tool jar, antlr-4.X-complete.jar")
            println("Maven users need group ID org.antlr and artifact ID antlr4")
        }
    }
}
