package com.android.wm.shell.triplesplit.split

import android.app.ActivityManager
//import android.app.ActivityManager.START_SUCCESS
//import android.app.ActivityManager.START_TASK_TO_FRONT
import android.app.ActivityOptions
import android.app.ActivityTaskManager
import android.app.PendingIntent
import android.app.TaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.RemoteException
import android.os.UserHandle
import android.util.ArrayMap
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.IRemoteAnimationFinishedCallback
import android.view.IRemoteAnimationRunner
import android.view.RemoteAnimationAdapter
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.widget.Adapter
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.annotation.BinderThread
import androidx.annotation.IntDef
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayImeController
import com.android.wm.shell.common.DisplayInsetsController
import com.android.wm.shell.common.RemoteCallable
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.common.TransactionPool
import com.android.wm.shell.draganddrop.DragAndDropController
import com.android.wm.shell.draganddrop.DragAndDropPolicy
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.triplesplit.split.util.SplitIconProvider
import com.android.wm.shell.triplesplit.split.HiddenApiWrapper.fromBundle
import com.android.wm.shell.triplesplit.split.HiddenApiWrapper.getWindowingMode
import com.android.wm.shell.triplesplit.split.HiddenApiWrapper.makeRemoteAnimation
import com.android.wm.shell.triplesplit.split.HiddenApiWrapper.setContainerLayer
import com.android.wm.shell.triplesplit.split.HiddenApiWrapper.userId
import com.android.wm.shell.triplesplit.split.SplitScreen.STAGE_TYPE_UNDEFINED
import com.android.wm.shell.triplesplit.split.SplitScreenConstants.SPLIT_INDEX_1
import com.android.wm.shell.triplesplit.split.SplitScreenConstants.SPLIT_INDEX_2
import com.android.wm.shell.triplesplit.split.SplitScreenConstants.SPLIT_INDEX_3
import com.android.wm.shell.triplesplit.split.SplitScreenConstants.SPLIT_INDEX_UNDEFINED
import com.android.wm.shell.triplesplit.split.SplitScreenUtils.samePackage
import com.android.wm.shell.triplesplit.split.util.ComponentUtils
import java.util.concurrent.Executor

class SplitScreenController(
        val mContext: Context, val shellInit: ShellInit, val mShellController: ShellController,
        val mTaskOrganizer: ShellTaskOrganizer, val syncQueue: SyncTransactionQueue,
        val mRootTDAOrganizer: RootTaskDisplayAreaOrganizer, val mDisplayController: DisplayController,
        val mDisplayImeController: DisplayImeController, val mDisplayInsetsController: DisplayInsetsController,
        val mSplitState: SplitState,
        val transactionPool: TransactionPool,
        val mainExecutor: ShellExecutor
    ): DragAndDropPolicy.Starter, RemoteCallable<SplitScreenController> {
        init {
            shellInit.addInitCallback(this::onInit, this);
        }

    private val START_SUCCESS: Int = 0
    private val START_TASK_TO_FRONT: Int = 2
    private val mImpl: SplitScreenImpl = SplitScreenImpl()

    internal val mStageCoordinator: StageCoordinator = createStageCoordinator()

    private lateinit var mStartingSplitTaskLayer: SurfaceControl

    @IntDef(
        EXIT_REASON_UNKNOWN,
        EXIT_REASON_RECREATE_SPLIT,
        EXIT_REASON_RETURN_HOME,
        EXIT_REASON_APP_FINISHED,
        EXIT_REASON_DRAG_DIVIDER,
        EXIT_REASON_ROOT_TASK_VANISHED,
        EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class ExitReason

    @IntDef(
        ENTER_REASON_UNKNOWN,
        ENTER_REASON_DRAG,
        ENTER_REASON_LAUNCHER,
        ENTER_REASON_MULTI_INSTANCE
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class SplitEnterReason

    fun asSplitScreen(): SplitScreenImpl {
        return mImpl
    }

    fun onInit() {

    }

    protected fun createStageCoordinator(): StageCoordinator {
        return StageCoordinator(mContext, DEFAULT_DISPLAY, syncQueue, mTaskOrganizer,
            mDisplayController, mDisplayImeController, mDisplayInsetsController, mSplitState,
            transactionPool, mainExecutor)
    }

    override fun startTask(p0: Int, p1: Int, p2: Bundle?) {
        startTask(p0, p1, p2, null)
    }

    override fun startShortcut(
        p0: String?,
        p1: String?,
        p2: Int,
        p3: Bundle?,
        p4: UserHandle?
    ) {
        Log.w(TAG, "startShortcut is not supported by this split controller")
    }

    override fun startIntent(
        p0: PendingIntent?,
        p1: Int,
        p2: Intent?,
        p3: Int,
        p4: Bundle?
    ) {
        if (p0 == null) {
            Log.w(TAG, "Skip startIntent with null pendingIntent")
            return
        }
        startIntentToSplit(p0, p1, p2, p3, p4)
    }

    override fun enterSplitScreen(p0: Int, p1: Boolean) {
        enterSplitScreen(p0, if (p1) SPLIT_INDEX_1 else SPLIT_INDEX_3)
    }

    override fun getContext(): Context {
        return mContext
    }

    override fun getRemoteCallExecutor(): ShellExecutor {
        return mainExecutor
    }

    fun isSplitScreenVisible(): Boolean {
        return mStageCoordinator.isSplitScreenVisible
    }

    fun onHostActivityVisible() {
        mStageCoordinator.onHostActivityVisible()
    }

    fun getTaskInfo(@SplitScreenConstants.SplitIndex index: Int): ActivityManager.RunningTaskInfo? {
        if (!isSplitScreenVisible() || index == SPLIT_INDEX_UNDEFINED) {
            return null;
        }

        val taskId: Int = mStageCoordinator.getTaskId(index)
        return mTaskOrganizer.getRunningTaskInfo(taskId)
    }

    /**
     * @return An array from left to right
     */
    fun getAllTaskInfos(): Array<ActivityManager.RunningTaskInfo> {
        val leftTask: ActivityManager.RunningTaskInfo? = getTaskInfo(SPLIT_INDEX_1)
        val middleTask: ActivityManager.RunningTaskInfo? = getTaskInfo(SPLIT_INDEX_2)
        val rightTask: ActivityManager.RunningTaskInfo? = getTaskInfo(SPLIT_INDEX_3)
        if (leftTask != null && middleTask != null && rightTask != null) {
            return arrayOf(leftTask, middleTask, rightTask)
        }
        return emptyArray<ActivityManager.RunningTaskInfo>()
    }

    fun isTaskInSplitScreen(taskId: Int): Boolean {
        return mStageCoordinator.getStageOfTask(taskId) != STAGE_TYPE_UNDEFINED
    }

    @SplitScreen.StageType
    fun getStageOfTask(taskId: Int): Int {
        return mStageCoordinator.getStageOfTask(taskId)
    }

    fun isTaskInSplitScreenForeground(taskId: Int): Boolean {
        return isTaskInSplitScreen(taskId) && isSplitScreenVisible()
    }

    fun isTaskRootOrStageRoot(taskId: Int): Boolean {
        return mStageCoordinator.isRootOrStageRoot(taskId)
    }

    @SplitScreenConstants.SplitIndex
    fun getSplitIndex(taskId: Int): Int {
        return mStageCoordinator.getSplitIndex(taskId)
    }

    fun moveToStage(taskId: Int, @SplitScreenConstants.SplitIndex splitIndex: Int): Boolean {
        return moveToStage(taskId, splitIndex, WindowContainerTransaction())
    }

    fun moveToStage(taskId: Int, @SplitScreenConstants.SplitIndex splitIndex: Int,
                    wct: WindowContainerTransaction): Boolean {
        val task: ActivityManager.RunningTaskInfo? = mTaskOrganizer.getRunningTaskInfo(taskId)
        if (task == null) {
            throw IllegalArgumentException("Unknown taskId=$taskId")
        }
        if (isTaskInSplitScreen(taskId)) {
            throw IllegalArgumentException("taskId=$taskId is in split")
        }
        return mStageCoordinator.moveToStage(task, splitIndex, wct)
    }

    fun updateSplitScreenSurfaces(transaction: SurfaceControl.Transaction) {
        mStageCoordinator.updateSurfaces(transaction)
    }

    fun setStagePosition(stage: StageTaskListener,
                         @SplitScreenConstants.SplitIndex index: Int) {
        mStageCoordinator.setStagePosition(stage, index,
            WindowContainerTransaction())
    }

    /**
     * Determine in which split index a new instance should be
     */
    fun determineNewInstanceIndex(callingTask: ActivityManager.RunningTaskInfo): Int {
        //wrapper here
        if (getWindowingMode(callingTask) == WINDOWING_MODE_FULLSCREEN ||
            getSplitIndex(callingTask.taskId) == SPLIT_INDEX_1) {
            return SPLIT_INDEX_1
        } else if (getSplitIndex(callingTask.taskId) == SPLIT_INDEX_3) {
            return SPLIT_INDEX_3
        } else {
            return SPLIT_INDEX_2
        }
    }

    fun enterSplitScreen(taskId: Int, @SplitScreenConstants.SplitIndex index: Int) {
        enterSplitScreen(taskId, index, WindowContainerTransaction())
    }

    fun enterSplitScreen(taskId: Int, @SplitScreenConstants.SplitIndex index: Int,
                         wct: WindowContainerTransaction) {
        moveToStage(taskId, index, wct)
    }

    override fun exitSplitScreen(toTopTaskId: Int, @ExitReason exitReason: Int) {
        mStageCoordinator.dismissSplitScreen(toTopTaskId, exitReason)
    }

    fun exitSplitScreenOnHide(exitSplitScreenOnHide: Boolean) {
        mStageCoordinator.exitSplitScreenOnHide(exitSplitScreenOnHide)
    }

    fun moveSplitToBack() {
        mStageCoordinator.moveSplitToBack()
    }

    fun restoreSplitToFront() {
        mStageCoordinator.restoreSplitToFront()
    }

    fun getStageBounds(outLeftBounds: Rect, outMiddleBounds: Rect, outRightBounds: Rect) {
        mStageCoordinator.getStageBounds(outLeftBounds, outMiddleBounds,
            outRightBounds)
    }

    fun getRefStageBounds(outLeftBounds: Rect, outMiddleBounds: Rect, outRightBounds: Rect) {
        mStageCoordinator.getRefStageBounds(outLeftBounds,
            outMiddleBounds,
            outRightBounds)
    }

    fun registerSplitScreenListener(listener: SplitScreen.StageScreenListener) {
        mStageCoordinator.registerSplitScreenListener(listener)
    }

    fun unRegisterSplitScreenListener(listener: SplitScreen.StageScreenListener) {
        mStageCoordinator.unregisterSplitScreenListener(listener)
    }

    fun registerSplitSelectListener(listener: SplitScreen.SplitSelectListener) {
        mStageCoordinator.registerSplitSelectListener(listener)
    }

    fun unRegisterSplitSelectListener(listener: SplitScreen.SplitSelectListener) {
        mStageCoordinator.unregisterSplitSelectListener(listener)
    }

    fun goToFullScreenFromSplit() {
        if (mStageCoordinator.isSplitActive) {
            mStageCoordinator.goToFullscreenFromSplit()
        }
    }

    fun setSplitScreenFocus(@SplitScreenConstants.SplitIndex index: Int) {
        if (mStageCoordinator.isSplitActive) {
            mStageCoordinator.grantFocusToPosition(index)
        }
    }

    fun setStageDecorBitmap(@SplitScreenConstants.SplitIndex index: Int, bitmap: Bitmap?) {
        mStageCoordinator.setStageDecorBitmap(index, bitmap)
    }

    fun getSplitScreenPackageNames(@SplitScreenConstants.SplitIndex index: Int): List<String>? {
        return mStageCoordinator.getSplitScreenPackageNames(index)
    }

    fun captureSplitScreen(): Bitmap? {
        return mStageCoordinator.captureSplitScreen()
    }

    fun setSplitIconProvider(splitIconProvider: SplitIconProvider?) {
        mStageCoordinator.setSplitIconProvider(splitIconProvider)
    }

    fun moveTaskToFullScreen(taskId: Int, @ExitReason exitReason: Int) {
        mStageCoordinator.moveTaskToFullscreen(taskId, exitReason)
    }

    fun isLaunchToSplit(taskInfo: TaskInfo): Boolean {
        return mStageCoordinator.isLaunchToSplit(taskInfo)
    }

    fun getActivateSplitPosition(taskInfo: TaskInfo): Int {
        return mStageCoordinator.getActivateSplitPosition(taskInfo)
    }

    /**
     * start three tasks in split screen
     * @param index1 position in which taskId1 should be started in.
     * @param snapPosition how to put the divider to split the screen
     */
    fun startTasks(taskId1: Int, options1: Bundle?, taskId2: Int, options2: Bundle?,
                   taskId3: Int, options3: Bundle?, @SplitScreenConstants.SplitIndex index1: Int,
                   @SplitScreenConstants.SplitIndex index2: Int,
                   @SplitScreenConstants.SplitIndex index3: Int,
                   @SplitScreenConstants.PersistentSnapPosition snapPosition: Int,
                   adapter: RemoteAnimationAdapter) {
        mStageCoordinator.startTasks(taskId1, options1, taskId2, options2, taskId3, options3,
            index1, index2, index3, snapPosition, adapter)
    }

    /**
     * Move a task to split select
     * @param taskInfo taskInfo the task being moved to split select
     * @param index the split index this task should move to
     * @param taskBounds current bounds of the task
     */
    fun requestEnterSplitScreen(taskInfo: ActivityManager.RunningTaskInfo,
                                @SplitScreenConstants.SplitIndex index: Int,
                                taskBounds: Rect) {
        mStageCoordinator.requestEnterSplitSelect(taskInfo, index, taskBounds)
    }

    fun startTask(taskId: Int, @SplitScreenConstants.SplitIndex index: Int, options: Bundle?,
                  hideTaskToken: WindowContainerToken?) {
        if (isTaskInSplitScreenForeground(taskId)) return
        val result = Array<Int>(1){-1}
        val wrapper: IRemoteAnimationRunner = object : IRemoteAnimationRunner.Stub() {
            override fun onAnimationStart(
                transit: Int,
                apps: Array<out RemoteAnimationTarget?>?,
                wallpapers: Array<out RemoteAnimationTarget?>?,
                nonApps: Array<out RemoteAnimationTarget?>?,
                finishedCallback: IRemoteAnimationFinishedCallback?
            ) {
                try {
                    finishedCallback?.onAnimationFinished()
                } catch (e: RemoteException) {
                    Log.e(TAG, "Failed to invoke onAnimationFinished")
                }
                if (result[0] == START_SUCCESS || result[0] == START_TASK_TO_FRONT) {
                    val evictWct = WindowContainerTransaction()
                    mStageCoordinator.prepareEvictNonOpeningChildTasks(index, apps, evictWct)
                    syncQueue.queue(evictWct)
                }
            }

            override fun onAnimationCancelled() {
                val evictWct = WindowContainerTransaction()
                mStageCoordinator.prepareEvictInvisibleChildTasks(evictWct)
                syncQueue.queue(evictWct)
            }

        }
        val newOptions = mStageCoordinator.resolveStartStage(STAGE_TYPE_UNDEFINED, options, index,
            null)
        //wrapper here
        val wrappedAdapter = RemoteAnimationAdapter(wrapper, 0, 0)
        val activityOptions: ActivityOptions = fromBundle(newOptions)
        activityOptions.update(makeRemoteAnimation(wrappedAdapter))

        try {
            result[0] = ActivityTaskManager.getService().startActivityFromRecents(taskId,
                activityOptions.toBundle())
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to launch Task", e)
        }
    }

    private fun startIntentToSplit(pendingIntent: PendingIntent, userId: Int,
                                   fillInIntent: Intent?,
                                   @SplitScreenConstants.SplitIndex index: Int,
                                   options: Bundle?) {
        val resolvedFillInIntent = fillInIntent ?: Intent()
        resolvedFillInIntent.addFlags(FLAG_ACTIVITY_NO_USER_ACTION)

        val packageName = ComponentUtils.getPackageName(pendingIntent)
        val existingPackageName = getPackageName(index, null)
        val existingUserId = getUserId(index, null)
        if (samePackage(packageName, existingPackageName) && userId == existingUserId) {
            Log.i(TAG, "Task package already in split index=$index, focus existing task")
            setSplitScreenFocus(index)
            return
        }

        mStageCoordinator.startIntent(pendingIntent, resolvedFillInIntent, options,
            null, null, index, DEFAULT_DISPLAY)
    }


    fun startIntents(pendingIntent1: PendingIntent, options1: Bundle?,
                             pendingIntent2: PendingIntent, options2: Bundle?,
                             pendingIntent3: PendingIntent, options3: Bundle?,
                             @SplitScreenConstants.SplitIndex index1: Int,
                             @SplitScreenConstants.SplitIndex index2: Int,
                             @SplitScreenConstants.SplitIndex index3: Int,
                             @SplitScreenConstants.PersistentSnapPosition snapPosition: Int,
                             adapter: RemoteAnimationAdapter?) {
        var fillIntent1: Intent?
        var fillIntent2: Intent?
        var fillIntent3: Intent?
        val packageName1: String? = ComponentUtils.getPackageName(pendingIntent1)
        val packageName2: String? = ComponentUtils.getPackageName(pendingIntent2)
        val packageName3: String? = ComponentUtils.getPackageName(pendingIntent3)
        //wrapper here
        val activityOptions1: ActivityOptions = if (options1 != null) fromBundle(options1)
            else ActivityOptions.makeBasic()
        val activityOptions2: ActivityOptions = if (options2 != null) fromBundle(options2)
            else ActivityOptions.makeBasic()
        val activityOptions3: ActivityOptions = if (options3 != null) fromBundle(options3)
            else ActivityOptions.makeBasic()
        if (samePackage(packageName1, packageName2) || samePackage(packageName2, packageName3)
            || samePackage(packageName1, packageName3)) {
            throw IllegalArgumentException("Can't accept same package now")
        }

        fillIntent1 = Intent()
        fillIntent2 = Intent()
        fillIntent3 = Intent()

        mStageCoordinator.startIntents(pendingIntent1, fillIntent1, options1,
            pendingIntent2, fillIntent2, options2,
            pendingIntent3, fillIntent3, options3,
            index1, index2, index3, snapPosition, adapter)
    }

    private fun getPackageName(@SplitScreenConstants.SplitIndex index: Int,
                               ignoreTaskToken: WindowContainerTransaction?): String? {
        var taskInfo: ActivityManager.RunningTaskInfo?
        if (isSplitScreenVisible()) {
            taskInfo = getTaskInfo(index)
        } else {
            taskInfo = null
        }

        return taskInfo?.let {
            ComponentUtils.getPackageName(taskInfo.baseIntent)
        }
    }

    private fun getUserId(@SplitScreenConstants.SplitIndex index: Int,
                          ignoreTaskToken: WindowContainerTransaction?): Int {
        var taskInfo: ActivityManager.RunningTaskInfo?
        if (isSplitScreenVisible()) {
            taskInfo = getTaskInfo(index)
        } else {
            taskInfo = null
        }
        //wrapper here
        return if (taskInfo != null) userId(taskInfo) else -1
    }

    private fun reparentSplitTasksForAnimation(apps: Array<RemoteAnimationTarget>,
                                               t: SurfaceControl.Transaction,
                                               callsite: String): SurfaceControl {
        // wrapper here
        val builder: SurfaceControl.Builder = setContainerLayer(SurfaceControl.Builder())
                                                .setName("RecentsAnimationSplitTasks")
                                                .setHidden(false)
//                                                .setCallsite(callsite)
        mRootTDAOrganizer.attachToDisplayArea(DEFAULT_DISPLAY, builder)
        val splitTasksLayer = builder.build()

        for (i in 0..apps.size) {
            val appTarget: RemoteAnimationTarget = apps[i]
            t.reparent(appTarget.leash,  splitTasksLayer)
            t.setPosition(appTarget.leash, appTarget.screenSpaceBounds.left.toFloat(),
                appTarget.screenSpaceBounds.top.toFloat()
            )
        }
        return splitTasksLayer
    }

    internal fun switchSplitPosition(@SplitScreenConstants.SplitIndex index1: Int,
                                     @SplitScreenConstants.SplitIndex index2: Int,
                                     reason: String) {
        if (isSplitScreenVisible()) {
            mStageCoordinator.switchSplitPosition(index1, index2, reason)
        }
    }



    inner class SplitScreenImpl(): SplitScreen{
        val mExecutors: ArrayMap<SplitScreen.StageScreenListener, Executor> =
            ArrayMap<SplitScreen.StageScreenListener, Executor>()
        val mListener: SplitScreen.StageScreenListener  = object : SplitScreen.StageScreenListener {
            override fun onStagePositionChanged(stage: Int, position: Int) {
                for (i in 0 until mExecutors.size) {
                    val index = i
                    mExecutors.valueAt(index).execute {
                        mExecutors.keyAt(index).onStagePositionChanged(stage, position)
                    }
                }
            }

            override fun onTaskStageChanged(taskId: Int, stage: Int, isVisible: Boolean) {
                for (i in 0 until mExecutors.size) {
                    val index = i
                    mExecutors.valueAt(index).execute {
                        mExecutors.keyAt(index).onTaskStageChanged(taskId, stage, isVisible)
                    }
                }
            }

            override fun onSplitBoundsChanged(
                rootBounds: Rect?,
                boundsA: Rect?,
                boundsB: Rect?,
                boundsC: Rect?
            ) {
                for (i in 0 until mExecutors.size) {
                    val index = i
                    mExecutors.valueAt(index).execute {
                        mExecutors.keyAt(index).onSplitBoundsChanged(rootBounds,
                            boundsA, boundsB, boundsC)
                    }
                }
            }

            override fun onSplitVisibilityChanged(visible: Boolean) {
                for (i in 0 until mExecutors.size) {
                    val index = i
                    mExecutors.valueAt(index).execute {
                        mExecutors.keyAt(index).onSplitVisibilityChanged(visible)
                    }
                }
            }
        }

        fun startTasks(taskId1: Int, options1: Bundle?, taskId2: Int, options2: Bundle?,
                       taskId3: Int, options3: Bundle?, @SplitScreenConstants.SplitIndex index1: Int,
                       @SplitScreenConstants.SplitIndex index2: Int,
                       @SplitScreenConstants.SplitIndex index3: Int,
                       @SplitScreenConstants.PersistentSnapPosition snapPosition: Int,
                       adapter: RemoteAnimationAdapter) {
            mainExecutor.execute {
                this@SplitScreenController.startTasks(taskId1, options1, taskId2, options2,
                    taskId3, options3, index1, index2, index3, snapPosition, adapter)
            }
        }

        override fun startTask(taskId: Int, index: Int, options: Bundle?) {
            mainExecutor.execute {
                this@SplitScreenController.startTask(taskId, index, options, null)
            }
        }

        override fun startIntent(
            pendingIntent: PendingIntent,
            fillInIntent: Intent?,
            index: Int,
            options: Bundle?
        ) {
            mainExecutor.execute {
                this@SplitScreenController.startIntentToSplit(
                    pendingIntent, -1, fillInIntent, index, options)
            }
        }

        override fun enterSplitScreen(taskId: Int, index: Int) {
            mainExecutor.execute {
                this@SplitScreenController.enterSplitScreen(taskId, index)
            }
        }

        override fun exitSplitScreen(toTopTaskId: Int) {
            mainExecutor.execute {
                this@SplitScreenController.exitSplitScreen(
                    toTopTaskId, EXIT_REASON_UNKNOWN)
            }
        }

        override fun exitSplitScreenOnHide(exitSplitScreenOnHide: Boolean) {
            mainExecutor.execute {
                this@SplitScreenController.exitSplitScreenOnHide(exitSplitScreenOnHide)
            }
        }

        override fun registerSplitScreenListener(
            listener: SplitScreen.StageScreenListener,
            executor: Executor
        ) {
            if (mExecutors.containsKey(listener)) return

            mainExecutor.execute {
                if (mExecutors.isEmpty()) {
                    this@SplitScreenController.registerSplitScreenListener(mListener)
                }

                mExecutors[listener] = executor
            }

            executor.execute {
                mStageCoordinator.sendStatusToListener(listener)
            }
        }

        override fun unregisterSplitScreenListener(listener: SplitScreen.StageScreenListener) {
            mainExecutor.execute {
                mExecutors.remove(listener)

                if (mExecutors.isEmpty()) {
                    this@SplitScreenController.unRegisterSplitScreenListener(mListener)
                }
            }
        }

        override fun goToFullscreenFromSplit() {
            mainExecutor.execute { this@SplitScreenController.goToFullScreenFromSplit() }
        }

        override fun moveSplitToBack() {
            mainExecutor.execute { this@SplitScreenController.moveSplitToBack() }
        }

        override fun restoreSplitToFront() {
            mainExecutor.execute { this@SplitScreenController.restoreSplitToFront() }
        }

        override fun setSplitScreenFocus(index: Int) {
            mainExecutor.execute {
                this@SplitScreenController.setSplitScreenFocus(index)
            }
        }

        override fun moveTaskToFullscreen(taskId: Int) {
            mainExecutor.execute {
                this@SplitScreenController.moveTaskToFullScreen(
                    taskId, EXIT_REASON_FULLSCREEN_REQUEST)
            }
        }

        override fun isTaskInSplitScreen(taskId: Int): Boolean {
            return this@SplitScreenController.isTaskInSplitScreen(taskId)
        }

        override fun getSplitScreenPackageNames(index: Int): List<String>? {
            return this@SplitScreenController.getSplitScreenPackageNames(index)
        }

        override fun setStageDecorBitmap(index: Int, bitmap: Bitmap?) {
            mainExecutor.execute {
                this@SplitScreenController.setStageDecorBitmap(index, bitmap)
            }
        }

        override fun captureSplitScreen(): Bitmap? {
            return this@SplitScreenController.captureSplitScreen()
        }

        override fun setSplitIconProvider(splitIconProvider: SplitIconProvider?) {
            mainExecutor.execute {
                this@SplitScreenController.setSplitIconProvider(splitIconProvider)
            }
        }

    }

//    @BinderThread
//    private class ISplitScreenImpl: ISplitScreenImpl.Stub() {
//
//    }

    companion object {
        private const val TAG: String = "SplitScreenController"

        /**
         * Exit Reason
         */
        const val EXIT_REASON_UNKNOWN: Int = 0;
        const val EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW = 1;
        const val EXIT_REASON_APP_FINISHED = 2;
        const val EXIT_REASON_DRAG_DIVIDER = 3;
        const val EXIT_REASON_RETURN_HOME = 4;
        const val EXIT_REASON_ROOT_TASK_VANISHED = 5;
        const val EXIT_REASON_RECREATE_SPLIT = 6;
        const val EXIT_REASON_FULLSCREEN_REQUEST = 7;

        /**
         * EnterReason
         */
        const val ENTER_REASON_UNKNOWN = 0;
        const val ENTER_REASON_MULTI_INSTANCE = 1;
        const val ENTER_REASON_DRAG = 2;
        const val ENTER_REASON_LAUNCHER = 3;

        @JvmStatic
        fun exitReasonToString(exitReason: Int): String {
            return when(exitReason) {
                EXIT_REASON_UNKNOWN -> "EXIT_REASON_UNKNOWN"
                EXIT_REASON_RETURN_HOME -> "EXIT_REASON_RETURN_HOME"
                EXIT_REASON_DRAG_DIVIDER -> "EXIT_REASON_DRAG_DIVIDER"
                EXIT_REASON_RECREATE_SPLIT -> "EXIT_REASON_RECREATE_SPLIT"
                EXIT_REASON_APP_FINISHED -> "EXIT_REASON_APP_FINISHED"
                EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW -> "EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW"
                EXIT_REASON_ROOT_TASK_VANISHED -> "EXIT_REASON_ROOT_TASK_VANISHED"
                else -> "Unknown reason, reason=$exitReason"
            }
        }
    }
}
