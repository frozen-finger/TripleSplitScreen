package com.android.wm.shell.triplesplit.split;

import android.app.ActivityManager;
import android.hardware.display.DisplayManager;
import android.media.audio.common.Int;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;

import java.util.ArrayList;
import java.util.HashMap;


/**
 * Class for managing split-screen across multiple displays
 */
public class SplitMultiDisplayHelper {

    private static final String TAG = SplitMultiDisplayHelper.class.getSimpleName();
    private DisplayManager displayManager;

    private HashMap<Integer, SplitTaskHierarchy> displayTaskMap = new HashMap<>();
    private ArrayList<Integer> displayIds;

    public SplitMultiDisplayHelper(DisplayManager displayManager) {
        this.displayManager = displayManager;
    }

    static class SplitTaskHierarchy{
        public ActivityManager.RunningTaskInfo rootTaskInfo;
        public StageTaskListener leftStage;
        public StageTaskListener middleStage;
        public StageTaskListener rightStage;
        public SurfaceControl rootTaskLeash;
        public SplitLayout splitLayout;

        public SplitTaskHierarchy(ActivityManager.RunningTaskInfo taskInfo, StageTaskListener left,
                                  StageTaskListener middle, StageTaskListener right,
                                  SurfaceControl rootTaskLeash, SplitLayout splitLayout) {
            this.rootTaskInfo = taskInfo;
            this.leftStage = left;
            this.middleStage = middle;
            this.rightStage = right;
            this.rootTaskLeash = rootTaskLeash;
            this.splitLayout = splitLayout;
        }

        public SplitTaskHierarchy() {
        }
    }

    public ArrayList<Integer> getCachedOrSystemDisplayIds() {
        if (displayIds == null) {
            ArrayList<Integer> ids = new ArrayList<>();
            for (Display display: displayManager.getDisplays()) {
                ids.add(display.getDisplayId());
            }
            displayIds = ids;
        }

        return displayIds;
    }

    public void swapDisplayTaskHierarchy(int firstDisplay, int secondDisplay) {
        if (!displayTaskMap.containsKey(firstDisplay) || !displayTaskMap.containsKey(secondDisplay)) {
            Log.w(TAG, "Attempted to swap task hierarchies for invalid displayIDs: "
                    + firstDisplay + " " + secondDisplay);
            return;
        }

        if (firstDisplay == secondDisplay) {
            return;
        }

        SplitTaskHierarchy firstHierarchy = displayTaskMap.get(firstDisplay);
        SplitTaskHierarchy secondHierarchy = displayTaskMap.get(secondDisplay);

        displayTaskMap.put(firstDisplay, secondHierarchy);
        displayTaskMap.put(secondDisplay, firstHierarchy);
    }

    public ActivityManager.RunningTaskInfo getDisplayRootTaskInfo(int displayId) {
        if(!displayTaskMap.containsKey(displayId)||
                displayTaskMap.get(displayId) == null) {
            return null;
        } else {
            return displayTaskMap.get(displayId).rootTaskInfo;
        }
    }

    public void setDisplayRootTaskInfo(int displayId, ActivityManager.RunningTaskInfo rootTaskInfo) {
        SplitTaskHierarchy hierarchy = displayTaskMap.computeIfAbsent(displayId,
                id -> new SplitTaskHierarchy());
        hierarchy.rootTaskInfo = rootTaskInfo;
    }

    public SurfaceControl getDisplayRootTaskLeash(int displayId) {
        return displayTaskMap.containsKey(displayId)?displayTaskMap.get(displayId).rootTaskLeash:
                null;
    }

    public void setDisplayRootTaskLeash(int displayId, SurfaceControl leash) {
        SplitTaskHierarchy hierarchy = displayTaskMap.computeIfAbsent(displayId,
                id -> new SplitTaskHierarchy());
        hierarchy.rootTaskLeash = leash;
    }
}
