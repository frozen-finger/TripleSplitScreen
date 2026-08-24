package com.android.wm.shell.triplesplit.split;

import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.NOT_IN_SPLIT;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_100_33_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_100_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_33_100;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_33_66;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_50_50;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_66_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_66_33_2;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_50_50_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_66_33_33;

import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SplitState {
    private @SplitScreenConstants.SplitScreenState int mState = NOT_IN_SPLIT;
    private SplitSpec mSplitSpec;
    private final Set<SplitStateChangeListener> mListeners = new HashSet<>();

    public void  set(@SplitScreenConstants.SplitScreenState int state) {
        mState = state;
        notifyListeners();
    }

    public void set(@SplitScreenConstants.SplitScreenState int state1,
                    @SplitScreenConstants.SplitScreenState int state2) {
        if (state1 != state2) {
            throw new IllegalStateException("Two Divider in different state " +
                    "splitstate1=" + state1
                    + " splitstate2=" + state2);
        }
        mState = state1;
        notifyListeners();
    }

    public @SplitScreenConstants.SplitScreenState int get() {
        return mState;
    }

    public void exit() {
        set(NOT_IN_SPLIT);
    }

    public void setSplitSpec(SplitSpec splitSpec) {
        mSplitSpec = splitSpec;
    }

    public List<RectF> getLayout(@SplitScreenConstants.SplitScreenState int state) {
        return mSplitSpec.getSpec(state);
    }

    public List<RectF> getCurrentLayout(@SplitScreenConstants.SplitScreenState int state) {
        return mSplitSpec.getSpec(mState);
    }

    boolean isOffscreen(Rect rect) {
        if (mSplitSpec == null) {
            throw new IllegalStateException("SplitSpec should not be null");
        }
        return mSplitSpec.isOffScreen(rect);
    }

    public boolean currentStateHasOffscreenApps() {
        return mState == SNAP_TO_3_33_33_100  ||
                mState == SNAP_TO_3_33_33_66 ||
                mState == SNAP_TO_3_33_50_50 ||
                mState == SNAP_TO_3_33_66_33 ||
                mState == SNAP_TO_3_33_100_33 ||
                mState == SNAP_TO_3_33_66_33_2 ||
                mState == SNAP_TO_3_50_50_33 ||
                mState == SNAP_TO_3_66_33_33 ||
                mState == SNAP_TO_3_100_33_33;
    }

    public void registerSplitStateChangeListener(@NonNull SplitStateChangeListener listener) {
        mListeners.add(listener);
    }

    public void unregisterSplitStateChangeListener(@NonNull SplitStateChangeListener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners() {
        for (SplitStateChangeListener listener: mListeners) {
            listener.onSplitStateChanged(mState);
        }
    }

    public interface SplitStateChangeListener {
        void onSplitStateChanged(@SplitScreenConstants.SplitScreenState int splitState);
    }
}
