package org.antlr.v4.runtime.misc

class Pair<A, B>(
    val a: A?,
    val b: B?,
) {
    override fun equals(obj: Any?): Boolean {
        if (obj === this) {
            return true
        } else if (obj !is Pair<*, *>) {
            return false
        }
        val other = obj
        return AnyEqualityComparator.INSTANCE.equals(a, other.a) &&
            AnyEqualityComparator.INSTANCE.equals(b, other.b)
    }

    override fun hashCode(): Int {
        var hash: Int = MurmurHash.initialize()
        hash = MurmurHash.update(hash, a)
        hash = MurmurHash.update(hash, b)
        return MurmurHash.finish(hash, 2)
    }

    override fun toString(): String = "($a, $b)"
}
