package com.android.wm.shell.triplesplit.split.util;

import android.graphics.Rect;

public class DockedDividerUtils {
    public static void sanitizeStackBounds(Rect bounds, int index) {
        if (index == 1 || index == 2) {
            if (bounds.left >= bounds.right) {
                bounds.left = bounds.right - 1;
            }
            if (bounds.top >= bounds.bottom) {
                bounds.top = bounds.bottom - 1;
            }
        } else {
            if (bounds.left >= bounds.right) {
                bounds.right = bounds.left + 1;
            }
            if (bounds.top >= bounds.bottom) {
                bounds.bottom = bounds.top + 1;
            }
        }
    }
}
