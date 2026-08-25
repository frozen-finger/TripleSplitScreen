package com.android.wm.shell.triplesplit.split.view;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.Property;
import android.view.GestureDetector;
import android.view.InsetsState;
import android.view.MotionEvent;
import android.view.SurfaceControlViewHost;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.wm.shell.animation.Interpolators;
import com.android.wm.shell.triplesplit.R;
import com.android.wm.shell.triplesplit.split.SplitLayout;
import com.android.wm.shell.triplesplit.split.SplitScreenDimenConfig;
import com.android.wm.shell.triplesplit.split.SplitWindowManager;
import com.android.wm.shell.triplesplit.split.util.DividerSnapAlgorithm.SnapTarget;
import com.android.wm.shell.triplesplit.split.util.InputDirection;
import com.android.wm.shell.triplesplit.split.util.ViewGestureContext.DistanceGestureContext;

public class DividerView extends FrameLayout implements View.OnTouchListener {
    private static final String TAG = DividerView.class.getSimpleName();
    public static final long TOUCH_ANIMATION_DURATION = 150;
    public static final long TOUCH_RELEASE_ANIMATION_DURATION = 200;

    private final Paint mPaint = new Paint();
    private final Rect mBackgroundRect = new Rect();
    private final int mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    private SplitLayout mSplitLayout;
    private SplitWindowManager mSplitWindowManager;
    private SurfaceControlViewHost mViewHost;
    private DividerHandleView mHandle;
    private DividerRoundedCorner mCorners;
    private int mTouchElevation;

    private VelocityTracker mVelocityTracker;
    private boolean mMoving;
    private int mStartPos;
    private int mStartDividerPosition;
    private GestureDetector mDoubleTapDetector;
    private boolean mInteractive;
    private boolean mHideHandle;
    private boolean mSetTouchRegion = true;
    private int mLastDraggingPosition;

//    private DistanceGestureContext mDistanceGestureContext;
//    private ViewMotionValue mViewMotionValue;

    @Nullable private Integer mDragStartingSnapPosition;
    @Nullable private Integer mLastHoveredOverSnapPosition;
    private boolean mDraggedOutOfStartingRegion = false;

    private final Rect mDividerBounds = new Rect();
    private final Rect mTempRect = new Rect();
    @IdRes private int mDividerBarId;
    @IdRes private int mDividerHandleId;
    @IdRes private int mDividerCornerId;
    private View mDividerBar;
    private int mId;

    static final Property<DividerView, Integer> DIVIDER_HEIGHT_PROPERTY =
            new Property<DividerView, Integer>(Integer.class, "height") {
                @Override
                public Integer get(DividerView object) {
                    if (object.mDividerBar == null || object.mDividerBar.getLayoutParams() == null) {
                        return 0;
                    }
                    return object.mDividerBar.getLayoutParams().height;
                }

                @Override
                public void set(DividerView object, Integer value) {
                    if (object.mDividerBar == null || object.mDividerBar.getLayoutParams() == null) {
                        return;
                    }
                    ViewGroup.LayoutParams lp = object.mDividerBar.getLayoutParams();
                    if (lp.height == value) {
                        return;
                    }
                    lp.height = value;
                    object.mDividerBar.setLayoutParams(lp);
                }
            };

    private AnimatorListenerAdapter mAnimationListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationCancel(Animator animation) {
            mSetTouchRegion = true;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mSetTouchRegion = true;
        }
    };

    //TODO: Accessibility delegate and action

    public DividerView(Context context) {
        super(context);
    }

    public DividerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DividerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DividerView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setup(SplitLayout layout, SplitWindowManager splitWindowManager,
                      SurfaceControlViewHost viewHost, InsetsState insetsState, int id) {
        setup(layout, splitWindowManager, viewHost, insetsState, id,
                SplitScreenDimenConfig.DEFAULT);
    }

    public void setup(SplitLayout layout, SplitWindowManager splitWindowManager,
                      SurfaceControlViewHost viewHost, InsetsState insetsState, int id,
                      SplitScreenDimenConfig dimenConfig) {
        mSplitLayout = layout;
        mSplitWindowManager = splitWindowManager;
        mViewHost = viewHost;
        if (id == 1) {
            mDividerBounds.set(layout.getDividerBounds(true));
        } else {
            mDividerBounds.set(layout.getDividerBounds(false));
        }
        mId = id;
        applyDimens(dimenConfig != null ? dimenConfig : SplitScreenDimenConfig.DEFAULT);
        onInsetsChanged(insetsState, false/* animate */);
    }

    public void setDividerViewIds(@IdRes int dividerBarId, @IdRes int dividerHandleId,
            @IdRes int dividerCornerId) {
        mDividerBarId = dividerBarId;
        mDividerHandleId = dividerHandleId;
        mDividerCornerId = dividerCornerId;
        resolveDividerViews();
    }

    public void applyDimens(SplitScreenDimenConfig dimenConfig) {
        if (mHandle != null) {
            mHandle.setDimens(dimenConfig.getDividerHandleWidth(getContext()),
                    dimenConfig.getDividerHandleHeight(getContext()));
        }
        if (mCorners != null) {
            mCorners.setDividerWidth(dimenConfig.getDividerVisualWidth(getContext()));
            mCorners.setRadiusResource(dimenConfig.getDividerCornerSizeResId());
        }
        requestLayout();
        invalidate();
    }

    public void onInsetsChanged(InsetsState insetsState, boolean animate) {
        mDividerBounds.set(mSplitLayout.getDividerBounds(mId == 1));
        Log.i(TAG, "onInsetsChanged current bounds=" + mDividerBounds);
        //TODO: calculate and set new Rect for divider when insets changed
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        resolveDividerViews();
        mTouchElevation = 10;
        mDoubleTapDetector = new GestureDetector(getContext(), new DoubleTapListener());
        mInteractive = true;
        mHideHandle = false;
        setOnTouchListener(this);
        setWillNotDraw(false);
        mPaint.setColor(Color.BLACK);
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
    }

    private void resolveDividerViews() {
        mDividerBar = findConfiguredView(mDividerBarId, R.id.divider_bar);
        if (mDividerBar == null && getChildCount() > 0) {
            mDividerBar = getChildAt(0);
        }
        mHandle = findConfiguredViewOfType(mDividerHandleId, R.id.docked_divider_handle,
                DividerHandleView.class);
        if (mHandle == null) {
            mHandle = findFirstChildOfType(this, DividerHandleView.class);
        }
        mCorners = findConfiguredViewOfType(mDividerCornerId, R.id.docked_divider_rounded_corner,
                DividerRoundedCorner.class);
        if (mCorners == null) {
            mCorners = findFirstChildOfType(this, DividerRoundedCorner.class);
        }
    }

    @Nullable
    private View findConfiguredView(@IdRes int configuredId, @IdRes int defaultId) {
        View view = configuredId != 0 ? findViewById(configuredId) : null;
        return view != null ? view : findViewById(defaultId);
    }

    @Nullable
    private <T extends View> T findConfiguredViewOfType(@IdRes int configuredId,
            @IdRes int defaultId, Class<T> type) {
        T view = findViewByIdOfType(configuredId, type);
        return view != null ? view : findViewByIdOfType(defaultId, type);
    }

    @Nullable
    private <T extends View> T findViewByIdOfType(@IdRes int id, Class<T> type) {
        if (id == 0) return null;
        View view = findViewById(id);
        if (view == null) return null;
        if (type.isInstance(view)) return type.cast(view);
        Log.w(TAG, "Divider child id=" + id + " is not " + type.getSimpleName());
        return null;
    }

    @Nullable
    private static <T extends View> T findFirstChildOfType(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            T child = findFirstChildOfType(group.getChildAt(i), type);
            if (child != null) return child;
        }
        return null;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mSetTouchRegion) {
            updateTouchableRegion();
        }

        if (changed) {
            int dividerSize = mSplitLayout.getDividerVisualWidth();
            left = (getWidth() - dividerSize) / 2;
            top = 0;
            right = left + dividerSize;
            bottom = getHeight();
            mBackgroundRect.set(left, top, right, bottom);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mSplitLayout == null || !mInteractive) {
            return false;
        }

        final int action = event.getAction() & MotionEvent.ACTION_MASK;
        final int touchPos = getTouchPosition(event);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDoubleTapDetector.onTouchEvent(event);
                mVelocityTracker = VelocityTracker.obtain();
                mVelocityTracker.addMovement(event);
                setTouching();
                mStartPos = touchPos;
                mStartDividerPosition = mSplitLayout.getDividerVisualPositionForTouch(mId);
                mLastDraggingPosition = mStartDividerPosition;
                mMoving = false;
                mSplitLayout.onStartDragging(mId);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mVelocityTracker == null) {
                    mVelocityTracker = VelocityTracker.obtain();
                }
                mVelocityTracker.addMovement(event);
                int displacement = touchPos - mStartPos;
                if (!mMoving && Math.abs(displacement) > mTouchSlop) {
                    mStartPos = touchPos;
                    mStartDividerPosition = mSplitLayout.getDividerVisualPositionForTouch(mId);
                    mMoving = true;
                    resetDoubleTapDetector();
//                    initSnapOnMove(displacement);
                }
                if (!mMoving) {
                    mDoubleTapDetector.onTouchEvent(event);
                }
                if (mMoving) {
                    final int position = mStartDividerPosition + touchPos - mStartPos;
                    mLastDraggingPosition = mSplitLayout.updateDividerPositionDuringDrag(
                            mId, position);
//                    updateMagneticSnapCalculation(position);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseTouching();
                if (!mMoving) {
                    mDoubleTapDetector.onTouchEvent(event);
                    mSplitLayout.cancelCurrentDragPositions();
                    mSplitLayout.onDraggingCancelled(mId);
                    recycleVelocityTracker();
                    break;
//                    cleanUpMagneticSnapFramework();
                }
                mSplitLayout.cancelCurrentDragPositions();
                mSplitLayout.onDraggingCancelled(mId);
                recycleVelocityTracker();
                mMoving = false;
                break;
            case MotionEvent.ACTION_UP:
                releaseTouching();
                if (!mMoving) {
                    mDoubleTapDetector.onTouchEvent(event);
                    mSplitLayout.onDraggingCancelled(mId);
                    recycleVelocityTracker();
                    break;
//                    cleanUpMagneticSnapFramework();
                }
                if (mVelocityTracker == null) {
                    mVelocityTracker = VelocityTracker.obtain();
                }
                mVelocityTracker.addMovement(event);
                mVelocityTracker.computeCurrentVelocity(1000 /* units */);
                final float velocity = mVelocityTracker.getXVelocity();
//                final Pair<SnapTarget, SnapTarget> snapTarget = mSplitLayout.
//                        findSnapTarget(
//                                mId == 1 ? position : mSplitLayout.getDividerPosition(1),
//                                mId == 1 ? mSplitLayout.getDividerPosition(2) : position,
//                                velocity, false);
                final int leftDividerPosition = mSplitLayout.getDraggingLeftDividerPosition();
                final int rightDividerPosition = mSplitLayout.getDraggingRightDividerPosition();
                final Pair<SnapTarget, SnapTarget> snapTarget = mSplitLayout.
                        findSnapTarget(leftDividerPosition, rightDividerPosition, velocity, false);
                mSplitLayout.snapCurrentDragPositionsToTarget(snapTarget);
                recycleVelocityTracker();
                mMoving = false;
                resetDoubleTapDetector();
//                cleanUpMagneticSnapFramework();
                break;
        }

        return true;
    }

    private int getTouchPosition(MotionEvent event) {
        final Rect rootBounds = mSplitLayout.getRootBounds();
        final int rawPosition = Math.round(event.getRawX()) - rootBounds.left;
        final int tolerance = Math.max(mDividerBounds.width(), mTouchSlop);
        if (rawPosition >= -tolerance && rawPosition <= rootBounds.width() + tolerance) {
            return rawPosition;
        }

        mDividerBounds.set(mSplitLayout.getDividerBounds(mId == 1));
        return Math.round(mDividerBounds.left + event.getX()) - rootBounds.left;
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    /**
     * Drag gestures must not become the first tap in a later double-tap sequence.
     * Recreate the detector to drop any pending DOWN/UP history after a real divider move.
     */
    private void resetDoubleTapDetector() {
        mDoubleTapDetector = new GestureDetector(getContext(), new DoubleTapListener());
    }

    /**
     * Updates the WindowlessWindowManager touch region in local divider-window coordinates.
     *
     * The logical divider position may sit exactly at root left/right when a stage is offscreen.
     * The input region is independently clipped to the visible divider area and then inset from
     * the physical display edge, so edge Back gesture does not steal divider drag down events.
     */
    public void updateTouchableRegion() {
        if (!mSetTouchRegion || mSplitLayout == null || mSplitWindowManager == null) {
            return;
        }

        mDividerBounds.set(mSplitLayout.getDividerBounds(mId == 1));
        final Rect rootBounds = mSplitLayout.getRootBounds();
        final int edgeGestureInset = mSplitLayout.getDividerEdgeGestureInset();
        final int safeLeft = rootBounds.left + edgeGestureInset;
        final int safeRight = rootBounds.right - edgeGestureInset;

        final Rect visibleDividerBounds = mTempRect;
        visibleDividerBounds.set(mDividerBounds);
        if (!visibleDividerBounds.intersect(rootBounds)) {
            // Fully offscreen dividers must not leave stale touchable regions behind.
            mSplitWindowManager.setTouchRegion(new Rect());
            return;
        }

        int touchLeft = Math.max(visibleDividerBounds.left, safeLeft);
        int touchRight = Math.min(visibleDividerBounds.right, safeRight);
        int touchTop = visibleDividerBounds.top;
        int touchBottom = visibleDividerBounds.bottom;

        if (touchLeft >= touchRight || touchTop >= touchBottom) {
            mSplitWindowManager.setTouchRegion(new Rect());
            return;
        }

        mTempRect.set(touchLeft - mDividerBounds.left, touchTop - mDividerBounds.top,
                touchRight - mDividerBounds.left, touchBottom - mDividerBounds.top);
        mSplitWindowManager.setTouchRegion(mTempRect);
    }

    private void initSnapOnMove(int displacement) {
        // TODO Magnetic Snap
    }

    private void updateMagneticSnapCalculation(int position) {
        // TODO Magnetic Snap
    }

    private void cleanUpMagneticSnapFramework() {
        // TODO Magnetic Snap
    }

    private void setTouching() {
        mHandle.setTouching(true, true);
        mHandle.animate()
                .setInterpolator(Interpolators.TOUCH_RESPONSE)
                .setDuration(TOUCH_ANIMATION_DURATION)
                .translationZ(0)
                .start();
    }

    private void releaseTouching() {
        mHandle.setTouching(false, true);
        mHandle.animate()
                .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                .setDuration(TOUCH_RELEASE_ANIMATION_DURATION)
                .translationZ(0)
                .start();
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
            setHovering();
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
            releaseHovering();
            return true;
        }
        return false;
    }

    void setHovering() {
        mHandle.setHovering(true, true);
        mHandle.animate()
                .setInterpolator(Interpolators.TOUCH_RESPONSE)
                .setDuration(TOUCH_ANIMATION_DURATION)
                .translationZ(mTouchElevation)
                .start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(mBackgroundRect, mPaint);
    }

    void releaseHovering() {
        mHandle.setHovering(false, true);
        mHandle.animate()
                .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                .setDuration(TOUCH_RELEASE_ANIMATION_DURATION)
                .translationZ(0)
                .start();
    }

    public void setInteractive(boolean interactive, boolean hideHandle, String from) {
        if (interactive == mInteractive) return;
        Log.i(TAG, "Set divider bar hide=" + hideHandle + " interactive=" + interactive
            + " from " + from);
        mInteractive = interactive;
        mHideHandle = hideHandle;
        if (!mInteractive && mHideHandle && mMoving) {
            final int position = mSplitLayout.getDividerPosition(mId);
            mSplitLayout.flingDividerPosition(
                    mId,
                    mLastDraggingPosition,
                    position,
                    mSplitLayout.FLING_RESIZE_DURATION,
                    Interpolators.FAST_OUT_SLOW_IN,
                    () -> mSplitLayout.setDividerPosition(position, mId == 1, true));
            recycleVelocityTracker();
            mMoving = false;
            releaseTouching();
            mHandle.setVisibility(!mInteractive && mHideHandle ? View.INVISIBLE : View.VISIBLE);
        }
    }

    public boolean isInteractive() {
        return mInteractive;
    }

    public boolean isHandleHidden() {
        return mHideHandle;
    }

    public boolean isMoving() {
        return mMoving;
    }

    private class DoubleTapListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            if (!mMoving && e.getAction() == MotionEvent.ACTION_UP) {
                if (mSplitLayout != null) {
                    mSplitLayout.onDoubleTappedDivider(mId);
                }
                return true;
            }
            return false;
        }
    }
}
