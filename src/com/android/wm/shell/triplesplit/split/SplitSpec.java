package com.android.wm.shell.triplesplit.split;

import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_100_33_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_100_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_33_100;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_33_66;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_50_50;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_66_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_66_33_2;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_50_50_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_66_33_33;
import static com.android.wm.shell.triplesplit.split.util.DividerSnapAlgorithm.SNAP_FLEXIBLE_HYBRID;
import static com.android.wm.shell.triplesplit.split.util.DividerSnapAlgorithm.SNAP_MODE_FIXED_RATIO;
import static com.android.wm.shell.triplesplit.split.util.DividerSnapAlgorithm.SNAP_ONLY_1_1;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_33_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_END_AND_DISMISS;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_MINIMIZE;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_START_AND_DISMISS;

import android.graphics.Rect;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SplitSpec {
    private static final String TAG = SplitSpec.class.getSimpleName();

    private static final float ONSCREEN_SYMMETRIC_RATIO = 0.33f;
    private static final float MIDDLE_RATIO = 0.5f;

//    public static final List<Integer> ONE_TARGET = List.of(SNAP_TO_3_33_33_100, SNAP_TO_3_10_50_50);
    public static final List<Integer> ONE_TARGET_MINIMIZED = List.of(SNAP_TO_MINIMIZE);
    public static final List<Integer> THREE_TARGETS_ONSCREEN =
            List.of(SNAP_TO_3_33_33_66, SNAP_TO_3_33_50_50, SNAP_TO_3_33_66_33,
                    SNAP_TO_3_33_33_33, SNAP_TO_3_33_66_33_2, SNAP_TO_3_50_50_33,
                    SNAP_TO_3_66_33_33);
    public static final List<Integer> FIVE_TARGETS_OFFSCREEN =
            List.of(SNAP_TO_3_33_33_33);
    public static final List<Integer> DISMISS_TARGETS =
            List.of(SNAP_TO_START_AND_DISMISS, SNAP_TO_END_AND_DISMISS);

    public static final List<Integer> HYBRID_FLEXIBLE_TARGETS =
            List.of(SNAP_TO_3_33_33_100, SNAP_TO_3_33_33_66, SNAP_TO_3_33_50_50,
                    SNAP_TO_3_33_66_33, SNAP_TO_3_33_100_33, SNAP_TO_3_33_33_33,
                    SNAP_TO_3_33_66_33_2, SNAP_TO_3_50_50_33, SNAP_TO_3_66_33_33,
                    SNAP_TO_3_100_33_33);

    public final Rect mDisplayBounds;
    private final RectF mUsableArea;
    private final float mHalfDiv;

    private final Map<Integer, List<RectF>> mLayouts = new HashMap<>();

    public SplitSpec(Rect displayBounds, int dividerSize, Rect pinnedTaskInsets) {
        mDisplayBounds = new Rect(displayBounds);
        mUsableArea = new RectF(displayBounds);
        mUsableArea.left += pinnedTaskInsets.left;
        mUsableArea.top += pinnedTaskInsets.top;
        mUsableArea.right += pinnedTaskInsets.right;
        mUsableArea.bottom += pinnedTaskInsets.bottom;
        mHalfDiv = dividerSize / 2f;

        float s = mUsableArea.left;
        float e = mUsableArea.right;
        float l = e - s;
        float divPos1;
        float divPos2;
        float leftMargin;
        float rightMargin;

        //Add AppLayout according to hybrid flexible targets
        //33 33 100
        divPos1 = s - l * ONSCREEN_SYMMETRIC_RATIO;
        divPos2 = s;
        leftMargin = s - l * (1 - ONSCREEN_SYMMETRIC_RATIO);
        rightMargin = e;
        createAppLayout(SNAP_TO_3_33_33_100, leftMargin, rightMargin,divPos1, divPos2);

        //33 33 66
        divPos1 = s;
        divPos2 = s + l * ONSCREEN_SYMMETRIC_RATIO;
        leftMargin = s - l * ONSCREEN_SYMMETRIC_RATIO;
        rightMargin = e;
        createAppLayout(SNAP_TO_3_33_33_66, leftMargin, rightMargin, divPos1, divPos2);

        //33 50 50
        divPos1 = s;
        divPos2 = s + l * MIDDLE_RATIO;
        leftMargin = s - l * ONSCREEN_SYMMETRIC_RATIO;
        rightMargin = e;
        createAppLayout(SNAP_TO_3_33_50_50, leftMargin, rightMargin, divPos1, divPos2);

        //33 66 33
        divPos1 = s;
        divPos2 = s + l * (1 - ONSCREEN_SYMMETRIC_RATIO);
        leftMargin = s - l * ONSCREEN_SYMMETRIC_RATIO;
        rightMargin = e;
        createAppLayout(SNAP_TO_3_33_66_33, leftMargin, rightMargin, divPos1, divPos2);

        //33 100 33
        divPos1 = s;
        divPos2 = e;
        leftMargin = s - l * ONSCREEN_SYMMETRIC_RATIO;
        rightMargin = e + l * ONSCREEN_SYMMETRIC_RATIO;
        createAppLayout(SNAP_TO_3_33_100_33, leftMargin, rightMargin, divPos1, divPos2);

        //33 33 33
        divPos1 = s + l * ONSCREEN_SYMMETRIC_RATIO;
        divPos2 = s + l * ONSCREEN_SYMMETRIC_RATIO * 2;
        leftMargin = s;
        rightMargin = e;
        createAppLayout(SNAP_TO_3_33_33_33, leftMargin, rightMargin, divPos1, divPos2);

        //|33 66| 33
        divPos1 = s + l * ONSCREEN_SYMMETRIC_RATIO;
        divPos2 = e;
        leftMargin = s;
        rightMargin = e + l * ONSCREEN_SYMMETRIC_RATIO;
        createAppLayout(SNAP_TO_3_33_66_33_2, leftMargin, rightMargin, divPos1, divPos2);

        //50 50 33
        divPos1 = s + l * MIDDLE_RATIO;
        divPos2 = e;
        leftMargin = s;
        rightMargin = e + l * ONSCREEN_SYMMETRIC_RATIO;
        createAppLayout(SNAP_TO_3_50_50_33, leftMargin, rightMargin, divPos1, divPos2);

        //66 33 33
        divPos1 = s + l * (1 - ONSCREEN_SYMMETRIC_RATIO);
        divPos2 = e;
        leftMargin = s;
        rightMargin = e + l * ONSCREEN_SYMMETRIC_RATIO;
        createAppLayout(SNAP_TO_3_66_33_33, leftMargin, rightMargin, divPos1, divPos2);

        //100 33 33
        divPos1 = e;
        divPos2 = e + l * ONSCREEN_SYMMETRIC_RATIO;
        leftMargin = s;
        rightMargin = e + l * ONSCREEN_SYMMETRIC_RATIO;
        createAppLayout(SNAP_TO_3_100_33_33, leftMargin, rightMargin, divPos1, divPos2);
    }

//    private void createAppLayout(@SplitScreenConstants.SplitScreenState int state, float divPos) {
//        List<RectF> list = new ArrayList<>();
//        RectF rect1 = new RectF(mUsableArea);
//        RectF rect2 = new RectF(mUsableArea);
//        rect1.right = divPos - mHalfDiv;
//        rect2.left = divPos + mHalfDiv;
//        list.add(rect1);
//        list.add(rect2);
//        mLayouts.put(state, list);
//    }

    private void createAppLayout(@SplitScreenConstants.SplitScreenState int state,
                                 float divPos1, float divPos2) {
        List<RectF> list = new ArrayList<>();
        RectF rect1 = new RectF(mUsableArea);
        RectF rect2 = new RectF(mUsableArea);
        RectF rect3 = new RectF(mUsableArea);
        rect1.right = divPos1 - mHalfDiv;
        rect2.left = divPos1 + mHalfDiv;
        rect2.right = divPos2 - mHalfDiv;
        rect3.left  = divPos2 + mHalfDiv;
        list.add(rect1);
        list.add(rect2);
        list.add(rect3);
        mLayouts.put(state, list);
    }

    private void createAppLayout(@SplitScreenConstants.SplitScreenState int state, float leftMargin,
                                 float rightMargin, float divPos1, float divPos2) {
        List<RectF> list = new ArrayList<>();
        RectF rect1 = new RectF(mUsableArea);
        RectF rect2 = new RectF(mUsableArea);
        RectF rect3 = new RectF(mUsableArea);
        rect1.left = leftMargin;
        rect1.right = divPos1 - mHalfDiv;
        rect2.left = divPos1 + mHalfDiv;
        rect2.right = divPos2 - mHalfDiv;
        rect3.left  = divPos2 + mHalfDiv;
        rect3.right = rightMargin;
        list.add(rect1);
        list.add(rect2);
        list.add(rect3);
        mLayouts.put(state, list);
    }

    List<RectF> getSpec(@SplitScreenConstants.SplitScreenState int state) {
        return mLayouts.get(state);
    }

    boolean isOffScreen(Rect rect) {
        return !mDisplayBounds.contains(rect);
    }

    public static List<Integer> getSnapTargetLayout(int snapMode) {
        switch (snapMode) {
//            case SNAP_ONLY_1_1:
//                return ONE_TARGET;
            case SNAP_MODE_FIXED_RATIO:
                return THREE_TARGETS_ONSCREEN;
            case SNAP_FLEXIBLE_HYBRID:
                return HYBRID_FLEXIBLE_TARGETS;
            default:
                throw new IllegalStateException("unrecognized snap mode");
        }
    }
}
