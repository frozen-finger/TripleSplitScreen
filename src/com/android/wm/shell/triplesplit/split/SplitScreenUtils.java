package com.android.wm.shell.triplesplit.split;

import android.window.DisplayAreaInfo;
import android.window.WindowContainerToken;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wm.shell.RootTaskDisplayAreaOrganizer;

public class SplitScreenUtils {
    @Nullable
    public static WindowContainerToken getNewParentTokenForStage(
            @Nullable StageTaskListener stage,
            @NonNull RootTaskDisplayAreaOrganizer rootTDAOrganizer) {
        if (stage == null)  return null;
        final int displayId = stage.getRunningTaskInfo().displayId;
        final DisplayAreaInfo displayAreaInfo = rootTDAOrganizer.getDisplayAreaInfo(displayId);
        return displayAreaInfo != null ? displayAreaInfo.token : null;
    }

    public static boolean samePackage(String packageName1, String packageName2) {
        if (packageName2 != null) {
            return packageName2.equals(packageName1);
        } else {
            return packageName1 == null;
        }
    }
}
