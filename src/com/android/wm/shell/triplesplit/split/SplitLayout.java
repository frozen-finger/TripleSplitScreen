package com.android.wm.shell.triplesplit.split;


import static android.content.res.Configuration.SCREEN_HEIGHT_DP_UNDEFINED;
import static android.content.res.Configuration.SCREEN_WIDTH_DP_UNDEFINED;
import static com.android.internal.jank.InteractionJankMonitor.CUJ_SPLIT_SCREEN_DOUBLE_TAP_DIVIDER;
import static com.android.internal.jank.InteractionJankMonitor.CUJ_SPLIT_SCREEN_RESIZE;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.ANIMATING_OFFSCREEN_TAP;
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
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_START_AND_DISMISS;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SPLIT_INDEX_UNDEFINED;
import static com.android.wm.shell.triplesplit.split.util.SplitUtilConstants.EMPHASIZED;
import static com.android.wm.shell.triplesplit.split.util.SplitUtilConstants.EXIT_REASON_DRAG_DIVIDER;
import static com.android.wm.shell.triplesplit.split.util.SplitUtilConstants.FAST_OUT_SLOW_IN;
import static com.android.wm.shell.triplesplit.split.util.SplitUtilConstants.LINEAR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.Region;
import android.health.connect.datatypes.SleepSessionRecord;
import android.media.MediaMetrics;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Display;
import android.view.InsetsController;
import android.view.InsetsSourceControl;
import android.view.InsetsState;
import android.view.RoundedCorner;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.view.animation.Interpolator;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.triplesplit.split.util.DockedDividerUtils;
import com.android.wm.shell.triplesplit.R;
import com.android.wm.shell.triplesplit.split.util.DividerSnapAlgorithm;
import com.android.wm.shell.triplesplit.split.util.ResizingEffectPolicy;
import com.android.wm.shell.triplesplit.split.view.OffscreenTouchZone;
import com.android.wm.shell.triplesplit.split.SplitWindowManager.ParentContainerCallbacks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

public class SplitLayout implements DisplayInsetsController.OnInsetsChangedListener {
    private static final String TAG = SplitLayout.class.getSimpleName();
    /* parallax */
    public static final int PARALLAX_NONE = 0;
    public static final int PARALLAX_DISMISSED = 1;
    public static final int PARALLAX_ALIGN_CENTER = 2;
    /*Fling spec*/
    public static final int FLING_RESIZE_DURATION = 320;
    private static final int FLING_ENTER_DURATION = 450;
    private static final int FLING_EXIT_DURATION = 450;
    private static final int FLING_OFFSCREEN_DURATION = 500;
    private static final boolean ENABLE_OFFSCREEN_TOUCH_ZONES = false;
    /*layer def during movement*/
    public static final int ANIMATING_DIVIDER_LAYER = 0;
    public static final int ANIMATING_FRONT_APP_VEIL_LAYER = ANIMATING_DIVIDER_LAYER + 20;
    public static final int ANIMATING_FRONT_APP_LAYER = ANIMATING_DIVIDER_LAYER + 10;
    public static final int ANIMATING_BACK_APP_VEIL_LAYER = ANIMATING_DIVIDER_LAYER - 10;
    public static final int ANIMATING_BACK_APP_LAYER = ANIMATING_DIVIDER_LAYER - 20;
    public static final int RESTING_DIVIDER_LAYER = Integer.MAX_VALUE;
    public static final int RESTING_TOUCHING_LAYER = Integer.MAX_VALUE;
    public static final int RESTING_DIM_LAYER = RESTING_TOUCHING_LAYER - 1;
    /* swap spec*/
    private static final int SWAP_ANIMATION_TOTAL_DURATION = 500;
    private static final float SWAP_ANIMATION_SHRINK_DURATION = 83;
    private static final float SWAP_ANIMATION_SHRINK_MARGIN_DP = 14;
    /** Keeps divider input away from the edge-back gesture area without moving stage bounds. */
    private static final int DIVIDER_EDGE_GESTURE_INSET_DP = 5;
    private static final PathInterpolator SHRINK_INTERPOLATOR =
            new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final PathInterpolator GROW_INTERPOLATOR =
            new PathInterpolator(0.45f, 0f, 0.5f, 1f);
    private final Handler mHandler;
    private final SplitState mSplitState;
    private int mDividerWindowWidth;
    private int mDividerInsets;
    private int mDividerSize;
    /** Pixel distance reserved at both display edges so divider touch does not compete with Back. */
    private int mDividerEdgeGestureInset;
    private int mOffscreenTouchZoneWidth;
    private AnimatorSet mSwapAnimator;
    private int mSwapDivider = -1;

    private final Rect mTempRect = new Rect();
    private final Rect mTempRect1 = new Rect();
    private final Rect mTempRect2 = new Rect();
    private final Rect mRootBounds = new Rect();
    private final Rect mDividerBounds1 = new Rect();
    private final Rect mDividerBounds2 = new Rect();

    /**
     * List of stage bounds, in order from left to right, sizes of app surfaces.
     */
    private final List<Rect> mStageBounds = List.of(new Rect(), new Rect(), new Rect());
    /**
     * sizes of apps' rendered areas
     */
    private final List<Rect> mContentBounds = List.of(new Rect(), new Rect(), new Rect());
    /**
     * Invisible bounds for stage, contains left offscreen invisible bounds
     */
    private final Rect mInvisibleBoundLeft = new Rect();
    /**
     * right offscreen invisible bounds
     */
    private final Rect mInvisibleBoundRight = new Rect();
    /**
     * Areas for user touch to bring offscreen stage back to screen, n area to n touch zone.
     */
    private final List<OffscreenTouchZone> mOffscreenTouchZone = new ArrayList<OffscreenTouchZone>();
    private final SplitLayoutHandler mSplitLayoutHandler;
    private final SplitWindowManager mSplitWindowManager1;
    private final SplitWindowManager mSplitWindowManager2;
    private final DisplayController mDisplayController;
    private final DisplayImeController mDisplayImeController;
    private final ParentContainerCallbacks mParentContainerCallbacks;
    private final ImePositionProcessor mImePositionProcessor;
    private final ResizingEffectPolicy mSurfaceEffectPolicy;
    private final ShellTaskOrganizer mTaskOrganizer;
    private final InsetsState mInsetsState = new InsetsState();

    private Context mContext;
    DividerSnapAlgorithm mDividerSnapAlgorithm;
    private WindowContainerToken mWinToken1;
    private WindowContainerToken mWinToken2;
    private WindowContainerToken mWinToken3;
    private int mDividerPosition1;
    private int mDividerPosition2;
    private int mDraggingDividerPosition1;
    private int mDraggingDividerPosition2;
    private int mDragStartDividerPosition1;
    private int mDragStartDividerPosition2;
    private int mDragStartVisualDividerPosition1;
    private int mDragStartVisualDividerPosition2;
    private boolean mInitialized = false;
    private boolean mFreezeDividerWindow = false;
//    private boolean mIsLargeScreen = false;
    private int mDensity;

    private int mUiMode;

    private final InteractionJankMonitor mInteractionJankMonitor;
    private ValueAnimator mDividerFlingAnimator;

    private int mMovingDivider;

    public SplitLayout(String windowName, Context context, Configuration config,
                       SplitLayoutHandler splitLayoutHandler,
                       ParentContainerCallbacks parentContainerCallbacks,
                       DisplayController displayController,
                       DisplayImeController displayImeController,
                       ShellTaskOrganizer taskOrganizer, SplitState splitState,
                       Handler handler) {
        mHandler = handler;
        mContext = context.createConfigurationContext(config);
        mDensity = config.densityDpi;
        mUiMode = config.uiMode;
        mSplitLayoutHandler = splitLayoutHandler;
        mDisplayController = displayController;
        mDisplayImeController = displayImeController;
        mParentContainerCallbacks = parentContainerCallbacks;
        mSplitWindowManager1 = new SplitWindowManager(windowName, context, config,
                mParentContainerCallbacks, 1);
        mSplitWindowManager2 = new SplitWindowManager(windowName, context, config,
                mParentContainerCallbacks, 2);
        mTaskOrganizer = taskOrganizer;
        mImePositionProcessor = new ImePositionProcessor(mContext.getDisplayId());
        mSurfaceEffectPolicy = new ResizingEffectPolicy(this);
        mSplitState = splitState;

        updateDividerConfig(mContext);
        mRootBounds.set(config.windowConfiguration.getBounds());
        updateLayouts();
        mInteractionJankMonitor = InteractionJankMonitor.getInstance();
        resetDividerPosition();
        updateInvisibleRect();
    }

    private void updateDividerConfig(Context context) {
        final Resources resources = context.getResources();
        final Display display = context.getDisplay();
        final int dividerInset = resources.getDimensionPixelSize(
                com.android.internal.R.dimen.docked_stack_divider_insets);
        int radius = 0;
        RoundedCorner corner = display.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
        radius = corner != null ? Math.max(radius, corner.getRadius()) : radius;
        corner = display.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);
        radius = corner != null ? Math.max(radius, corner.getRadius()) : radius;
        corner = display.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);
        radius = corner != null ? Math.max(radius, corner.getRadius()) : radius;
        corner = display.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
        radius = corner != null ? Math.max(radius, corner.getRadius()) : radius;

        mDividerSize = resources.getDimensionPixelSize(R.dimen.split_divider_bar_width);
        final int touchRegionWidth =
                resources.getDimensionPixelSize(R.dimen.split_divider_handle_region_width);
        final int touchRegionInset = Math.max(0, (touchRegionWidth - mDividerSize) / 2);
        // Keep the visible divider narrow, but make the window/touch region finger-friendly.
        mDividerInsets = Math.max(Math.max(dividerInset, radius), touchRegionInset);
        mDividerWindowWidth = mDividerSize + 2 * mDividerInsets;
        mDividerEdgeGestureInset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                DIVIDER_EDGE_GESTURE_INSET_DP, resources.getDisplayMetrics());
        mOffscreenTouchZoneWidth =
                resources.getDimensionPixelSize(R.dimen.split_offscreen_touch_zone_width);
    }

    public Rect getDisplayStableInsets(Context context) {
        final DisplayLayout displayLayout =
                mDisplayController.getDisplayLayout(context.getDisplayId());
        return displayLayout != null
                ? displayLayout.stableInsets()
                : context.getSystemService(WindowManager.class)
                .getMaximumWindowMetrics()
                .getWindowInsets()
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()
                | WindowInsets.Type.displayCutout())
                .toRect();
    }

    public Rect getLeftBounds() {
        return mStageBounds.get(0);
    }

    public Rect getMiddleBounds() {return  mStageBounds.get(1);}

    public Rect getRightBounds() {
        return mStageBounds.get(2);
    }

    public Rect getLeftRefBounds() {
        Rect outBounds = getLeftBounds();
        outBounds.offset(-mRootBounds.left, -mRootBounds.top);
        return outBounds;
    }

    public Rect getMiddleRefBounds() {
        Rect outBounds = getMiddleBounds();
        outBounds.offset(-mRootBounds.left, -mRootBounds.top);
        return outBounds;
    }

    public Rect getRightRefBounds() {
        Rect outBounds = getRightBounds();
        outBounds.offset(-mRootBounds.left, -mRootBounds.top);
        return outBounds;
    }

    public Rect getRootBounds() {
        return new Rect(mRootBounds);
    }

    /**
     * Insets divider touch from the physical display edge to avoid system back gesture capture.
     * This affects only input regions; divider/stage logical positions still use raw bounds.
     */
    public int getDividerEdgeGestureInset() {
        return mDividerEdgeGestureInset;
    }

    public void copyLeftBounds(Rect rect) {
        rect.set(getLeftBounds());
    }

    /** Copies the top/left bounds to the provided Rect (parent-based coordinates). */
    public void copyLeftRefBounds(Rect rect) {
        copyLeftBounds(rect);
        rect.offset(-mRootBounds.left, -mRootBounds.top);
    }

    /** Copies the bottom/right bounds to the provided Rect (screen-based coordinates). */
    public void copyRightBounds(Rect rect) {
        rect.set(getRightBounds());
    }

    /** Copies the bottom/right bounds to the provided Rect (parent-based coordinates). */
    public void copyRightRefBounds(Rect rect) {
        copyRightBounds(rect);
        rect.offset(-mRootBounds.left, -mRootBounds.top);
    }

    public void copyMiddleBounds(Rect rect) {rect.set(getMiddleBounds());}
    public void copyMiddleRefBounds(Rect rect) {
        copyMiddleBounds(rect);
        rect.offset(-mRootBounds.left, -mRootBounds.top);
    }

    public void copyLeftContentBounds(Rect rect) {
        rect.set(getLeftContentBounds());
    }

    public void copyMiddleContentBounds(Rect rect) {
        rect.set(getMiddleContentBounds());
    }

    public void copyRightContentBounds(Rect rect) {
        rect.set(getRightContentBounds());
    }

    public Rect getLeftContentBounds() {
        return mContentBounds.get(0);
    }
    public void setLeftContentBounds(Rect rect) {mContentBounds.get(0).set(rect);}

    public Rect getRightContentBounds() {
        return mContentBounds.get(2);
    }

    public void setRightContentBounds(Rect rect) {mContentBounds.get(2).set(rect);}

    public Rect getMiddleContentBounds() {
        return mContentBounds.get(1);
    }

    public void setMiddleContentBounds(Rect rect) {mContentBounds.get(1).set(rect);}

    public Rect getDividerBounds(boolean leftDivider) {
        return leftDivider ? new Rect(mDividerBounds1) : new Rect(mDividerBounds2);
    }

    public Rect getRefDividerBounds(boolean leftDivider, Rect rect) {
        rect.set(getDividerBounds(leftDivider));
        rect.offset(-mRootBounds.left, -mRootBounds.top);
        return rect;
    }

    public Rect getRefDividerBounds(boolean leftDivider) {
        Rect res = getDividerBounds(leftDivider);
        res.offset(-mRootBounds.left, -mRootBounds.top);
        return res;
    }

    public void getLeftInvisibleBound(Rect rect) {
        rect.set(mInvisibleBoundLeft);
    }

    public void getRightInvisibleBound(Rect rect) {
        rect.set(mInvisibleBoundRight);
    }

    @Nullable
    public SurfaceControl getLeftDividerLeash() {
        return mSplitWindowManager1 == null ? null : mSplitWindowManager1.getSurfaceControl();
    }

    @Nullable
    public SurfaceControl getRightDividerLeash() {
        return mSplitWindowManager2 == null ? null : mSplitWindowManager2.getSurfaceControl();
    }

    int getLeftDividerPosition() {
        return mDividerPosition1;
    }

    int getRightDividerPosition() {
        return mDividerPosition2;
    }

    public int calculateCurrentSnapPosition() {
        return mDividerSnapAlgorithm.calculateSnapPosition(mDividerPosition1, mDividerPosition2);
    }

    public void updateStateWithCurrentPosition() {
        mSplitState.set(calculateCurrentSnapPosition());
    }

    public float getDividerPositionAsFraction() {
        return Math.min(1f, Math.max(0f, (getLeftBounds().right + getRightBounds().left)
                            / 2f / getDisplayWidth()));
    }

    public int getDividerPosition(int id) {
        if (id == 1) {
            return getLeftDividerPosition();
        } else {
            return getRightDividerPosition();
        }
    }

    public int getDraggingDividerPosition(int id) {
        return id == 1 ? mDraggingDividerPosition1 : mDraggingDividerPosition2;
    }

    public int getDraggingLeftDividerPosition() {
        return mDraggingDividerPosition1;
    }

    public int getDraggingRightDividerPosition() {
        return mDraggingDividerPosition2;
    }

    /**
     * Returns the visual x-position of the divider bar used only as a drag anchor.
     *
     * Some snap targets keep the logical divider at root.right/root.left while the visible bar is
     * shifted inward so it remains touchable. Persisted split state still uses logical snap targets;
     * this value only prevents the first drag delta from consuming the hidden offset.
     */
    public int getDividerVisualPositionForTouch(int id) {
        final Rect dividerBounds = getDividerBounds(id == 1);
        return dividerBounds.left + mDividerInsets - mRootBounds.left;
    }

    public int getMovingDividerPosition() {
        if (mMovingDivider == 1) {
            return getLeftDividerPosition();
        } else {
            return getRightDividerPosition();
        }
    }

    private void updateInvisibleRect() {
        final Rect rect = new Rect(mRootBounds.left, mRootBounds.top,
                mRootBounds.right / 2, mRootBounds.bottom);
        rect.offset(mRootBounds.right, 0);
        mInvisibleBoundRight.set(rect);
        rect.offset(-mRootBounds.right * 2, 0);
        mInvisibleBoundLeft.set(rect);
    }

    public void populateTouchZones() {
        if (!mOffscreenTouchZone.isEmpty()) {
            removeTouchZones();
        }

        final int declaredPosition = mSplitState.get();
        final int resolvedPosition = calculateCurrentSnapPosition();
        int currentPosition = resolvedPosition;
        if (declaredPosition != resolvedPosition) {
            Log.w(TAG, "populateTouchZones state mismatch declared=" + declaredPosition
                    + " resolved=" + resolvedPosition);
            if (declaredPosition != ANIMATING_OFFSCREEN_TAP) {
                mSplitState.set(resolvedPosition);
            }
        }

        Log.i(TAG, "populateTouchZones declaredState=" + declaredPosition
                + " resolvedState=" + resolvedPosition
                + " divider1=" + mDividerPosition1 + " divider2=" + mDividerPosition2
                + " rootBounds=" + mRootBounds);

        if (!ENABLE_OFFSCREEN_TOUCH_ZONES) {
            Log.i(TAG, "populateTouchZones disabled; use divider bars to restore offscreen stages");
            return;
        }

        switch (currentPosition) {
            case SNAP_TO_3_33_33_66:
            case SNAP_TO_3_33_66_33:
            case SNAP_TO_3_33_50_50:
                addOffscreenTouchZone(1, getLeftEdgeTouchZoneBounds(),
                        () -> flingDividerToOtherSide(1, currentPosition));
                break;
            case SNAP_TO_3_33_66_33_2:
            case SNAP_TO_3_66_33_33:
            case SNAP_TO_3_50_50_33:
                addOffscreenTouchZone(3, getRightEdgeTouchZoneBounds(),
                        () -> flingDividerToOtherSide(2, currentPosition));
                break;
            case SNAP_TO_3_33_33_100:
                addOffscreenTouchZone(2, getLeftEdgeTouchZoneBounds(),
                        () -> flingDividerToOtherSide(2, currentPosition));
                break;
            case SNAP_TO_3_100_33_33:
                addOffscreenTouchZone(2, getRightEdgeTouchZoneBounds(),
                        () -> flingDividerToOtherSide(1, currentPosition));
                break;
        }
        if (currentPosition == SNAP_TO_3_33_100_33) {
            addOffscreenTouchZone(1, getLeftEdgeTouchZoneBounds(),
                    () -> flingDividerToOtherSide(1, currentPosition));
            addOffscreenTouchZone(3, getRightEdgeTouchZoneBounds(),
                    () -> flingDividerToOtherSide(2, currentPosition));
        }
        Log.i(TAG, "populateTouchZones count=" + mOffscreenTouchZone.size());
        mOffscreenTouchZone.forEach(mParentContainerCallbacks::inflateOnStageRoot);
    }

    public void removeTouchZones() {
        Log.i(TAG, "removeTouchZones count=" + mOffscreenTouchZone.size());
        mOffscreenTouchZone.forEach(touchZone -> {
            touchZone.release();
        });
        mOffscreenTouchZone.clear();
    }

    private void addOffscreenTouchZone(int index, Rect bounds, Runnable action) {
        Log.i(TAG, "addOffscreenTouchZone index=" + index + " bounds=" + bounds);
        mOffscreenTouchZone.add(new OffscreenTouchZone(index, bounds, action));
    }

    private Rect getLeftEdgeTouchZoneBounds() {
        return new Rect(mRootBounds.left, mRootBounds.top,
                Math.min(mRootBounds.right, mRootBounds.left + mOffscreenTouchZoneWidth),
                mRootBounds.bottom);
    }

    private Rect getRightEdgeTouchZoneBounds() {
        return new Rect(Math.max(mRootBounds.left, mRootBounds.right - mOffscreenTouchZoneWidth),
                mRootBounds.top, mRootBounds.right, mRootBounds.bottom);
    }

    public boolean updateConfiguration(Configuration config, int displayId) {
        final Rect rootBounds = config.windowConfiguration.getBounds();
        final int density = config.densityDpi;
        final int uiMode = config.uiMode;

        if (density == mDensity && mUiMode == uiMode && mRootBounds.equals(rootBounds)) {
            return false;
        }

        final Context displayContext = mContext.createConfigurationContext(config);
        mSplitWindowManager1.setConfiguration(config);
        mSplitWindowManager2.setConfiguration(config);
        mUiMode = uiMode;
        mDensity = density;
        mTempRect.set(mRootBounds);
        mRootBounds.set(rootBounds);
        updateDividerConfig(mContext);
        initDividerPosition(mTempRect);
        updateLayouts();updateInvisibleRect();

        return true;
    }

    public void initDividerPosition(Rect bounds) {
        final float snapRatio1 = (float) mDividerPosition1 /
                (float) (bounds.width());
        final float snapRatio2 = (float) mDividerPosition2 /
                (float) (bounds.width());
        final float length = (float) mRootBounds.width();
        int estimatedPosition1 = (int)(length * snapRatio1);
        int estimatedPosition2 = (int)(length * snapRatio2);
        Pair<DividerSnapAlgorithm.SnapTarget, DividerSnapAlgorithm.SnapTarget> snaps =
                mDividerSnapAlgorithm.calculateNonDismissSnapTarget(estimatedPosition1, estimatedPosition2);
        mDividerPosition1 = snaps.first.position;
        mDividerPosition2 = snaps.second.position;
        updateBounds(mDividerPosition1, mDividerPosition2);
        mSplitState.set(snaps.first.snapPosition, snaps.second.snapPosition);
    }

    /**
     * Update divider and stage bounds according to new position.
     * Final stage bounds will between mRootBounds.left and mRootBounds.right
     * @param position1 left divider position
     * @param position2 right divider positioin
     */
    public void updateBounds(int position1, int position2) {
        updateBounds(position1, position2, getLeftBounds(), getMiddleBounds(), getRightBounds(),
                mDividerBounds1, mDividerBounds2, true);
    }

    /**
     * Update divider and stage bounds just like updateBounds(position1, position2),
     * but only take one divider's position
     * @param left whether divider is left or right
     * @param position divider's position
     */
    private void updateBounds(boolean left, int position) {
        if (left) {
            updateBounds(position, getRightDividerPosition(), getLeftBounds(), getMiddleBounds(),
                    getRightBounds(), mDividerBounds1, mDividerBounds2, true);
        } else {
            updateBounds(getLeftDividerPosition(), position, getLeftBounds(), getMiddleBounds(),
                    getRightBounds(), mDividerBounds1, mDividerBounds2, true);
        }
    }

    /**
     * Update all three bounds according to mRootBounds and input divider position
     * @param position1 left divider's position
     * @param position2 right divider's position
     * @param bounds1 left stage's bounds
     * @param bounds2 middle stage's bounds
     * @param bounds3 right stage's bounds
     * @param dividerBounds1 left divider's bounds
     * @param dividerBounds2 right divider's bounds
     * @param setEffectBounds
     */
    private void updateBounds(int position1, int position2, Rect bounds1, Rect bounds2, Rect bounds3,
                              Rect dividerBounds1, Rect dividerBounds2, boolean setEffectBounds) {
        Log.i(TAG, "Update Bounds, divider1 pos=" + position1 + " divider pos2=" + position2 +
                " bounds1=" + bounds1 + " bounds2=" + bounds2 + " bounds3=" + bounds3 +
                " dividerBounds1=" + mDividerBounds1 + " dividerBounds2=" + mDividerBounds2);
        if (position1 >= position2) {
            Log.w(TAG, "Divider 1 position must be smaller than divider 2!");
        }

        dividerBounds1.set(mRootBounds);
        dividerBounds2.set(mRootBounds);
        bounds1.set(mRootBounds);
        bounds2.set(mRootBounds);
        bounds3.set(mRootBounds);
        position1 += mRootBounds.left;
        dividerBounds1.left = position1 - mDividerInsets;
        dividerBounds1.right = dividerBounds1.left + mDividerWindowWidth;
        position2 += mRootBounds.left;
        dividerBounds2.left = position2 - mDividerInsets;
        dividerBounds2.right = dividerBounds2.left + mDividerWindowWidth;
        bounds1.right = position1;
        bounds2.left = bounds1.right + mDividerSize;
        bounds2.right = position2;
        bounds3.left = bounds2.right + mDividerSize;
        int leftFlexTargetPos = mDividerSnapAlgorithm.getFirstSplitTarget().first.position;
        int rightFlexTargetPos = mDividerSnapAlgorithm.getLastSplitTarget().second.position;
        int sizeOf33App = mRootBounds.width() / 3;
        if (position1 <= leftFlexTargetPos) {
            bounds1.left = position1 - sizeOf33App;
        }
        if (position2 >= rightFlexTargetPos) {
            bounds3.right = position2 + sizeOf33App;
        }
        if (shouldKeepRightDividerVisible(position2, bounds2, bounds3)) {
            pinRightDividerToRootEdge(dividerBounds2);
        }
        ensureAtLeastOneDividerVisible(position1, dividerBounds1, dividerBounds2);

        DockedDividerUtils.sanitizeStackBounds(bounds1, 1);
        DockedDividerUtils.sanitizeStackBounds(bounds2, 2);
        DockedDividerUtils.sanitizeStackBounds(bounds3, 3);
        updateContentBounds();
//        if (setEffectBounds) {
//            mSurfaceEffectPolicy.applyDividerPosition(position1, position2,
//                    mDividerSnapAlgorithm, mSplitState);
//        }
        Log.i(TAG, "After Update bounds1=" + bounds1 + " bounds2=" + bounds2
                + " bounds3=" + bounds3 + " leftDividerBounds=" + mDividerBounds1 +
                " rightDividerBounds=" + mDividerBounds2);
    }

    private boolean shouldKeepRightDividerVisible(int rightDividerPosition, Rect middleBounds,
                                                  Rect rightBounds) {
        return rightDividerPosition >= mRootBounds.right
                || (middleBounds.right <= mRootBounds.right
                && rightBounds.left >= mRootBounds.right);
    }

    private void ensureAtLeastOneDividerVisible(int leftDividerPosition, Rect dividerBounds1,
                                                Rect dividerBounds2) {
        if (Rect.intersects(mRootBounds, dividerBounds1)
                || Rect.intersects(mRootBounds, dividerBounds2)) {
            return;
        }

        if (leftDividerPosition <= mRootBounds.left) {
            pinLeftDividerToRootEdge(dividerBounds1);
        } else {
            pinRightDividerToRootEdge(dividerBounds2);
        }
        Log.w(TAG, "Pinned divider back to root because both dividers were offscreen:"
                + " dividerBounds1=" + dividerBounds1 + " dividerBounds2=" + dividerBounds2
                + " rootBounds=" + mRootBounds);
    }

    private void pinLeftDividerToRootEdge(Rect dividerBounds) {
        dividerBounds.left = mRootBounds.left - mDividerInsets;
        dividerBounds.right = dividerBounds.left + mDividerWindowWidth;
    }

    private void pinRightDividerToRootEdge(Rect dividerBounds) {
        dividerBounds.left = mRootBounds.right - mDividerInsets - mDividerSize;
        dividerBounds.right = dividerBounds.left + mDividerWindowWidth;
    }

    private void updateContentBounds() {
        updateContentBoundsForStage(getLeftBounds(), getLeftContentBounds(), 1);
        updateContentBoundsForStage(getMiddleBounds(), getMiddleContentBounds(), 2);
        updateContentBoundsForStage(getRightBounds(), getRightContentBounds(), 3);
    }

    /**
     * Surface bounds may extend offscreen for flexible split animations. Task/window bounds must
     * stay aligned to the actual rendered area that apps draw into.
     */
    private void updateContentBoundsForStage(Rect stageBounds, Rect contentBounds, int stageIndex) {
        contentBounds.set(stageBounds);
        if (contentBounds.intersect(mRootBounds)) {
            DockedDividerUtils.sanitizeStackBounds(contentBounds, stageIndex);
            return;
        }

        final int contentWidth = Math.min(stageBounds.width(), mRootBounds.width());
        final int contentHeight = Math.min(stageBounds.height(), mRootBounds.height());
        contentBounds.top = mRootBounds.top;
        contentBounds.bottom = contentBounds.top + contentHeight;
        if (stageBounds.centerX() < mRootBounds.centerX()) {
            contentBounds.left = mRootBounds.left;
            contentBounds.right = contentBounds.left + contentWidth;
        } else {
            contentBounds.right = mRootBounds.right;
            contentBounds.left = contentBounds.right - contentWidth;
        }

        DockedDividerUtils.sanitizeStackBounds(contentBounds, stageIndex);
    }

    public void init() {
        runOnLayoutThreadBlocking(() -> {
            if (mInitialized) return;
            mInitialized = true;
            mSplitWindowManager1.init(this, mInsetsState, false);
            mSplitWindowManager2.init(this, mInsetsState, false);
            populateTouchZones();
        });
    }

    public void update(SurfaceControl.Transaction t, boolean resetImePosition) {
        runOnLayoutThreadBlocking(() -> {
            if (!mInitialized) {
                mInitialized = true;
                mSplitWindowManager1.init(this, mInsetsState, false);
                mSplitWindowManager2.init(this, mInsetsState, false);
                populateTouchZones();
                return;
            }
            mSplitWindowManager1.release(t);
            mSplitWindowManager2.release(t);
            if (resetImePosition) {
                mImePositionProcessor.reset();
            }

            mSplitWindowManager1.init(this, mInsetsState, true);
            mSplitWindowManager2.init(this, mInsetsState, true);
            populateTouchZones();
        });

        mSplitLayoutHandler.onLayoutPositionChanging(SplitLayout.this);
    }

    public void release() {
        release(null);
    }

    public void release(SurfaceControl.Transaction t) {
        runOnLayoutThreadBlocking(() -> {
            if (!mInitialized) {
                return;
            }
            mInitialized = false;
            mSplitWindowManager1.release(t);
            mSplitWindowManager2.release(t);
            removeTouchZones();
            if (mDividerFlingAnimator != null) {
                mDividerFlingAnimator.cancel();
            }
            resetDividerPosition();
        });
    }

    private void runOnLayoutThreadBlocking(Runnable work) {
        if (Looper.myLooper() != null && Looper.myLooper() == mHandler.getLooper()) {
            work.run();
            return;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        mHandler.post(() -> {
            try {
                work.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Interrupted while waiting for divider view operation", e);
        }
    }


    @Override
    public void insetsChanged(InsetsState state) {
        mInsetsState.set(state);

        if (!mInitialized) {
            return;
        }

        if (mFreezeDividerWindow) {
            return;
        }

        //TODO: Change divider position since which kind of insets change.
        updateLayouts();
        Pair<DividerSnapAlgorithm.SnapTarget, DividerSnapAlgorithm.SnapTarget> snapTargets =
                findSnapTarget(mDividerPosition1, mDividerPosition2, 0, false);
        if (snapTargets.first.position != mDividerPosition1 ||
                snapTargets.second.position != mDividerPosition2) {
            snapToTargets(mDividerPosition1, mDividerPosition2, snapTargets,
                    InsetsController.ANIMATION_DURATION_RESIZE,
                    InsetsController.RESIZE_INTERPOLATOR);
        }

        mSplitWindowManager1.onInsetsChanged(state);
        mSplitWindowManager2.onInsetsChanged(state);
    }

    @Override
    public void insetsControlChanged(InsetsState insetsState,
                                     InsetsSourceControl[] activeControls) {
        if (!mInsetsState.equals(insetsState)) {
            insetsChanged(insetsState);
        }
    }

    public void setFreezeDividerWindow(boolean freezeDividerWindow) {
        mFreezeDividerWindow = freezeDividerWindow;
    }

    /**
     * Put a divider at the start or the end.
     */
    public void setDividerAtBorder(int id, boolean start) {
        if (start) {
            setDividerPosition(mDividerSnapAlgorithm.getDismissStartTarget().second.position,
                    id == 1, false);
        } else {
            setDividerPosition(mDividerSnapAlgorithm.getDismissEndTarget().first.position,
                    id == 1, false);
        }
    }

    public void setDividerAtBorder() {
        setDividerPosition(mDividerSnapAlgorithm.getMiddleOnlyTarget().first.position,
                mDividerSnapAlgorithm.getMiddleOnlyTarget().second.position, false);
    }

    void updateDividerBounds(int position1, int position2) {
        updateBounds(position1, position2);
        mSplitLayoutHandler.onLayoutSizeChanged(this);
        refreshDividerTouchableRegions();
    }

    void updateDividerSurfaceBounds(int position1, int position2) {
        mDraggingDividerPosition1 = position1;
        mDraggingDividerPosition2 = position2;
        updateBounds(position1, position2);
        mSplitLayoutHandler.onLayoutPositionChanging(this);
    }

    public int updateDividerPositionDuringDrag(int id, int position) {
        final DividerPositions positions = calculateDividerPositionsDuringDrag(id, position);
        updateDividerSurfaceBounds(positions.left, positions.right);
        return id == 1 ? positions.left : positions.right;
    }

    private static class DividerPositions {
        final int left;
        final int right;

        DividerPositions(int left, int right) {
            this.left = left;
            this.right = right;
        }
    }

    private int getMinLeftDividerPosition() {
        return mDividerSnapAlgorithm.getDismissStartTarget().first.position;
    }

    private int getMaxLeftDividerPosition() {
        return mDividerSnapAlgorithm.getDismissEndTarget().first.position;
    }

    private int getMinRightDividerPosition() {
        return mDividerSnapAlgorithm.getDismissStartTarget().second.position;
    }

    private int getMaxRightDividerPosition() {
        return mDividerSnapAlgorithm.getDismissEndTarget().second.position;
    }

    private int clamp(int value, int min, int max) {
        if (min > max) {
            return Math.max(max, Math.min(value, min));
        }
        return Math.max(min, Math.min(value, max));
    }

    private boolean shouldMoveCompanionDivider(int id, int delta) {
        return (id == 1 && delta > 0) || (id == 2 && delta < 0);
    }

    private boolean isDividerVisuallyOffsetFromLogical(int logicalPosition, int visualPosition) {
        return logicalPosition != visualPosition;
    }

    private int getActiveDragBasePosition(
            int logicalPosition, int visualPosition, boolean useVisualPosition) {
        return useVisualPosition && isDividerVisuallyOffsetFromLogical(logicalPosition, visualPosition)
                ? visualPosition : logicalPosition;
    }

    private DividerPositions calculateDividerPositionsDuringDrag(int id, int requestedPosition) {
        final int minGap = Math.max(mDividerSize, 1);
        final DividerPositions normalizedStart =
                normalizeDividerPositionsForMinGap(mDragStartDividerPosition1,
                        mDragStartDividerPosition2, minGap);
        final int startLeft = normalizedStart.left;
        final int startRight = normalizedStart.right;

        if (id == 1) {
            final int visualDelta = requestedPosition - mDragStartVisualDividerPosition1;
            final int activeBase = getActiveDragBasePosition(startLeft,
                    mDragStartVisualDividerPosition1, shouldMoveCompanionDivider(id, visualDelta));
            final int companionBase = startRight;
            final DividerPositions normalizedBases =
                    normalizeDividerPositionsForMinGap(activeBase, companionBase, minGap);
            final int normalizedActiveBase = normalizedBases.left;
            final int normalizedCompanionBase = normalizedBases.right;

            if (shouldMoveCompanionDivider(id, visualDelta)) {
                final int minDelta = Math.max(
                        getMinLeftDividerPosition() - normalizedActiveBase,
                        getMinRightDividerPosition() - normalizedCompanionBase);
                final int maxDelta = Math.min(
                        getMaxLeftDividerPosition() - normalizedActiveBase,
                        getMaxRightDividerPosition() - normalizedCompanionBase);
                final int delta = clamp(visualDelta, minDelta, maxDelta);
                return new DividerPositions(normalizedActiveBase + delta,
                        normalizedCompanionBase + delta);
            }

            final int left = clamp(normalizedActiveBase + visualDelta,
                    getMinLeftDividerPosition(),
                    Math.max(getMinLeftDividerPosition(), normalizedCompanionBase - minGap));
            return new DividerPositions(left, normalizedCompanionBase);
        }

        final int visualDelta = requestedPosition - mDragStartVisualDividerPosition2;
        final int activeBase = getActiveDragBasePosition(startRight,
                mDragStartVisualDividerPosition2, shouldMoveCompanionDivider(id, visualDelta));
        final int companionBase = startLeft;
        final DividerPositions normalizedBases =
                normalizeDividerPositionsForMinGap(companionBase, activeBase, minGap);
        final int normalizedCompanionBase = normalizedBases.left;
        final int normalizedActiveBase = normalizedBases.right;

        if (shouldMoveCompanionDivider(id, visualDelta)) {
            final int minDelta = Math.max(
                    getMinLeftDividerPosition() - normalizedCompanionBase,
                    getMinRightDividerPosition() - normalizedActiveBase);
            final int maxDelta = Math.min(
                    getMaxLeftDividerPosition() - normalizedCompanionBase,
                    getMaxRightDividerPosition() - normalizedActiveBase);
            final int delta = clamp(visualDelta, minDelta, maxDelta);
            return new DividerPositions(normalizedCompanionBase + delta,
                    normalizedActiveBase + delta);
        }

        final int right = clamp(normalizedActiveBase + visualDelta,
                Math.min(normalizedCompanionBase + minGap, getMaxRightDividerPosition()),
                getMaxRightDividerPosition());
        return new DividerPositions(normalizedCompanionBase, right);
    }

    private DividerPositions normalizeDividerPositionsForMinGap(int left, int right, int minGap) {
        if (right - left >= minGap) {
            return new DividerPositions(left, right);
        }

        int normalizedLeft = left;
        int normalizedRight = normalizedLeft + minGap;
        if (normalizedRight > getMaxRightDividerPosition()) {
            normalizedRight = getMaxRightDividerPosition();
            normalizedLeft = normalizedRight - minGap;
        }
        if (normalizedLeft < getMinLeftDividerPosition()) {
            normalizedLeft = getMinLeftDividerPosition();
            normalizedRight = normalizedLeft + minGap;
        }

        return new DividerPositions(normalizedLeft, normalizedRight);
    }

    /**
     * Make sure divider 1 is always left to divider 2.
     */
    public void setDividerPosition(int position, boolean left, boolean applyLayoutChange) {
        if (left) {
            setDividerPosition(position,
                    Math.max(mDividerPosition2, position + 10), applyLayoutChange);
        } else {
            setDividerPosition(
                    Math.min(mDividerPosition1, position - 10), position, applyLayoutChange);
        }
    }

    void setDividerPosition(int position1, int position2, boolean applyLayoutChange) {
        mDividerPosition1 = position1;
        mDividerPosition2 = position2;
        mDraggingDividerPosition1 = position1;
        mDraggingDividerPosition2 = position2;
        mDragStartDividerPosition1 = position1;
        mDragStartDividerPosition2 = position2;
        updateBounds(mDividerPosition1, mDividerPosition2);
        if (applyLayoutChange) {
            mSplitLayoutHandler.onLayoutSizeChanged(this);
        }
        refreshDividerTouchableRegions();
    }

    private void refreshDividerTouchableRegions() {
        mSplitWindowManager1.updateTouchableRegion();
        mSplitWindowManager2.updateTouchableRegion();
    }

    public void setDivideRatio(@SplitScreenConstants.PersistentSnapPosition int snapPosition) {
        final Pair<DividerSnapAlgorithm.SnapTarget, DividerSnapAlgorithm.SnapTarget> snapTargets =
                mDividerSnapAlgorithm.findSnapTarget(snapPosition);

        setDividerPosition(snapTargets.first.position, snapTargets.second.position, false);
    }

    public void resetDividerPosition() {
        Pair< DividerSnapAlgorithm.SnapTarget, DividerSnapAlgorithm.SnapTarget> snapTargets =
                mDividerSnapAlgorithm.getMiddleTarget();
        mDividerPosition1 = snapTargets.first.position;
        mDividerPosition2 = snapTargets.second.position;
        mDraggingDividerPosition1 = mDividerPosition1;
        mDraggingDividerPosition2 = mDividerPosition2;
        mDragStartDividerPosition1 = mDividerPosition1;
        mDragStartDividerPosition2 = mDividerPosition2;
        updateBounds(mDividerPosition1, mDividerPosition2);
        mDragStartVisualDividerPosition1 = getDividerVisualPositionForTouch(1);
        mDragStartVisualDividerPosition2 = getDividerVisualPositionForTouch(2);
        mWinToken1 = null;
        mWinToken2 = null;
        mWinToken3 = null;
        getLeftContentBounds().setEmpty();
        getMiddleContentBounds().setEmpty();
        getRightContentBounds().setEmpty();
    }

    public void setDividerInteractive(boolean interactive, boolean hideHandle, String from) {
        mSplitWindowManager1.setInteractive(interactive, hideHandle, from);
        mSplitWindowManager2.setInteractive(interactive, hideHandle, from);
    }

    public void snapToTarget(int id, int currentPosition,
                 Pair<DividerSnapAlgorithm.SnapTarget, DividerSnapAlgorithm.SnapTarget> snapTargets,
                 int duration, Interpolator interpolator) {
        DividerSnapAlgorithm.SnapTarget snapTarget = id == 1 ?
                snapTargets.first : snapTargets.second;
        switch (snapTarget.snapPosition) {
            case SNAP_TO_START_AND_DISMISS:
                flingDividerPosition(id, currentPosition, snapTarget.position, duration,
                        interpolator, () -> mSplitLayoutHandler.onSnappedToDismiss(
                                false, id == 1,  EXIT_REASON_DRAG_DIVIDER));
                break;
            case SNAP_TO_END_AND_DISMISS:
                flingDividerPosition(id, currentPosition, snapTarget.position, duration,
                        interpolator, () -> mSplitLayoutHandler.onSnappedToDismiss(
                                true, id == 1,  EXIT_REASON_DRAG_DIVIDER));
                break;
            default:
                flingDividerPosition(id, currentPosition, snapTarget.position, duration,
                        interpolator, () -> {
                            setDividerPosition(snapTarget.position, id == 1, true);
                            mSplitState.set(snapTarget.snapPosition);
                            populateTouchZones();
                        });
                break;
        }
    }

    public void snapToTargets(int currentLeft, int currentRight,
                  Pair<DividerSnapAlgorithm.SnapTarget, DividerSnapAlgorithm.SnapTarget> targets,
                  int duration, Interpolator interpolator) {
        flingBothDividerPosition(currentLeft, currentRight, targets.first.position,
                targets.second.position, duration, interpolator,
                () -> {
                    setDividerPosition(targets.first.position, targets.second.position, true);
                    mSplitState.set(targets.first.snapPosition);
                    populateTouchZones();
                });
    }

    public void snapCurrentDragPositionsToTarget(
            Pair<DividerSnapAlgorithm.SnapTarget, DividerSnapAlgorithm.SnapTarget> targets) {
        snapToTargets(mDraggingDividerPosition1, mDraggingDividerPosition2, targets,
                FLING_RESIZE_DURATION, FAST_OUT_SLOW_IN);
    }

    public void cancelCurrentDragPositions() {
        mDraggingDividerPosition1 = mDividerPosition1;
        mDraggingDividerPosition2 = mDividerPosition2;
        mDragStartDividerPosition1 = mDividerPosition1;
        mDragStartDividerPosition2 = mDividerPosition2;
        updateDividerSurfaceBounds(mDividerPosition1, mDividerPosition2);
    }

    public void snapToTarget(int id, int currentPosition,
             Pair<DividerSnapAlgorithm.SnapTarget, DividerSnapAlgorithm.SnapTarget> snapTarget) {
        snapToTarget(id, currentPosition, snapTarget, FLING_RESIZE_DURATION, FAST_OUT_SLOW_IN);
    }

    public void onStartDragging(int id) {
        if (mDividerFlingAnimator != null) {
            mDividerFlingAnimator.cancel();
        }
        mInteractionJankMonitor.begin(InteractionJankMonitor.Configuration.Builder.withSurface(
                    CUJ_SPLIT_SCREEN_RESIZE, mContext,
                    id == 1 ? getLeftDividerLeash() : getRightDividerLeash()
                ));
        mMovingDivider = id;
        mDragStartDividerPosition1 = mDraggingDividerPosition1;
        mDragStartDividerPosition2 = mDraggingDividerPosition2;
        mDragStartVisualDividerPosition1 = getDividerVisualPositionForTouch(1);
        mDragStartVisualDividerPosition2 = getDividerVisualPositionForTouch(2);
    }

    public void onDraggingCancelled(int id) {
        mInteractionJankMonitor.cancel(CUJ_SPLIT_SCREEN_RESIZE);
    }

    public void onDoubleTappedDivider(int id) {
        if (isCurrentlySwapping()) {
            return;
        }
        mSplitLayoutHandler.onDoubleTappedDivider(id);
    }

    public Pair<DividerSnapAlgorithm.SnapTarget, DividerSnapAlgorithm.SnapTarget> findSnapTarget
            (int position1, int position2, float velocity, boolean hardDismiss) {
        return mDividerSnapAlgorithm.calculateSnapTarget(position1, position2,
                mMovingDivider, velocity, hardDismiss);
    }

    private void updateLayouts() {
        //TODO: Flexible layouts
        final Rect insets = getDisplayStableInsets(mContext);

        mDividerSnapAlgorithm = new DividerSnapAlgorithm(
                mContext.getResources(),
                mRootBounds.width(),
                mRootBounds.height(),
                mDividerSize,
                insets,
                mContext.getDisplay().getDisplayId()
        );
        mSplitState.setSplitSpec(new SplitSpec(mRootBounds, mDividerSize, new Rect()));
    }

    /**
     * Fling Divider to dismiss(offscreen)
     * @param id Which divider to fling
     * @param toEnd true if divider fling to the right, while false to the left
     */
    public void flingDividerToDismiss(int id, boolean toEnd, int reason) {
        final int target = toEnd ? mDividerSnapAlgorithm.getDismissEndTarget().first.position
                : mDividerSnapAlgorithm.getDismissStartTarget().second.position;
        flingDividerPosition(id, id == 1 ? getLeftDividerPosition() : getRightDividerPosition(),
                target, FLING_EXIT_DURATION, FAST_OUT_SLOW_IN,
                () -> mSplitLayoutHandler.onSnappedToDismiss(toEnd, id == 1, reason));
    }

    /**
     * Fling two divider together, especially for 33 - 100 - 33 now.
     */
    public void flingBothDividerToDismiss() {
        flingBothDividerPosition(getLeftDividerPosition(), getRightDividerPosition(),
                mDividerSnapAlgorithm.getDismissStartTarget().second.position,
                mDividerSnapAlgorithm.getDismissStartTarget().first.position,
                FLING_ENTER_DURATION, FAST_OUT_SLOW_IN, () -> {
                    mSplitState.set(mDividerSnapAlgorithm.getMiddleOnlyTarget().first.snapPosition);
                });
    }


    public void flingDividerToCenter(int id, @Nullable Runnable finishCallback) {
        Pair<DividerSnapAlgorithm.SnapTarget, DividerSnapAlgorithm.SnapTarget> snapTargets =
                mDividerSnapAlgorithm.getMiddleTarget();
        final int pos1 = snapTargets.first.position;
        final int pos2 = snapTargets.second.position;
        flingBothDividerPosition(getLeftDividerPosition(), getRightDividerPosition(),
                snapTargets.first.position, snapTargets.second.position,
                FLING_ENTER_DURATION, FAST_OUT_SLOW_IN, () -> {
                    setDividerPosition(pos1, pos2, true);
                    mSplitState.set(snapTargets.first.snapPosition);
                    populateTouchZones();
                    if (finishCallback != null) {
                        finishCallback.run();
                    }
                });
    }

    /**
     * Move divider from one side to the other
     * @param dividerIndex which divider to move
     * @param currentPos current divider position e.g. 33 100 33
     */
    public void flingDividerToOtherSide(int dividerIndex,
                                        @SplitScreenConstants.SplitScreenState int currentPos) {
        if (mDividerFlingAnimator != null) {
            return;
        }
        //TODO make the change effect better.
        mSplitState.set(ANIMATING_OFFSCREEN_TAP);
        if (dividerIndex == 1) {
            switch (currentPos) {
                case SNAP_TO_3_33_33_66:
                case SNAP_TO_3_33_50_50:
                case SNAP_TO_3_33_66_33:
                    snapToTarget(1, getLeftDividerPosition(),
                            requireSnapTarget(SNAP_TO_3_33_33_33),
                            FLING_OFFSCREEN_DURATION, EMPHASIZED);
                    break;
                case SNAP_TO_3_33_100_33:
                    snapToTarget(1, getLeftDividerPosition(),
                            requireSnapTarget(SNAP_TO_3_33_66_33_2),
                            FLING_OFFSCREEN_DURATION, EMPHASIZED);
                    break;
                case SNAP_TO_3_100_33_33:
                    snapToTarget(1, getLeftDividerPosition(),
                            requireSnapTarget(SNAP_TO_3_66_33_33),
                            FLING_OFFSCREEN_DURATION, EMPHASIZED);
                    break;
            }
        }
        if (dividerIndex == 2) {
            switch (currentPos) {
                case SNAP_TO_3_33_66_33_2:
                case SNAP_TO_3_50_50_33:
                case SNAP_TO_3_66_33_33:
                    snapToTarget(2, getRightDividerPosition(),
                            requireSnapTarget(SNAP_TO_3_33_33_33),
                            FLING_OFFSCREEN_DURATION, EMPHASIZED);
                    break;
                case SNAP_TO_3_33_100_33:
                    snapToTarget(2, getRightDividerPosition(),
                            requireSnapTarget(SNAP_TO_3_33_33_66),
                            FLING_OFFSCREEN_DURATION, EMPHASIZED);
                    break;
                case SNAP_TO_3_33_33_100:
                    snapToTarget(2, getRightDividerPosition(),
                            requireSnapTarget(SNAP_TO_3_33_33_66),
                            FLING_OFFSCREEN_DURATION, EMPHASIZED);
                    break;
            }
        }
    }

    private Pair<DividerSnapAlgorithm.SnapTarget, DividerSnapAlgorithm.SnapTarget> requireSnapTarget(
            @SplitScreenConstants.SnapPosition int snapPosition) {
        final Pair<DividerSnapAlgorithm.SnapTarget, DividerSnapAlgorithm.SnapTarget> target =
                mDividerSnapAlgorithm.findSnapTarget(snapPosition);
        if (target == null) {
            throw new IllegalStateException("Missing snap target " + snapPosition);
        }
        return target;
    }
    public void flingDividerPosition(int id, int from, int to, int duration, Interpolator interpolator,
                              @Nullable Runnable flingFinishedCallback) {
        if (from == to) {
            if (flingFinishedCallback != null) {
                flingFinishedCallback.run();
            }
            mInteractionJankMonitor.end(CUJ_SPLIT_SCREEN_RESIZE);
            return;
        }

        Pair<DividerSnapAlgorithm.SnapTarget, DividerSnapAlgorithm.SnapTarget> snapTargets =
                mDividerSnapAlgorithm.getAdjustedTargets(id, to,
                        getLeftDividerPosition(), getRightDividerPosition());
        Log.i(TAG, "fling divider position to " + snapTargets.first.snapPosition);
        if (mDividerFlingAnimator != null) {
            mDividerFlingAnimator.cancel();
        }
        mDividerFlingAnimator = ValueAnimator.ofInt(from, to)
                .setDuration(duration);
        mDividerFlingAnimator.setInterpolator(interpolator);
        final int otherDividerPosition = id == 1 ? getRightDividerPosition()
                : getLeftDividerPosition();
        final int otherDividerDest = id == 1 ? snapTargets.second.position
                : snapTargets.first.position;
        mDividerFlingAnimator.addUpdateListener(
                animation -> {
                    final float fraction = animation.getAnimatedFraction();
                    final int otherPosition = otherDividerPosition
                            + (int) (fraction * (otherDividerDest - otherDividerPosition));
                    if (id == 1) {
                        updateDividerSurfaceBounds((int) animation.getAnimatedValue(),
                                otherPosition);
                    } else {
                        updateDividerSurfaceBounds(otherPosition,
                                (int) animation.getAnimatedValue());
                    }
                }
        );
        mDividerFlingAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                mCancelled = true;
                mDividerFlingAnimator = null;
                mParentContainerCallbacks.onSplitLayoutAnimating(false);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mCancelled) {
                    return;
                }
                if (flingFinishedCallback != null) {
                    flingFinishedCallback.run();
                }
                mInteractionJankMonitor.end(CUJ_SPLIT_SCREEN_RESIZE);
                mDividerFlingAnimator = null;
                mParentContainerCallbacks.onSplitLayoutAnimating(false);
            }

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                mParentContainerCallbacks.onSplitLayoutAnimating(true);
            }
        });
        mDividerFlingAnimator.start();
    }

    public void flingBothDividerPosition(int leftFrom, int rightFrom, int leftTo, int rightTo,
                                         int duration, Interpolator interpolator,
                                         @Nullable Runnable flingFinishedCallback) {
        if (leftFrom == leftTo && rightFrom == rightTo) {
            if (flingFinishedCallback != null) {
                flingFinishedCallback.run();
            }
            mInteractionJankMonitor.end(CUJ_SPLIT_SCREEN_RESIZE);
            return;
        }

        if (mDividerFlingAnimator != null) {
            mDividerFlingAnimator.cancel();
        }

        mDividerFlingAnimator = ValueAnimator.ofFloat(0f, 1f);
        mDividerFlingAnimator.setDuration(duration).setInterpolator(interpolator);

        mDividerFlingAnimator.addUpdateListener(
                animation -> {
                    int leftPos = (int) (animation.getAnimatedFraction() * (leftTo - leftFrom))
                            + leftFrom;
                    int rightPos = (int) (animation.getAnimatedFraction() * (rightTo - rightFrom))
                            + rightFrom;
                    updateDividerSurfaceBounds(leftPos, rightPos);
                }
        );
        mDividerFlingAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                mCancelled = true;
                mDividerFlingAnimator = null;
                mParentContainerCallbacks.onSplitLayoutAnimating(false);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (mCancelled) {
                    return;
                }
                if (flingFinishedCallback != null) {
                    flingFinishedCallback.run();
                }
                mInteractionJankMonitor.end(CUJ_SPLIT_SCREEN_RESIZE);
                mDividerFlingAnimator = null;
                mParentContainerCallbacks.onSplitLayoutAnimating(false);
            }

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                mParentContainerCallbacks.onSplitLayoutAnimating(true);
            }
        });
        mDividerFlingAnimator.start();
    }

    public void playSwapAnimation(SurfaceControl.Transaction t, int index1, int index2,
                                  StageTaskListener leftStage, StageTaskListener middleStage,
                                  StageTaskListener rightStage,
                                  Consumer<Rect> finishCallback) {
        if (index2 == index1) {
            Log.wtf(TAG, "Swap same stage index=" + index1);
            return;
        }
        final int boundsIndex1 = toStageBoundsIndex(index1);
        final int boundsIndex2 = toStageBoundsIndex(index2);
        final Rect insets = getDisplayStableInsets(mContext);
        insets.set(insets.left, 0, insets.right, 0);
        final boolean shouldVeil = insets.left != 0 || insets.top != 0 || insets.right != 0
                || insets.bottom != 0;

        // scene1: 1 <-> 2 <-> 3
        // scene2: 1 <-> 3
        final Rect endBounds1 = new Rect();
        final Rect endBounds2 = new Rect();
        final Rect endBounds3 = new Rect();
        final Rect dividerBounds1 = new Rect();
        final Rect dividerBounds2 = new Rect();

        int left = mStageBounds.get(boundsIndex1).left;
        final int dividerPos = left + mStageBounds.get(boundsIndex2).width();
        if (index2 - index1 == 1) {
            mSwapDivider = index2 == 2 ? 1 : 2;
            updateBounds(index2 == 2 ? dividerPos : getLeftDividerPosition(),
                    index2 == 2 ? getRightDividerPosition() : dividerPos,
                    endBounds1, endBounds2, endBounds3, dividerBounds1,
                    dividerBounds2, false);
            endBounds1.offset(-mRootBounds.left, -mRootBounds.top);
            endBounds2.offset(-mRootBounds.left, -mRootBounds.top);
            endBounds3.offset(-mRootBounds.left, -mRootBounds.top);
            dividerBounds1.offset(-mRootBounds.left, -mRootBounds.top);
            dividerBounds2.offset(-mRootBounds.left, -mRootBounds.top);
//            endBounds1.left = mStageBounds.get(0).left;
//            endBounds2.right = mStageBounds.get(2).right;
        } else {
            dividerBounds1.set(getDividerBounds(true));
            dividerBounds2.set(getDividerBounds(false));
            endBounds1.set(getLeftBounds());
            endBounds3.set(getRightBounds());
//            endBounds1.left = endBounds1.right - getRightBounds().width();
//            endBounds2.right = endBounds2.left + getLeftBounds().width();
        }

        mSwapAnimator = new AnimatorSet();
        if (mSwapDivider == -1) {
            // 1 <-> 3
            ValueAnimator animator1 = moveSurface(t, leftStage, true, getLeftRefBounds(),
                    endBounds3, -insets.left, -insets.top, true, true);
            ValueAnimator animator2 = moveSurface(t, rightStage, false, getRightRefBounds(),
                    endBounds1, -insets.left, -insets.top, true, false);
            mSwapAnimator.playTogether(animator1, animator2);
        } else {
            // 1 <-> 2, 2 <-> 3
            if (index1 == 1) {
                ValueAnimator animator1 = moveSurface(t, leftStage, true, getLeftRefBounds(),
                        endBounds2, -insets.left, -insets.top, true, true);
                ValueAnimator animator2 = moveSurface(t, middleStage, true, getMiddleRefBounds(),
                        endBounds1, -insets.left, -insets.top, true, false);
                ValueAnimator animator3 = moveSurface(t, null, true,
                        getRefDividerBounds(true), dividerBounds1, -insets.left,
                        -insets.top, true, false);
                mSwapAnimator.playTogether(animator1, animator2, animator3);
            } else {
                ValueAnimator animator1 = moveSurface(t, middleStage, true, getMiddleRefBounds(),
                        endBounds3, -insets.left, -insets.top, true, true);
                ValueAnimator animator2 = moveSurface(t, rightStage, true, getRightRefBounds(),
                        endBounds2, -insets.left, -insets.top, true, false);
                ValueAnimator animator3 = moveSurface(t, null, false,
                        getRefDividerBounds(false), dividerBounds2, -insets.left,
                        -insets.top, true, false);
                mSwapAnimator.playTogether(animator1, animator2, animator3);
            }
        }
        mSwapAnimator.setDuration(SWAP_ANIMATION_TOTAL_DURATION);
        mSwapAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                if (mSwapDivider != -1) {
                    mInteractionJankMonitor.cancel(CUJ_SPLIT_SCREEN_DOUBLE_TAP_DIVIDER);
                }
                mSwapDivider = -1;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mSwapDivider != -1) {
                    mInteractionJankMonitor.end(CUJ_SPLIT_SCREEN_DOUBLE_TAP_DIVIDER);
                    if (mSwapDivider == 1) mDividerPosition1 = dividerPos;
                    else mDividerPosition2 = dividerPos;
                    updateBounds(mSwapDivider == 1,
                            mSwapDivider == 1 ? mDividerPosition1 : mDividerPosition2);
                } else {
                    updateBounds(mDividerPosition1, mDividerPosition2);
                }
                mSwapDivider = -1;
                finishCallback.accept(insets);
            }

            @Override
            public void onAnimationStart(Animator animation) {
                if (mSwapDivider != -1) {
                    mInteractionJankMonitor.begin(InteractionJankMonitor.Configuration.
                            Builder.withSurface(CUJ_SPLIT_SCREEN_DOUBLE_TAP_DIVIDER,
                                    mContext, getLeftDividerLeash()));
                }
            }
        });
        mSwapAnimator.start();
    }

    private int toStageBoundsIndex(@SplitScreenConstants.SplitIndex int splitIndex) {
        final int listIndex = splitIndex - 1;
        if (listIndex < 0 || listIndex >= mStageBounds.size()) {
            throw new IllegalArgumentException("Invalid split index " + splitIndex
                    + " for stage bounds size=" + mStageBounds.size());
        }
        return listIndex;
    }

    public boolean isCurrentlySwapping() {
        return mSwapAnimator != null && mSwapAnimator.isRunning();
    }

    /**
     * Animate a task leash across the screen.
     * @param stage Stage that holds the task. if null, it is divider.
     * @param left Which divider to apply the animation. (Probably change twice)
     * @param roundCorners Whether showing the round corner during animation
     * @param isGoingBehind Whether to use a shrink-and-grow effect to the task while it is moving.
     * @return
     */
    private ValueAnimator moveSurface(SurfaceControl.Transaction t, StageTaskListener stage,
                                      boolean left, Rect start, Rect end, float offsetX,
                                      float offsetY, boolean roundCorners,
                                      boolean isGoingBehind) {
        final boolean isApp = stage != null;
        final SurfaceControl leash = isApp ? stage.mRootLeash
                : (left ? getLeftDividerLeash() : getRightDividerLeash());
        final ActivityManager.RunningTaskInfo taskInfo = isApp ? stage.mRootTaskInfo : null;
        boolean goingOffScreen = !mSplitState.isOffscreen(start) && mSplitState.isOffscreen(end);
        boolean comingOffScreen = mSplitState.isOffscreen(start) && !mSplitState.isOffscreen(end);
        Rect tempStart = new Rect(start);
        Rect tempEnd = new Rect(end);
        final float diffX = tempEnd.left - tempStart.left;
        final float diffY = tempEnd.top - tempStart.top;
        final float diffWidth = tempEnd.width() - tempStart.width();
        final float diffHeight = tempEnd.height() - tempStart.height();

        final RoundedCorner roundedCorner = mSplitWindowManager1.getDividerView().getDisplay().
                                                getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
        float cornerRadius = roundedCorner == null ? 0 : roundedCorner.getRadius();
        float shrinkMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                SWAP_ANIMATION_SHRINK_MARGIN_DP, mContext.getResources().getDisplayMetrics());
        float shrinkAmountPx = shrinkMarginPx * 2;

        float shrinkPortion = SWAP_ANIMATION_SHRINK_DURATION / SWAP_ANIMATION_TOTAL_DURATION;
        float growPortion = 1 - shrinkPortion;

        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setInterpolator(LINEAR);
        animator.addUpdateListener( animation -> {
            if (leash == null) return;
            if (roundCorners) {
                t.setCornerRadius(leash, cornerRadius);
            }

            final float progress = (float) animation.getAnimatedValue();
            final float moveProgress = EMPHASIZED.getInterpolation(progress);

            float instantaneousX = tempStart.left + moveProgress * diffX;
            float instantaneousY = tempStart.top + moveProgress * diffY;
            int width = (int) (tempStart.width() + moveProgress * diffWidth);
            int height = (int) (tempStart.height() + moveProgress * diffHeight);

            if (isGoingBehind) {
                float shrinkDiffX;
                float shrinkDiffY;
                float shrinkScaleX;
                float shrinkScaleY;

                float maxShrinkX = shrinkAmountPx / height;
                float maxShrinkY = shrinkAmountPx / width;

                boolean shrinking = progress <= shrinkPortion;

                if (shrinking) {
                    float shrinkProgress = progress / shrinkPortion;
                    float interpolatedShrinkProgress =
                            SHRINK_INTERPOLATOR.getInterpolation(shrinkProgress);
                    float widthProportionLost = maxShrinkX * interpolatedShrinkProgress;
                    shrinkScaleX = 1 - widthProportionLost;
                    float heightProportionLost = maxShrinkY * interpolatedShrinkProgress;
                    shrinkScaleY = 1 - heightProportionLost;
                    shrinkDiffX = (width * widthProportionLost) / 2;
                    shrinkDiffY = (height * widthProportionLost) / 2;
                } else {
                    float growProgress = (progress  - shrinkPortion) / growPortion;
                    float interpolatedGrowProgress =
                            GROW_INTERPOLATOR.getInterpolation(growProgress);
                    float widthProportionLost = maxShrinkX * (1 - interpolatedGrowProgress);
                    shrinkScaleX = 1 - widthProportionLost;
                    float heightProportionLost = maxShrinkY * (1 - interpolatedGrowProgress);
                    shrinkScaleY = 1 - heightProportionLost;
                    shrinkDiffX = (width * widthProportionLost) / 2;
                    shrinkDiffY = (height * heightProportionLost) / 2;
                }

                instantaneousX += shrinkDiffX;
                instantaneousY += shrinkDiffY;
                width *= shrinkScaleX;
                height *= shrinkScaleY;
                t.setScale(leash, shrinkScaleX, shrinkScaleY);
            }

            if (taskInfo != null) {
                t.setLayer(leash, isGoingBehind ? ANIMATING_BACK_APP_LAYER
                        : ANIMATING_FRONT_APP_LAYER);
            } else {
                t.setLayer(leash, ANIMATING_DIVIDER_LAYER);
            }

            if (offsetX == 0 && offsetY == 0) {
                t.setPosition(leash, instantaneousX, instantaneousY);
                mTempRect.set((int) instantaneousX, (int) instantaneousY,
                        (int) (instantaneousX + width), (int) (instantaneousY + height));
                t.setWindowCrop(leash, width, height);
            } else {
                final int diffOffsetX = (int) (moveProgress * offsetX);
                final int diffOffsetY = (int) (moveProgress * offsetY);
                t.setPosition(leash, instantaneousX + diffOffsetX,
                        instantaneousY + diffOffsetY);
                mTempRect.set(0, 0, width, height);
                mTempRect.offsetTo(-diffOffsetX, -diffOffsetY);
                t.setCrop(leash, mTempRect);
            }

            t.apply();
        });
        return animator;
    }

    /**
     * Appply surface changes to leash and divider.
     * @param leash1 left task leash
     * @param leash2 middle task leash
     * @param leash3 right task leash
     */
    public void applySurfaceChanges(SurfaceControl.Transaction t, SurfaceControl leash1,
                                    SurfaceControl leash2, SurfaceControl leash3,
                                    boolean applyResizingEffect) {
        final SurfaceControl leftDividerLeash = getLeftDividerLeash();
        final SurfaceControl rightDividerLeash = getRightDividerLeash();
        if (leftDividerLeash != null && rightDividerLeash != null) {
            getRefDividerBounds(true, mTempRect1);
            getRefDividerBounds(false, mTempRect2);
            t.setPosition(leftDividerLeash, mTempRect1.left, mTempRect1.top);
            t.setPosition(rightDividerLeash, mTempRect2.left, mTempRect2.top);
            t.setLayer(leftDividerLeash, RESTING_DIVIDER_LAYER);
            t.setLayer(rightDividerLeash, RESTING_DIVIDER_LAYER);
        }

        copyLeftRefBounds(mTempRect);
        Log.i(TAG, "applySurfaceChanges leftbounds=" + mTempRect);
        t.setPosition(leash1, mTempRect.left, mTempRect.top)
                        .setWindowCrop(leash1, mTempRect.width(), mTempRect.height());
        copyMiddleRefBounds(mTempRect);
        Log.i(TAG, "applySurfaceChanges middlebounds=" + mTempRect);
        t.setPosition(leash2, mTempRect.left, mTempRect.top)
                .setWindowCrop(leash2, mTempRect.width(), mTempRect.height());
        copyRightRefBounds(mTempRect);
        Log.i(TAG, "applySurfaceChanges rightbounds=" + mTempRect);
        t.setPosition(leash3, mTempRect.left, mTempRect.top)
                .setWindowCrop(leash3, mTempRect.width(), mTempRect.height());

        if (mImePositionProcessor.adjustSurfaceLayoutForIme(
                t, leftDividerLeash, rightDividerLeash, leash1, leash2, leash3)) {
            return;
        }

//        if (applyResizingEffect) {
//            mSurfaceEffectPolicy.adjustRootSurface(t, leash1, leash2, leash3);
//        }
    }

    /**
     * Apply recorded task layout to WindowContainerTransaction
     * @param task1 left task
     * @param task2 middle task
     * @param task3 right task
     * @return true if stage bounds actually update
     */
    public boolean applyTaskChanges(WindowContainerTransaction wct,
                                    ActivityManager.RunningTaskInfo task1,
                                    ActivityManager.RunningTaskInfo task2,
                                    ActivityManager.RunningTaskInfo task3) {
        boolean boundsChanged = false;
        final Rect currentLeftTaskBounds = task1.configuration.windowConfiguration.getBounds();
        final Rect currentMiddleTaskBounds = task2.configuration.windowConfiguration.getBounds();
        final Rect currentRightTaskBounds = task3.configuration.windowConfiguration.getBounds();
        if (!currentLeftTaskBounds.equals(getLeftContentBounds()) || !task1.token.equals(mWinToken1)) {
            Log.i(TAG, "applyTaskChanges for left bounds=" + getLeftBounds()
                    + " content bounds=" + getLeftContentBounds());
            setTaskBounds(wct, task1, getLeftContentBounds());
            mWinToken1 = task1.token;
            boundsChanged = true;
        }
        if (!currentMiddleTaskBounds.equals(getMiddleContentBounds())
                || !task2.token.equals(mWinToken2)) {
            Log.i(TAG, "applyTaskChanges for middle bounds=" + getMiddleBounds()
                    + " content bounds=" + getMiddleContentBounds());
            setTaskBounds(wct, task2, getMiddleContentBounds());
            mWinToken2 = task2.token;
            boundsChanged = true;
        }
        if (!currentRightTaskBounds.equals(getRightContentBounds())
                || !task3.token.equals(mWinToken3)) {
            Log.i(TAG, "applyTaskChanges for right bounds=" + getRightBounds()
                    + " content bounds=" + getRightContentBounds());
            setTaskBounds(wct, task3, getRightContentBounds());
            mWinToken3 = task3.token;
            boundsChanged = true;
        }
        return boundsChanged;
    }

    public void setTaskBounds(WindowContainerTransaction wct, ActivityManager.RunningTaskInfo task,
                              Rect bounds) {
        wct.setBounds(task.token, bounds);
        // Keep client configuration stable. Some embedded apps finish themselves on size relaunch,
        // so divider snaps should move/crop the stage without forcing a new screen dp config.
        wct.setSmallestScreenWidthDp(task.token, task.configuration.smallestScreenWidthDp);
        wct.setScreenSizeDp(task.token, task.configuration.screenWidthDp,
                task.configuration.screenHeightDp);
    }

    public int getSmallestWidthDp(Rect bounds) {
        mTempRect.set(bounds);
        mTempRect.inset(Insets.of(getDisplayStableInsets(mContext)));
        final int minWidth = Math.min(mTempRect.width(), mTempRect.height());
        final float density = mContext.getResources().getDisplayMetrics().density;
        return (int)(minWidth / density);
    }

    public int getDisplayWidth() {
        return mRootBounds.width();
    }

    public int getDisplayHeight() {
        return mRootBounds.height();
    }

    /**
     * Shift configuration bounds to prevent client apps get config changed or relaunch.
     * @param taskInfo1 left stage
     * @param taskInfo2 middle stage
     * @param taskInfo3 right stage
     */
    public void applyLayoutOffsetTarget(WindowContainerTransaction wct, int offsetX, int offsetY,
                                        ActivityManager.RunningTaskInfo taskInfo1,
                                        ActivityManager.RunningTaskInfo taskInfo2,
                                        ActivityManager.RunningTaskInfo taskInfo3) {
        if (offsetX == 0 && offsetY == 0) {
            wct.setBounds(taskInfo1.token, getLeftContentBounds());
            wct.setScreenSizeDp(taskInfo1.token, SCREEN_WIDTH_DP_UNDEFINED, SCREEN_HEIGHT_DP_UNDEFINED);
            wct.setBounds(taskInfo2.token, getMiddleContentBounds());
            wct.setScreenSizeDp(taskInfo2.token, SCREEN_WIDTH_DP_UNDEFINED, SCREEN_HEIGHT_DP_UNDEFINED);
            wct.setBounds(taskInfo3.token, getRightContentBounds());
            wct.setScreenSizeDp(taskInfo3.token, SCREEN_WIDTH_DP_UNDEFINED, SCREEN_HEIGHT_DP_UNDEFINED);
        } else {
            copyLeftContentBounds(mTempRect);
            mTempRect.offset(offsetX, offsetY);
            wct.setBounds(taskInfo1.token, mTempRect);
            wct.setScreenSizeDp(taskInfo1.token, taskInfo1.configuration.screenWidthDp,
                    taskInfo1.configuration.screenHeightDp);
            copyMiddleContentBounds(mTempRect);
            mTempRect.offset(offsetX, offsetY);
            wct.setBounds(taskInfo2.token, mTempRect);
            wct.setScreenSizeDp(taskInfo2.token, taskInfo2.configuration.screenWidthDp,
                    taskInfo2.configuration.screenHeightDp);
            copyRightContentBounds(mTempRect);
            mTempRect.offset(offsetX, offsetY);
            wct.setBounds(taskInfo3.token, mTempRect);
            wct.setScreenSizeDp(taskInfo3.token, taskInfo3.configuration.screenWidthDp,
                    taskInfo3.configuration.screenHeightDp);
        }
    }

    public interface SplitLayoutHandler {
        /** Calls when dismissing split. */
        void onSnappedToDismiss(boolean snappedToEnd, boolean left, int reason);

        /**
         * Calls when resizing the split bounds.
         *
         */
        void onLayoutSizeChanging(SplitLayout layout, int offsetX, int offsetY,
                                  boolean shouldUseParallaxEffect);

        /**
         * Calls when finish resizing the split bounds.
         *
         */
        void onLayoutSizeChanged(SplitLayout layout);

        /**
         * Calls when re-positioning the split bounds. Like moving split bounds while showing IME
         * panel.
         *
         */
        void onLayoutPositionChanging(SplitLayout layout);

        /**
         * Notifies the target offset for shifting layout. So layout handler can shift configuration
         * bounds correspondingly to make sure client apps won't get configuration changed or
         * relaunched. If the layout is no longer shifted, layout handler should restore shifted
         * configuration bounds.
         *
         */
        void setLayoutOffsetTarget(int offsetX, int offsetY, SplitLayout layout);

        /** Calls when user double tapped on the divider bar. */
        default void onDoubleTappedDivider(int id) {
        }

        /**
         * Sets the excludedInsetsTypes for the IME in the root WindowContainer.
         */
        void setExcludeImeInsets(boolean exclude);

        /** Returns split position of the token. */
        int getSplitItemPosition(WindowContainerToken token);
    }

    private class ImePositionProcessor implements DisplayImeController.ImePositionProcessor {

        /**
         * Max adjusted bounds relative to original stage bounds. Used to make sure that a min portion
         * of split remains visible
         */
        private static final float ADJUSTED_SPLIT_FRACTION_MAX = 0.7f;
        private static final float ADJUSTED_NONFOCUS_DIM = 0.3F;

        private final int mDisplayId;

        private boolean mHasImeFocus;
        private boolean mImeShown;
        private int mYOffsetForIme;
        private int mStartImeTop;
        private int mEndImeTop;

        private int mTargetYOffset;
        private int mLastYOffset;

        private ImePositionProcessor(int displayId) {
            mDisplayId = displayId;
        }

        public void onImeRequested(int displayId, boolean isRequested) {
            if (displayId != mDisplayId) return;
            Log.i(TAG, "Ime was set to requested=" + isRequested);
            mSplitLayoutHandler.setExcludeImeInsets(true);
        }

        public int onImeStartPositioning(int displayId, int hiddenTop, int shownTop, boolean showing,
                                         boolean isFloating, SurfaceControl.Transaction t) {
            if (displayId != mDisplayId || !mInitialized) {
                return 0;
            }

            final int imeLayeringTargetPosition = getImeLayeringTargetPosition();
            mHasImeFocus = imeLayeringTargetPosition != SPLIT_INDEX_UNDEFINED;

            if (!mHasImeFocus) {
                if (showing) {
                    return 0;
                }
            }

            mStartImeTop = showing ? hiddenTop : shownTop;
            mEndImeTop = showing ? shownTop : hiddenTop;
            mImeShown = showing;

            mLastYOffset = mYOffsetForIme;
            mTargetYOffset = getTargetYOffset();

            if (mTargetYOffset != mLastYOffset) {
                mSplitLayoutHandler.setLayoutOffsetTarget(0, mTargetYOffset, SplitLayout.this);
            }

            setDividerInteractive(!mImeShown || !mHasImeFocus || isFloating, true,
                    "onImeStartPositioning");

            if (mImeShown) {
                mSplitLayoutHandler.setExcludeImeInsets(false);
            }

            return mTargetYOffset != mLastYOffset ? IME_ANIMATION_NO_ALPHA : 0;
        }

        public void onImePositionChanged(int displayId, int imeTop, SurfaceControl.Transaction t) {
            if (displayId != mDisplayId || !mHasImeFocus) {
                if (mImeShown) {
                    return;
                }
            }
            onProgress(getProgress(imeTop));
            mSplitLayoutHandler.onLayoutPositionChanging(SplitLayout.this);
        }

        public void onImeEndPositioning(int displayId, boolean cancel, SurfaceControl.Transaction t) {
            if (displayId != mDisplayId || cancel) return;
            if (!mHasImeFocus) {
                if (mImeShown) {
                    return;
                }
            }
            Log.i(TAG, "Split IME animation ending, canceled=" + cancel);
            onProgress(1.0f);
            mSplitLayoutHandler.onLayoutPositionChanging(SplitLayout.this);
            if (!mImeShown) {
                mSplitLayoutHandler.setExcludeImeInsets(false);
            }
        }

        public void onImeControlTargetChanged(int displayId, boolean controlling) {
            if (displayId != mDisplayId) return;
            if (!controlling && mImeShown) {
                reset();
                setDividerInteractive(true, true, "onImeControlTargetChanged");
                mSplitLayoutHandler.setLayoutOffsetTarget(0, 0, SplitLayout.this);
                mSplitLayoutHandler.onLayoutPositionChanging(SplitLayout.this);
            }
        }

        private int getTargetYOffset() {
            final int desiredOffset = Math.abs(mEndImeTop - mStartImeTop);
            final float amountOfAppToKeepVisible = getLeftBounds().height() *
                    (1 - ADJUSTED_SPLIT_FRACTION_MAX);
            final float currentOnScreenSizeOfTopApp = getLeftBounds().bottom;
            final int maxOffset = (int) Math.max(currentOnScreenSizeOfTopApp -
                    amountOfAppToKeepVisible, 0);

            return -Math.max(desiredOffset, maxOffset);
        }

        private int getImeLayeringTargetPosition() {
            final WindowContainerToken token = mTaskOrganizer.getImeTarget(mDisplayId);
            return mSplitLayoutHandler.getSplitItemPosition(token);
        }

        private float getProgress(int currentImeTop) {
            return ((float) currentImeTop - mStartImeTop) / (mEndImeTop - mStartImeTop);
        }

        private void onProgress(float progress) {
            mYOffsetForIme = (int) getProgressValue((float) mLastYOffset, (float) mTargetYOffset,
                    progress);
        }

        private float getProgressValue(float start, float end, float progress) {
            return start + (end - start) * progress;
        }

        void reset() {
            mHasImeFocus = false;
            mImeShown = false;
            mYOffsetForIme = mLastYOffset = mTargetYOffset = 0;
        }

        public boolean adjustSurfaceLayoutForIme(SurfaceControl.Transaction t,
                                                 SurfaceControl dividerLeash1,
                                          SurfaceControl dividerLeash2, SurfaceControl leash1,
                                          SurfaceControl leash2, SurfaceControl leash3) {
            boolean adjusted = false;
            if (mYOffsetForIme != 0) {
                if (dividerLeash1 != null) {
                    getRefDividerBounds(true, mTempRect);
                    mTempRect.offset(0, mYOffsetForIme);
                    t.setPosition(dividerLeash1, mTempRect.left, mTempRect.top);
                }
                if (dividerLeash2 != null) {
                    getRefDividerBounds(false, mTempRect);
                    mTempRect.offset(0, mYOffsetForIme);
                    t.setPosition(dividerLeash2, mTempRect.left, mTempRect.top);
                }

                copyLeftRefBounds(mTempRect);
                mTempRect.offset(0, mYOffsetForIme);
                t.setPosition(leash1, mTempRect.left, mTempRect.top);
                copyMiddleRefBounds(mTempRect);
                mTempRect.offset(0, mYOffsetForIme);
                t.setPosition(leash2, mTempRect.left, mTempRect.top);
                copyRightRefBounds(mTempRect);
                mTempRect.offset(0, mYOffsetForIme);
                t.setPosition(leash3, mTempRect.left, mTempRect.top);
                adjusted = true;
            }

            return adjusted;
        }
    }

}
