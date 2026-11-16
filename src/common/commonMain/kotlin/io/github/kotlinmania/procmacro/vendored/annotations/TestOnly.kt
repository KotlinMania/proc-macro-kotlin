// Vendored marker annotation from JetBrains annotations (annotation only, no behavior)
package io.github.kotlinmania.procmacro.vendored.annotations

/** Matches upstream org.jetbrains.annotations.TestOnly API shape. */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
)
@Retention(AnnotationRetention.SOURCE)
annotation class TestOnly
