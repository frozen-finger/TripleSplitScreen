package com.android.wm.shell.triplesplit.split.view;

import static com.android.wm.shell.triplesplit.split.view.DividerView.TOUCH_ANIMATION_DURATION;
import static com.android.wm.shell.triplesplit.split.view.DividerView.TOUCH_RELEASE_ANIMATION_DURATION;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Property;
import android.view.View.MeasureSpec;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.wm.shell.animation.Interpolators;
import com.android.wm.shell.triplesplit.R;

public class DividerHandleView extends View {
    private static final float HANDLE_LENGTH_RATIO = 1f / 3f;

    public DividerHandleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mPaint.setColor(Color.WHITE);
        mPaint.setAntiAlias(true);
        updateDimens();
    }

    private final Paint mPaint = new Paint();
    private int mWidth;
    private int mHeight;
    private int mTouchingWidth;
    private int mTouchingHeight;
    private int mCurrentWidth;
    private int mCurrentHeight;
    private AnimatorSet mAnimator;
    private boolean mTouching;
    private boolean mHovering;
    private int mHoveringWidth;
    private int mHoveringHeight;

    private static final Property<DividerHandleView, Integer> WIDTH_PROPERTY =
            new Property<DividerHandleView, Integer>(Integer.class, "width") {
                @Override
                public Integer get(DividerHandleView object) {
                    return object.mCurrentWidth;
                }

                @Override
                public void set(DividerHandleView object, Integer value) {
                    object.mCurrentWidth = value;
                    object.invalidate();
                    object.requestLayout();
                }
            };

    private static final Property<DividerHandleView, Integer> HEIGHT_PROPERTY =
            new Property<DividerHandleView, Integer>(Integer.class, "height") {
                @Override
                public Integer get(DividerHandleView object) {
                    return object.mCurrentHeight;
                }

                @Override
                public void set(DividerHandleView object, Integer value) {
                    object.mCurrentHeight = value;
                    object.invalidate();
                    object.requestLayout();
                }
            };

    private void updateDimens() {
        mWidth = getResources().getDimensionPixelSize(R.dimen.split_divider_handle_width);
        mHeight = getResources().getDimensionPixelSize(R.dimen.split_divider_handle_height);
        mCurrentHeight = mHeight;
        mCurrentWidth = mWidth;
        mTouchingWidth = mWidth * 2;
        mTouchingHeight = mHeight;
        mHoveringWidth = (int) (mWidth * 1.5f);
        mHoveringHeight = mHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int desiredWidth = Math.max(mWidth, Math.max(mTouchingWidth, mHoveringWidth));
        final int desiredHeight = Math.max(mHeight, Math.max(mTouchingHeight, mHoveringHeight));
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
        updateHandleLength(getMeasuredHeight());
    }

    public void setColor(int color, boolean invalidateView) {
        mPaint.setColor(color);
        if (invalidateView) {
            invalidate();
        }
    }

    public void setTouching(boolean touching, boolean animate) {
        if (touching == mTouching) {
            return;
        }
        mTouching = touching;
        updateHandleLength(getHeight());
        setInputState(touching, animate, mTouchingWidth, mTouchingHeight);
    }

    public void setHovering(boolean hovering, boolean animate) {
        if (hovering == mHovering) {
            return;
        }
        updateHandleLength(getHeight());
        setInputState(hovering, animate, mHoveringWidth, mHoveringHeight);
        mHovering = hovering;
    }

    private void updateHandleLength(int hostHeight) {
        if (hostHeight <= 0) {
            return;
        }
        final int baseLength = Math.max(mHeight, Math.round(hostHeight * HANDLE_LENGTH_RATIO));
        mHeight = baseLength;
        mTouchingHeight = baseLength;
        mHoveringHeight = baseLength;
        if (!mTouching && !mHovering) {
            mCurrentHeight = baseLength;
        }
    }

    private void setInputState(boolean stateOn, boolean animate, int stateWidth, int stateHeight) {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        if (!animate) {
            mCurrentWidth = stateOn ? stateWidth : mWidth;
            mCurrentHeight = stateOn ? stateHeight : mHeight;
            invalidate();
        } else {
            animateToTarget(stateOn ? stateWidth : mWidth,
                    stateOn ? stateHeight : mHeight, stateOn);
        }
    }

    private void animateToTarget(int targetWidth, int targetHeight, boolean touching) {
        ObjectAnimator widthAnimator = ObjectAnimator.ofInt(this, WIDTH_PROPERTY,
                mCurrentWidth, targetWidth);
        ObjectAnimator heightAnimator = ObjectAnimator.ofInt(this, HEIGHT_PROPERTY,
                mCurrentHeight, targetHeight);
        mAnimator = new AnimatorSet();
        mAnimator.playTogether(widthAnimator, heightAnimator);
        mAnimator.setDuration(touching
                ? TOUCH_ANIMATION_DURATION
                : TOUCH_RELEASE_ANIMATION_DURATION);
        mAnimator.setInterpolator(touching
                ? Interpolators.TOUCH_RESPONSE
                : Interpolators.FAST_OUT_SLOW_IN);
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimator = null;
            }
        });
        mAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int left = getWidth() / 2 - mCurrentWidth / 2;
        int top = getHeight() / 2 - mCurrentHeight / 2;
        int radius = Math.min(mCurrentWidth, mCurrentHeight) / 2;
        canvas.drawRoundRect(left, top, left + mCurrentWidth, top + mCurrentHeight,
                radius, radius, mPaint);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

}
