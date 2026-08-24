package com.android.wm.shell.triplesplit.split;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import android.annotation.IntDef;

public class SplitScreenConstants {

    public static final int FADE_DURATION = 133;
    public static final int VEIL_DELAY_DURATION = 300;
    public static final float DEFAULT_OFFSCREEN_DIM = 0.32f;

    public static final int SPLIT_INDEX_UNDEFINED = -1;
    public static final int SPLIT_INDEX_1 = 1;
    public static final int SPLIT_INDEX_2 = 2;
    public static final int SPLIT_INDEX_3 = 3;

    /**
     * Stage Position left{SPLIT_INDEX_1}, middle{SPLIT_INDEX_2},
     * right{SPLIT_INDEX_3}.
     */
    @IntDef(prefix = {"SPLIT_INDEX_"}, value = {
            SPLIT_INDEX_UNDEFINED,
            SPLIT_INDEX_1,
            SPLIT_INDEX_2,
            SPLIT_INDEX_3
    })
    public @interface SplitIndex {
    }

    public static int getIndex(int i) {
        switch (i) {
            case 1: return SPLIT_INDEX_1;
            case 2: return SPLIT_INDEX_2;
            case 3: return SPLIT_INDEX_3;
            default: return SPLIT_INDEX_UNDEFINED;
        }
    }

    /**
     * Snap Targets in different situation. We set the bounds to 33% when stage is offscreen
     * stage 1 offscreen
     * 33 - 33 - 100
     * 33 - 33 - 66
     * 33 - 50 - 50
     * 33 - 66 - 33
     * 33 - 100 - 33
     *
     * stage 1 on screen
     * 	stage 3 on screen
     * 		33 - 33 - 33
     * 	stage 3 offscreen
     * 		33 - 66 - 33
     * 		50 - 50 - 33
     * 		66 - 33 - 33
     * 		100 - 33 - 33
     */

    public static final int NOT_IN_SPLIT = -1;
    public static final int SNAP_TO_3_33_33_100 = 0;
    public static final int SNAP_TO_3_33_10_90 = 1;
    public static final int SNAP_TO_3_33_33_66 = 2;
    public static final int SNAP_TO_3_33_50_50 = 3;
    public static final int SNAP_TO_3_33_66_33 = 4;
    public static final int SNAP_TO_3_33_90_10 = 5;
    public static final int SNAP_TO_3_33_100_33 = 6;
    public static final int SNAP_TO_3_10_45_45 = 7;
    public static final int SNAP_TO_3_33_33_33 = 8;
    public static final int SNAP_TO_3_45_45_10 = 9;
    public static final int SNAP_TO_3_45_10_45 = 10;
    public static final int SNAP_TO_3_10_90_33 = 11;
    public static final int SNAP_TO_3_33_66_33_2 = 12;
    public static final int SNAP_TO_3_50_50_33 = 13;
    public static final int SNAP_TO_3_66_33_33 = 14;
    public static final int SNAP_TO_3_90_10_33 = 15;
    public static final int SNAP_TO_3_100_33_33 = 16;

    public static final int SNAP_TO_NONE = 17;
    public static final int SNAP_TO_START_AND_DISMISS = 18;
    public static final int SNAP_TO_END_AND_DISMISS = 19;
    public static final int SNAP_TO_MINIMIZE = 20;

    public static final int ANIMATING_OFFSCREEN_TAP = 100;

    @IntDef(prefix = { "SNAP_TO_" }, value = {
            SNAP_TO_3_33_33_100,
            SNAP_TO_3_33_33_66,
            SNAP_TO_3_33_50_50,
            SNAP_TO_3_33_66_33,
            SNAP_TO_3_33_100_33,
            SNAP_TO_3_33_33_33,
            SNAP_TO_3_33_66_33_2,
            SNAP_TO_3_50_50_33,
            SNAP_TO_3_66_33_33,
            SNAP_TO_3_100_33_33
    })
    public @interface PersistentSnapPosition {}

    @IntDef(value = {
            NOT_IN_SPLIT, // user is not in split screen
            SNAP_TO_NONE, // in "free snap mode," where apps are fully resizable
            SNAP_TO_3_33_33_100,
            SNAP_TO_3_33_33_66,
            SNAP_TO_3_33_50_50,
            SNAP_TO_3_33_66_33,
            SNAP_TO_3_33_100_33,
            SNAP_TO_3_33_33_33,
            SNAP_TO_3_33_66_33_2,
            SNAP_TO_3_50_50_33,
            SNAP_TO_3_66_33_33,
            SNAP_TO_3_100_33_33,
            ANIMATING_OFFSCREEN_TAP, // user tapped offscreen app to retrieve it
            SNAP_TO_START_AND_DISMISS,
            SNAP_TO_END_AND_DISMISS,
            SNAP_TO_MINIMIZE,
    })
    public @interface SplitScreenState {}

    @IntDef(prefix = { "SNAP_TO_" }, value = {
            SNAP_TO_3_33_33_100,
            SNAP_TO_3_33_33_66,
            SNAP_TO_3_33_50_50,
            SNAP_TO_3_33_66_33,
            SNAP_TO_3_33_100_33,
            SNAP_TO_3_33_33_33,
            SNAP_TO_3_33_66_33_2,
            SNAP_TO_3_50_50_33,
            SNAP_TO_3_66_33_33,
            SNAP_TO_3_100_33_33,
            SNAP_TO_NONE,
            SNAP_TO_START_AND_DISMISS,
            SNAP_TO_END_AND_DISMISS,
            SNAP_TO_MINIMIZE,
    })
    public @interface SnapPosition {}

    public static final int[] CONTROLLED_ACTIVITY_TYPES = {ACTIVITY_TYPE_STANDARD};
    public static final int[] CONTROLLED_WINDOWING_MODES =
            {WINDOWING_MODE_FULLSCREEN, WINDOWING_MODE_UNDEFINED};
    public static final int[] CONTROLLED_WINDOWING_MODES_WHEN_ACTIVE =
            {WINDOWING_MODE_FULLSCREEN, WINDOWING_MODE_UNDEFINED, WINDOWING_MODE_MULTI_WINDOW,
                    WINDOWING_MODE_FREEFORM};

    public static final int FLAG_IS_DIVIDER_BAR = 10;
    public static final int FLAG_IS_DIM_LAYER = 11;

    public static final String splitPositionToString(int pos) {
        switch (pos) {
            case SPLIT_INDEX_UNDEFINED:
                return "SPLIT_INDEX_UNDEFINED";
            case SPLIT_INDEX_1:
                return "SPLIT_INDEX_1";
            case SPLIT_INDEX_2:
                return "SPLIT_INDEX_2";
            case SPLIT_INDEX_3:
                return "SPLIT_INDEX_3";
            default:
                return "UNKNOWN";
        }
    }
}
