package com.android.wm.shell.triplesplit.split.util;

import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

/**
 * Provides an application icon or cover drawable for split resize decor.
 *
 * <p>Implementations receive the package name of the task currently being covered and may return
 * a drawable that represents that app. Return {@code null} when no drawable is available; split
 * decor will then fall back to the caller-provided bitmap and finally to a captured screenshot.</p>
 */
public interface SplitIconProvider {
    @Nullable
    Drawable getIconDrawable(@Nullable String packageName);
}
