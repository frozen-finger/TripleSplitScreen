package com.android.wm.shell.triplesplit.split;

import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.content.res.Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED;
import static android.view.RemoteAnimationTarget.MODE_OPENING;
import static com.android.wm.shell.common.split.SplitScreenConstants.CONTROLLED_ACTIVITY_TYPES;
import static com.android.wm.shell.common.split.SplitScreenConstants.CONTROLLED_WINDOWING_MODES;
import static com.android.wm.shell.common.split.SplitScreenConstants.CONTROLLED_WINDOWING_MODES_WHEN_ACTIVE;
import static com.android.wm.shell.triplesplit.split.util.SplitUtilConstants.ENABLE_SHELL_TRANSITION;
import static com.android.wm.shell.triplesplit.split.util.SplitUtilConstants.INVALID_TASK_ID;

import android.annotation.CallSuper;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Point;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.util.ArrayUtils;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SyncTransactionQueue;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class StageTaskListener implements ShellTaskOrganizer.TaskListener{
    private static final String TAG = StageTaskListener.class.getSimpleName();


    public interface StageListenerCallbacks {
        void onRootTaskAppeared(ActivityManager.RunningTaskInfo taskInfo);
        void onStageVisibilityChanged(StageTaskListener stageTaskListener);
        void onStatusChanged(boolean visible, boolean hasChildren);
        void onChildTaskStatusChanged(StageTaskListener listener, int taskId,
                                      boolean present, boolean visible);
        void onRootTaskVanished(ActivityManager.RunningTaskInfo taskInfo);
        void onNoLongerSupportMultiWindow(StageTaskListener stage,
                                          ActivityManager.RunningTaskInfo taskInfo);
    }
    private final Context context;
    private boolean mIsActive;
    @SplitScreen.StageType
    private final int mId;
    boolean mVisible = false;
    boolean mHasRootTask = false;
    boolean mHasChildren = false;
    private final StageListenerCallbacks mCallbacks;
    private final SyncTransactionQueue mSyncQueue;
    protected ActivityManager.RunningTaskInfo mRootTaskInfo;
    protected SurfaceControl mRootLeash;
    protected SparseArray<ActivityManager.RunningTaskInfo> mChildrenTaskInfo = new SparseArray<>();
    private final SparseArray<SurfaceControl> mChildrenLeashes = new SparseArray<>();
    private final SplitDecorManager mSplitDecorManager = new SplitDecorManager();

    StageTaskListener(Context context, ShellTaskOrganizer taskOrganizer, int displayId,
                      StageListenerCallbacks callbacks, SyncTransactionQueue queue, int id) {
        this.context = context;
        mCallbacks = callbacks;
        mSyncQueue = queue;
        mId = id;
        taskOrganizer.createRootTask(displayId, WINDOWING_MODE_MULTI_WINDOW, this);
    }

    int getChildCount() {
        return mChildrenTaskInfo.size();
    }

    public boolean containsTask(int taskId) {
        return mChildrenTaskInfo.contains(taskId);
    }

    boolean containsToken(WindowContainerToken token) {
        return contains(t -> t.token.equals(token));
    }

    boolean containsContainer(IBinder binder) {
        return contains(t -> t.token.asBinder() == binder);
    }

    int getTopVisibleChildTaskId() {
        final ActivityManager.RunningTaskInfo taskInfo =
                getChildTaskInfo(t -> t.topActivityInfo != null);
        return taskInfo != null ? taskInfo.taskId : INVALID_TASK_ID;
    }

    boolean isFocused() {
        return contains(t -> t.isFocused);
    }

    @SplitScreen.StageType
    int getStageType() {
        return mId;
    }


    public ActivityManager.RunningTaskInfo getRunningTaskInfo() {
        return mRootTaskInfo;
    }

    private boolean contains(Predicate<ActivityManager.RunningTaskInfo> predicate) {
        if (mRootTaskInfo != null && predicate.test(mRootTaskInfo)) {
            return true;
        }
        return getChildTaskInfo(predicate) != null;
    }

    private ActivityManager.RunningTaskInfo getChildTaskInfo(
            Predicate<ActivityManager.RunningTaskInfo> predicate) {
        for (int i = mChildrenTaskInfo.size() - 1; i >= 0; i--) {
            final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.get(i);
            if (predicate.test(taskInfo)) {
                return taskInfo;
            }
        }
        return null;
    }

    List<Integer> getAllVisibleChildTaskIds() {
        return getAllChildTaskInfos(t -> t.isVisible &&
                t.isVisibleRequested && t.taskId != INVALID_TASK_ID).stream()
                .map(runningTaskInfo -> runningTaskInfo.taskId)
                .collect(Collectors.toList());
    }

    private List<ActivityManager.RunningTaskInfo> getAllChildTaskInfos(
            Predicate<ActivityManager.RunningTaskInfo> predicate) {
        List<ActivityManager.RunningTaskInfo> matchingTasks = new ArrayList<>();
        for(int i = mChildrenTaskInfo.size() - 1; i >= 0; --i) {
            final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.valueAt(i);
            if (predicate.test(taskInfo)) {
                matchingTasks.add(taskInfo);
            }
        }
        return matchingTasks;
    }

    @Override
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        if (mRootTaskInfo == null) {
            Log.i(TAG, "onTaskAppeared rootTaskInfo=" + taskInfo + " leash=" + leash);
            mRootLeash = leash;
            mRootTaskInfo = taskInfo;
            mSplitDecorManager.attachToHost(leash);
            mHasRootTask = true;
            mCallbacks.onRootTaskAppeared(taskInfo);
            if (mVisible != mRootTaskInfo.isVisible) {
                mVisible = mRootTaskInfo.isVisible;
                mCallbacks.onStageVisibilityChanged(this);
            }
            sendStatusChanged();
        } else if (taskInfo.parentTaskId == mRootTaskInfo.taskId) {
            Log.i(TAG, "Stage" + getStageType() + " onTaskAppeared childTaskInfo="
                    + taskInfo + " leash=" + leash);
            final int taskId = taskInfo.taskId;
            mChildrenTaskInfo.put(taskId, taskInfo);
            mChildrenLeashes.put(taskId, leash);
            mCallbacks.onChildTaskStatusChanged(this, taskId, true,
                    taskInfo.isVisible && taskInfo.isVisibleRequested);
            if (ENABLE_SHELL_TRANSITION) {
                return;
            }
            updateChildTaskSurface(taskInfo, leash, true);
            sendStatusChanged();
        } else {
            throw new IllegalArgumentException(this + "\n unknown task: " + taskInfo);
        }
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        if (mRootTaskInfo.taskId == taskInfo.taskId) {
            final boolean visibilityChanged = mRootTaskInfo.isVisible != taskInfo.isVisible;
            if (!ENABLE_SHELL_TRANSITION && visibilityChanged) {
                if (taskInfo.isVisible) {
                    //TODO: DecorViewManager
                } else {
                    //TODO: DecorViewManager
                }
            }
            mRootTaskInfo = taskInfo;
            if (visibilityChanged) {
                mVisible = taskInfo.isVisible;
                mCallbacks.onStageVisibilityChanged(this);
            }
        } else if (taskInfo.parentTaskId == mRootTaskInfo.taskId) {
            if (!taskInfo.supportsMultiWindow
                    || !ArrayUtils.contains(CONTROLLED_ACTIVITY_TYPES, taskInfo.getActivityType())
                    || !ArrayUtils.contains(CONTROLLED_WINDOWING_MODES_WHEN_ACTIVE,
                    taskInfo.getWindowingMode())) {
                mCallbacks.onNoLongerSupportMultiWindow(this, taskInfo);
                return;
            }
            mChildrenTaskInfo.put(taskInfo.taskId, taskInfo);
            mCallbacks.onChildTaskStatusChanged(this, taskInfo.taskId, true,
                    taskInfo.isVisible && taskInfo.isVisibleRequested);
            if (!ENABLE_SHELL_TRANSITION) {
                updateChildTaskSurface(taskInfo, mChildrenLeashes.get(taskInfo.taskId),
                        false);
            }
        } else {
            throw new IllegalArgumentException(this + "\n unknown task: " + taskInfo);
        }
        if (ENABLE_SHELL_TRANSITION) {
            return;
        }
        sendStatusChanged();
    }

    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        final int taskId = taskInfo.taskId;
        if (mRootTaskInfo.taskId == taskId) {
            mCallbacks.onRootTaskVanished(mRootTaskInfo);
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            mSplitDecorManager.release(t);
            t.apply();
            t.close();
            mRootTaskInfo = null;
            mRootLeash = null;
            mHasChildren = false;
            mVisible = false;
            //TODO Decor and Dim
        } else if (mChildrenTaskInfo.contains(taskId)) {
            mChildrenTaskInfo.remove(taskId);
            mChildrenLeashes.remove(taskId);
            mCallbacks.onChildTaskStatusChanged(this, taskId,
                    false, taskInfo.isVisible);
            if (ENABLE_SHELL_TRANSITION) {
                return;
            }
            sendStatusChanged();
        } else {
            throw new IllegalArgumentException(this + "\n unknown task: " + taskInfo);
        }
    }

    @Override
    public void attachChildSurfaceToTask(int taskId, SurfaceControl.Builder b) {
        b.setParent(findTaskSurface(taskId));
    }

    @Override
    public void reparentChildSurfaceToTask(int taskId, SurfaceControl sc,
                                           SurfaceControl.Transaction t) {
        t.reparent(sc, findTaskSurface(taskId));
    }

    private SurfaceControl findTaskSurface(int taskId) {
        if (mRootTaskInfo.taskId == taskId) {
            return mRootLeash;
        } else if (mChildrenLeashes.contains(taskId)) {
            return mChildrenLeashes.get(taskId);
        } else {
            throw new IllegalArgumentException("There is no surface for taskId=" + taskId);
        }
    }

    boolean isRootTaskId(int taskId) {
        return mRootTaskInfo != null && mRootTaskInfo.taskId == taskId;
    }

    void addTask(ActivityManager.RunningTaskInfo taskInfo, WindowContainerTransaction wct) {
        wct.setWindowingMode(taskInfo.token, WINDOWING_MODE_UNDEFINED)
                .setBounds(taskInfo.token, null);

        wct.reparent(taskInfo.token, mRootTaskInfo.token, true);
    }

    void reorderChild(int taskId, boolean onTop, WindowContainerTransaction wct) {
        if (!containsTask(taskId)) {
            return;
        }
        wct.reorder(mChildrenTaskInfo.get(taskId).token, onTop);
    }

    void doForAllChildTasks(Consumer<Integer> consumer) {
        for (int i = mChildrenTaskInfo.size() - 1; i >= 0; i--) {
            final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.valueAt(i);
            consumer.accept(taskInfo.taskId);
        }
    }

    void doForAllChildTaskInfos(Consumer<ActivityManager.RunningTaskInfo> consumer) {
        for (int i = mChildrenTaskInfo.size() - 1; i >= 0; i--) {
            final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.valueAt(i);
            consumer.accept(taskInfo);
        }
    }

    void evictAllChildren(WindowContainerTransaction wct) {
        for (int i = mChildrenTaskInfo.size() - 1; i >= 0; i--) {
            final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.valueAt(i);
            evictChild(wct, taskInfo, "all");
        }
    }

    void evictOtherChildren(WindowContainerTransaction wct, int taskId) {
        for (int i = mChildrenTaskInfo.size() - 1; i >= 0; i--) {
            final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.valueAt(i);
            if (taskInfo.taskId == taskId) {
                continue;
            }
            evictChild(wct, taskInfo, "other_" + mId);
        }
    }

    void evictNonOpeningChildren(RemoteAnimationTarget[] apps, WindowContainerTransaction wct) {
        if (apps == null || apps.length == 0) {
            return;
        }
        final SparseArray<ActivityManager.RunningTaskInfo> toBeEvict = mChildrenTaskInfo.clone();
        for(int i = 0; i < apps.length; i++) {
            if (apps[i].mode == MODE_OPENING) {
                toBeEvict.remove(apps[i].taskId);
            }
        }
        for(int i = 0; i < toBeEvict.size(); i++) {
            final ActivityManager.RunningTaskInfo taskInfo = toBeEvict.valueAt(i);
            evictChild(wct, taskInfo, "non-opening");
        }
    }

    void evictInvisibleChildren(WindowContainerTransaction wct) {
        for (int i = mChildrenTaskInfo.size() - 1; i >= 0; i--) {
            final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.valueAt(i);
            if (!taskInfo.isVisible()) {
                evictChild(wct, taskInfo, "invisible");
            }
        }
    }

    private void evictChild(WindowContainerTransaction wct,
                            int taskId,
                            String reason) {
        final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.get(taskId);
        if (taskInfo != null) {
            evictChild(wct, taskInfo, reason);
        }
    }

    private void evictChild(@NonNull WindowContainerTransaction wct,
                            @NonNull ActivityManager.RunningTaskInfo taskInfo,
                            String reason) {
        Log.d(TAG, "Evict child task=" + taskInfo + " reason=" + reason);
        taskInfo.isVisible = false;
        taskInfo.isVisibleRequested = false;
        wct.reparent(taskInfo.token, null, false);
    }

    void evictChildren(WindowContainerTransaction wct, int taskId) {
        final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.get(taskId);
        if (taskInfo != null) {
            wct.reparent(taskInfo.token, null, false);
        }
    }

    boolean isActive() {
        return mIsActive;
    }

    void activate(WindowContainerTransaction wct, boolean includingTopTask) {
        if (mIsActive) return;
        Log.i(TAG, "Activate stage " + SplitScreen.stageTypeToString(mId) +
                " includingTopTask=" + includingTopTask);
        if (includingTopTask) {
            reparentTopTask(wct);
        }

        mIsActive = true;
    }

    void deActivate(WindowContainerTransaction wct) {
        deActivate(wct, false, null);
    }

    void deActivate(WindowContainerTransaction wct, boolean reparentTasksToTop,
                    @Nullable WindowContainerToken newParent) {
        if (!mIsActive) return;
        Log.i(TAG, "deActivate stage=" + SplitScreen.stageTypeToString(mId) +
                " reparentTasksToTop=" + reparentTasksToTop);
        mIsActive = false;
        if (mRootTaskInfo == null) return;
        final WindowContainerToken rootToken = mRootTaskInfo.token;
        wct.reparentTasks(
                rootToken,
                newParent,
                null,
                null,
                reparentTasksToTop
        );
    }

    boolean removeAllTasks(WindowContainerTransaction wct, boolean toTop,
                           @Nullable WindowContainerToken newParent) {
        Log.i(TAG, "Remove all tasks in stage=" + SplitScreen.stageTypeToString(mId) +
                " toTop=" + toTop);
        if (mChildrenTaskInfo.size() == 0) return false;
        wct.reparentTasks(
                mRootTaskInfo.token,
                newParent,
                null,
                null,
                toTop
        );
        return true;
    }

    boolean removeTask(int taskId, WindowContainerToken newParent, WindowContainerTransaction wct) {
        final ActivityManager.RunningTaskInfo task = mChildrenTaskInfo.get(taskId);
        Log.i(TAG, "remove stage=" + SplitScreen.stageTypeToString(mId) + " task=" +
                taskId);
        if (task == null) return false;
        wct.reparent(task.token, newParent, false);
        return true;
    }

    void reparentTopTask(WindowContainerTransaction wct) {
        wct.reparentTasks(null, mRootTaskInfo.token, CONTROLLED_WINDOWING_MODES,
                CONTROLLED_ACTIVITY_TYPES, true, true);
    }

    void resetBounds(WindowContainerTransaction wct) {
        wct.setBounds(mRootTaskInfo.token, null);
        wct.setAppBounds(mRootTaskInfo.token, null);
        wct.setSmallestScreenWidthDp(mRootTaskInfo.token, SMALLEST_SCREEN_WIDTH_DP_UNDEFINED);
    }

    void setDecorBitmap(Bitmap bitmap) {
        mSplitDecorManager.setCustomBitmap(bitmap);
    }

    void onResizing(Rect newBounds, SurfaceControl.Transaction t) {
        if (!mHasChildren || getTopVisibleChildTaskId() == INVALID_TASK_ID) {
            return;
        }
        mSplitDecorManager.onResizing(newBounds, t);
    }

    void onResized(Rect stableBounds, SurfaceControl.Transaction t) {
        mSplitDecorManager.onResized(stableBounds, t);
    }

    void onSplitScreenListenerRegistered(SplitScreen.StageScreenListener listener,
                                         @SplitScreen.StageType int stage) {
        for(int i = mChildrenTaskInfo.size() - 1; i >= 0; i--) {
            int taskId = mChildrenTaskInfo.valueAt(i).taskId;
            listener.onTaskStageChanged(taskId, stage, mChildrenTaskInfo.get(taskId).isVisible);
        }
    }

    private void updateChildTaskSurface(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl leash, boolean firstAppeared) {
        final Point taskPositionInParent = taskInfo.positionInParent;
        mSyncQueue.runInSync(t -> {
            if (!leash.isValid()) {
                Log.w(TAG, "Skip invalid child task:" + taskInfo.taskId);
                return;
            }
            t.setCrop(leash, null);
            t.setPosition(leash, taskPositionInParent.x, taskPositionInParent.y);
            if (firstAppeared) {
                t.setAlpha(leash, 1f);
                t.setMatrix(leash, 1, 0, 0, 1);
                t.show(leash);
            }
        });
    }

    private void sendStatusChanged() {
        mCallbacks.onStatusChanged(mRootTaskInfo.isVisible, mChildrenTaskInfo.size() > 0);
    }

    public String toString() {
        return TAG + "("
                + "mId=" + SplitScreen.stageTypeToString(mId)
                + ", mVisible=" + mVisible
                + ", mActive=" + mIsActive
                + ", mHasRootTask=" + mHasRootTask
                + ", chidSize=" + mChildrenTaskInfo.size()
                + ")";
    }

    @Override
    @CallSuper
    public void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + " ";
        final String childPrefix = innerPrefix + " ";
        if (mChildrenTaskInfo.size() > 0) {
            pw.println(prefix + "Children list:");
            for(int i = mChildrenTaskInfo.size() - 1; i >= 0; --i) {
                final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.valueAt(i);
                pw.println(childPrefix + "Task#" + i + "taskId=" + taskInfo.taskId +
                        " baseActivity=" + taskInfo.baseActivity + " visible=" + taskInfo.isVisible);
            }
        }
    }
}
