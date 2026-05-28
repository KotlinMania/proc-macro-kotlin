// Vendored stub from JetBrains annotations (annotation only, no behavior)
package org.jetbrains.annotations

/** Matches upstream org.jetbrains:annotations API shape. No-op in this vendored copy. */
object ApiStatus {
    /** Marker annotation indicating experimental API. */
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Experimental

    /** Marker annotation indicating internal API. */
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Internal

    /** Marker annotation indicating obsolete API. */
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Obsolete
}
