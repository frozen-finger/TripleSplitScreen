package com.android.wm.shell.triplesplit.split;

import android.annotation.IntDef;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
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

import com.android.wm.shell.triplesplit.split.util.SplitIconProvider;

import java.util.List;
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

    /** Moves the current triple-split root behind fullscreen content without removing its tasks. */
    void moveSplitToBack();

    /** Restores a previously backgrounded triple-split root without relaunching its tasks. */
    void restoreSplitToFront();

    void goToFullscreenFromSplit();

    void setSplitScreenFocus(int index);

    void moveTaskToFullscreen(int taskId);

    boolean isTaskInSplitScreen(int taskId);

    /** Returns package names currently hosted by one stage, or {@code null} when it is empty. */
    @Nullable
    List<String> getSplitScreenPackageNames(@SplitScreenConstants.SplitIndex int index);

    void setStageDecorBitmap(@SplitScreenConstants.SplitIndex int index, @Nullable Bitmap bitmap);

    /** Captures the visible triple-split root, unless it is unavailable or contains secure layers. */
    @Nullable
    Bitmap captureSplitScreen();

    /** Sets the icon/cover provider used by every stage during resize decor animations. */
    void setSplitIconProvider(@Nullable SplitIconProvider splitIconProvider);

    /** Sets one custom DividerView layout for both triple-split dividers. */
    void setDividerLayout(@LayoutRes int layoutResId);

    /**
     * Sets a custom DividerView layout for both dividers and optional bar, handle, and corner ids.
     * Pass {@code 0} for an id to use default/type lookup; call before divider hosts are created.
     */
    void setDividerLayout(@LayoutRes int layoutResId, @IdRes int dividerBarId,
            @IdRes int dividerHandleId, @IdRes int dividerCornerId);

    /** Applies one dimension configuration to both dividers and all three stage bounds. */
    void setSplitScreenDimens(@Nullable SplitScreenDimenConfig dimenConfig);
}
