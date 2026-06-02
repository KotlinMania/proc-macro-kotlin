// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.logger

import com.intellij.platform.syntax.Logger

internal fun noopLogger(): Logger = NoopLoggerImpl

private object NoopLoggerImpl : Logger {
    override fun error(string: String) {
    }

    override fun errorWithAttachments(
        string: String,
        attachments: List<Logger.Attachment>,
    ) {
    }

    override fun warn(
        string: String,
        exception: Throwable?,
    ) {
    }

    override fun info(
        string: String,
        exception: Throwable?,
    ) {
    }

    override fun debug(
        string: String,
        exception: Throwable?,
    ) {
    }

    override fun trace(exception: Throwable) {
    }

    override fun trace(string: String) {
    }

    override fun isDebugEnabled(): Boolean = false
}
