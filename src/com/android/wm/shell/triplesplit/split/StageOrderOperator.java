package com.android.wm.shell.triplesplit.split;

import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_NONE;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SPLIT_INDEX_1;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SPLIT_INDEX_2;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SPLIT_INDEX_3;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SPLIT_INDEX_UNDEFINED;

import android.content.Context;
import android.util.Log;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SyncTransactionQueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class StageOrderOperator {

    private static final String TAG = StageOrderOperator.class.getSimpleName();

    public Context context;
    public ShellTaskOrganizer taskOrganizer;
    public int displayId;
    public StageTaskListener.StageListenerCallbacks stageCallbacks;
    public SyncTransactionQueue syncQueue;
    private static final int MAX_STAGES = 3;
    private List<Integer> stageIds = List.of(SPLIT_INDEX_1, SPLIT_INDEX_2, SPLIT_INDEX_3);
    private final List<StageTaskListener> activeStages = new ArrayList<StageTaskListener>();
    private final List<StageTaskListener> allStages = new ArrayList<StageTaskListener>();
    private boolean isActive = false;
    private boolean isVisible = false;
    private @SplitScreenConstants.SnapPosition int currentLayout = SNAP_TO_NONE;

    public StageOrderOperator(Context context, ShellTaskOrganizer taskOrganizer, int displayId,
                              StageTaskListener.StageListenerCallbacks callbacks,
                              SyncTransactionQueue syncQueue) {
        this.context = context;
        this.taskOrganizer = taskOrganizer;
        this.displayId = displayId;
        this.syncQueue = syncQueue;
        this.stageCallbacks = callbacks;
        for (int i = 0; i < MAX_STAGES; i ++) {
            allStages.add(new StageTaskListener(context, taskOrganizer, displayId, callbacks,
                    syncQueue, i));
        }
    }

    public StageOrderOperator(Context context, ShellTaskOrganizer taskOrganizer, int displayId,
                              StageTaskListener.StageListenerCallbacks callbacks,
                              SyncTransactionQueue syncQueue, StageTaskListener... stages) {
        this.context = context;
        this.taskOrganizer = taskOrganizer;
        this.displayId = displayId;
        this.syncQueue = syncQueue;
        this.stageCallbacks = callbacks;
        if (stages == null || stages.length != MAX_STAGES) {
            throw new IllegalArgumentException("Expected exactly " + MAX_STAGES + " stages");
        }
        Collections.addAll(allStages, stages);
    }

    public void onEnteringSplit(@SplitScreenConstants.SnapPosition int goingToLayout) {
        if (goingToLayout == currentLayout && activeStages.size() == MAX_STAGES) {
            Log.w(TAG, "Entering split requested same layout=" + currentLayout);
            return;
        }
        currentLayout = goingToLayout;
        activeStages.clear();
        if (currentLayout != SNAP_TO_NONE) {
            activeStages.addAll(allStages);
        }

        Log.d(TAG, "Activated Stage: " + activeStages.size() +
                " ids=" + activeStages.stream());

        isActive = currentLayout != SNAP_TO_NONE;
    }

    public void onExitingSplit() {
        activeStages.clear();
        isActive = false;
    }

    public StageTaskListener getStageForLegacyPosition(@SplitScreenConstants.SplitIndex int index) {
        return getStageForLegacyPosition(index, false);
    }

    public StageTaskListener getStageForLegacyPosition(@SplitScreenConstants.SplitIndex int index,
                                                       boolean checkAllStagesIfNotActive) {
        if (activeStages.size() != 3 && !checkAllStagesIfNotActive) {
            return null;
        }

        List<StageTaskListener> listToCheck = activeStages.isEmpty() &&
                checkAllStagesIfNotActive? allStages:activeStages;
        if (index == SPLIT_INDEX_1) {
            return listToCheck.get(0);
        } else if (index == SPLIT_INDEX_2) {
            return listToCheck.get(1);
        } else if (index == SPLIT_INDEX_3){
            return listToCheck.get(2);
        } else {
            throw new IllegalArgumentException("No stage for invalid position");
        }
    }

    public void onDoubleTappedDivider(int dividerId) {
        if (activeStages.isEmpty()) {
            Log.w(TAG, "Split screen is not active");
            return;
        }
        if (dividerId < activeStages.size()) {
            Collections.swap(activeStages, dividerId - 1, dividerId);
            Collections.swap(allStages, dividerId - 1, dividerId);
        }
    }

    @SplitScreenConstants.SplitIndex
    public int getLegacyIndexForStage(StageTaskListener stage) {
        if (allStages.get(0) == stage) {
            return SPLIT_INDEX_1;
        } else if (allStages.get(1) == stage) {
            return SPLIT_INDEX_2;
        } else {
            return SPLIT_INDEX_3;
        }
    }

    public StageTaskListener getStageForIndex(@SplitScreenConstants.SplitIndex int index) {
        final List<StageTaskListener> listToCheck = isActive?activeStages:allStages;
        if (index == SPLIT_INDEX_1) {
            return listToCheck.get(0);
        } else if (index == SPLIT_INDEX_2) {
            return listToCheck.get(1);
        } else if (index == SPLIT_INDEX_3) {
            return listToCheck.get(2);
        } else {
            throw new IllegalStateException("No stage for the given index");
        }
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isVisible() {
        return isVisible;
    }

    public List<StageTaskListener> getAllStages() {
        return allStages;
    }

    public List<StageTaskListener> getActiveStages() {
        return activeStages;
    }

    public int getLegacyPositionForStage(StageTaskListener stageForToken) {
        final List<StageTaskListener> listToCheck = isActive?activeStages:allStages;
        if (listToCheck.contains(stageForToken)) {
            return listToCheck.indexOf(stageForToken) + 1;
        } else {
            return SPLIT_INDEX_UNDEFINED;
        }
    }
}
