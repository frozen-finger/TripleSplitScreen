package com.android.wm.shell.triplesplit.split.util;


import android.graphics.Rect;

import com.android.wm.shell.triplesplit.split.SplitLayout;
import com.android.wm.shell.triplesplit.split.SplitState;

/**
 * Govern dimming effect on task surface
 */
public class ResizingEffectPolicy {
    private SplitLayout mSplitLayout;
    int mShrinkSide = -1;
    int mDimmingSide = -1;
    float mDimValue = 0.0f;
    /**
     * Content bounds for the app that the divider is moving toward. This is the content that is
     * currently drawn at the start of the divider movement. It stays unchanged.
     */
    final Rect mRetreatingContent = new Rect();
    /**
     * Content bounds for the app that the divider is moving toward. This is the canvas on which
     * an app could potentially be drawn. It changes on every frame as the divider moves.
     */
    final Rect mRetreatingSurface = new Rect();
    /**
     * Content bounds for the app that the divider is moving from. This is the content that is
     * currently drawn at the start of the divider movement. It stays unchanged.
     */
    final Rect mAdvancingContent = new Rect();
    /**
     * Content bounds for the app that the divider is moving from. This is the canvas on which
     * an app could potentially be drawn. It changes on every frame as the divider moves.
     */
    final Rect mAdvancingSurface = new Rect();

    final Rect mTempRect = new Rect();

    public ResizingEffectPolicy(SplitLayout splitLayout) {
        mSplitLayout = splitLayout;
    }

    /**
     * Calculates the dimming values for a task surface nad stores
     * @param position1
     * @param position2
     * @param dividerSnapAlgorithm
     * @param splitState
     */
    void applyDividerPosition(int position1, int position2, DividerSnapAlgorithm dividerSnapAlgorithm,
                              SplitState splitState) {

    }
}
