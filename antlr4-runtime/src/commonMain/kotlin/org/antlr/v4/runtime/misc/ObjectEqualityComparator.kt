package org.antlr.v4.runtime.misc

class AnyEqualityComparator : AbstractEqualityComparator<Any?>() {
    override fun hashCode(obj: Any?): Int {
        if (obj == null) {
            return 0
        }
        return obj.hashCode()
    }

    override fun equals(
        a: Any?,
        b: Any?,
    ): Boolean {
        if (a == null) {
            return b == null
        }
        return a.equals(b)
    }

    companion object {
        val INSTANCE: AnyEqualityComparator = AnyEqualityComparator()
    }
}
