package org.antlr.v4.runtime.misc

class Triple<A, B, C>(
    val a: A?,
    val b: B?,
    val c: C?,
) {
    override fun equals(obj: Any?): Boolean {
        if (obj === this) {
            return true
        } else if (obj !is Triple<*, *, *>) {
            return false
        }
        val other = obj
        return AnyEqualityComparator.INSTANCE.equals(a, other.a) &&
            AnyEqualityComparator.INSTANCE.equals(b, other.b) &&
            AnyEqualityComparator.INSTANCE.equals(c, other.c)
    }

    override fun hashCode(): Int {
        var hash: Int = MurmurHash.initialize()
        hash = MurmurHash.update(hash, a)
        hash = MurmurHash.update(hash, b)
        hash = MurmurHash.update(hash, c)
        return MurmurHash.finish(hash, 3)
    }

    override fun toString(): String = "($a, $b, $c)"
}
