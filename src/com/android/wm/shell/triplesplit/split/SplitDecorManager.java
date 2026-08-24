package com.android.wm.shell.triplesplit.split;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.util.Log;
import android.view.SurfaceControl;
import android.window.ScreenCapture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.FADE_DURATION;

/**
 * Lightweight split decor that covers a resizing stage with the last available frame.
 *
 * This mirrors the AOSP SplitDecorManager screenshot layer behavior, but only keeps the screenshot
 * cover path. The first resize after launch only records bounds; a cover is shown only when an
 * existing screenshot/custom image is available.
 */
class SplitDecorManager {
    private static final String TAG = SplitDecorManager.class.getSimpleName();
    private static final String SCREENSHOT_LAYER_NAME = "SplitDecorScreenshot";
    private static final int SCREENSHOT_LAYER = Integer.MAX_VALUE - 1;

    private final Rect mLastStableBounds = new Rect();
    private final Rect mTmpCaptureBounds = new Rect();

    private SurfaceControl mHostLeash;
    private SurfaceControl mScreenshotLeash;
    private HardwareBuffer mCustomBuffer;
    private ColorSpace mCustomColorSpace;
    private int mCustomBufferWidth;
    private int mCustomBufferHeight;
    private boolean mHasShownDuringCurrentResize;
    private ValueAnimator mFadeAnimator;

    void attachToHost(@Nullable SurfaceControl hostLeash) {
        if (hostLeash == null || !hostLeash.isValid()) {
            return;
        }
        mHostLeash = hostLeash;
    }

    void release(@NonNull SurfaceControl.Transaction t) {
        cancelFadeAnimation();
        removeScreenshot(t);
        clearCustomBuffer();
        mLastStableBounds.setEmpty();
        mHostLeash = null;
        mHasShownDuringCurrentResize = false;
    }

    void setCustomBitmap(@Nullable Bitmap bitmap) {
        clearCustomBuffer();
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }

        Bitmap hardwareBitmap = bitmap;
        if (bitmap.getConfig() != Bitmap.Config.HARDWARE) {
            try {
                hardwareBitmap = bitmap.copy(Bitmap.Config.HARDWARE, false);
            } catch (IllegalArgumentException | IllegalStateException e) {
                Log.w(TAG, "Unable to convert custom decor bitmap to hardware bitmap", e);
                return;
            }
        }

        try {
            mCustomBuffer = hardwareBitmap.getHardwareBuffer();
        } catch (IllegalStateException e) {
            Log.w(TAG, "Unable to get hardware buffer from custom decor bitmap", e);
            return;
        }
        if (mCustomBuffer == null || mCustomBuffer.isClosed()) {
            mCustomBuffer = null;
            return;
        }
        mCustomColorSpace = hardwareBitmap.getColorSpace();
        mCustomBufferWidth = hardwareBitmap.getWidth();
        mCustomBufferHeight = hardwareBitmap.getHeight();
    }

    void onResizing(@NonNull Rect newBounds, @NonNull SurfaceControl.Transaction t) {
        if (!canShowDecor(newBounds)) {
            return;
        }
        if (mLastStableBounds.equals(newBounds)) {
            return;
        }

        if (mScreenshotLeash == null && !mHasShownDuringCurrentResize) {
            if (mCustomBuffer != null) {
                showBuffer(mCustomBuffer, mCustomColorSpace, mCustomBufferWidth, mCustomBufferHeight,
                        newBounds, t);
            } else {
                showCapturedScreenshot(newBounds, t);
            }
            mHasShownDuringCurrentResize = mScreenshotLeash != null;
        } else if (mScreenshotLeash != null) {
            layoutScreenshot(newBounds, getScreenshotWidth(), getScreenshotHeight(), t);
        }
    }

    void onResized(@NonNull Rect stableBounds, @NonNull SurfaceControl.Transaction t) {
        mLastStableBounds.set(stableBounds);
        mHasShownDuringCurrentResize = false;
        if (mScreenshotLeash == null) {
            return;
        }
        fadeOutScreenshot();
    }

    private boolean canShowDecor(Rect newBounds) {
        return mHostLeash != null && mHostLeash.isValid()
                && !newBounds.isEmpty()
                && !mLastStableBounds.isEmpty();
    }

    private void showCapturedScreenshot(Rect newBounds, SurfaceControl.Transaction t) {
        mTmpCaptureBounds.set(mLastStableBounds);
        mTmpCaptureBounds.offsetTo(0, 0);
        final ScreenCapture.ScreenshotHardwareBuffer screenshot =
                ScreenCapture.captureLayers(mHostLeash, mTmpCaptureBounds, 1f);
        if (screenshot == null || screenshot.containsSecureLayers()) {
            return;
        }
        final HardwareBuffer buffer = screenshot.getHardwareBuffer();
        if (buffer == null || buffer.isClosed()) {
            return;
        }
        showBuffer(buffer, screenshot.getColorSpace(), buffer.getWidth(), buffer.getHeight(),
                newBounds, t);
    }

    private void showBuffer(HardwareBuffer buffer, @Nullable ColorSpace colorSpace,
            int bufferWidth, int bufferHeight, Rect newBounds, SurfaceControl.Transaction t) {
        if (bufferWidth <= 0 || bufferHeight <= 0) {
            return;
        }
        cancelFadeAnimation();
        if (mScreenshotLeash != null) {
            t.remove(mScreenshotLeash);
        }
        mScreenshotLeash = new SurfaceControl.Builder()
                .setName(SCREENSHOT_LAYER_NAME)
                .setBLASTLayer()
                .setFormat(buffer.getFormat())
                .setHidden(false)
                .setParent(mHostLeash)
                .setCallsite("SplitDecorManager#showBuffer")
                .build();
        t.setBuffer(mScreenshotLeash, buffer)
                .setLayer(mScreenshotLeash, SCREENSHOT_LAYER)
                .setAlpha(mScreenshotLeash, 1f)
                .show(mScreenshotLeash);
        if (colorSpace != null) {
            t.setColorSpace(mScreenshotLeash, colorSpace);
        }
        layoutScreenshot(newBounds, bufferWidth, bufferHeight, t);
    }

    private void layoutScreenshot(Rect bounds, int bufferWidth, int bufferHeight,
            SurfaceControl.Transaction t) {
        if (mScreenshotLeash == null || bufferWidth <= 0 || bufferHeight <= 0) {
            return;
        }
        t.setPosition(mScreenshotLeash, 0, 0)
                .setScale(mScreenshotLeash,
                        bounds.width() / (float) bufferWidth,
                        bounds.height() / (float) bufferHeight)
                .setWindowCrop(mScreenshotLeash, bounds.width(), bounds.height());
    }

    private int getScreenshotWidth() {
        if (mCustomBuffer != null) {
            return mCustomBufferWidth;
        }
        return mLastStableBounds.width();
    }

    private int getScreenshotHeight() {
        if (mCustomBuffer != null) {
            return mCustomBufferHeight;
        }
        return mLastStableBounds.height();
    }

    private void fadeOutScreenshot() {
        final SurfaceControl leash = mScreenshotLeash;
        if (leash == null || !leash.isValid()) {
            return;
        }
        final SurfaceControl.Transaction animT = new SurfaceControl.Transaction();
        mFadeAnimator = ValueAnimator.ofFloat(1f, 0f);
        mFadeAnimator.setDuration(FADE_DURATION);
        mFadeAnimator.addUpdateListener(animator -> {
            final float alpha = (float) animator.getAnimatedValue();
            if (leash.isValid()) {
                animT.setAlpha(leash, alpha).apply();
            }
        });
        mFadeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                if (leash.isValid()) {
                    animT.remove(leash).apply();
                }
                animT.close();
                if (mScreenshotLeash == leash) {
                    mScreenshotLeash = null;
                }
                mFadeAnimator = null;
            }
        });
        mFadeAnimator.start();
    }

    private void removeScreenshot(SurfaceControl.Transaction t) {
        if (mScreenshotLeash != null) {
            t.remove(mScreenshotLeash);
            mScreenshotLeash = null;
        }
    }

    private void cancelFadeAnimation() {
        if (mFadeAnimator != null) {
            mFadeAnimator.cancel();
            mFadeAnimator = null;
        }
    }

    private void clearCustomBuffer() {
        mCustomBuffer = null;
        mCustomColorSpace = null;
        mCustomBufferWidth = 0;
        mCustomBufferHeight = 0;
    }
}
