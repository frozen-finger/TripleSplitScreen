package com.android.wm.shell.triplesplit.split;

import static android.app.ActivityOptions.KEY_LAUNCH_ROOT_TASK_TOKEN;
import static android.app.ComponentOptions.KEY_PENDING_INTENT_BACKGROUND_ACTIVITY_ALLOWED;
import static android.app.ComponentOptions.KEY_PENDING_INTENT_BACKGROUND_ACTIVITY_ALLOWED_BY_PERMISSION;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.content.res.Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.RemoteAnimationTarget.MODE_OPENING;
import static android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER;
import static com.android.wm.shell.triplesplit.split.SplitScreen.STAGE_TYPE_A;
import static com.android.wm.shell.triplesplit.split.SplitScreen.STAGE_TYPE_B;
import static com.android.wm.shell.triplesplit.split.SplitScreen.STAGE_TYPE_C;
import static com.android.wm.shell.triplesplit.split.SplitScreen.STAGE_TYPE_UNDEFINED;
import static com.android.wm.shell.triplesplit.split.SplitScreen.stageTypeToString;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.ANIMATING_OFFSCREEN_TAP;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.NOT_IN_SPLIT;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_100_33_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_100_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_33_100;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_33_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_33_66;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_50_50;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_66_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_66_33_2;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_50_50_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_66_33_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_END_AND_DISMISS;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_MINIMIZE;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_NONE;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_START_AND_DISMISS;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SPLIT_INDEX_1;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SPLIT_INDEX_2;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SPLIT_INDEX_3;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SPLIT_INDEX_UNDEFINED;
import static com.android.wm.shell.triplesplit.split.SplitScreenUtils.getNewParentTokenForStage;
import static com.android.wm.shell.triplesplit.split.util.SplitUtilConstants.EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW;
import static com.android.wm.shell.triplesplit.split.util.SplitUtilConstants.EXIT_REASON_APP_FINISHED;
import static com.android.wm.shell.triplesplit.split.util.SplitUtilConstants.EXIT_REASON_FULLSCREEN_REQUEST;
import static com.android.wm.shell.triplesplit.split.util.SplitUtilConstants.EXIT_REASON_RETURN_HOME;
import static com.android.wm.shell.triplesplit.split.util.SplitUtilConstants.EXIT_REASON_ROOT_TASK_VANISHED;
import static com.android.wm.shell.triplesplit.split.util.SplitUtilConstants.EXIT_REASON_UNKNOWN;
import static com.android.wm.shell.triplesplit.split.util.SplitUtilConstants.INVALID_TASK_ID;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.app.PendingIntent;
import android.app.TaskInfo;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.IntArray;
import android.util.Log;
import android.util.SparseArray;
import android.view.Choreographer;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.DisplayAreaInfo;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.logging.InstanceId;
import com.android.internal.util.ArrayUtils;
import com.android.wm.shell.RootDisplayAreaOrganizer;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.transition.LegacyTransitions;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.triplesplit.split.view.OffscreenTouchZone;
import com.android.wm.shell.triplesplit.split.view.TouchInterceptLayer;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Coordinate the staging of split screen stages
 * - {@link StageCoordinator} is only considered active when the other stages contain at least one
 * child
 * - Three {@link StageTaskListener} are under the same single-top root task.
 * TODO For modern android platforms > 12, add shell transition
 */
public class StageCoordinator extends StageCoordinatorAbstract{

    private static final String TAG = StageCoordinator.class.getSimpleName();

    private StageTaskListener stageA;
    private StageTaskListener stageB;
    private StageTaskListener stageC;
    @SplitScreenConstants.SplitIndex
    private int mStageAIndex = SPLIT_INDEX_1;
    @SplitScreenConstants.SplitIndex
    private int mStageBIndex = SPLIT_INDEX_2;
    @SplitScreenConstants.SplitIndex
    private int mStageCIndex = SPLIT_INDEX_3;
    private StageOrderOperator mStageOrderoperator;

    private final int mDisplayId;
    private SplitLayout mSplitLayout;
    private final IActivityTaskManager mActivityTaskManager;
    private boolean mLeftDividerVisible;
    private boolean mRightDividerVisible;
    private final SyncTransactionQueue mSyncQueue;
    private final ShellTaskOrganizer mTaskOrganzier;
    private final RootDisplayAreaOrganizer mRootDisplayAreaOrganizer;
    private final Context mContext;
    private final List<com.android.wm.shell.triplesplit.split.SplitScreen.StageScreenListener> mListeners =
            new ArrayList<>();
    private final Set<SplitScreen.SplitSelectListener> mSelectListeners = new HashSet<>();
    private final DisplayController mDisplayController;
    private final DisplayImeController mDisplayImeController;
    private final DisplayInsetsController mDisplayInsetsController;
    private final TransactionPool mTransactionPool;
    private ShellExecutor mMainExecutor;
    private final Handler mMainHandler;
    private final RootTaskDisplayAreaOrganizer mRootTDAOrganizer;
    private final ArrayList<Integer> mPausingTasks = new ArrayList<>();
    private final SplitState mSplitState;
    private final Rect mTempRect1 = new Rect();
    private final Rect mTempRect2 = new Rect();
    private final Rect mTempRect3 = new Rect();

    private boolean mExitSplitScreenOnHide;
    private boolean mIsDividerRemoteAnimating;
    private boolean mIsExiting;
    private boolean mIsRootTranslucent;
    private @com.android.wm.shell.triplesplit.split.SplitScreen.StageType int mLastActiveStage;

    private SplitRequest mSplitRequest;

    private SplitMultiDisplayHelper mSplitMultiDisplayHelper;

    /**
     * Default RemoteAnimation
     */
    private final IRemoteAnimationRunner defaultAnimationRunner = new IRemoteAnimationRunner.Stub()
    {
        @Override
        public void onAnimationStart(int i, RemoteAnimationTarget[] apps,
                                     RemoteAnimationTarget[] wallpapers,
                                     RemoteAnimationTarget[] nonApps,
                                     IRemoteAnimationFinishedCallback finishedCallback)
                throws RemoteException {
            Log.d(TAG, "Remote Animation Start" + Arrays.stream(apps).
                    filter(app -> app.mode == MODE_OPENING)
                    .collect(Collectors.toList()));
            if (finishedCallback != null) {
                finishedCallback.onAnimationFinished();
            }
        }

        @Override
        public void onAnimationCancelled() throws RemoteException {
        }

        @Override
        public IBinder asBinder() {
            return null;
        }
    };

    private final RemoteAnimationAdapter defaultAnimationAdapter = new RemoteAnimationAdapter(
                    defaultAnimationRunner, 200, 0);

    @Override
    public boolean supportCompatUI() {
        return false;
    }
//    Listener for split screen animating in
//    public void registerSplitAnimationListener() {}

    @Override
    public WindowContainerToken getDisplayRootForDisplayId(int displayId) {
        ActivityManager.RunningTaskInfo rootTaskInfo =
                mSplitMultiDisplayHelper.getDisplayRootTaskInfo(displayId);
        return rootTaskInfo != null ? rootTaskInfo.token : null;
    }

    @Override
    public void prepareMovingSplitScreenRoot(WindowContainerTransaction wct, int displayId) {
        // Here we assume multi-display  split is not enabled. If multi-display is enabled, every
        // display will have its own root task for split screen, thus we don't need to move one to
        // the other.

        ActivityManager.RunningTaskInfo currentRootTaskInfo =
                mSplitMultiDisplayHelper.getCachedOrSystemDisplayIds().stream()
                        .map(id -> mSplitMultiDisplayHelper.getDisplayRootTaskInfo(id))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);

        if (currentRootTaskInfo == null) {
            throw new IllegalStateException("Failed to find current split screen root");
        }

        if (displayId != currentRootTaskInfo.displayId) {
            try {
                Field field = RootDisplayAreaOrganizer.class.getDeclaredField("mDisplayAreasInfo");
                field.setAccessible(true);
                SparseArray<DisplayAreaInfo> infos =
                        (SparseArray<DisplayAreaInfo>) field.get(mRootDisplayAreaOrganizer);
                final DisplayAreaInfo targetDisplayAreaInfo = infos.get(displayId);
                if (targetDisplayAreaInfo != null) {
                    wct.reparent(currentRootTaskInfo.token, targetDisplayAreaInfo.token, true);
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public boolean startAnimation(IBinder iBinder, TransitionInfo transitionInfo,
                                  SurfaceControl.Transaction transaction,
                                  SurfaceControl.Transaction transaction1,
                                  Transitions.TransitionFinishCallback transitionFinishCallback) {
        return false;
    }

    @Override
    public WindowContainerTransaction handleRequest(IBinder iBinder,
                                                    TransitionRequestInfo transitionRequestInfo) {
        return null;
    }


    class SplitRequest {
        @SplitScreenConstants.SplitIndex
        int mActivatePosition;
        int mActivateTaskId1;
        int mActivateTaskId2;
        int mActivateTaskId3;
        Intent mStartIntent1;
        Intent mStartIntent2;
        Intent mStartIntent3;

        @SplitScreenConstants.PersistentSnapPosition
        int mSnapPosition;

        SplitRequest(int taskId, Intent startIntent, int position) {
            mActivateTaskId1 = taskId;
            mStartIntent1 = startIntent;
            mActivatePosition = position;
        }

        SplitRequest(Intent startIntent, int position) {
            mStartIntent1 = startIntent;
            mActivatePosition = position;
        }

        SplitRequest(Intent intent1, Intent intent2, Intent intent3,
                    @SplitScreenConstants.PersistentSnapPosition int position) {
            mStartIntent1 = intent1;
            mStartIntent2 = intent2;
            mStartIntent3 = intent3;
            mSnapPosition = position;
        }

        SplitRequest(Intent intent1, Intent intent2,
                     @SplitScreenConstants.PersistentSnapPosition int position) {
            mStartIntent1 = intent1;
            mStartIntent2 = intent2;
            mSnapPosition = position;
        }

        SplitRequest(int taskId, int position) {
            mActivateTaskId1 = taskId;
            mActivatePosition = position;
        }

        SplitRequest(int taskId1, int taskId2, int taskId3,
                     @SplitScreenConstants.PersistentSnapPosition int position) {
            mActivateTaskId1 = taskId1;
            mActivateTaskId2 = taskId2;
            mActivateTaskId3 = taskId3;
            mSnapPosition = position;
        }
    }

    private final SplitWindowManager.ParentContainerCallbacks mParentContainerCallbacks =
            new SplitWindowManager.ParentContainerCallbacks() {
                @Override
                public void attachToParentSurface(SurfaceControl.Builder b) {
                    b.setParent(mSplitMultiDisplayHelper.getDisplayRootTaskLeash(DEFAULT_DISPLAY));
                }

                @Override
                public void onLeashReady(SurfaceControl leash) {
                    if (isAnyDividerVisible()) {
                        mSyncQueue.runInSync(t ->
                        {
                            applyDividerVisibility(1, t);
                            applyDividerVisibility(2, t);
                        });
                    }
                }

                @Override
                public void inflateOnStageRoot(OffscreenTouchZone offscreenTouchZone) {
                    offscreenTouchZone.getBounds(mTempRect1);
                    Log.i(TAG, "Inflate touch zone index=" + offscreenTouchZone.getIndex()
                            + " bounds=" + mTempRect1);
                    SurfaceControl displayRootLeash =
                            mSplitMultiDisplayHelper.getDisplayRootTaskLeash(DEFAULT_DISPLAY);
                    ActivityManager.RunningTaskInfo rootTaskInfo =
                            mSplitMultiDisplayHelper.getDisplayRootTaskInfo(DEFAULT_DISPLAY);
                    if (displayRootLeash == null || rootTaskInfo == null) {
                        Log.w(TAG, "Skip inflating touch zone because display root is not ready");
                        return;
                    }
                    offscreenTouchZone.inflate(mContext, displayRootLeash, rootTaskInfo);
                }

                @Override
                public void onSplitLayoutAnimating(boolean animating) {
                    notifySplitAnimationStatus(animating);
                }
            };

    protected StageCoordinator(Context context, int displayId, SyncTransactionQueue syncQueue,
                               ShellTaskOrganizer taskOrganizer, DisplayController displayController,
                               DisplayImeController displayImeController,
                               DisplayInsetsController displayInsetsController,
                               TransactionPool transactionPool, ShellExecutor mainExecutor,
                               Handler mainHandler, SplitState splitState,
                               RootTaskDisplayAreaOrganizer rootTDAOrganizer,
                               RootDisplayAreaOrganizer rootDisplayAreaOrganizer,
                               IActivityTaskManager activityTaskManager) {

        mContext = context;
        mDisplayId = displayId;
        mSyncQueue = syncQueue;
        mTaskOrganzier = taskOrganizer;
        mRootDisplayAreaOrganizer = rootDisplayAreaOrganizer;
        mMainExecutor = mainExecutor;
        mMainHandler = mainHandler;
        mSplitState = splitState;
        mRootTDAOrganizer = rootTDAOrganizer;

        DisplayManager displayManager = context.getSystemService(DisplayManager.class);

        mSplitMultiDisplayHelper = new SplitMultiDisplayHelper(
                Objects.requireNonNull(displayManager));

        // assume not enable multi display split

        taskOrganizer.createRootTask(displayId, WINDOWING_MODE_FULLSCREEN, this);

        stageA = new StageTaskListener(
                mContext,
                mTaskOrganzier,
                mDisplayId,
                this,
                mSyncQueue,
                STAGE_TYPE_A
        );
        stageB = new StageTaskListener(
                mContext,
                mTaskOrganzier,
                mDisplayId,
                this,
                mSyncQueue,
                STAGE_TYPE_B
        );
        stageC = new StageTaskListener(
                mContext,
                mTaskOrganzier,
                mDisplayId,
                this,
                mSyncQueue,
                STAGE_TYPE_C
        );

        Log.d(TAG, "create left, middle and right stage");
        mStageOrderoperator = new StageOrderOperator(mContext, mTaskOrganzier, mDisplayId,
                this, mSyncQueue, stageA, stageB, stageC);

        mDisplayController = displayController;
        mDisplayImeController = displayImeController;
        mDisplayInsetsController = displayInsetsController;
        mTransactionPool = transactionPool;
        mDisplayController.addDisplayWindowListener(this);
        mDisplayController.addDisplayChangingController(this);
        mActivityTaskManager = activityTaskManager;

    }

    @VisibleForTesting
    StageCoordinator(Context context, int displayId, SyncTransactionQueue syncQueue,
                     ShellTaskOrganizer taskOrganizer, StageTaskListener stageA,
                     StageTaskListener stageB, StageTaskListener stageC,
                     DisplayController displayController, DisplayImeController displayImeController,
                     DisplayInsetsController displayInsetsController, SplitLayout splitLayout,
                     TransactionPool transactionPool, ShellExecutor mainExecutor,
                     Handler mainHandler, SplitState splitState,
                     RootTaskDisplayAreaOrganizer rootTDAOrganizer,
                     RootDisplayAreaOrganizer rootDisplayAreaOrganizer,
                     IActivityTaskManager activityTaskManager) {
        mContext = context;
        mDisplayId = displayId;
        mSyncQueue = syncQueue;
        mTaskOrganzier = taskOrganizer;
        this.stageA = stageA;
        this.stageB = stageB;
        this.stageC = stageC;
        mDisplayController = displayController;
        mDisplayImeController = displayImeController;
        mDisplayInsetsController = displayInsetsController;
        mTransactionPool = transactionPool;
        mSplitLayout = splitLayout;
        mActivityTaskManager = activityTaskManager;
        mMainExecutor = mainExecutor;
        mMainHandler = mainHandler;
        mSplitState = splitState;
        mRootTDAOrganizer = rootTDAOrganizer;
        mDisplayController.addDisplayWindowListener(this);
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        mSplitMultiDisplayHelper = new SplitMultiDisplayHelper(
                Objects.requireNonNull(displayManager));
        mRootDisplayAreaOrganizer = rootDisplayAreaOrganizer;
        Log.d(TAG, "create left, middle and right stage");
        mStageOrderoperator = new StageOrderOperator(mContext, mTaskOrganzier, mDisplayId,
                this, mSyncQueue, stageA, stageB, stageC);
    }

    protected StageCoordinator(Context context, int displayId, SyncTransactionQueue syncQueue,
                               ShellTaskOrganizer shellTaskOrganizer, DisplayController displayController,
                               DisplayImeController displayImeController,
                               DisplayInsetsController displayInsetsController,
                               SplitState splitState,
                               TransactionPool transactionPool, ShellExecutor mainExecutor) {
        mContext = context;
        mDisplayId = displayId;
        mSyncQueue = syncQueue;
        mTaskOrganzier = shellTaskOrganizer;
        mMainExecutor = mainExecutor;
        mSplitState = splitState;
        mActivityTaskManager = IActivityTaskManager.Stub.asInterface(ServiceManager.getService("activity_task"));
        mRootDisplayAreaOrganizer = context.getSystemService(RootDisplayAreaOrganizer.class);
        mRootTDAOrganizer = context.getSystemService(RootTaskDisplayAreaOrganizer.class);
        mMainHandler = new Handler(Looper.getMainLooper());
        Log.i(TAG, "Create root task");
        mTaskOrganzier.createRootTask(mDisplayId, WINDOWING_MODE_FULLSCREEN, this);

        stageA = new StageTaskListener(
                mContext,
                mTaskOrganzier,
                mDisplayId,
                this,
                mSyncQueue,
                STAGE_TYPE_A
        );
        stageB = new StageTaskListener(
                mContext,
                mTaskOrganzier,
                mDisplayId,
                this,
                mSyncQueue,
                STAGE_TYPE_B
        );
        stageC = new StageTaskListener(
                mContext,
                mTaskOrganzier,
                mDisplayId,
                this,
                mSyncQueue,
                STAGE_TYPE_C
        );
        mDisplayController = displayController;
        mDisplayImeController = displayImeController;
        mDisplayInsetsController = displayInsetsController;
        mTransactionPool = transactionPool;
        mDisplayController.addDisplayWindowListener(this);
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        mSplitMultiDisplayHelper = new SplitMultiDisplayHelper(
                Objects.requireNonNull(displayManager));
        Log.d(TAG, "create left, middle and right stage");
        mStageOrderoperator = new StageOrderOperator(mContext, mTaskOrganzier, mDisplayId,
                this, mSyncQueue, stageA, stageB, stageC);
    }

    public boolean isSplitScreenVisible() {
        return runForActiveStagesAllMatch((stage) -> stage.mVisible);
    }

    /**
     * Host activity became visible. Restore the existing split root if the current coordinator still
     * has a valid split; otherwise do nothing and let the debug entry point decide how to start.
     */
    public void onHostActivityVisible() {
        restoreSplitToFrontIfValid("host_activity_visible");
    }

    /**
     * Bring the existing split root task back to the foreground without launching the apps again.
     *
     * This preserves the current stage tasks and divider state when the host activity is resumed
     * from launcher/recents. Re-running startIntents() would create a new transition and reset the
     * layout to the requested snap position.
     */
    private boolean restoreSplitToFrontIfValid(String reason) {
        if (!canRestoreSplitToFront(reason)) {
            return false;
        }
        if (isSplitScreenVisible()) {
            return true;
        }

        final ActivityManager.RunningTaskInfo rootTaskInfo = mSplitMultiDisplayHelper
                .getDisplayRootTaskInfo(DEFAULT_DISPLAY);
        final SurfaceControl rootLeash = mSplitMultiDisplayHelper
                .getDisplayRootTaskLeash(DEFAULT_DISPLAY);

        Log.i(TAG, "restoreSplitToFront reason=" + reason + " splitState=" + mSplitState.get());
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        updateWindowBounds(mSplitLayout, wct);
        wct.reorder(rootTaskInfo.token, true);
        setRootForceTranslucent(false, wct);
        mSyncQueue.queue(wct);
        mSyncQueue.runInSync(t -> {
            ensureDividerWindowManager();
            setDividerVisibility(1, true, t);
            setDividerVisibility(2, true, t);
            updateSurfaceBounds(mSplitLayout, t, false /* applyResizingOffset */);
            t.show(rootLeash);
        });
        setSplitsVisible(true);
        return true;
    }

    private boolean canRestoreSplitToFront(String reason) {
        if (!isRestorableSplitState(mSplitState.get())) {
            Log.w(TAG, "skip restoreSplitToFront, invalid splitState=" + mSplitState.get()
                    + " reason=" + reason);
            return false;
        }
        final ActivityManager.RunningTaskInfo rootTaskInfo = mSplitMultiDisplayHelper
                .getDisplayRootTaskInfo(DEFAULT_DISPLAY);
        final SurfaceControl rootLeash = mSplitMultiDisplayHelper
                .getDisplayRootTaskLeash(DEFAULT_DISPLAY);
        if (rootTaskInfo == null || rootLeash == null || mSplitLayout == null) {
            Log.w(TAG, "skip restoreSplitToFront, rootTask=" + rootTaskInfo
                    + " rootLeash=" + rootLeash + " layout=" + mSplitLayout
                    + " reason=" + reason);
            return false;
        }
        final boolean stageRootsReady = mStageOrderoperator.getAllStages().size() == 3
                && mStageOrderoperator.getAllStages().stream()
                .allMatch(stage -> stage.mRootTaskInfo != null);
        final boolean hasSplitTasks = mStageOrderoperator.getAllStages().stream()
                .anyMatch(stage -> stage.getChildCount() > 0);
        if (!stageRootsReady || !hasSplitTasks) {
            Log.w(TAG, "skip restoreSplitToFront, stage roots/tasks are not ready reason=" + reason
                    + " activeCount=" + mStageOrderoperator.getActiveStages().size()
                    + " allStages=" + mStageOrderoperator.getAllStages());
            return false;
        }
        if (!isSplitActive()) {
            Log.i(TAG, "restoreSplitToFront reactivating stage order reason=" + reason
                    + " splitState=" + mSplitState.get());
            mStageOrderoperator.onEnteringSplit(mSplitState.get());
        }
        return true;
    }

    private boolean isRestorableSplitState(int splitState) {
        switch (splitState) {
            case SNAP_TO_3_33_33_100:
            case SNAP_TO_3_33_33_66:
            case SNAP_TO_3_33_50_50:
            case SNAP_TO_3_33_66_33:
            case SNAP_TO_3_33_100_33:
            case SNAP_TO_3_33_33_33:
            case SNAP_TO_3_33_66_33_2:
            case SNAP_TO_3_50_50_33:
            case SNAP_TO_3_66_33_33:
            case SNAP_TO_3_100_33_33:
                return true;
            default:
                return false;
        }
    }

    private void activateSplit(WindowContainerTransaction wct, boolean includingTopTask,
                               int index) {
        mStageOrderoperator.onEnteringSplit(SNAP_TO_3_33_33_33);
        if (index == SPLIT_INDEX_UNDEFINED || !includingTopTask) {
            return;
        }
        @SplitScreenConstants.SplitIndex int middleIndex = SPLIT_INDEX_2;
        StageTaskListener activatingStage = mStageOrderoperator.getStageForIndex(middleIndex);
        activatingStage.activate(wct, includingTopTask);
    }

    public boolean isSplitActive() {
        return mStageOrderoperator.isActive();
    }

    /**
     * Deactivates one stage by moving the stage from the top level split root.
     * This function should be called as part of exiting split screen
     * @param stageToTop which stage we want to put on top
     */
    private void deactivateSplit(WindowContainerTransaction wct,
                                 @com.android.wm.shell.triplesplit.split.SplitScreen.StageType int stageToTop) {
        StageTaskListener stageToDeactivate = getStageForStageType(stageToTop);
        if (stageToDeactivate != null) {
            stageToDeactivate.deActivate(wct, true, getNewParentTokenForStage(
                    stageToDeactivate, mRootTDAOrganizer
            ));
        } else {
            mStageOrderoperator.getAllStages().forEach(stage ->
                    stage.deActivate(wct, false, null));
        }
        mStageOrderoperator.onExitingSplit();
    }

    @com.android.wm.shell.triplesplit.split.SplitScreen.StageType
    int getStageOfTask(int taskId) {
        StageTaskListener stageTaskListener = mStageOrderoperator.getActiveStages().stream()
                .filter(stage -> stage.containsTask(taskId))
                .findFirst().orElse(null);
        if (stageTaskListener != null) {
            return stageTaskListener.getStageType();
        }
        return STAGE_TYPE_UNDEFINED;
    }

    boolean isRootOrStageRoot(int taskId) {
        ArrayList<Integer> displayIds = mSplitMultiDisplayHelper.getCachedOrSystemDisplayIds();
        for (int displayId: displayIds) {
            ActivityManager.RunningTaskInfo rootTaskInfo = mSplitMultiDisplayHelper
                    .getDisplayRootTaskInfo(displayId);
            if (rootTaskInfo != null && rootTaskInfo.taskId == taskId) {
                return true;
            }
        }

        return mStageOrderoperator.getActiveStages().stream()
                .anyMatch((stage) -> stage.isRootTaskId(taskId));
    }

    boolean moveToStage(ActivityManager.RunningTaskInfo task, @SplitScreenConstants.SplitIndex int
                        index, WindowContainerTransaction wct) {
        Log.d(TAG, "Move to stage task=" + task + " index=" + index);
        prepareEnterSplitScreen(wct, index, task,false, SPLIT_INDEX_UNDEFINED);

        mSyncQueue.queue(wct);
        mSyncQueue.runInSync(this::updateSurfaces);

        return true;
    }

    void requestEnterSplitSelect(ActivityManager.RunningTaskInfo taskInfo, int splitIndex,
                                 Rect taskBounds) {
        for (com.android.wm.shell.triplesplit.split.SplitScreen.SplitSelectListener listener: mSelectListeners) {
            listener.onRequestEnterSplitSelect(taskInfo, splitIndex, taskBounds
                    , false, null);
        }
    }

    /**
     * Use to launch an existing task via a task id.
     */
    void startTask(int taskId, @Nullable Bundle options,
                   @Nullable WindowContainerToken hideTaskToken, @SplitScreenConstants.SplitIndex
                   int index) {
        Log.d(TAG, "startTask taskId=" + taskId + " index=" + index);
        if (index == SPLIT_INDEX_UNDEFINED || taskId == INVALID_TASK_ID) {
            Log.w(TAG, "Skip startTask with invalid taskId=" + taskId + " index=" + index);
            return;
        }
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        if (hideTaskToken != null) {
            Log.d(TAG, "Reordering hide-task to bottom");
            wct.reorder(hideTaskToken, false);
        }
        final int snapPosition = isSplitActive()
                ? resolveEffectiveSnapPosition()
                : SNAP_TO_3_33_33_33;
        mSplitRequest = new SplitRequest(taskId, index);
        startWithLegacyTransition(wct, taskId, options, snapPosition, index,
                defaultAnimationAdapter);
    }

    void startIntent(PendingIntent intent, Intent fillinIntent,
                     @Nullable Bundle options, @Nullable WindowContainerToken hideTaskToken,
                     @Nullable WindowContainerTransaction transaction,
                     @com.android.wm.shell.triplesplit.split.SplitScreenConstants.SplitIndex int
                             index, int displayId) {
        Log.d(TAG, "startIntent intent=" + intent + " index=" + index + " displayId=" + displayId);
        if (intent == null || index == SPLIT_INDEX_UNDEFINED) {
            Log.w(TAG, "Skip startIntent with invalid intent=" + intent + " index=" + index);
            return;
        }
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        if (transaction != null) {
            wct.merge(transaction, true);
        }
        if (hideTaskToken != null) {
            Log.d(TAG, "Reordering hide-task to bottom");
            wct.reorder(hideTaskToken, false);
        }
        final int snapPosition = isSplitActive()
                ? resolveEffectiveSnapPosition()
                : SNAP_TO_3_33_33_33;
        mSplitRequest = new SplitRequest(intent.getIntent(), index);
        startWithLegacyTransition(wct, intent, fillinIntent, options, snapPosition, index,
                defaultAnimationAdapter);
    }

    void startIntentLegacy(PendingIntent intent, Intent fillIntent,
                           @SplitScreenConstants.SplitIndex int index, @Nullable Bundle options) {
        final boolean isEnteringSplit = !isSplitActive();

        LegacyTransitions.ILegacyTransition transition = new LegacyTransitions.ILegacyTransition() {
            @Override
            public void onAnimationStart(int i, RemoteAnimationTarget[] apps,
                                         RemoteAnimationTarget[] wallpapers,
                                         RemoteAnimationTarget[] nonApps,
                                         IRemoteAnimationFinishedCallback finishedCallback,
                                         SurfaceControl.Transaction transaction) {
                List<StageTaskListener> hasChild = Stream.of(stageA, stageB, stageC)
                        .filter(stage -> stage.getChildCount() != 0).collect(Collectors.toList());
                if (hasChild.size() <= 1) {
                    mMainExecutor.execute(() -> exitSplitScreen(null, EXIT_REASON_UNKNOWN));
                    Log.w(TAG, "startIntent legacy failed, only one stage is populated");
                }

                if (apps != null) {
                    for (int j = 0; j < apps.length; j ++) {
                        if (apps[j].mode == MODE_OPENING) {
                            transaction.show(apps[j].leash);
                        }
                    }
                }
                transaction.apply();

                if (finishedCallback != null) {
                    try {
                        finishedCallback.onAnimationFinished();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Error finishing", e);
                    }
                }

                if (!isEnteringSplit && apps != null) {
                    final WindowContainerTransaction evictWct = new WindowContainerTransaction();
                    prepareEvictNonOpeningChildTasks(index, apps, evictWct);
                    mSyncQueue.queue(evictWct);
                }
            }
        };

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        options = resolveStartStage(STAGE_TYPE_UNDEFINED, options, index, wct);

        if (isEnteringSplit) {
            updateWindowBounds(mSplitLayout, wct);
        }
        wct.sendPendingIntent(intent, fillIntent, options);
        mSyncQueue.queue(transition, WindowManager.TRANSIT_OPEN, wct);
    }

    void startTasks(int taskId1, @Nullable Bundle options1, int taskId2, @Nullable
                    Bundle options2, @SplitScreenConstants.SnapPosition int position) {
    }

    /**
     * Start Tasks via taskId with a legacy transition. index1, index2, index3 should be different
     * and between 1 <-> 3.
     * TODO: using shell-transition instead
     * @param taskId1 leftStage task
     * @param taskId2 middleStage task
     * @param taskId3 rightStage task
     */
    void startTasks(int taskId1, @Nullable Bundle options1, int taskId2, @Nullable Bundle options2,
                    int taskId3, @Nullable Bundle options3,
                    @SplitScreenConstants.SplitIndex int index1,
                    @SplitScreenConstants.SplitIndex int index2,
                    @SplitScreenConstants.SplitIndex int index3,
                    @SplitScreenConstants.PersistentSnapPosition int snapPosition,
                    RemoteAnimationAdapter adapter
                    ) {
        Log.i(TAG, "Start Tasks task1=" + taskId1 + " task2=" + taskId2 +
                " taskId3=" + taskId3);
        final WindowContainerTransaction wct = new WindowContainerTransaction();

        if (taskId2 == INVALID_TASK_ID && taskId3 == INVALID_TASK_ID) {
            startSingleTask(taskId1, options1, wct);
        }

        if (mStageOrderoperator.getActiveStages().size() == 1
                ||!isSplitScreenVisible()) {
            setDividerVisibility(1, false, null);
            setDividerVisibility(2, false, null);
        }
        StageTaskListener stageForTask1 = mStageOrderoperator.getStageForLegacyPosition(index1,
                true /* checkAllStagesIfNotActive */);
        StageTaskListener stageForTask2 = mStageOrderoperator.getStageForLegacyPosition(index2,
                true /* checkAllStagesIfNotActive */);
        addActivityOptions(options1, stageForTask1);
        addActivityOptions(options2, stageForTask2);
        wct.startTask(taskId1, options1);
        wct.startTask(taskId2, options2);
        mSplitRequest = new SplitRequest(taskId1, taskId2, taskId3, snapPosition);
        startWithLegacyTransition(wct, taskId3, options3, snapPosition, index3, adapter);
    }

    /**
     * Launch three Intents together. At least one pending intent should be nonNull, since we are
     * launching something. Index should be different.
     * @param pendingIntent1
     * @param pendingIntent2
     * @param pendingIntent3
     */
    void startIntents(@NonNull PendingIntent pendingIntent1, Intent fillInIntent1,
                      @Nullable Bundle options1, @Nullable PendingIntent pendingIntent2,
                      Intent fillInIntent2, @Nullable Bundle options2,
                      @Nullable PendingIntent pendingIntent3, Intent fillInIntent3,
                      @Nullable Bundle options3,
                      @SplitScreenConstants.SplitIndex int index1,
                      @SplitScreenConstants.SplitIndex int index2,
                      @SplitScreenConstants.SplitIndex int index3,
                      @SplitScreenConstants.PersistentSnapPosition int snapPosition,
                      RemoteAnimationAdapter adapter) {
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        if (options1 == null) options1 = new Bundle();
        if (options2 == null) options2 = new Bundle();
        if (pendingIntent2 == null && pendingIntent3 == null) {
            launchAsFullscreenWithRemoteAnimation(pendingIntent1, fillInIntent1, options1, adapter,
                    wct);
            return;
        }

        addActivityOptions(options1, mStageOrderoperator.getStageForLegacyPosition(index1,
                true /* checkAllStagesIfNotActive */));
        Log.i(TAG, "get Stage=" + mStageOrderoperator.getStageForLegacyPosition(index1,
                true /* checkAllStagesIfNotActive */).getStageType());

        wct.sendPendingIntent(pendingIntent1, fillInIntent1, options1);

        if (pendingIntent2 == null || pendingIntent3 == null) {
            PendingIntent nonNullPending = pendingIntent2 != null ? pendingIntent2 : pendingIntent3;
            int nonNullIndex = pendingIntent2 != null ? index2 : index3;
            Intent nonNullIntent = pendingIntent2 != null ? fillInIntent2 : fillInIntent3;
            Bundle nonNullOpts = pendingIntent2 != null ? options2 : options3;
            mSplitRequest = new SplitRequest(pendingIntent1.getIntent(), nonNullPending.getIntent(),
                    snapPosition);
            startWithLegacyTransition(wct, nonNullPending, nonNullIntent, nonNullOpts, snapPosition,
                    nonNullIndex, adapter);
        } else  {
            addActivityOptions(options2, mStageOrderoperator.getStageForLegacyPosition(index2,
                    true /* checkAllStagesIfNotActive */));
            Log.i(TAG, "get Stage=" + mStageOrderoperator.getStageForLegacyPosition(index2,
                    true /* checkAllStagesIfNotActive */).getStageType());
            wct.sendPendingIntent(pendingIntent2, fillInIntent2, options2);
            mSplitRequest = new SplitRequest(pendingIntent1.getIntent(),
                    pendingIntent2.getIntent(), pendingIntent3.getIntent(), snapPosition);
            startWithLegacyTransition(wct, pendingIntent3, fillInIntent3, options3, snapPosition,
                    index3, adapter);
        }
    }


    /**
     * Start a fullscreen task, removing it from exiting pairs if it exists.
     */
    private void startSingleTask(int taskId, Bundle options, WindowContainerTransaction wct) {
        if (mStageOrderoperator.getAllStages().stream()
                .anyMatch(stage -> stage.containsTask(taskId))) {
            applyExitSplitScreen(mStageOrderoperator.getAllStages().stream()
                    .filter(stage -> stage.containsTask(taskId))
                            .findFirst().orElse(null),
                    wct, EXIT_REASON_FULLSCREEN_REQUEST);
//            prepareExitSplitScreen(STAGE_TYPE_UNDEFINED, wct, EXIT_REASON_FULLSCREEN_REQUEST);
        }

        options = options == null ? new Bundle() : options;
        addActivityOptions(options, null, WINDOWING_MODE_FULLSCREEN);

        Bundle[] outOptions = new Bundle[]{options};
        ActivityManager.RunningTaskInfo runningTaskInfo = mTaskOrganzier.getRunningTaskInfo(taskId);

        wct.startTask(taskId, outOptions[0]);
    }

    /**
     * Only play resize animation when dropping a 10% stage on the screen.
     */
    private boolean shouldPlayResizeAnimation(@SplitScreenConstants.SplitIndex int position) {
        return false;
    }

    private @SplitScreenConstants.PersistentSnapPosition int resolveEffectiveSnapPosition() {
        return mSplitLayout.calculateCurrentSnapPosition();
    }

    @Override
    public void setExcludeImeInsets(boolean exclude) {
//        final WindowContainerTransaction wct = new WindowContainerTransaction();
//        ActivityManager.RunningTaskInfo rootTaskInfo =
//                mSplitMultiDisplayHelper.getDisplayRootTaskInfo(DEFAULT_DISPLAY);
//        if (rootTaskInfo == null) {
//            Log.e(TAG, "setExcludeImeInsets rootTaskInfo is null");
//            return;
//        }
//        Log.d(TAG, "setExcludedImeInsets: root taskId=" + rootTaskInfo.taskId + "exclude=" +
//                exclude);
//        wct.setExcludeImeInsets(rootTaskInfo.token, exclude);
//        mTaskOrganzier.applySyncTransaction(wct, null);
    }

    void prepareEvictNonOpeningChildTasks(@SplitScreenConstants.SplitIndex int index,
                                          RemoteAnimationTarget[] apps,
                                          WindowContainerTransaction wct) {
        mStageOrderoperator.getStageForLegacyPosition(index).evictNonOpeningChildren(apps, wct);
    }

    void prepareEvictInvisibleChildTasks(WindowContainerTransaction wct) {
        for (StageTaskListener stage: mStageOrderoperator.getAllStages()) {
            stage.evictInvisibleChildren(wct);
        }
    }

    Bundle resolveStartStageForIndex(@Nullable Bundle options,
                                     @Nullable WindowContainerTransaction wct,
                                     @SplitScreenConstants.SplitIndex int index) {
        StageTaskListener oppositeStage;
        if (index == SPLIT_INDEX_UNDEFINED) {
            oppositeStage = mStageOrderoperator.getStageForIndex(SPLIT_INDEX_1);
        } else {
            oppositeStage = mStageOrderoperator.getStageForIndex(index);
        }

        if (options == null) {
            options = new Bundle();
        }

        updateStageWindowBoundsForIndex(wct, index);
        addActivityOptions(options, oppositeStage);
        return options;
    }

    @Override
    Bundle resolveStartStage(@SplitScreen.StageType int stage,
                             @Nullable Bundle options,
                             @SplitScreenConstants.SplitIndex int index,
                             @Nullable WindowContainerTransaction wct) {
        switch (stage) {
            case STAGE_TYPE_UNDEFINED:
                if (index != SPLIT_INDEX_UNDEFINED) {
                    if (isSplitScreenVisible()) {
                        options = resolveStartStage(
                                index == mStageAIndex ? stageA.getStageType() :
                                        (index == mStageBIndex ? stageB.getStageType()
                                                : stageC.getStageType()),
                                options, index, wct);
                    } else {
                        options = resolveStartStage(mStageAIndex == SPLIT_INDEX_2
                                ? stageA.getStageType() : (mStageBIndex == SPLIT_INDEX_2 ?
                                stageB.getStageType() : stageC.getStageType()),
                                options, index, wct);
                    }
                } else {
                    Log.w(TAG, "No stage type nor split position specified to resolve start" +
                            " stage");
                }
                break;
            case STAGE_TYPE_A:
                if (index != SPLIT_INDEX_UNDEFINED) {
                    setStageAIndex(index, wct);
                } else {
                    index = getStageAIndex();
                }
                if (options == null) options = new Bundle();
                updateActivityOptions(options, index);
                break;
            case STAGE_TYPE_B:
                if (index != SPLIT_INDEX_UNDEFINED) {
                    setStageBIndex(index, wct);
                } else {
                    index = getStageBIndex();
                }
                if (options == null) options = new Bundle();
                updateActivityOptions(options, index);
                break;
            case STAGE_TYPE_C:
                if (index != SPLIT_INDEX_UNDEFINED) {
                    setStageCIndex(index, wct);
                } else {
                    index = getStageCIndex();
                }
                if (options == null) options = new Bundle();
                updateActivityOptions(options, index);
                break;
            default:
                throw new IllegalArgumentException("Unknown stage=" + stage);
        }
        return options;
    }

    @SplitScreenConstants.SplitIndex
    int getStageAIndex() {
        return mStageAIndex;
    }

    @SplitScreenConstants.SplitIndex
    int getStageBIndex() {
        return mStageBIndex;
    }

    @SplitScreenConstants.SplitIndex
    int getStageCIndex() {
        return mStageCIndex;
    }

    void setStageAIndex(@SplitScreenConstants.SplitIndex int index,
                        WindowContainerTransaction wct) {
        if (mStageAIndex == index) return;
        mStageAIndex = index;
        sendOnStageIndexChanged();
        StageTaskListener stage = mStageOrderoperator
                .getStageForLegacyPosition(mStageAIndex, true);

        if (stage.mVisible) {
            if (wct == null) {
                onLayoutSizeChanged(mSplitLayout);
            } else {
                updateWindowBounds(mSplitLayout, wct);
                sendOnBoundsChanged();
            }
        }
    }

    void setStageBIndex(@SplitScreenConstants.SplitIndex int index,
                        WindowContainerTransaction wct) {
        if (mStageBIndex == index) return;
        mStageBIndex = index;
        sendOnStageIndexChanged();
        StageTaskListener stage = mStageOrderoperator
                .getStageForLegacyPosition(mStageBIndex, true);

        if (stage.mVisible) {
            if (wct == null) {
                onLayoutSizeChanged(mSplitLayout);
            } else {
                updateWindowBounds(mSplitLayout, wct);
                sendOnBoundsChanged();
            }
        }
    }

    void setStageCIndex(@SplitScreenConstants.SplitIndex int index,
                        WindowContainerTransaction wct) {
        if (mStageCIndex == index) return;
        mStageCIndex = index;
        sendOnStageIndexChanged();
        StageTaskListener stage = mStageOrderoperator
                .getStageForLegacyPosition(mStageCIndex, true);

        if (stage.mVisible) {
            if (wct == null) {
                onLayoutSizeChanged(mSplitLayout);
            } else {
                updateWindowBounds(mSplitLayout, wct);
                sendOnBoundsChanged();
            }
        }
    }

    int getTaskId(@SplitScreenConstants.SplitIndex int index) {
        if (index == SPLIT_INDEX_UNDEFINED) {
            return INVALID_TASK_ID;
        }

        StageTaskListener stage = mStageOrderoperator.getStageForLegacyPosition(index, true);
        return stage != null ? stage.getTopVisibleChildTaskId() : INVALID_TASK_ID;
    }


    private void launchAsFullscreenWithRemoteAnimation(@Nullable PendingIntent pendingIntent,
                                                       @Nullable Intent fillIntent,
                                                       @Nullable Bundle options,
                                                       RemoteAnimationAdapter adapter,
                                                       WindowContainerTransaction wct) {
        LegacyTransitions.ILegacyTransition transition =
                (transit, apps,
                 wallpapers,
                 nonApps,
                 finishedCallback, t) -> {
                    if (apps == null || apps.length == 0) {
                        onRemoteAnimationFinished(apps, true /* evictNonOpeningChildren */);
                        t.apply();
                        try {
                            adapter.getRunner().onAnimationCancelled();
                        } catch (RemoteException e) {
                            Log.e(TAG, "Error starting remote animation", e);
                        }
                        return;
                    }

                    for (RemoteAnimationTarget app : apps) {
                        if (app.mode == MODE_OPENING) {
                            t.show(app.leash);
                        }
                    }
                    t.apply();

                    try {
                        adapter.getRunner().onAnimationStart(transit, apps, wallpapers,
                                nonApps, finishedCallback);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Error starting remote animation", e);
                    }
                };
        addActivityOptions(options, null);
        if (pendingIntent != null) {
            wct.sendPendingIntent(pendingIntent, fillIntent, options);
        } else {
            Log.e(TAG, "Pending Intent is invalid");
        }

        mSyncQueue.queue(transition, WindowManager.TRANSIT_OPEN, wct);
    }

    void startWithLegacyTransition(WindowContainerTransaction wct, int taskId,
                                   @Nullable Bundle options,
                                   @SplitScreenConstants.PersistentSnapPosition int snapPosition,
                                   @SplitScreenConstants.SplitIndex int index,
                                   RemoteAnimationAdapter adapter) {
        startWithLegacyTransition(wct, taskId, options, null, null,
                snapPosition, index, adapter);
    }

    void startWithLegacyTransition(WindowContainerTransaction wct, PendingIntent pendingIntent,
                                   Intent fillInIntent, @Nullable Bundle options,
                                   @SplitScreenConstants.PersistentSnapPosition int snapPosition,
                                   @SplitScreenConstants.SplitIndex int index,
                                   RemoteAnimationAdapter adapter) {
        startWithLegacyTransition(wct, INVALID_TASK_ID, options, pendingIntent, fillInIntent,
                snapPosition, index, adapter);
    }

    private void startWithLegacyTransition(WindowContainerTransaction wct, int taskId,
                                      @Nullable Bundle options,
                                      @Nullable PendingIntent mainPendingIntent,
                                      @Nullable Intent mainFillIntent,
                                      @SplitScreenConstants.PersistentSnapPosition int snapPosition,
                                      @SplitScreenConstants.SplitIndex int index,
                                      RemoteAnimationAdapter adapter) {
        Log.i(TAG, "startWithLegacyTransition intent=" + mainPendingIntent);
        if (options == null) options = new Bundle();
        activateSplit(wct, true, SPLIT_INDEX_UNDEFINED);

        StageTaskListener targetStage = mStageOrderoperator.getStageForLegacyPosition(index);
        Log.i(TAG, "startWithLegacyTransition target stage=" + targetStage.getStageType());

        mSplitLayout.init();
        mSplitLayout.setDivideRatio(snapPosition);

        SurfaceControl.Transaction startT = mTransactionPool.acquire();
        updateSurfaces(startT);
        startT.apply();
        mTransactionPool.release(startT);

        mIsDividerRemoteAnimating = true;
        if (mSplitRequest == null) {
            mSplitRequest = new SplitRequest(taskId, mainPendingIntent != null ?
                    mainPendingIntent.getIntent() : null, snapPosition);
        }

        setStagePosition(targetStage, index, wct);
        List<StageTaskListener> inActiveStages = getOtherStages(targetStage.getStageType());
        Log.i(TAG, "startWithLegacyTransition activeStages = " + inActiveStages.stream());

        for (StageTaskListener listener: inActiveStages) {
            if (!listener.isActive()) {
                listener.activate(wct, false);
            }
        }

        ActivityManager.RunningTaskInfo rootTaskInfo = mSplitMultiDisplayHelper.
                getDisplayRootTaskInfo(DEFAULT_DISPLAY);

        addActivityOptions(options, targetStage);

        updateWindowBounds(mSplitLayout, wct);
        wct.reorder(rootTaskInfo.token, true);
        setRootForceTranslucent(false, wct);

        if (taskId != INVALID_TASK_ID) {
            options = wrapSplitRemoteAnimation(adapter, options);
            wct.startTask(taskId, options);
            mSyncQueue.queue(wct);
        } else {
            wct.sendPendingIntent(mainPendingIntent, mainFillIntent, options);
            mSyncQueue.queue(wrapAsSplitRemoteAnimation(adapter), WindowManager.TRANSIT_OPEN, wct);
        }
    }

    public void setStagePosition(StageTaskListener targetStage,
                                  int stageIndex,
                                  WindowContainerTransaction wct) {
        int currentIndex = getIndexForStageType(targetStage.getStageType());
        if (stageIndex == currentIndex) return;
        switch (targetStage.getStageType()) {
            case STAGE_TYPE_A:
                mStageAIndex = stageIndex;
                break;
            case STAGE_TYPE_B:
                mStageBIndex = stageIndex;
                break;
            case STAGE_TYPE_C:
                mStageCIndex = stageIndex;
                break;
        }

        if (targetStage.mVisible) {
            if (wct == null) {
                onLayoutSizeChanged(mSplitLayout);
            } else {
                updateWindowBounds(mSplitLayout, wct);
                sendOnBoundsChanged();
            }
        }
    }

    public void onDoubleTappedDivider(int id) {
        if (id == 1) {
            switchSplitPosition(SPLIT_INDEX_1, SPLIT_INDEX_2, "double tap");
        } else {
            switchSplitPosition(SPLIT_INDEX_2, SPLIT_INDEX_3, "double tap");
        }
    }

    void switchSplitPosition(int index1, int index2, String reason) {
        final SurfaceControl.Transaction t = mTransactionPool.acquire();
        mTempRect1.setEmpty();

        final TouchInterceptLayer touchInterceptLayer = new TouchInterceptLayer("double tap");
        final SurfaceControl displayRootLeash =
                mSplitMultiDisplayHelper.getDisplayRootTaskLeash(DEFAULT_DISPLAY);
        final ActivityManager.RunningTaskInfo displayRootTaskInfo =
                mSplitMultiDisplayHelper.getDisplayRootTaskInfo(DEFAULT_DISPLAY);
        touchInterceptLayer.inflate(
                mContext, displayRootLeash, displayRootTaskInfo, mSplitLayout.getRootBounds());

        mSplitLayout.removeTouchZones();
        notifySplitAnimationStatus(true);

        final StageTaskListener left = mStageOrderoperator.getStageForIndex(SPLIT_INDEX_1);
        final StageTaskListener middle = mStageOrderoperator.getStageForIndex(SPLIT_INDEX_2);
        final StageTaskListener right = mStageOrderoperator.getStageForIndex(SPLIT_INDEX_3);

        mSplitLayout.playSwapAnimation(t, index1, index2, left, middle, right, insets -> {
            mStageOrderoperator.onDoubleTappedDivider(Math.min(index1, index2));
            WindowContainerTransaction wct = new WindowContainerTransaction();

            int leftIndex = getStageAIndex();
            int middleIndex = getStageBIndex();
            int rightIndex = getStageCIndex();

            if (leftIndex == index1) {
                leftIndex = index2;
            } else if (leftIndex == index2) {
                leftIndex = index1;
            }

            if (middleIndex == index1) {
                middleIndex = index2;
            } else if (middleIndex == index2) {
                middleIndex = index1;
            }

            if (rightIndex == index1) {
                rightIndex = index2;
            } else if (rightIndex == index2) {
                rightIndex = index1;
            }

            setStageAIndex(leftIndex, wct);
            setStageBIndex(middleIndex, wct);
            setStageCIndex(rightIndex, wct);

            mSyncQueue.queue(wct);
            mSyncQueue.runInSync(st -> {
                mSplitLayout.updateStateWithCurrentPosition();
                updateSurfaceBounds(mSplitLayout, st, false);
                mSplitLayout.populateTouchZones();
                notifySplitAnimationStatus(false);
                touchInterceptLayer.release();

                if (mSplitState.currentStateHasOffscreenApps()) {
                    grantFocusToPosition(splitStateToSnapPosition(mSplitState.get()));
                }
            });
        });

        Log.v(TAG, "switch split position=" + reason);
    }

    private int splitStateToSnapPosition(int i) {
        switch(i) {
            case NOT_IN_SPLIT:
            case SNAP_TO_NONE:
            case ANIMATING_OFFSCREEN_TAP: // user tapped offscreen app to retrieve it
            case SNAP_TO_START_AND_DISMISS:
            case SNAP_TO_END_AND_DISMISS:
            case SNAP_TO_MINIMIZE:
                    return SPLIT_INDEX_UNDEFINED;
            case SNAP_TO_3_33_33_100:
            case SNAP_TO_3_33_33_66:
            case SNAP_TO_3_33_50_50:
                    return SPLIT_INDEX_3;
            case SNAP_TO_3_33_66_33:
            case SNAP_TO_3_33_100_33:
            case SNAP_TO_3_33_33_33:
            case SNAP_TO_3_33_66_33_2:
            case SNAP_TO_3_50_50_33:
                    return SPLIT_INDEX_2;
            case SNAP_TO_3_66_33_33:
            case SNAP_TO_3_100_33_33:
                    return SPLIT_INDEX_1;
        }
        return SPLIT_INDEX_UNDEFINED;
    }

    @Override
    void prepareEnterSplitScreen(WindowContainerTransaction wct, int stage,
                                 @Nullable ActivityManager.RunningTaskInfo taskInfo,
                                 boolean resizeAnim,
                                 @SplitScreenConstants.SplitIndex int index) {
        Log.d(TAG, "prepareEnterSplitScreen index=" + index + "resize=" + resizeAnim);
        onSplitScreenEnter();
        int displayId = taskInfo != null ? taskInfo.taskId : DEFAULT_DISPLAY;
        wct.setReparentLeafTaskIfRelaunch(mSplitMultiDisplayHelper.getDisplayRootTaskInfo(displayId)
                .token, false);
        if (!isSplitActive()) {
            prepareBringSplit(wct, taskInfo, index, resizeAnim);
        } else {
            prepareActiveSplit(wct, taskInfo, resizeAnim, index);
        }
    }

    @Override
    void finishEnterSplitScreen(SurfaceControl.Transaction transaction) {
        Log.d(TAG, "finsih enter split screen");
        mSplitLayout.updateStateWithCurrentPosition();
        ensureDividerWindowManager();
        runForActiveStages((stage) -> {
            // decor manager op
        });
        setDividerVisibility(1, true, transaction);
        setDividerVisibility(2, true, transaction);
        transaction.reparent(mSplitLayout.getLeftDividerLeash(),
                mSplitMultiDisplayHelper.getDisplayRootTaskLeash(DEFAULT_DISPLAY));
        transaction.reparent(mSplitLayout.getRightDividerLeash(),
                mSplitMultiDisplayHelper.getDisplayRootTaskLeash(DEFAULT_DISPLAY));
        mStageOrderoperator.getActiveStages().forEach(stage -> {
            // dim layer op
        });

        updateSurfaceBounds(mSplitLayout, transaction, false);
        transaction.show(mSplitMultiDisplayHelper.getDisplayRootTaskLeash(DEFAULT_DISPLAY));
        setSplitsVisible(true);
        mSplitRequest = null;
    }

    private void updateStageWindowBoundsForIndex(@Nullable WindowContainerTransaction wct,
                                                 @SplitScreenConstants.SplitIndex int index) {
        StageTaskListener stage = mStageOrderoperator.getStageForIndex(index);
        if (stage.mVisible) {
            if (wct == null) {
                onLayoutSizeChanged(mSplitLayout);
            } else {
                updateWindowBounds(mSplitLayout, wct);
                sendOnBoundsChanged();
            }
        }
    }

    void recordLastActiveStage() {
        if (!isSplitActive() || !isSplitScreenVisible()) {
            mLastActiveStage = STAGE_TYPE_UNDEFINED;
        } else {
            mStageOrderoperator.getActiveStages().stream().filter(StageTaskListener::isFocused)
                    .findFirst()
                    .ifPresent(stage -> mLastActiveStage = stage.getStageType());
        }
    }

    /**
     * Dismiss split screen and keep the focused app alive
     */
    void dismissSplitKeepingLastActiveStage(int exitReason) {
        if (mLastActiveStage == STAGE_TYPE_UNDEFINED) {
            return;
        }

        dismissSplit(mLastActiveStage, exitReason);
    }

    /**
     * Dismiss split screen in background
     */
    public void dismissSplitInBackground(int exitReason) {
        dismissSplit(STAGE_TYPE_UNDEFINED, exitReason);
    }

    void dismissSplit(@SplitScreen.StageType int stageToTop, int exitReason) {
        if (!isSplitActive()) {
            return;
        }
        Log.d(TAG, "dismiss split: stageToTop=" + stageToTop + ", reason=" + exitReason);

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        //TODO: replace with real shell transition
        applyExitSplitScreen(getStageForStageType(stageToTop), wct, exitReason);
    }

    void exitSplitScreenOnHide(boolean exitSplitScreenOnHide) {
        mExitSplitScreenOnHide = exitSplitScreenOnHide;
    }

    @Override
    void onStartedWakingUp() {

    }

    @Override
    void onStartedGoingToSleep() {

    }

    @Override
    void onDroppedToSplit(int index, InstanceId dragSessionId) {

    }

    /**
     * Legacy Transition for exiting split screen.
     * TODO: replace it with shell transition.
     */
    private void exitSplitScreen(@Nullable StageTaskListener stage, int exitReason) {
        Log.d(TAG, "exit split screen stageToTop=" + stage + " reason=" + exitReason);
        if (!isSplitActive()) return;

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        applyExitSplitScreen(stage, wct, exitReason);
    }

    void dismissSplitScreen(int toTopTaskId, int exitReason) {
        if (!isSplitActive()) return;
        final int stageType = getStageOfTask(toTopTaskId);
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        //TODO: replace with shell transition
        StageTaskListener stage = mStageOrderoperator.getAllStages().stream()
                .filter(s -> s.getStageType() == stageType)
                .findFirst().orElse(null);
        applyExitSplitScreen(stage, wct, exitReason);
    }

    protected void onSplitScreenEnter() {}

    protected void onSplitScreenExit() {}

    protected void exitStage(@SplitScreenConstants.SplitIndex int stageToClose) {
        Log.d(TAG, "exitStage: stageToClose=" + stageToClose);
        if (stageToClose == SPLIT_INDEX_1) {
            mSplitLayout.flingDividerToDismiss(1, false,
                    EXIT_REASON_APP_FINISHED);
        } else if (stageToClose == SPLIT_INDEX_2) {
            mSplitLayout.flingDividerToDismiss(1, true, EXIT_REASON_APP_FINISHED);
        } else {
            mSplitLayout.flingDividerToDismiss(2, true, EXIT_REASON_APP_FINISHED);
        }
    }

    //TODO: check every split index use against stage type

    protected void grantFocusToStage(@SplitScreen.StageType int stageToFocus) {
        int index = getIndexForStageType(stageToFocus);
        grantFocusToPosition(index);
    }

    protected void grantFocusToPosition(@SplitScreenConstants.SplitIndex int stageToFocus) {
        try {
            mActivityTaskManager.setFocusedTask(getTaskId(stageToFocus));
        } catch (RemoteException | NullPointerException e) {
            Log.e(TAG, "Unable to grant focus for index=" + stageToFocus);
        }
    }

    @SplitScreenConstants.SplitIndex
    private int getIndexForStageType(@SplitScreen.StageType int type) {
        List<StageTaskListener> stages = mStageOrderoperator.getAllStages();
        int index = -1;
        if (type == STAGE_TYPE_A) {
            index = stages.indexOf(stageA) + 1;
        } else if (type == STAGE_TYPE_B) {
            index = stages.indexOf(stageB) + 1;
        } else if (type == STAGE_TYPE_C) {
            index = stages.indexOf(stageC) + 1;
        }

        return index;
    }


    /**
     * This is intended to be used for shell transition
     * @param stageToTop The stage to move to the top
     */
    void prepareExitSplitScreen(@SplitScreen.StageType int stageToTop,
                                @NonNull WindowContainerTransaction wct, int exitReason) {
        if (!isSplitActive()) return;
        Log.d(TAG, "prepareExitSplitScreen, stageToTop=" + stageToTop
                + ", reason=" + exitReason);
        mStageOrderoperator.getActiveStages().stream()
                .filter(stage -> stage.getStageType() != stageToTop)
                .forEach(stage -> stage.removeAllTasks(wct, false,
                        getNewParentTokenForStage(stage, mRootTDAOrganizer)));

        StageTaskListener toTopStage = mStageOrderoperator.getAllStages().stream()
                .filter(stage -> stage.getStageType() == stageToTop)
                .findFirst().orElse(null);
        final int targetWindowingMode = WINDOWING_MODE_UNDEFINED;
        toTopStage.doForAllChildTaskInfos(taskInfo -> {
            wct.setWindowingMode(taskInfo.token, targetWindowingMode);
        });

        ActivityManager.RunningTaskInfo rootTaskInfo = mSplitMultiDisplayHelper
                .getDisplayRootTaskInfo(DEFAULT_DISPLAY);
        if (rootTaskInfo.displayId != DEFAULT_DISPLAY) {
            DisplayAreaInfo displayAreaInfo = mRootTDAOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY);
            if (displayAreaInfo != null) {
                wct.reparent(rootTaskInfo.token, displayAreaInfo.token, false);
            }
        }
        deactivateSplit(wct, stageToTop);
        mSplitState.exit();
    }

    void setSplitsVisible(boolean visible) {
        runForActiveStages(stage -> {
            stage.mVisible = visible;
            stage.mHasChildren = visible;
        });

        sendSplitVisibilityChanged(visible);
    }

    private void runForActiveStages(Consumer<StageTaskListener> activeStages) {
        for (StageTaskListener stage : mStageOrderoperator.getActiveStages()) {
            activeStages.accept(stage);
        }
    }

    /**
     * Predefined RemoteAnimationAdapter for different split screen animation
     */
    private LegacyTransitions.ILegacyTransition wrapAsSplitRemoteAnimation(
            @Nullable RemoteAnimationAdapter adapter) {

        final RemoteAnimationAdapter tempAdapter = adapter == null? defaultAnimationAdapter:adapter;
        final boolean isEnteringSplit = !isSplitScreenVisible();

        return (transit, apps, wallpapers, nonApps, finishedCallback, t) -> {
            if (apps == null || apps.length == 0) {
                onRemoteAnimationFinished(apps, !isEnteringSplit);
                t.apply();
                try {
                    tempAdapter.getRunner().onAnimationCancelled();
                } catch (RemoteException e) {
                    Log.e(TAG, "Error starting remote animation", e);
                }
                return;
            }

            nonApps = ArrayUtils.appendElement(RemoteAnimationTarget.class,
                    nonApps, getDividerBarLegacyTarget(1));
            nonApps = ArrayUtils.appendElement(RemoteAnimationTarget.class,
                    nonApps, getDividerBarLegacyTarget(2));

            for (int i = 0; i < apps.length; i++) {
                if (apps[i].mode == MODE_OPENING) {
                    t.show(apps[i].leash);
                    t.setPosition(apps[i].leash, 0, 0);
                }
            }

            setDividerVisibility(1, true, t);
            setDividerVisibility(2, true, t);
            t.apply();

            IRemoteAnimationFinishedCallback wrapCallback =
                    new IRemoteAnimationFinishedCallback.Stub() {
                @Override
                public void onAnimationFinished() throws RemoteException {
                    onRemoteAnimationFinished(apps, !isEnteringSplit);
                    finishedCallback.onAnimationFinished();
                }

                @Override
                public IBinder asBinder() {
                    return null;
                }
            };
            try {
                tempAdapter.getRunner().onAnimationStart(transit, apps, wallpapers, nonApps,
                        wrapCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "Error starting remote animation", e);
            }
        };
    }

    private RemoteAnimationTarget getDividerBarLegacyTarget(int id) {
        final Rect bounds = mSplitLayout.getDividerBounds(id == 1);
        return new RemoteAnimationTarget(-1, -1,
                id == 1 ? mSplitLayout.getLeftDividerLeash() : mSplitLayout.getRightDividerLeash(),
                false, null, null, Integer.MAX_VALUE,
                new android.graphics.Point(0, 0),
                bounds, bounds, new WindowConfiguration(), true,
                null, null, null, false,
                TYPE_DOCK_DIVIDER);
    }

    private Bundle wrapSplitRemoteAnimation(RemoteAnimationAdapter adapter, Bundle options) {
        final WindowContainerTransaction evictWct = new WindowContainerTransaction();
        if (isSplitScreenVisible()) {
            stageA.evictAllChildren(evictWct);
            stageB.evictAllChildren(evictWct);
            stageC.evictAllChildren(evictWct);
        }

        final RemoteAnimationAdapter tempAdapter = adapter == null? defaultAnimationAdapter:adapter;

        IRemoteAnimationRunner wrapper = new IRemoteAnimationRunner.Stub() {
            @Override
            public void onAnimationStart(int i, RemoteAnimationTarget[] apps,
                                         RemoteAnimationTarget[] wallpapers,
                                         RemoteAnimationTarget[] nonApps,
                                         IRemoteAnimationFinishedCallback finishedCallback)
                    throws RemoteException {
                IRemoteAnimationFinishedCallback wrapCallback =
                        new IRemoteAnimationFinishedCallback.Stub() {
                    @Override
                    public void onAnimationFinished() throws RemoteException {
                        onRemoteAnimationFinishedOrCancelled(evictWct);
                        finishedCallback.onAnimationFinished();
                    }
                };
                try {
                    nonApps = ArrayUtils.appendElement(RemoteAnimationTarget.class,
                            nonApps, getDividerBarLegacyTarget(1));
                    nonApps = ArrayUtils.appendElement(RemoteAnimationTarget.class,
                            nonApps, getDividerBarLegacyTarget(2));
                    tempAdapter.getRunner().onAnimationStart(i, apps, wallpapers, nonApps,
                            wrapCallback);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error starting remote animation", e);
                }
            }

            @Override
            public void onAnimationCancelled() throws RemoteException {
                onRemoteAnimationFinishedOrCancelled(evictWct);
                setDividerVisibility(1, true, null);
                setDividerVisibility(2, true, null);
                try {
                    tempAdapter.getRunner().onAnimationCancelled();
                } catch (RemoteException e) {
                    Log.e(TAG, "Error starting remote animation", e);
                }
            }

            @Override
            public IBinder asBinder() {
                return null;
            }
        };

        RemoteAnimationAdapter wrappedAdapter = new RemoteAnimationAdapter(wrapper,
                tempAdapter.getDuration(), tempAdapter.getStatusBarTransitionDelay());
        ActivityOptions opts = ActivityOptions.fromBundle(options);
        opts.update(ActivityOptions.makeRemoteAnimation(wrappedAdapter));
        return opts.toBundle();
    }

    private void onRemoteAnimationFinishedOrCancelled(WindowContainerTransaction evictWct) {
        mIsDividerRemoteAnimating = false;
        clearRequestIfPresented();

        List<StageTaskListener> hasChild = Stream.of(stageA, stageB, stageC).
                filter(stage -> stage.getChildCount() != 0).collect(Collectors.toList());
        if (hasChild.size() == 1) {
            mMainExecutor.execute(() -> {
                exitSplitScreen(hasChild.get(0), EXIT_REASON_UNKNOWN);
            });
            Log.w(TAG, "onRemoteAnimationFinishedOrCancelled, only one stage populated");
        } else {
            mSyncQueue.queue(evictWct);
            mSyncQueue.runInSync(t -> {
                updateSurfaces(t);
                applyDividerVisibility(1, t);
                applyDividerVisibility(2, t);
            });
        }
    }

    private void clearRequestIfPresented() {
        Log.d(TAG, "clear Requested if presented");
        if (Stream.of(stageA, stageB, stageC).allMatch(stage ->
                stage.mVisible && stage.mHasChildren)) {
            mSplitRequest = null;
        }
    }

    private void onRemoteAnimationFinished(RemoteAnimationTarget[] apps,
                                           boolean evictNonOpeningChildren) {
        mIsDividerRemoteAnimating = false;
        clearRequestIfPresented();
        List<StageTaskListener> hasChild = mStageOrderoperator.getActiveStages().stream().
                filter(stage -> stage.getChildCount() != 0).collect(Collectors.toList());
        if (hasChild.size() == 1) {
            mMainExecutor.execute(() -> {
                exitSplitScreen(hasChild.get(0), EXIT_REASON_UNKNOWN);
            });
            Log.w(TAG, "onRemoteAnimationFinishedOrCancelled, only one stage populated");
            return;
        }

        if (!evictNonOpeningChildren) {
            mMainExecutor.execute(() -> {
                mSyncQueue.runInSync(t -> {
                    updateSurfaces(t);
                    applyDividerVisibility(1, t);
                    applyDividerVisibility(2, t);
                });
            });
            return;
        }

        final WindowContainerTransaction evictWct = new WindowContainerTransaction();
        stageA.evictNonOpeningChildren(apps, evictWct);
        stageB.evictNonOpeningChildren(apps, evictWct);
        stageC.evictNonOpeningChildren(apps, evictWct);
        mSyncQueue.queue(evictWct);
        mMainExecutor.execute(() -> {
            mSyncQueue.runInSync(t -> {
                updateSurfaces(t);
                applyDividerVisibility(1, t);
                applyDividerVisibility(2, t);
            });
        });
    }

    void exitSplitScreen(int toTopTaskId, int exitReason) {
        if (mStageOrderoperator.getActiveStages().size() == 1) return;

        StageTaskListener childToTop = null;
        if (stageA.containsTask(toTopTaskId)) {
            childToTop = stageA;
        } else if (stageB.containsTask(toTopTaskId)) {
            childToTop = stageB;
        } else if (stageC.containsTask(toTopTaskId)) {
            childToTop = stageC;
        }
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        if (childToTop != null) {
            childToTop.reorderChild(toTopTaskId, true, wct);
        }

        applyExitSplitScreen(childToTop, wct, exitReason);
    }

    /**
     * Exit split screen associated with legacy transition. If childToTop is null, the middle stage
     * will become the top.
     * @param childToTop
     * @param wct
     * @param exitReason
     */
    private void applyExitSplitScreen(@Nullable StageTaskListener childToTop,
                                      WindowContainerTransaction wct,
                                      int exitReason) {
        if (mStageOrderoperator.getActiveStages().size() == 1 || mIsExiting) return;

        onSplitScreenExit();
        mSplitState.exit();

        mIsDividerRemoteAnimating = false;
        mSplitRequest = null;

        ActivityManager.RunningTaskInfo rootTaskInfo = mSplitMultiDisplayHelper.
                getDisplayRootTaskInfo(DEFAULT_DISPLAY);

        mSplitLayout.getLeftInvisibleBound(mTempRect1);
        mSplitLayout.getRightInvisibleBound(mTempRect2);
        if (childToTop == null || childToTop.getTopVisibleChildTaskId() == INVALID_TASK_ID) {
            stageA.removeAllTasks(wct, false, null);
            stageC.removeAllTasks(wct, false, null);
            stageB.deActivate(wct);
            wct.reorder(rootTaskInfo.token, false);
            setRootForceTranslucent(true, wct);
            wct.setBounds(stageA.mRootTaskInfo.token, mTempRect1);
            wct.setBounds(stageC.mRootTaskInfo.token, mTempRect2);
//            onTransitionAnimationComplete();
        } else {
            mIsExiting = true;
            childToTop.resetBounds(wct);
            wct.reorder(childToTop.mRootTaskInfo.token, true);
        }

        wct.setReparentLeafTaskIfRelaunch(rootTaskInfo.token, false);
        mSyncQueue.queue(wct);
        mSyncQueue.runInSync(t -> {
            t.setWindowCrop(stageA.mRootLeash, null)
                    .setWindowCrop(stageB.mRootLeash, null)
                    .setWindowCrop(stageC.mRootLeash, null);
            setDividerVisibility(1, false, t);
            setDividerVisibility(2, false, t);

            if (childToTop == null) {
                t.setPosition(stageA.mRootLeash, mTempRect1.left, mTempRect1.top);
                t.setPosition(stageC.mRootLeash, mTempRect2.left, mTempRect2.top);
            } else {
                WindowContainerTransaction finishedWCT = new WindowContainerTransaction();
                mIsExiting = false;
                deactivateSplit(finishedWCT, childToTop.getStageType());
                stageA.removeAllTasks(finishedWCT, childToTop == stageA,
                        getNewParentTokenForStage(stageA, mRootTDAOrganizer));
                stageC.removeAllTasks(finishedWCT, childToTop == stageC,
                        getNewParentTokenForStage(stageC, mRootTDAOrganizer));
                finishedWCT.reorder(rootTaskInfo.token, false);
                setRootForceTranslucent(true, finishedWCT);
                finishedWCT.setBounds(stageA.mRootTaskInfo.token, mTempRect1);
                finishedWCT.setBounds(stageC.mRootTaskInfo.token, mTempRect2);
                mSyncQueue.queue(finishedWCT);
                mSyncQueue.runInSync(transaction -> {
                    transaction.setPosition(stageA.mRootLeash, mTempRect1.left, mTempRect1.top);
                    transaction.setPosition(stageC.mRootLeash, mTempRect2.left, mTempRect2.top);
                });
//                onTransitionAnimationComplete();
            }
        });

        Log.d(TAG, "Exit split screen childToTop=" + childToTop + " exitReason=" + exitReason);
    }

    private void prepareBringSplit(WindowContainerTransaction wct,
                                   @Nullable ActivityManager.RunningTaskInfo taskInfo,
                                   @SplitScreenConstants.SplitIndex int startIndex,
                                   boolean resizeAnim) {
        boolean taskInfoNotNull = taskInfo != null;
        Log.d(TAG, "prepare bring split task=" + taskInfo + " startIndex=" + startIndex);
        if (taskInfoNotNull) {
            wct.startTask(taskInfo.taskId,
                    resolveStartStage(STAGE_TYPE_UNDEFINED, null, startIndex, wct));
        }

        if (!isSplitScreenVisible()) {
            runForActiveStages(stage -> stage.reparentTopTask(wct));
        }
        prepareSplitLayout(wct, resizeAnim);
    }

    private void prepareActiveSplit(WindowContainerTransaction wct,
                                    ActivityManager.RunningTaskInfo taskInfo,
                                    boolean resizeAnim,
                                    @SplitScreenConstants.SplitIndex int index) {
        boolean taskInfoNotNull = taskInfo != null;
        Log.d(TAG, "prepareActiveSplit taskInfo=" + taskInfo + " splitindex=" + index
                + " resizeAnim=" + resizeAnim + " isSplitVisible=" + isSplitScreenVisible());
        setSplitsVisible(false);
        if (taskInfoNotNull) {
            setStageBIndex(index, wct);
            stageB.addTask(taskInfo, wct);
        }
        activateSplit(wct, true, index);
        prepareSplitLayout(wct, resizeAnim);
    }

    /**
     * Split begins with 33 - 100 - 33
     * @param wct
     * @param resizeAnim
     */
    private void prepareSplitLayout(WindowContainerTransaction wct, boolean resizeAnim) {
        Log.d(TAG, "prepareSplitLayout: resize=" + resizeAnim);
        if (resizeAnim) {
            mSplitLayout.setDividerAtBorder();
        } else {
            mSplitLayout.resetDividerPosition();
        }
        updateWindowBounds(mSplitLayout, wct);
        if (resizeAnim) {
            // Reset its smallest width dp to avoid is change layout before it actually resized to
            // split bounds.
            wct.setSmallestScreenWidthDp(stageA.mRootTaskInfo.token,
                    SMALLEST_SCREEN_WIDTH_DP_UNDEFINED);
            wct.setSmallestScreenWidthDp(stageB.mRootTaskInfo.token,
                    SMALLEST_SCREEN_WIDTH_DP_UNDEFINED);
            mSplitLayout.getLeftInvisibleBound(mTempRect1);
            mSplitLayout.getRightInvisibleBound(mTempRect2);
            mSplitLayout.setTaskBounds(wct, stageA.mRootTaskInfo, mTempRect1);
            mSplitLayout.setTaskBounds(wct, stageC.mRootTaskInfo, mTempRect2);
        }

        wct.reorder(mSplitMultiDisplayHelper.getDisplayRootTaskInfo(DEFAULT_DISPLAY).token, true);
        setRootForceTranslucent(false, wct);
    }

    void finishEnterTransition(SurfaceControl.Transaction t) {
        mSplitLayout.updateStateWithCurrentPosition();
        ensureDividerWindowManager();

        setDividerVisibility(1, true, t);
        setDividerVisibility(2, true, t);
        t.reparent(mSplitLayout.getLeftDividerLeash(),
                mSplitMultiDisplayHelper.getDisplayRootTaskLeash(DEFAULT_DISPLAY));
        t.reparent(mSplitLayout.getRightDividerLeash(),
                mSplitMultiDisplayHelper.getDisplayRootTaskLeash(DEFAULT_DISPLAY));

        updateSurfaceBounds(mSplitLayout, t, false);
        t.show(mSplitMultiDisplayHelper.getDisplayRootTaskLeash(DEFAULT_DISPLAY));
        setSplitsVisible(true);
        mSplitRequest = null;
    }

    Rect getStageABounds() {
        return mStageAIndex == SPLIT_INDEX_1 ? mSplitLayout.getLeftBounds()
                : (mStageAIndex == SPLIT_INDEX_2 ? mSplitLayout.getMiddleBounds()
                : mSplitLayout.getRightBounds());
    }

    Rect getStageBBounds() {
        return mStageBIndex == SPLIT_INDEX_1 ? mSplitLayout.getLeftBounds()
                : (mStageBIndex == SPLIT_INDEX_2 ? mSplitLayout.getMiddleBounds()
                : mSplitLayout.getRightBounds());
    }

    Rect getStageCBounds() {
        return mStageCIndex == SPLIT_INDEX_1 ? mSplitLayout.getLeftBounds()
                : (mStageCIndex == SPLIT_INDEX_2 ? mSplitLayout.getMiddleBounds()
                : mSplitLayout.getRightBounds());
    }

    StageTaskListener getLeftStage() {
        return mStageAIndex == SPLIT_INDEX_1 ? stageA : (mStageBIndex == SPLIT_INDEX_1 ?
                stageB : stageC);
    }

    StageTaskListener getMiddleStage() {
        return mStageAIndex == SPLIT_INDEX_2 ? stageA : (mStageBIndex == SPLIT_INDEX_2 ?
                stageB : stageC);
    }

    StageTaskListener getRightStage() {
        return mStageAIndex == SPLIT_INDEX_3 ? stageA : (mStageBIndex == SPLIT_INDEX_3 ?
                stageB : stageC);
    }

    void getStageBounds(Rect stageABounds, Rect stageBBounds, Rect stageCBounds) {
        stageABounds.set(mSplitLayout.getLeftBounds());
        stageBBounds.set(mSplitLayout.getMiddleBounds());
        stageCBounds.set(mSplitLayout.getRightBounds());
    }

    void getRefStageBounds(Rect stageABounds, Rect stageBBounds, Rect stageCBounds) {
        stageABounds.set(mSplitLayout.getLeftRefBounds());
        stageBBounds.set(mSplitLayout.getMiddleRefBounds());
        stageCBounds.set(mSplitLayout.getRightRefBounds());
    }

    @Override
    int getSplitIndex(int taskId) {
        if (stageA.containsTask(taskId)) {
            return mStageAIndex;
        } else if (stageB.containsTask(taskId)) {
            return mStageBIndex;
        } else if (stageC.containsTask(taskId)) {
            return mStageCIndex;
        } else {
            return SPLIT_INDEX_UNDEFINED;
        }
    }

    private boolean runForActiveStagesAllMatch(Predicate<StageTaskListener> predicate) {
        List<StageTaskListener> activeStages = mStageOrderoperator.getActiveStages();
        return !activeStages.isEmpty() && activeStages.stream().allMatch(predicate);
    }

    @SplitScreenConstants.SplitIndex
    int getSplitPosition(int taskId) {
        if (stageA.getTopVisibleChildTaskId() == taskId) {
            return getStageAIndex();
        } else if (stageB.getTopVisibleChildTaskId() == taskId) {
            return getStageBIndex();
        } else if (stageC.getTopVisibleChildTaskId() == taskId) {
            return getStageCIndex();
        }
        return SPLIT_INDEX_UNDEFINED;
    }

    @Nullable
    private StageTaskListener getStageForStageType(@SplitScreen.StageType int type) {
        return mStageOrderoperator.getAllStages().stream()
                .filter(stage -> stage.getStageType() == type)
                .findFirst().orElse(null);
    }
    private List<StageTaskListener> getOtherStages(@SplitScreen.StageType int type) {
        List<StageTaskListener> allStages = mStageOrderoperator.getAllStages();
        return allStages.stream().filter(stage -> stage.getStageType() != type)
                .collect(Collectors.toList());
    }

    private void addActivityOptions(Bundle opts, @Nullable StageTaskListener launchTarget) {
        if (launchTarget != null) {
            ActivityOptions options = ActivityOptions.fromBundle(opts);
            options.setLaunchWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
            opts.putAll(options.toBundle());
            Log.i(TAG, "addActivityOptions launchTarget=" + launchTarget.getStageType());
            opts.putParcelable(KEY_LAUNCH_ROOT_TASK_TOKEN, launchTarget.mRootTaskInfo.token);
        }

        opts.putBoolean(KEY_PENDING_INTENT_BACKGROUND_ACTIVITY_ALLOWED, true);
        opts.putBoolean(KEY_PENDING_INTENT_BACKGROUND_ACTIVITY_ALLOWED_BY_PERMISSION, true);
    }

    private void addActivityOptions(Bundle opts, @Nullable StageTaskListener launchTarget,
                                    int windowingMode) {
        ActivityOptions options = ActivityOptions.fromBundle(opts);
        options.setLaunchWindowingMode(windowingMode);
//        options.setReparentLeafTaskToTda(true);

        opts.putAll(options.toBundle());
        addActivityOptions(opts, launchTarget);
    }

    void updateActivityOptions(Bundle opts, @SplitScreenConstants.SplitIndex int position) {
        addActivityOptions(opts,
                position == SPLIT_INDEX_1 ? stageA : (position == SPLIT_INDEX_2 ? stageB : stageC));
    }

    @Override
    void registerSplitScreenListener(SplitScreen.StageScreenListener listener) {
        if (mListeners.contains(listener)) return;
        mListeners.add(listener);
        sendStatusToListener(listener);
    }

    @Override
    void unregisterSplitScreenListener(SplitScreen.StageScreenListener listener) {
        mListeners.remove(listener);
    }

    void registerSplitSelectListener(SplitScreen.SplitSelectListener listener) {
        mSelectListeners.add(listener);
    }

    void unregisterSplitSelectListener(SplitScreen.SplitSelectListener listener) {
        mSelectListeners.remove(listener);
    }

    void sendStatusToListener(SplitScreen.StageScreenListener listener) {
        listener.onStagePositionChanged(STAGE_TYPE_A, getStageAIndex());
        listener.onStagePositionChanged(STAGE_TYPE_B, getStageBIndex());
        listener.onStagePositionChanged(STAGE_TYPE_C, getStageCIndex());
        listener.onSplitVisibilityChanged(isSplitScreenVisible());
        if (mSplitLayout != null) {
            listener.onSplitBoundsChanged(mSplitLayout.getRootBounds(), getStageABounds(),
                    getStageBBounds(), getStageCBounds());
        }
            // TODO(b/349828130) replace w/ stageID
        mStageOrderoperator.getAllStages().forEach(
                stage -> stage.onSplitScreenListenerRegistered(listener, STAGE_TYPE_UNDEFINED));
    }

    @Override
    void handleUnsupportedSplitStart() {

    }

    private void sendOnStageIndexChanged() {
        for (int i = mListeners.size() - 1; i >= 0; --i) {
            final SplitScreen.StageScreenListener l = mListeners.get(i);
            l.onStagePositionChanged(STAGE_TYPE_A, getStageAIndex());
            l.onStagePositionChanged(STAGE_TYPE_B, getStageBIndex());
            l.onStagePositionChanged(STAGE_TYPE_C, getStageCIndex());
        }
    }

    private void sendOnBoundsChanged() {
        if (mSplitLayout == null) return;
        for (int i = mListeners.size() - 1; i >= 0; --i) {
            mListeners.get(i).onSplitBoundsChanged(mSplitLayout.getRootBounds(),
                    getStageABounds(), getStageBBounds(), getStageCBounds());
        }
    }

    @Override
    public void onChildTaskStatusChanged(StageTaskListener stageListener, int taskId,
                                         boolean present, boolean visible) {
        int stage;
        if (present) {
            stage = stageListener.getStageType();
        } else {
            // No longer on any stage
            stage = STAGE_TYPE_UNDEFINED;
        }

        if (present) {
//            updateRecentTasksSplitPair();
        } else {
            // TODO (b/349828130): Test b/333270112 for flex split (launch adjacent for flex
            //  currently not working)
            boolean allRootsEmpty = runForActiveStagesAllMatch(stageTaskListener ->
                    stageTaskListener.getChildCount() == 0);
            if (allRootsEmpty) {
//                mRecentTasks.ifPresent(recentTasks -> {
//                    // remove the split pair mapping from recentTasks, and disable further updates
//                    // to splits in the recents until we enter split again.
//                    recentTasks.removeSplitPair(taskId);
//                });
                dismissSplitScreen(INVALID_TASK_ID, EXIT_REASON_ROOT_TASK_VANISHED);
            }
        }

        for (int i = mListeners.size() - 1; i >= 0; --i) {
            mListeners.get(i).onTaskStageChanged(taskId, stage, visible);
        }
    }

    /** Notify external parties when split is visible or not. NOT related to split activation. */
    private void sendSplitVisibilityChanged(boolean visible) {
        Log.d(TAG, "sendSplitVisibilityChanged: dividerVisible=[" + mLeftDividerVisible
                + ", " + mRightDividerVisible + "]");
        for (int i = mListeners.size() - 1; i >= 0; --i) {
            final SplitScreen.StageScreenListener l = mListeners.get(i);
            l.onSplitVisibilityChanged(visible);
        }
        sendOnBoundsChanged();
    }

    @Override
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        Log.d(TAG, "onTaskAppeared: task=" + taskInfo);
        if (mSplitMultiDisplayHelper.getDisplayRootTaskInfo(taskInfo.displayId) != null
                || taskInfo.hasParentTask()) {
            throw new IllegalArgumentException(this + "\n Unknown task appeared: " + taskInfo);
        }

        mSplitMultiDisplayHelper.setDisplayRootTaskInfo(taskInfo.displayId, taskInfo);
        mSplitMultiDisplayHelper.setDisplayRootTaskLeash(taskInfo.displayId, leash);

        if (mSplitLayout == null) {
//            int parallaxType =
//                    enableFlexibleTwoAppSplit() ? PARALLAX_FLEX_HYBRID : PARALLAX_ALIGN_CENTER;
            mSplitLayout = new SplitLayout(TAG + "SplitDivider", mContext,
                    taskInfo.configuration, this, mParentContainerCallbacks,
                    mDisplayController, mDisplayImeController, mTaskOrganzier,
                    mSplitState, mMainHandler);
            mDisplayInsetsController.addInsetsChangedListener(mDisplayId, mSplitLayout);
        }
        onRootTaskAppeared(taskInfo);
    }

    @Override
    @CallSuper
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        ArrayList<Integer> displayIds = mSplitMultiDisplayHelper.getCachedOrSystemDisplayIds();
        boolean allRootsNull = true;
        boolean taskIsNotRootTask = true;
        for (int displayId : displayIds) {
            ActivityManager.RunningTaskInfo rootTaskInfo =
                    mSplitMultiDisplayHelper.getDisplayRootTaskInfo(displayId);
            if (rootTaskInfo != null) {
                allRootsNull = false;
            }
            if (rootTaskInfo != null && rootTaskInfo.taskId == taskInfo.taskId) {
                taskIsNotRootTask = false;
            }
        }
        if (allRootsNull || taskIsNotRootTask) {
            throw new IllegalArgumentException(this + "\n Unknown task info changed: "
                    + taskInfo);
        }
        mSplitMultiDisplayHelper.setDisplayRootTaskInfo(taskInfo.displayId, taskInfo);

        if (mSplitLayout != null
                && mSplitLayout.updateConfiguration(taskInfo.configuration, taskInfo.displayId)
                && isSplitActive()) {
            Log.d(TAG, "onTaskInfoChanged: task=" + taskInfo.taskId);
            // Clear the divider remote animating flag as the divider will be re-rendered to apply
            // the new rotation config.  Don't reset the IME state since those updates are not in
            // sync with task info changes.
            mIsDividerRemoteAnimating = false;
            mSplitLayout.update(null /* t */, false /* resetImePosition */);
            onLayoutSizeChanged(mSplitLayout);
        }
    }

    @Override
    @CallSuper
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        Log.d(TAG, "onTaskVanished: task=" + taskInfo);
        if (mSplitMultiDisplayHelper.getDisplayRootTaskInfo(taskInfo.displayId) == null) {
            throw new IllegalArgumentException(this + "\n Unknown task vanished: " + taskInfo);
        }

        onRootTaskVanished(taskInfo);

        if (mSplitLayout != null) {
            mSplitLayout.release();
            mSplitLayout = null;
        }

        mSplitMultiDisplayHelper.setDisplayRootTaskInfo(taskInfo.displayId, null);
        mSplitMultiDisplayHelper.setDisplayRootTaskLeash(taskInfo.displayId, null);
        mIsRootTranslucent = false;
    }

    @Override
    public void onRootTaskAppeared(ActivityManager.RunningTaskInfo taskInfo) {
        ActivityManager.RunningTaskInfo rootTaskInfo =
                mSplitMultiDisplayHelper.getDisplayRootTaskInfo(taskInfo.displayId);
        Log.d(TAG, "onRootTaskAppeared: rootTask=" +
                rootTaskInfo);
        mStageOrderoperator.getAllStages().forEach(stage -> {
            Log.d(TAG,
                    "    onRootStageAppeared stageId=" +
                    stageTypeToString(stage.getStageType()) + " hasRoot=" + stage.mHasRootTask);
        });
        boolean notAllStagesHaveRootTask;
        notAllStagesHaveRootTask = mStageOrderoperator.getAllStages().stream()
                .anyMatch((stage) -> !stage.mHasRootTask);
        // Wait unit all root tasks appeared.
        if (rootTaskInfo == null || notAllStagesHaveRootTask) {
            return;
        }

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        mStageOrderoperator.getAllStages().forEach(stage ->
                    wct.reparent(stage.mRootTaskInfo.token, rootTaskInfo.token, true));

        // Disallow child tasks to override bounds and always inherits from the stage root tasks
//        wct.setDisallowOverrideBoundsForChildren(mMainStage.mRootTaskInfo.token, true);
//        wct.setDisallowOverrideBoundsForChildren(mSideStage.mRootTaskInfo.token, true);

        setRootForceTranslucent(true, wct);

        mSyncQueue.queue(wct);

        mSplitLayout.getLeftInvisibleBound(mTempRect1);
        mSplitLayout.getRightInvisibleBound(mTempRect2);
        mSyncQueue.runInSync(t -> {
            t.setPosition(stageA.mRootLeash, mTempRect1.left, mTempRect1.top);
            t.setPosition(stageC.mRootLeash, mTempRect2.left, mTempRect2.top);
        });
    }

    @Override
    public void onRootTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        Log.d(TAG, "onRootTaskVanished");
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        applyExitSplitScreen(null /* childrenToTop */, wct, EXIT_REASON_ROOT_TASK_VANISHED);
        if (mSplitLayout != null) {
            mDisplayInsetsController.removeInsetsChangedListener(taskInfo.displayId, mSplitLayout);
        }
    }

    private void setRootForceTranslucent(boolean translucent, WindowContainerTransaction wct) {
        if (mIsRootTranslucent == translucent) return;

        mIsRootTranslucent = translucent;
        wct.setForceTranslucent(mSplitMultiDisplayHelper
                .getDisplayRootTaskInfo(DEFAULT_DISPLAY).token, translucent);
    }

    /** Callback when split roots visiblility changed.
     * NOTICE: This only be called on legacy transition. */
    @Override
    public void onStageVisibilityChanged(StageTaskListener stageListener) {
        // If split didn't active, just ignore this callback because we should already did these
        // on #applyExitSplitScreen.
        if (!isSplitActive()) {
            return;
        }

        final boolean stageAVisible = stageA.mVisible;
        final boolean stageBVisible = stageB.mVisible;
        final boolean stageCVisible = stageC.mVisible;

        // Wait for all stages having the same visibility to prevent causing flicker.
        if (!(stageAVisible == stageBVisible && stageBVisible == stageCVisible)) {
            return;
        }

        Log.d(TAG, "onStageVisibilityChanged stage=" + stageListener.getStageType());

        // Check if it needs to dismiss split screen when both stage invisible.
        if (Stream.of(stageAVisible, stageBVisible, stageCVisible)
                .filter(stage -> !stage).count() >= 2 &&
                mExitSplitScreenOnHide) {
            exitSplitScreen(null /* childrenToTop */, EXIT_REASON_RETURN_HOME);
            return;
        }

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        // TODO: b/393217881 - replace DEFAULT DISPLAY with the current display id
        ActivityManager.RunningTaskInfo rootTaskInfo =
                mSplitMultiDisplayHelper.getDisplayRootTaskInfo(DEFAULT_DISPLAY);
        if (!stageBVisible) {
            // Split entering background.
            wct.setReparentLeafTaskIfRelaunch(rootTaskInfo.token,
                    true /* setReparentLeafTaskIfRelaunch */);
            setRootForceTranslucent(true, wct);
        } else {
            clearRequestIfPresented();
            wct.setReparentLeafTaskIfRelaunch(rootTaskInfo.token,
                    false /* setReparentLeafTaskIfRelaunch */);
            setRootForceTranslucent(false, wct);
        }

        mSyncQueue.queue(wct);
        setDividerVisibility(1, stageBVisible, null);
        setDividerVisibility(2, stageBVisible, null);
    }

    @Override
    public void onStatusChanged(boolean visible, boolean hasChildren) {
    }

    private boolean isDividerVisible(int id) {
        return id == 1 ? mLeftDividerVisible : mRightDividerVisible;
    }

    private void setDividerVisibleState(int id, boolean visible) {
        if (id == 1) {
            mLeftDividerVisible = visible;
        } else {
            mRightDividerVisible = visible;
        }
    }

    private boolean isAnyDividerVisible() {
        return mLeftDividerVisible || mRightDividerVisible;
    }

    void setDividerVisibility(int id, boolean visible, @Nullable SurfaceControl.Transaction t) {
        if (visible == isDividerVisible(id)) {
            return;
        }

        Log.d(TAG, "setDividerVisibility: id=" + id + " visible=" + visible
                + " dividerAnimating=" + mIsDividerRemoteAnimating);

        // Defer showing divider bar after keyguard dismissed, so it won't interfere with keyguard
        // dismissing animation.

        setDividerVisibleState(id, visible);

        if (mIsDividerRemoteAnimating) {
            Log.d(TAG,
                    "   Skip animating divider bar due to it's remote animating.");
            return;
        }

        applyDividerVisibility(id, t);
    }

    void applyDividerVisibility(int id, @Nullable SurfaceControl.Transaction t) {
        final SurfaceControl dividerLeash = id == 1 ? mSplitLayout.getLeftDividerLeash() :
                mSplitLayout.getRightDividerLeash();
        if (dividerLeash == null) {
            Log.d(TAG, "   Skip animating divider bar due to divider leash not ready.");
            return;
        }
        if (mIsDividerRemoteAnimating) {
            Log.d(TAG,
                    "   Skip animating divider bar due to it's remote animating.");
            return;
        }

        final boolean visible = isDividerVisible(id);

        if (t != null) {
            updateSurfaceBounds(mSplitLayout, t, false /* applyResizingOffset */);
            t.setAlpha(dividerLeash, 1f);
            t.setVisibility(dividerLeash, visible);
        } else {
            final SurfaceControl.Transaction transaction = mTransactionPool.acquire();
            updateSurfaceBounds(mSplitLayout, transaction, false /* applyResizingOffset */);
            transaction.setAlpha(dividerLeash, 1f);
            transaction.setVisibility(dividerLeash, visible);
            transaction.apply();
            mTransactionPool.release(transaction);
        }
    }

    @Override
    public void onNoLongerSupportMultiWindow(StageTaskListener stageTaskListener,
                                             ActivityManager.RunningTaskInfo taskInfo) {
        Log.d(TAG, "onNoLongerSupportMultiWindow: task=" + taskInfo);
        if (isSplitActive()) {


            // If visible, we preserve the app and keep it running. If an app becomes
            // unsupported in the bg, break split without putting anything on top
            boolean splitScreenVisible = isSplitScreenVisible();
            StageTaskListener stageToTop = getStageForStageType(
                    getOtherStages(stageTaskListener.getStageType()).get(0).getStageType());

            final WindowContainerTransaction wct = new WindowContainerTransaction();
            applyExitSplitScreen(stageToTop, wct, EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW);
            if (splitScreenVisible) {
                handleUnsupportedSplitStart();
            }
        }
    }

//    public void onSnappedToDismiss(boolean closedBottomRightStage, int exitReason) {
//        Log.d(TAG, "onSnappedToDismiss: bottomOrRight=" +  reason=" + exitReason);
//        boolean mainStageToTop =
//                closedBottomRightStage ? mSideStagePosition == SPLIT_POSITION_BOTTOM_OR_RIGHT
//                        : mSideStagePosition == SPLIT_POSITION_TOP_OR_LEFT;
//        StageTaskListener toTopStage = mainStageToTop ? mMainStage : mSideStage;
//        int dismissTop = mainStageToTop ? STAGE_TYPE_MAIN : STAGE_TYPE_SIDE;
//        if (enableFlexibleSplit()) {
//            toTopStage = mStageOrderOperator.getStageForLegacyPosition(closedBottomRightStage
//                            ? SPLIT_POSITION_TOP_OR_LEFT
//                            : SPLIT_POSITION_BOTTOM_OR_RIGHT,
//                    false /*checkAllStagesIfNotActive*/);
//            dismissTop = toTopStage.getId();
//        }
//        final WindowContainerTransaction wct = new WindowContainerTransaction();
//        toTopStage.resetBounds(wct);
//        prepareExitSplitScreen(dismissTop, wct, EXIT_REASON_DRAG_DIVIDER);
//
//        mSplitTransitions.startDismissTransition(wct, this, dismissTop, EXIT_REASON_DRAG_DIVIDER);
//    }

    /**
     * Solve some jank problem through VsyncId
     * @param layout
     */
    @Override
    public void onLayoutPositionChanging(SplitLayout layout) {
        final SurfaceControl.Transaction t = mTransactionPool.acquire();
        t.setFrameTimelineVsync(Choreographer.getInstance().getVsyncId());
        updateSurfaceBounds(layout, t, false /* applyResizingOffset */);
        t.apply();
        mTransactionPool.release(t);
    }

    @Override
    public void onSnappedToDismiss(boolean snappedToEnd, boolean left, int reason) {

    }

    @Override
    public void onLayoutSizeChanging(SplitLayout layout, int offsetX, int offsetY,
                                     boolean shouldUseParallaxEffect) {
        final SurfaceControl.Transaction t = mTransactionPool.acquire();
        t.setFrameTimelineVsync(Choreographer.getInstance().getVsyncId());
        updateSurfaceBounds(layout, t, shouldUseParallaxEffect);
        getStageBounds(mTempRect1, mTempRect2, mTempRect3);

        StageTaskListener leftStage =
                mStageOrderoperator.getStageForLegacyPosition(SPLIT_INDEX_1,
                        false /*checkAllStagesIfNotActive*/);
        StageTaskListener middleStage =
                mStageOrderoperator.getStageForLegacyPosition(SPLIT_INDEX_2,
                        false /*checkAllStagesIfNotActive*/);
        StageTaskListener rightStage =
                mStageOrderoperator.getStageForLegacyPosition(SPLIT_INDEX_3,
                        false /*checkAllStagesIfNotActive*/);
        if (leftStage != null) {
            leftStage.onResizing(mTempRect1, t);
        }
        if (middleStage != null) {
            middleStage.onResizing(mTempRect2, t);
        }
        if (rightStage != null) {
            rightStage.onResizing(mTempRect3, t);
        }

        t.apply();
        mTransactionPool.release(t);
    }

    @Override
    public void onLayoutSizeChanged(SplitLayout layout) {
        Log.d(TAG, "onLayoutSizeChanged");


        final WindowContainerTransaction wct = new WindowContainerTransaction();
        boolean sizeChanged = updateWindowBounds(layout, wct);
        if (!sizeChanged) {
            // We still need to resize on decor for ensure all current status clear.
            final SurfaceControl.Transaction t = mTransactionPool.acquire();
            updateSurfaceBounds(layout, t, false /* applyResizingOffset */);
            finishStageDecorResize(t);
            t.apply();
            mTransactionPool.release(t);
            return;
        }

        mSyncQueue.queue(wct);
        mSyncQueue.runInSync(t -> {
            updateSurfaceBounds(layout, t, false /* applyResizingOffset */);
            finishStageDecorResize(t);
        });
        sendOnBoundsChanged();

    }

    @Override
    void setStageDecorBitmap(@SplitScreenConstants.SplitIndex int index, @Nullable Bitmap bitmap) {
        StageTaskListener stage = mStageOrderoperator.getStageForLegacyPosition(index,
                true /* checkAllStagesIfNotActive */);
        if (stage == null) {
            Log.w(TAG, "Unable to set decor bitmap for invalid index=" + index);
            return;
        }
        stage.setDecorBitmap(bitmap);
    }

    private void finishStageDecorResize(SurfaceControl.Transaction t) {
        getStageBounds(mTempRect1, mTempRect2, mTempRect3);
        StageTaskListener leftStage = mStageOrderoperator.getStageForLegacyPosition(SPLIT_INDEX_1,
                false /*checkAllStagesIfNotActive*/);
        StageTaskListener middleStage = mStageOrderoperator.getStageForLegacyPosition(SPLIT_INDEX_2,
                false /*checkAllStagesIfNotActive*/);
        StageTaskListener rightStage = mStageOrderoperator.getStageForLegacyPosition(SPLIT_INDEX_3,
                false /*checkAllStagesIfNotActive*/);
        if (leftStage != null) {
            leftStage.onResized(mTempRect1, t);
        }
        if (middleStage != null) {
            middleStage.onResized(mTempRect2, t);
        }
        if (rightStage != null) {
            rightStage.onResized(mTempRect3, t);
        }
    }

    /**
     * Populates `wct` with operations that match the split windows to the current layout.
     * To match relevant surfaces, make sure to call updateSurfaceBounds after `wct` is applied
     *
     * @return true if stage bounds actually .
     */
    private boolean updateWindowBounds(SplitLayout layout, WindowContainerTransaction wct) {
        final StageTaskListener leftStage;
        final StageTaskListener middleStage;
        final StageTaskListener rightStage;

        ActivityManager.RunningTaskInfo rootTaskInfo =
                mSplitMultiDisplayHelper.getDisplayRootTaskInfo(DEFAULT_DISPLAY);
        if (rootTaskInfo != null) {
            wct.setReparentLeafTaskIfRelaunch(rootTaskInfo.token,
                    false /* setReparentLeafTaskIfRelaunch */);
        }

        leftStage = mStageOrderoperator
                .getStageForLegacyPosition(SPLIT_INDEX_1,
                        true /*checkAllStagesIfNotActive*/);
        middleStage = mStageOrderoperator
                .getStageForLegacyPosition(SPLIT_INDEX_2,
                        true /*checkAllStagesIfNotActive*/);
        rightStage = mStageOrderoperator
                .getStageForLegacyPosition(SPLIT_INDEX_3,
                        true /*checkAllStagesIfNotActive*/);

        boolean updated = layout.applyTaskChanges(wct, leftStage.mRootTaskInfo,
                middleStage.mRootTaskInfo, rightStage.mRootTaskInfo);
        Log.d(TAG, "updateWindowBounds: leftStage=" + layout.getLeftBounds() +
                        " middleStage=" + layout.getMiddleBounds() + " rightStage=" +
                        layout.getRightBounds());

        return updated;
    }

    void updateSurfaceBounds(@Nullable SplitLayout layout, @NonNull SurfaceControl.Transaction t,
                             boolean applyResizingOffset) {
        final StageTaskListener leftStage;
        final StageTaskListener middleStage;
        final StageTaskListener rightStage;

        leftStage = mStageOrderoperator
                .getStageForLegacyPosition(SPLIT_INDEX_1,
                        true /*checkAllStagesIfNotActive*/);
        middleStage = mStageOrderoperator
                .getStageForLegacyPosition(SPLIT_INDEX_2,
                        true /*checkAllStagesIfNotActive*/);
        rightStage = mStageOrderoperator
                .getStageForLegacyPosition(SPLIT_INDEX_3,
                        true /*checkAllStagesIfNotActive*/);

        layout = layout == null ? mSplitLayout : layout;

        layout.applySurfaceChanges(t, leftStage.mRootLeash,
                middleStage.mRootLeash, rightStage.mRootLeash, applyResizingOffset);
        Log.d(TAG,
                "updateSurfaceBounds: leftStage=" + layout.getLeftBounds() +
                        " middleStage=" + layout.getMiddleBounds() + " rightStage=" +
                        layout.getRightBounds());
    }

    @Override
    public int getSplitItemPosition(WindowContainerToken token) {
        if (token == null) {
            return SPLIT_INDEX_UNDEFINED;
        }

        // We could migrate to/return the new INDEX enums here since most callers just care that
        // this value isn't SPLIT_POSITION_UNDEFINED, but
        // ImePositionProcessor#getImeLayeringTargetPosition actually uses the
        // leftTop/bottomRight value
        StageTaskListener stageForToken = mStageOrderoperator.getAllStages().stream()
                .filter(stage -> stage.containsToken(token))
                .findFirst().orElse(null);

        return stageForToken == null
                ? SPLIT_INDEX_UNDEFINED
                : mStageOrderoperator.getLegacyPositionForStage(stageForToken);
    }

    /**
     * Returns the {@link com.android.wm.shell.triplesplit.split.SplitScreen.StageType}
     * where {@param token} is being used {@link SplitScreen#STAGE_TYPE_UNDEFINED} otherwise
     */
    @SplitScreen.StageType
    public int getSplitItemStage(@Nullable WindowContainerToken token) {
        if (token == null) {
            return STAGE_TYPE_UNDEFINED;
        }

        if (stageA.containsToken(token)) {
            return STAGE_TYPE_A;
        } else if (stageB.containsToken(token)) {
            return STAGE_TYPE_B;
        } else if (stageC.containsToken(token)) {
            return STAGE_TYPE_C;
        }

        return STAGE_TYPE_UNDEFINED;
    }

    @Override
    void notifySplitAnimationStatus(boolean animationRunning) {
//        if (mSplitInvocationListener == null || mSplitInvocationListenerExecutor == null) {
//            return;
//        }
//        mSplitInvocationListenerExecutor.execute(() ->
//                mSplitInvocationListener.onSplitAnimationInvoked(animationRunning));
    }

    @Override
    public void setLayoutOffsetTarget(int offsetX, int offsetY, SplitLayout layout) {
        Log.d(TAG, "setLayoutOffsetTarget: x=" + offsetX + " y=" + offsetY);
        final StageTaskListener leftStage = getLeftStage();
        final StageTaskListener middleStage = getMiddleStage();
        final StageTaskListener rightStage = getRightStage();
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        layout.applyLayoutOffsetTarget(wct, offsetX, offsetY, leftStage.mRootTaskInfo,
                middleStage.mRootTaskInfo, rightStage.mRootTaskInfo);
        mTaskOrganzier.applyTransaction(wct);
    }

    /**
     * Update surfaces of the split screen layout based on the current state
     * @param transaction to write the updates to
     */
    public void updateSurfaces(SurfaceControl.Transaction transaction) {
        ensureDividerWindowManager();
        updateSurfaceBounds(mSplitLayout, transaction, /* applyResizingOffset */ false);
    }

    private void ensureDividerWindowManager() {
        if (mSplitLayout.getLeftDividerLeash() == null || mSplitLayout.getRightDividerLeash() == null) {
            mSplitLayout.init();
        }
    }

    /**
     *
     * @param displayId display id of the display that is under the change
     * @param fromRotation rotation before the change
     * @param toRotation rotation after the change
     * @param newDisplayAreaInfo display area info after applying the update
     * @param wct A task transaction to populate.
     */
    public void onDisplayChange(int displayId, int fromRotation, int toRotation,
                                @Nullable DisplayAreaInfo newDisplayAreaInfo, WindowContainerTransaction wct) {
        if (displayId != DEFAULT_DISPLAY || !isSplitActive()) {
            return;
        }

        Log.d(TAG,
                "onDisplayChange: display=" + displayId + " fromRot=" + fromRotation + " toRot="
                        + toRotation + " config=" + (newDisplayAreaInfo != null ?
                        newDisplayAreaInfo.configuration : null)
                );
        if (newDisplayAreaInfo != null) {
            mSplitLayout.updateConfiguration(newDisplayAreaInfo.configuration, displayId);
        } else {
//            mSplitLayout.rotateTo(toRotation);
        }
        updateWindowBounds(mSplitLayout, wct);
        sendOnBoundsChanged();
    }

    /**
     * Get the stage that should contain this `taskInfo`. The stage doesn't necessarily contain
     * this task (yet) so this can also be used to identify which stage to put a task into.
     */
    private StageTaskListener getStageOfTask(ActivityManager.RunningTaskInfo taskInfo) {
        return mStageOrderoperator.getActiveStages().stream()
                .filter((stage) -> stage.mRootTaskInfo != null &&
                        taskInfo.parentTaskId == stage.mRootTaskInfo.taskId
                )
                .findFirst()
                .orElse(null);
    }


    @SplitScreen.StageType
    private int getStageType(StageTaskListener stage) {
        if (stage == null) return STAGE_TYPE_UNDEFINED;
        return stage.getStageType();
    }

    //Miss some shell transition function here

    /**
     * @return The provided taskId is the last child of any stage.
     */
    private boolean isLastTaskInAnyStage(int taskId) {
        return mStageOrderoperator.getActiveStages().stream()
                .anyMatch(stageListener ->
                        stageListener.containsTask(taskId)
                                && stageListener.getChildCount() == 1);
    }

    static class StageChangeRecord {
        boolean mContainShowFullscreenChange = false;
        static class StageChange {
            final StageTaskListener mStageTaskListener;
            final IntArray mAddedTaskId = new IntArray();
            final IntArray mRemovedTaskId = new IntArray();
            StageChange(StageTaskListener stage) {
                mStageTaskListener = stage;
            }

            boolean shouldDismissStage() {
                if (mAddedTaskId.size() > 0 || mRemovedTaskId.size() == 0) {
                    return false;
                }
                int removeChildTaskCount = 0;
                for (int i = mRemovedTaskId.size() - 1; i >= 0; --i) {
                    if (mStageTaskListener.containsTask(mRemovedTaskId.get(i))) {
                        ++removeChildTaskCount;
                    }
                }
                return removeChildTaskCount == mStageTaskListener.getChildCount();
            }
        }
        private final ArrayMap<StageTaskListener, StageChange> mChanges = new ArrayMap<>();

        void addRecord(StageTaskListener stage, boolean open, int taskId) {
            final StageChange next;
            if (!mChanges.containsKey(stage)) {
                next = new StageChange(stage);
                mChanges.put(stage, next);
            } else {
                next = mChanges.get(stage);
            }
            if (open) {
                next.mAddedTaskId.add(taskId);
            } else {
                next.mRemovedTaskId.add(taskId);
            }
        }

        ArraySet<StageTaskListener> getShouldDismissedStage() {
            final ArraySet<StageTaskListener> dismissTarget = new ArraySet<>();
            for (int i = mChanges.size() - 1; i >= 0; --i) {
                final StageChange change = mChanges.valueAt(i);
                if (change.shouldDismissStage()) {
                    dismissTarget.add(change.mStageTaskListener);
                }
            }
            return dismissTarget;
        }
    }

    public void goToFullscreenFromSplit() {
        Log.d(TAG, "goToFullscreenFromSplit");
        // If main stage is focused, toEnd = true if
        // mSideStagePosition = SPLIT_POSITION_BOTTOM_OR_RIGHT. Otherwise toEnd = false
        // If side stage is focused, toEnd = true if
        // mSideStagePosition = SPLIT_POSITION_TOP_OR_LEFT. Otherwise toEnd = false
        final boolean toEnd;
        if (stageA.isFocused()) {
            // 100 - 33 - 33
            mSplitLayout.flingDividerToDismiss(1, true, EXIT_REASON_FULLSCREEN_REQUEST);
        } else if (stageB.isFocused()) {
            // 33 - 100 -33
            mSplitLayout.flingBothDividerToDismiss();
        } else if (stageC.isFocused()) {
            mSplitLayout.flingDividerToDismiss(2, false, EXIT_REASON_FULLSCREEN_REQUEST);
        }
    }



    /** Move the specified task to fullscreen, regardless of focus state. */
    @Override
    public void moveTaskToFullscreen(int taskId, int exitReason) {
        Log.d(TAG, "moveTaskToFullscreen");
        if (stageA.containsTask(taskId)) {
            mSplitLayout.flingDividerToDismiss(1, true, EXIT_REASON_FULLSCREEN_REQUEST);
        } else if (stageB.containsTask(taskId)) {
            mSplitLayout.flingBothDividerToDismiss();
        } else if (stageC.containsTask(taskId)){
            mSplitLayout.flingDividerToDismiss(2, false, EXIT_REASON_FULLSCREEN_REQUEST);
        }
    }

    boolean isLaunchToSplit(TaskInfo taskInfo) {
        return getActivateSplitPosition(taskInfo) != SPLIT_INDEX_UNDEFINED;
    }

    int getActivateSplitPosition(TaskInfo taskInfo) {
        if (mSplitRequest == null || taskInfo == null) {
            return SPLIT_INDEX_UNDEFINED;
        }
        if (mSplitRequest.mActivateTaskId1 != 0
                && mSplitRequest.mActivateTaskId2 == taskInfo.taskId) {
            return mSplitRequest.mActivatePosition;
        }
        if (mSplitRequest.mActivateTaskId1 == taskInfo.taskId) {
            return mSplitRequest.mActivatePosition;
        }
        final String packageName1 = mSplitRequest.mStartIntent1 != null ?
                mSplitRequest.mStartIntent1.getPackage() : null;
        final String basePackageName = taskInfo.baseIntent.getPackage();
        if (packageName1 != null && packageName1.equals(basePackageName)) {
            return mSplitRequest.mActivatePosition;
        }
        final String packageName2 = mSplitRequest.mStartIntent2 != null ?
                mSplitRequest.mStartIntent2.getPackage() : null;
        if (packageName2 != null && packageName2.equals(basePackageName)) {
            return mSplitRequest.mActivatePosition;
        }
        final String packageName3 = mSplitRequest.mStartIntent3 != null ?
                mSplitRequest.mStartIntent3.getPackage() : null;
        if (packageName3 != null && packageName3.equals(basePackageName)) {
            return mSplitRequest.mActivatePosition;
        }
        return SPLIT_INDEX_UNDEFINED;
    }

    public boolean wctIsReorderingSplitToTop(@NonNull WindowContainerTransaction finishWct) {
        for (int i = 0; i < finishWct.getHierarchyOps().size(); ++i) {
            final WindowContainerTransaction.HierarchyOp op =
                    finishWct.getHierarchyOps().get(i);
            final IBinder container = op.getContainer();
            boolean anyStageContainsContainer;
            anyStageContainsContainer = mStageOrderoperator.getActiveStages().stream()
                    .anyMatch(stage -> stage.containsContainer(container));
            if (op.getType() == HIERARCHY_OP_TYPE_REORDER && op.getToTop()
                    && anyStageContainsContainer) {
                return true;
            }
        }
        return false;
    }

    @Override
    SplitMultiDisplayHelper getSplitMultiDisplayHelper() {
        return mSplitMultiDisplayHelper;
    }

    @Override
    void setSplitMultiDisplayHelper(SplitMultiDisplayHelper splitMultiDisplayHelper) {
        mSplitMultiDisplayHelper = splitMultiDisplayHelper;
    }

    @Override
    int getLastActiveStage() {
        return mLastActiveStage;
    }

    @Override
    public void dump(@NonNull PrintWriter pw, String prefix) {

    }

}
