// Vendored marker annotation from JetBrains annotations (annotation only, no behavior)
package io.github.kotlinmania.procmacro.vendored.annotations

/** Matches upstream org.jetbrains.annotations.Contract API shape. */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
)
@Retention(AnnotationRetention.SOURCE)
annotation class Contract(
    val value: String = "",
    val pure: Boolean = false,
)
