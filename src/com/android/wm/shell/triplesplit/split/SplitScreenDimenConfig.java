package com.android.wm.shell.triplesplit.split;

import android.content.Context;

import androidx.annotation.DimenRes;

import com.android.wm.shell.triplesplit.R;

/** Immutable resource-based configuration shared by both triple-split dividers. */
public final class SplitScreenDimenConfig {
    public static final SplitScreenDimenConfig DEFAULT = new Builder().build();

    @DimenRes private final int mDividerBarWidthResId;
    @DimenRes private final int mStageGapWidthResId;
    @DimenRes private final int mDividerVisualWidthResId;
    @DimenRes private final int mDividerHandleRegionWidthResId;
    @DimenRes private final int mOffscreenTouchZoneWidthResId;
    @DimenRes private final int mDividerHandleWidthResId;
    @DimenRes private final int mDividerHandleHeightResId;
    @DimenRes private final int mDividerCornerSizeResId;

    private SplitScreenDimenConfig(Builder builder) {
        mDividerBarWidthResId = builder.mDividerBarWidthResId;
        mStageGapWidthResId = builder.mStageGapWidthResId;
        mDividerVisualWidthResId = builder.mDividerVisualWidthResId;
        mDividerHandleRegionWidthResId = builder.mDividerHandleRegionWidthResId;
        mOffscreenTouchZoneWidthResId = builder.mOffscreenTouchZoneWidthResId;
        mDividerHandleWidthResId = builder.mDividerHandleWidthResId;
        mDividerHandleHeightResId = builder.mDividerHandleHeightResId;
        mDividerCornerSizeResId = builder.mDividerCornerSizeResId;
    }

    public int getStageGapWidth(Context context) {
        return readPixelSize(context, mStageGapWidthResId, mDividerBarWidthResId,
                R.dimen.split_divider_bar_width);
    }

    public int getDividerVisualWidth(Context context) {
        return readPixelSize(context, mDividerVisualWidthResId, mDividerBarWidthResId,
                R.dimen.split_divider_bar_width);
    }

    public int getDividerHandleRegionWidth(Context context) {
        return readPixelSize(context, mDividerHandleRegionWidthResId,
                R.dimen.split_divider_handle_region_width);
    }

    public int getOffscreenTouchZoneWidth(Context context) {
        return readPixelSize(context, mOffscreenTouchZoneWidthResId,
                R.dimen.split_offscreen_touch_zone_width);
    }

    public int getDividerHandleWidth(Context context) {
        return readPixelSize(context, mDividerHandleWidthResId,
                R.dimen.split_divider_handle_width);
    }

    public int getDividerHandleHeight(Context context) {
        return readPixelSize(context, mDividerHandleHeightResId,
                R.dimen.split_divider_handle_height);
    }

    @DimenRes
    public int getDividerCornerSizeResId() {
        return mDividerCornerSizeResId != 0
                ? mDividerCornerSizeResId : R.dimen.split_divider_corner_size;
    }

    private static int readPixelSize(Context context, @DimenRes int overrideResId,
            @DimenRes int defaultResId) {
        return context.getResources().getDimensionPixelSize(
                overrideResId != 0 ? overrideResId : defaultResId);
    }

    private static int readPixelSize(Context context, @DimenRes int overrideResId,
            @DimenRes int fallbackOverrideResId, @DimenRes int defaultResId) {
        final int resId = overrideResId != 0 ? overrideResId
                : fallbackOverrideResId != 0 ? fallbackOverrideResId : defaultResId;
        return context.getResources().getDimensionPixelSize(resId);
    }

    public static final class Builder {
        @DimenRes private int mDividerBarWidthResId;
        @DimenRes private int mStageGapWidthResId;
        @DimenRes private int mDividerVisualWidthResId;
        @DimenRes private int mDividerHandleRegionWidthResId;
        @DimenRes private int mOffscreenTouchZoneWidthResId;
        @DimenRes private int mDividerHandleWidthResId;
        @DimenRes private int mDividerHandleHeightResId;
        @DimenRes private int mDividerCornerSizeResId;

        /** Legacy fallback for both stage gap and visible divider width. */
        public Builder setDividerBarWidth(@DimenRes int resId) {
            mDividerBarWidthResId = resId;
            return this;
        }

        /** Sets the real gap between adjacent stage bounds. */
        public Builder setStageGapWidth(@DimenRes int resId) {
            mStageGapWidthResId = resId;
            return this;
        }

        /** Sets the visible divider-bar width without changing stage bounds. */
        public Builder setDividerVisualWidth(@DimenRes int resId) {
            mDividerVisualWidthResId = resId;
            return this;
        }

        /** Sets the wider divider touch region. */
        public Builder setDividerHandleRegionWidth(@DimenRes int resId) {
            mDividerHandleRegionWidthResId = resId;
            return this;
        }

        /** Sets the edge touch zone used by offscreen/minimized split states. */
        public Builder setOffscreenTouchZoneWidth(@DimenRes int resId) {
            mOffscreenTouchZoneWidthResId = resId;
            return this;
        }

        /** Sets the visible divider handle width. */
        public Builder setDividerHandleWidth(@DimenRes int resId) {
            mDividerHandleWidthResId = resId;
            return this;
        }

        /** Sets the visible divider handle height. */
        public Builder setDividerHandleHeight(@DimenRes int resId) {
            mDividerHandleHeightResId = resId;
            return this;
        }

        /** Sets the rounded-corner radius for divider bar overlays. */
        public Builder setDividerCornerSize(@DimenRes int resId) {
            mDividerCornerSizeResId = resId;
            return this;
        }

        public SplitScreenDimenConfig build() {
            return new SplitScreenDimenConfig(this);
        }
    }
}
