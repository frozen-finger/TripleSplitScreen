package com.android.wm.shell.triplesplit.split;

import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;

public interface SplitMultiDisplayProvider {
    /**
     * Returns the WindowContainerToken for the root of the given displayId.
     */
    WindowContainerToken getDisplayRootForDisplayId(int displayId);

    /**
     * Prepares to reparent the split-screen root to another display if the target task resides
     * on a different display. It only adds the reparent operation to the wct, without executing it.
     */
    void prepareMovingSplitScreenRoot(@Nullable WindowContainerTransaction wct,
                                      int displayId);
}
