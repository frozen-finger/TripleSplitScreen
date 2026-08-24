package com.android.wm.shell.triplesplit.split;

import android.app.ActivityManager;
import android.app.IActivityTaskManager;
import android.app.PendingIntent;
import android.app.TaskInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.logging.InstanceId;
import com.android.wm.shell.RootDisplayAreaOrganizer;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayChangeController;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.transition.Transitions;

import java.io.PrintWriter;

public abstract class StageCoordinatorAbstract implements SplitLayout.SplitLayoutHandler,
        DisplayController.OnDisplaysChangedListener,
        DisplayChangeController.OnDisplayChangingListener,
        Transitions.TransitionHandler,
        ShellTaskOrganizer.TaskListener,
        StageTaskListener.StageListenerCallbacks,
        SplitMultiDisplayProvider{

        public static StageCoordinator createStageCoordinator(Context context, int displayId,
              SyncTransactionQueue syncQueue, ShellTaskOrganizer taskOrganizer,
              DisplayController displayController, DisplayImeController displayImeController,
              DisplayInsetsController displayInsetsController,
              TransactionPool transactionPool, ShellExecutor mainExecutor,
              Handler mainHandler, SplitState splitState,
              RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
              RootDisplayAreaOrganizer rootDisplayAreaOrganizer,
              IActivityTaskManager activityTaskManager) {

                return new StageCoordinator(context, displayId, syncQueue, taskOrganizer,
                        displayController, displayImeController, displayInsetsController,
                        transactionPool, mainExecutor, mainHandler, splitState,
                        rootTaskDisplayAreaOrganizer, rootDisplayAreaOrganizer,
                        activityTaskManager);
        }

        //register
        abstract void registerSplitScreenListener(SplitScreen.StageScreenListener listener);

        abstract void unregisterSplitScreenListener(SplitScreen.StageScreenListener listener);

        abstract void registerSplitSelectListener(SplitScreen.SplitSelectListener listener);

        abstract void unregisterSplitSelectListener(SplitScreen.SplitSelectListener listener);

        /**
         * Launch an existing task via a taskId
         */
        abstract void startTask(int taskId, @Nullable Bundle options,
                                @Nullable WindowContainerToken hideTaskToken,
                                @com.android.wm.shell.triplesplit.split.SplitScreenConstants.SplitIndex int
                                index);

        /**
         * Launch an activity into split.
         */
        abstract void startIntent(PendingIntent intent, Intent fillinIntent,
                                  @Nullable Bundle options, @Nullable WindowContainerToken hideTaskToken,
                                  @Nullable WindowContainerTransaction transaction,
                                  @com.android.wm.shell.triplesplit.split.SplitScreenConstants.SplitIndex int
                                  index, int displayId);
        /**
         * starts 2 tasks in one transition.
         */
        abstract void startTasks(int taskId1, @Nullable Bundle options1, int taskId2, @Nullable
                                 Bundle options2, @SplitScreenConstants.SnapPosition int position);
        /**
         * start 3 tasks in one transition.
         */
        abstract void startTasks(int taskId1, @Nullable Bundle options1, int taskId2, @Nullable Bundle options2,
                                     int taskId3, @Nullable Bundle options3,
                                     @SplitScreenConstants.SplitIndex int index1,
                                     @SplitScreenConstants.SplitIndex int index2,
                                     @SplitScreenConstants.SplitIndex int index3,
                                     @SplitScreenConstants.PersistentSnapPosition int snapPosition,
                                     RemoteAnimationAdapter adapter);
        /**
         * start 3 intents in one transition
         */
        abstract void startIntents(PendingIntent pendingIntent1, Intent fillInIntent1, @Nullable Bundle options1,
                          PendingIntent pendingIntent2, Intent fillInIntent2, @Nullable Bundle options2,
                          PendingIntent pendingIntent3, Intent fillInIntent3, @Nullable Bundle options3,
                          @SplitScreenConstants.SplitIndex int index1,
                          @SplitScreenConstants.SplitIndex int index2,
                          @SplitScreenConstants.SplitIndex int index3,
                          @SplitScreenConstants.PersistentSnapPosition int snapPosition,
                          RemoteAnimationAdapter adapter);

        abstract Bundle resolveStartStage(@SplitScreen.StageType int stage,
                                          @Nullable Bundle options,
                                          @SplitScreenConstants.SplitIndex int index,
                                          @Nullable WindowContainerTransaction wct);

        //Split operations
        abstract void requestEnterSplitSelect(ActivityManager.RunningTaskInfo taskInfo,
                                              int index, Rect taskBounds);

        abstract boolean moveToStage(ActivityManager.RunningTaskInfo task,
                                     @SplitScreenConstants.SplitIndex int index,
                                     WindowContainerTransaction wct);

        abstract void switchSplitPosition(int index1, int index2, String reason);

        /**
         * Prepare transaction to active split screen. If there's a task indicated, the task will be
         * put into middle stage.
         */
        abstract void prepareEnterSplitScreen(WindowContainerTransaction wct,
                                              @SplitScreenConstants.SplitIndex int stage,
                                              @Nullable ActivityManager.RunningTaskInfo taskInfo,
                                              boolean resizeAnim,
                                              @SplitScreenConstants.SplitIndex int index);

        abstract void finishEnterSplitScreen(SurfaceControl.Transaction transaction);

        /**
         * Unlike exitSplitScreen, this takes a stagetype vs an actual stage-reference and populates
         * an existing WindowContainerTransaction (rather than applying immediately). This is intended
         * to be used when exiting split might be bundled with other window operations.
         *
         * @param stageToTop The stage to move to the top
         */
        abstract void prepareExitSplitScreen(@SplitScreen.StageType int stageToTop,
                                             @NonNull WindowContainerTransaction wct,
                                             int exitReason);

        abstract void prepareEvictNonOpeningChildTasks(
                @SplitScreenConstants.SplitIndex int index, RemoteAnimationTarget[] apps,
                WindowContainerTransaction wct);

        abstract void prepareEvictInvisibleChildTasks(WindowContainerTransaction wct);

        abstract void dismissSplitScreen(int toTopTaskId, int exitReason);

        abstract void grantFocusToStage(@SplitScreen.StageType int type);

        abstract void grantFocusToPosition(@SplitScreenConstants.SplitIndex int index);

        /**
         * Dismisses split in the background
         */
        public abstract void dismissSplitInBackground(int exitReason);

        /**
         * Update surfaces of the split screen layout based on current state
         */
        public abstract void updateSurfaces(SurfaceControl.Transaction transaction);

        public abstract void goToFullscreenFromSplit();

        /**
         * Move the specified task to fullscreen
         */
        public abstract void moveTaskToFullscreen(int taskId, int exitReason);

        abstract void exitSplitScreenOnHide(boolean exitSplitScreenOnHide);

        /**
         * Split events
         */
        abstract void onStartedWakingUp();

        abstract void onStartedGoingToSleep();

        /**
         * Sets drag info to be logged when split screen is next entered
         */
        abstract void onDroppedToSplit(@SplitScreenConstants.SplitIndex int index,
                                       InstanceId dragSessionId);

        /**
         * Returns whether hte given wct is reordering any of the split tasks to top
         */
        public abstract boolean wctIsReorderingSplitToTop(WindowContainerTransaction wct);

        abstract void sendStatusToListener(SplitScreen.StageScreenListener listener);

        abstract void handleUnsupportedSplitStart();

        //split properties
        @SplitScreen.StageType
        abstract int getStageOfTask(int taskId);

        abstract boolean isRootOrStageRoot(int taskId);

        abstract int getTaskId(@SplitScreenConstants.SplitIndex int index);

        abstract void getStageBounds(Rect bounds1, Rect bounds2, Rect bounds3);

        abstract void getRefStageBounds(Rect bounds1, Rect bounds2, Rect bounds3);

        @SplitScreenConstants.SplitIndex
        abstract int getSplitIndex(int taskId);

        abstract void setDividerVisibility(int id, boolean visible,
                                           @Nullable SurfaceControl.Transaction wct);

        abstract void setStageDecorBitmap(@SplitScreenConstants.SplitIndex int index,
                                          @Nullable Bitmap bitmap);

        abstract boolean isLaunchToSplit(TaskInfo taskInfo);

        abstract int getActivateSplitPosition(TaskInfo taskInfo);

        public abstract boolean isSplitActive();

        public abstract boolean isSplitScreenVisible();

        @SplitScreen.StageType
        public abstract int getSplitItemStage(@Nullable WindowContainerToken token);

        abstract void notifySplitAnimationStatus(boolean animationRunning);

        abstract @SplitScreenConstants.SplitIndex int getStageAIndex();

        abstract @SplitScreenConstants.SplitIndex int getStageBIndex();

        abstract @SplitScreenConstants.SplitIndex int getStageCIndex();

        abstract void setStageAIndex(@SplitScreenConstants.SplitIndex int index,
                                     WindowContainerTransaction wct);

        abstract void setStageBIndex(@SplitScreenConstants.SplitIndex int index,
                                     WindowContainerTransaction wct);

        abstract void setStageCIndex(@SplitScreenConstants.SplitIndex int index,
                                     WindowContainerTransaction wct);

        abstract SplitMultiDisplayHelper getSplitMultiDisplayHelper();

        abstract void setSplitMultiDisplayHelper(SplitMultiDisplayHelper splitMultiDisplayHelper);

        abstract @SplitScreen.StageType int getLastActiveStage();

        //Split debug log

        public abstract void dump(@NonNull PrintWriter pw, String prefix);


        public abstract void onNoLongerSupportMultiWindow(StageTaskListener stageTaskListener,
                                                          ActivityManager.RunningTaskInfo taskInfo);

}
