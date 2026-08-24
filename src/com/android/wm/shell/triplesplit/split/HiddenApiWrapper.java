package com.android.wm.shell.triplesplit.split;

import android.app.ActivityManager;
//import android.app.ActivityManager.START_SUCCESS;
//import android.app.ActivityManager.START_TASK_TO_FRONT;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.view.RemoteAnimationAdapter;
import android.view.SurfaceControl;

/**
 * A wrapper class for using hidden api via kotlin in app level build
 * TODO delete this wrapper in AOSP build version
 */
public class HiddenApiWrapper {

    public final int START_SUCCESS = 0;

    public final int START_TASK_TO_FRONT = 2;

    public static int getWindowingMode(ActivityManager.RunningTaskInfo callingTask) {
        return callingTask.getWindowingMode();
    }

    public static ActivityOptions fromBundle(Bundle bundle) {
        return ActivityOptions.fromBundle(bundle);
    }

    public static ActivityOptions makeRemoteAnimation(RemoteAnimationAdapter adapter) {
        return ActivityOptions.makeRemoteAnimation(adapter);
    }

    public static int userId(ActivityManager.RunningTaskInfo taskInfo) {
        return taskInfo.userId;
    }

    public static SurfaceControl.Builder setContainerLayer(SurfaceControl.Builder builder) {
        return builder.setContainerLayer();
    }

    public static Intent intent(PendingIntent pendingIntent) {
        return pendingIntent.getIntent();
    }
}
