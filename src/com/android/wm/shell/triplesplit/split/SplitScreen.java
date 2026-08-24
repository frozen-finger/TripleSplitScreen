package com.android.wm.shell.triplesplit.split;

import android.annotation.IntDef;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.WindowConfiguration;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.window.WindowContainerTransaction;

import java.util.concurrent.Executor;

public interface SplitScreen {
    final static int STAGE_TYPE_UNDEFINED = -1;
    final static int STAGE_TYPE_A = 0;
    final static int STAGE_TYPE_B = 1;
    final static int STAGE_TYPE_C = 2;

    /**
     * Identify
     */
    @IntDef(prefix = {"STAGE_TYPE_"}, value = {
            STAGE_TYPE_UNDEFINED,
            STAGE_TYPE_A,
            STAGE_TYPE_B,
            STAGE_TYPE_C
    })
    @interface StageType{}
    public interface StageScreenListener {
        default void onStagePositionChanged(@StageType int stage,
                                            @SplitScreenConstants.SplitIndex int position) {}
        default void onTaskStageChanged(int taskId, @StageType int stage, boolean isVisible){}
        default void onSplitBoundsChanged(Rect rootBounds, Rect boundsA,
                                          Rect boundsB, Rect boundsC){}
        default void onSplitVisibilityChanged(boolean visible){}
    }

    public interface SplitSelectListener {
        default boolean onRequestEnterSplitSelect(ActivityManager.RunningTaskInfo taskInfo,
                                                  int splitPosition, Rect taskBounds, boolean startRecents,
                                                  @Nullable WindowContainerTransaction wct) {
            return false;
        }
    }

    static String stageTypeToString(@StageType int stage) {
        switch (stage) {
            case STAGE_TYPE_UNDEFINED: return "STAGE_TYPE_UNDEFINED";
            case STAGE_TYPE_A: return "STAGE_TYPE_A";
            case STAGE_TYPE_B: return "STAGE_TYPE_B";
            case STAGE_TYPE_C: return "STAGE_TYPE_C";
            default: return "UNKNOWN " + stage;
        }
    }

    void registerSplitScreenListener(@NonNull StageScreenListener listener,
                                     @NonNull Executor executor);

    void unregisterSplitScreenListener(@NonNull StageScreenListener listener);

    void startTask(int taskId, @SplitScreenConstants.SplitIndex int index,
                   @Nullable Bundle options);

    void startIntent(@NonNull PendingIntent pendingIntent,
                     @Nullable Intent fillInIntent,
                     @SplitScreenConstants.SplitIndex int index,
                     @Nullable Bundle options);

    void enterSplitScreen(int taskId, @SplitScreenConstants.SplitIndex int index);

    void exitSplitScreen(int toTopTaskId);

    void exitSplitScreenOnHide(boolean exitSplitScreenOnHide);

    void goToFullscreenFromSplit();

    void setSplitScreenFocus(int index);

    void moveTaskToFullscreen(int taskId);

    void setStageDecorBitmap(@SplitScreenConstants.SplitIndex int index, @Nullable Bitmap bitmap);
}
