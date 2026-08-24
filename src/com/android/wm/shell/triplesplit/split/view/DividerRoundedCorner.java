package com.android.wm.shell.triplesplit.split.view;

import static android.view.RoundedCorner.POSITION_BOTTOM_LEFT;
import static android.view.RoundedCorner.POSITION_BOTTOM_RIGHT;
import static android.view.RoundedCorner.POSITION_TOP_LEFT;
import static android.view.RoundedCorner.POSITION_TOP_RIGHT;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.RoundedCorner;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.wm.shell.triplesplit.R;

public class DividerRoundedCorner extends View {
    private final int mDividerWidth;
    private final Paint mDividerBarBackground;
    private final Point mStartPos = new Point();
    private InvertedRoundedCornerDrawInfo mTopLeftCorner;
    private InvertedRoundedCornerDrawInfo mTopRightCorner;
    private InvertedRoundedCornerDrawInfo mBottomLeftCorner;
    private InvertedRoundedCornerDrawInfo mBottomRightCorner;
    private int mRadiusResourceId = 0;
    public DividerRoundedCorner(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mDividerWidth = getResources().getDimensionPixelOffset(R.dimen.split_divider_bar_width);
        mDividerBarBackground = new Paint();
        mDividerBarBackground.setColor(Color.BLACK);
        mDividerBarBackground.setFlags(Paint.ANTI_ALIAS_FLAG);
        mDividerBarBackground.setStyle(Paint.Style.FILL);
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mTopLeftCorner = new InvertedRoundedCornerDrawInfo(POSITION_TOP_LEFT);
        mTopRightCorner = new InvertedRoundedCornerDrawInfo(POSITION_TOP_RIGHT);
        mBottomLeftCorner = new InvertedRoundedCornerDrawInfo(POSITION_BOTTOM_LEFT);
        mBottomRightCorner = new InvertedRoundedCornerDrawInfo(POSITION_BOTTOM_RIGHT);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();

        mTopLeftCorner.calculateStartPos(mStartPos);
        canvas.translate(mStartPos.x, mStartPos.y);
        canvas.drawPath(mTopLeftCorner.mPath, mDividerBarBackground);

        canvas.translate(-mStartPos.x, -mStartPos.y);
        mTopRightCorner.calculateStartPos(mStartPos);
        canvas.translate(mStartPos.x, mStartPos.y);
        canvas.drawPath(mTopRightCorner.mPath, mDividerBarBackground);

        canvas.translate(-mStartPos.x, -mStartPos.y);
        mBottomLeftCorner.calculateStartPos(mStartPos);
        canvas.translate(mStartPos.x, mStartPos.y);
        canvas.drawPath(mBottomLeftCorner.mPath, mDividerBarBackground);

        canvas.translate(-mStartPos.x, -mStartPos.y);
        mBottomRightCorner.calculateStartPos(mStartPos);
        canvas.translate(mStartPos.x, mStartPos.y);
        canvas.drawPath(mBottomRightCorner.mPath, mDividerBarBackground);

        canvas.restore();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public void setRadiusResource(int radiusResId) {
        mRadiusResourceId = radiusResId;
    }

    public void setRoundCornerColor(int cornerColor, boolean invalidateView) {
        mDividerBarBackground.setColor(cornerColor);
        if (invalidateView) {
            invalidate();
        }
    }

    private class InvertedRoundedCornerDrawInfo {
        private final int mCornerPosition;

        private final int mRadius;

        private final Path mPath = new Path();

        InvertedRoundedCornerDrawInfo( int cornerPosition) {
            mCornerPosition = cornerPosition;

            if (mRadiusResourceId == 0) {
                final RoundedCorner roundedCorner = getDisplay().getRoundedCorner(cornerPosition);
                mRadius = roundedCorner == null ? 0 : roundedCorner.getRadius();
            } else {
                mRadius = getContext().getResources().getDimensionPixelSize(mRadiusResourceId);
            }

            // Starts with a filled square, and then subtracting out a circle from the appropriate
            // corner.
            final Path square = new Path();
            square.addRect(0, 0, mRadius, mRadius, Path.Direction.CW);
            final Path circle = new Path();
            circle.addCircle(
                    isLeftCorner() ? mRadius : 0 /* x */,
                    isTopCorner() ? mRadius : 0 /* y */,
                    mRadius, Path.Direction.CW);
            mPath.op(square, circle, Path.Op.DIFFERENCE);
        }

        private void calculateStartPos(Point outPos) {
            outPos.x = isLeftCorner()
                    ? getWidth() / 2 + mDividerWidth / 2
                    : getWidth() / 2 - mDividerWidth / 2 - mRadius;
            outPos.y = isTopCorner() ? 0 : getHeight() - mRadius;
        }

        private boolean isLeftCorner() {
            return mCornerPosition == POSITION_TOP_LEFT || mCornerPosition == POSITION_BOTTOM_LEFT;
        }

        private boolean isTopCorner() {
            return mCornerPosition == POSITION_TOP_LEFT || mCornerPosition == POSITION_TOP_RIGHT;
        }
    }
}
