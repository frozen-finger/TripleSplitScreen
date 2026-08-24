package com.android.wm.shell.triplesplit.split;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorSpace;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.HardwareBuffer;
import android.util.Log;
import android.view.SurfaceControl;
import android.window.ScreenCapture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wm.shell.triplesplit.split.util.SplitIconProvider;

import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.FADE_DURATION;

/**
 * Lightweight split decor that covers a resizing stage with the last available frame.
 *
 * This mirrors the AOSP SplitDecorManager screenshot layer behavior, but only keeps the screenshot
 * cover path. The first resize after launch only records bounds; a cover is shown only when an
 * existing screenshot/custom image is available.
 */
public class SplitDecorManager {
    private static final String TAG = "SplitScreen";
    private static final String SCREENSHOT_LAYER_NAME = "SplitDecorScreenshot";
    private static final String ICON_BACKGROUND_LAYER_NAME = "SplitDecorIconBackground";
    private static final int SCREENSHOT_LAYER = Integer.MAX_VALUE - 1;
    private static final int ICON_BACKGROUND_LAYER = SCREENSHOT_LAYER - 1;
    private static final int MIN_DRAWABLE_SIZE = 1;
    private static final int DEFAULT_ICON_SIZE_DP = 48;

    @Nullable
    private SplitIconProvider mSplitIconProvider;

    private final Rect mLastStableBounds = new Rect();
    private final Rect mTmpCaptureBounds = new Rect();
    private final Rect mTmpLayoutBounds = new Rect();
    private final Rect mLastLoggedLayoutBounds = new Rect();

    private SurfaceControl mHostLeash;
    private SurfaceControl mCaptureLeash;
    private SurfaceControl mScreenshotLeash;
    private SurfaceControl mIconBackgroundLeash;
    private HardwareBuffer mCustomBuffer;
    private ColorSpace mCustomColorSpace;
    private int mCustomBufferWidth;
    private int mCustomBufferHeight;
    private int mScreenshotBufferWidth;
    private int mScreenshotBufferHeight;
    private boolean mIsIconCover;
    private boolean mHasAttemptedCoverDuringCurrentResize;
    private ValueAnimator mFadeInAnimator;
    private ValueAnimator mFadeAnimator;

    /**
     * Sets the provider used by this stage decor to create resize cover drawables.
     *
     * <p>Split decor asks this provider for the covered task package before using the custom bitmap
     * or screenshot fallback. Passing {@code null} clears the provider for this decor instance.</p>
     */
    void setSplitIconProvider(@Nullable SplitIconProvider splitIconProvider) {
        mSplitIconProvider = splitIconProvider;
        Log.d(TAG, "SplitDecor setSplitIconProvider registered="
                + (splitIconProvider != null));
    }

    void attachToHost(@Nullable SurfaceControl hostLeash) {
        if (hostLeash == null || !hostLeash.isValid()) {
            Log.w(TAG, "SplitDecor attachToHost skipped host=" + hostLeash);
            return;
        }
        mHostLeash = hostLeash;
        Log.d(TAG, "SplitDecor attachToHost host=" + hostLeash);
    }

    void release(@NonNull SurfaceControl.Transaction t) {
        Log.d(TAG, "SplitDecor release hasScreenshot=" + (mScreenshotLeash != null)
                + " hasCustomBuffer=" + (mCustomBuffer != null));
        cancelFadeAnimation();
        removeScreenshot(t);
        clearCustomBuffer();
        mLastStableBounds.setEmpty();
        mHostLeash = null;
        mCaptureLeash = null;
        mHasAttemptedCoverDuringCurrentResize = false;
    }

    void setCustomBitmap(@Nullable Bitmap bitmap) {
        clearCustomBuffer();
        if (bitmap == null || bitmap.isRecycled()) {
            Log.w(TAG, "SplitDecor setCustomBitmap skipped bitmap=" + bitmap);
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
            Log.w(TAG, "SplitDecor setCustomBitmap skipped invalid hardware buffer");
            return;
        }
        mCustomColorSpace = hardwareBitmap.getColorSpace();
        mCustomBufferWidth = hardwareBitmap.getWidth();
        mCustomBufferHeight = hardwareBitmap.getHeight();
        Log.d(TAG, "SplitDecor setCustomBitmap success size=" + mCustomBufferWidth
                + "x" + mCustomBufferHeight + " colorSpace=" + mCustomColorSpace);
    }

    void onResizing(@NonNull Rect newBounds, @Nullable SurfaceControl captureLeash,
                    @NonNull SurfaceControl.Transaction t) {
        onResizing(newBounds, captureLeash, t, null);
    }

    void onResizing(@NonNull Rect newBounds, @Nullable SurfaceControl captureLeash,
                    @NonNull SurfaceControl.Transaction t, @Nullable String packageName) {
        onResizing(newBounds, captureLeash, t, packageName, false);
    }

    void onStartResizing(@NonNull Rect newBounds, @Nullable SurfaceControl captureLeash,
            @NonNull SurfaceControl.Transaction t, @Nullable String packageName) {
        cancelFadeAnimation();
        mHasAttemptedCoverDuringCurrentResize = false;
        onResizing(newBounds, captureLeash, t, packageName, true);
    }

    void onSwapResizing(int width, int height, @NonNull SurfaceControl.Transaction t) {
        if (mScreenshotLeash == null || width <= 0 || height <= 0) {
            return;
        }
        mTmpLayoutBounds.set(0, 0, width, height);
        layoutScreenshot(mTmpLayoutBounds, getScreenshotWidth(), getScreenshotHeight(), t);
    }

    private void onResizing(@NonNull Rect newBounds, @Nullable SurfaceControl captureLeash,
            @NonNull SurfaceControl.Transaction t, @Nullable String packageName,
            boolean allowUnchangedBounds) {
        mCaptureLeash = captureLeash != null && captureLeash.isValid() ? captureLeash : mHostLeash;
        if (!canShowDecor(newBounds)) {
            Log.d(TAG, "SplitDecor onResizing skipped canShow=false host=" + mHostLeash
                    + " hostValid=" + (mHostLeash != null && mHostLeash.isValid())
                    + " capture=" + mCaptureLeash
                    + " captureValid=" + (mCaptureLeash != null && mCaptureLeash.isValid())
                    + " newBounds=" + newBounds + " lastStable=" + mLastStableBounds
                    + " hasScreenshot=" + (mScreenshotLeash != null)
                    + " hasCustomBuffer=" + (mCustomBuffer != null));
            return;
        }
        if (!allowUnchangedBounds && !mLastStableBounds.isEmpty()
                && mLastStableBounds.equals(newBounds)) {
            Log.d(TAG, "SplitDecor onResizing skipped unchanged bounds=" + newBounds);
            return;
        }

        if (mScreenshotLeash == null && !mHasAttemptedCoverDuringCurrentResize) {
            mHasAttemptedCoverDuringCurrentResize = true;
            Log.d(TAG, "SplitDecor onResizing show attempt custom=" + (mCustomBuffer != null)
                    + " iconProvider=" + (mSplitIconProvider != null)
                    + " packageName=" + packageName
                    + " lastStable=" + mLastStableBounds + " newBounds=" + newBounds
                    + " host=" + mHostLeash + " capture=" + mCaptureLeash
                    + " sourceBounds=" + (mLastStableBounds.isEmpty()
                            ? "newBounds" : "lastStableBounds"));
            boolean shown = showProviderDrawable(packageName, newBounds, t);
            if (!shown && mCustomBuffer != null) {
                shown = showBuffer(mCustomBuffer, mCustomColorSpace, mCustomBufferWidth,
                        mCustomBufferHeight, newBounds, t);
            }
            if (!shown) {
                showCapturedScreenshot(newBounds, t);
            }
            Log.d(TAG, "SplitDecor onResizing show result hasScreenshot="
                    + (mScreenshotLeash != null));
        } else if (mScreenshotLeash != null) {
            Log.d(TAG, "SplitDecor onResizing layout existing screenshot bounds=" + newBounds
                    + " leashValid=" + mScreenshotLeash.isValid()
                    + " bufferSize=" + getScreenshotWidth() + "x" + getScreenshotHeight());
            layoutScreenshot(newBounds, getScreenshotWidth(), getScreenshotHeight(), t);
        }
    }

    void onResized(@NonNull Rect stableBounds, @NonNull SurfaceControl.Transaction t) {
        Log.d(TAG, "SplitDecor onResized stableBounds=" + stableBounds
                + " hasScreenshot=" + (mScreenshotLeash != null));
        mLastStableBounds.set(stableBounds);
        mHasAttemptedCoverDuringCurrentResize = false;
        if (mScreenshotLeash == null) {
            return;
        }
        fadeOutScreenshot();
    }

    private boolean canShowDecor(Rect newBounds) {
        return mHostLeash != null && mHostLeash.isValid()
                && mCaptureLeash != null && mCaptureLeash.isValid()
                && !newBounds.isEmpty();
    }

    private void showCapturedScreenshot(Rect newBounds, SurfaceControl.Transaction t) {
        mTmpCaptureBounds.set(mLastStableBounds.isEmpty() ? newBounds : mLastStableBounds);
        mTmpCaptureBounds.offsetTo(0, 0);
        Log.d(TAG, "SplitDecor captureLayers start captureBounds=" + mTmpCaptureBounds
                + " captureSize=" + mTmpCaptureBounds.width() + "x" + mTmpCaptureBounds.height()
                + " newBounds=" + newBounds + " hostValid="
                + (mHostLeash != null && mHostLeash.isValid())
                + " capture=" + mCaptureLeash
                + " captureValid=" + (mCaptureLeash != null && mCaptureLeash.isValid()));
        final ScreenCapture.ScreenshotHardwareBuffer screenshot =
                ScreenCapture.captureLayers(mCaptureLeash, mTmpCaptureBounds, 1f);
        if (screenshot == null || screenshot.containsSecureLayers()) {
            Log.w(TAG, "SplitDecor captureLayers failed screenshot=" + screenshot
                    + " secure=" + (screenshot != null && screenshot.containsSecureLayers()));
            return;
        }
        final HardwareBuffer buffer = screenshot.getHardwareBuffer();
        if (buffer == null || buffer.isClosed()) {
            Log.w(TAG, "SplitDecor captureLayers failed invalid buffer=" + buffer);
            return;
        }
        Log.d(TAG, "SplitDecor captureLayers success size=" + buffer.getWidth()
                + "x" + buffer.getHeight() + " format=" + buffer.getFormat()
                + " colorSpace=" + screenshot.getColorSpace());
        showBuffer(buffer, screenshot.getColorSpace(), buffer.getWidth(), buffer.getHeight(),
                newBounds, t);
    }

    private boolean showProviderDrawable(@Nullable String packageName, @NonNull Rect newBounds,
            @NonNull SurfaceControl.Transaction t) {
        final SplitIconProvider provider = mSplitIconProvider;
        if (provider == null) {
            return false;
        }
        final Drawable drawable;
        try {
            drawable = provider.getIconDrawable(packageName);
        } catch (RuntimeException e) {
            Log.w(TAG, "SplitDecor icon provider failed packageName=" + packageName, e);
            return false;
        }
        if (drawable == null) {
            Log.d(TAG, "SplitDecor icon provider returned null packageName=" + packageName);
            return false;
        }

        final int defaultIconSize = Math.max(MIN_DRAWABLE_SIZE, Math.round(
                DEFAULT_ICON_SIZE_DP * Resources.getSystem().getDisplayMetrics().density));
        final int intrinsicWidth = drawable.getIntrinsicWidth() > 0
                ? drawable.getIntrinsicWidth() : defaultIconSize;
        final int intrinsicHeight = drawable.getIntrinsicHeight() > 0
                ? drawable.getIntrinsicHeight() : defaultIconSize;
        // Provider drawables can expose very different intrinsic sizes. Use one fixed buffer for
        // both stages and center the drawable without changing its aspect ratio.
        final int width = defaultIconSize;
        final int height = defaultIconSize;
        final float drawableScale = Math.min(
                width / (float) intrinsicWidth, height / (float) intrinsicHeight);
        final int drawableWidth = Math.max(MIN_DRAWABLE_SIZE,
                Math.round(intrinsicWidth * drawableScale));
        final int drawableHeight = Math.max(MIN_DRAWABLE_SIZE,
                Math.round(intrinsicHeight * drawableScale));
        final int drawableLeft = (width - drawableWidth) / 2;
        final int drawableTop = (height - drawableHeight) / 2;
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        final Rect originalBounds = drawable.copyBounds();
        drawable.setBounds(drawableLeft, drawableTop,
                drawableLeft + drawableWidth, drawableTop + drawableHeight);
        drawable.draw(canvas);
        drawable.setBounds(originalBounds);

        final Bitmap hardwareBitmap;
        try {
            hardwareBitmap = bitmap.copy(Bitmap.Config.HARDWARE, false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.w(TAG, "SplitDecor unable to convert provider drawable packageName="
                    + packageName, e);
            return false;
        }
        final HardwareBuffer buffer;
        try {
            buffer = hardwareBitmap.getHardwareBuffer();
        } catch (IllegalStateException e) {
            Log.w(TAG, "SplitDecor unable to get provider drawable buffer packageName="
                    + packageName, e);
            return false;
        }
        if (buffer == null || buffer.isClosed()) {
            Log.w(TAG, "SplitDecor provider drawable buffer invalid packageName=" + packageName);
            return false;
        }

        Log.d(TAG, "SplitDecor provider drawable success packageName=" + packageName
                + " intrinsicSize=" + intrinsicWidth + "x" + intrinsicHeight
                + " iconSize=" + width + "x" + height + " stageBounds=" + newBounds);
        return showIconCover(buffer, hardwareBitmap.getColorSpace(), width, height, newBounds, t);
    }

    private boolean showIconCover(HardwareBuffer buffer, @Nullable ColorSpace colorSpace,
            int iconWidth, int iconHeight, Rect newBounds, SurfaceControl.Transaction t) {
        if (iconWidth <= 0 || iconHeight <= 0) {
            Log.w(TAG, "SplitDecor showIconCover skipped invalid icon size=" + iconWidth
                    + "x" + iconHeight + " bounds=" + newBounds);
            return false;
        }
        cancelFadeAnimation();
        removeScreenshot(t);
        mIconBackgroundLeash = new SurfaceControl.Builder()
                .setName(ICON_BACKGROUND_LAYER_NAME)
                .setColorLayer()
                .setHidden(false)
                .setParent(mHostLeash)
                .setCallsite("SplitDecorManager#showIconCoverBackground")
                .build();
        mScreenshotLeash = new SurfaceControl.Builder()
                .setName(SCREENSHOT_LAYER_NAME)
                .setBLASTLayer()
                .setFormat(buffer.getFormat())
                .setHidden(false)
                .setParent(mHostLeash)
                .setCallsite("SplitDecorManager#showIconCover")
                .build();
        mScreenshotBufferWidth = iconWidth;
        mScreenshotBufferHeight = iconHeight;
        mIsIconCover = true;
        t.setColor(mIconBackgroundLeash, new float[]{1f, 1f, 1f})
                .setLayer(mIconBackgroundLeash, ICON_BACKGROUND_LAYER)
                .setAlpha(mIconBackgroundLeash, 0f)
                .show(mIconBackgroundLeash)
                .setBuffer(mScreenshotLeash, buffer)
                .setLayer(mScreenshotLeash, SCREENSHOT_LAYER)
                .setAlpha(mScreenshotLeash, 0f)
                .show(mScreenshotLeash);
        if (colorSpace != null) {
            t.setColorSpace(mScreenshotLeash, colorSpace);
        }
        layoutScreenshot(newBounds, iconWidth, iconHeight, t);
        fadeInScreenshot();
        Log.d(TAG, "SplitDecor showIconCover created background=" + mIconBackgroundLeash
                + " icon=" + mScreenshotLeash + " iconSize=" + iconWidth + "x" + iconHeight
                + " bounds=" + newBounds + " host=" + mHostLeash);
        return true;
    }

    private boolean showBuffer(HardwareBuffer buffer, @Nullable ColorSpace colorSpace,
            int bufferWidth, int bufferHeight, Rect newBounds, SurfaceControl.Transaction t) {
        if (bufferWidth <= 0 || bufferHeight <= 0) {
            Log.w(TAG, "SplitDecor showBuffer skipped invalid size=" + bufferWidth
                    + "x" + bufferHeight + " bounds=" + newBounds);
            return false;
        }
        cancelFadeAnimation();
        if (mScreenshotLeash != null || mIconBackgroundLeash != null) {
            Log.d(TAG, "SplitDecor showBuffer remove old screenshot leash="
                    + mScreenshotLeash);
            removeScreenshot(t);
        }
        mScreenshotLeash = new SurfaceControl.Builder()
                .setName(SCREENSHOT_LAYER_NAME)
                .setBLASTLayer()
                .setFormat(buffer.getFormat())
                .setHidden(false)
                .setParent(mHostLeash)
                .setCallsite("SplitDecorManager#showBuffer")
                .build();
        mScreenshotBufferWidth = bufferWidth;
        mScreenshotBufferHeight = bufferHeight;
        mIsIconCover = false;
        t.setBuffer(mScreenshotLeash, buffer)
                .setLayer(mScreenshotLeash, SCREENSHOT_LAYER)
                .setAlpha(mScreenshotLeash, 0f)
                .show(mScreenshotLeash);
        if (colorSpace != null) {
            t.setColorSpace(mScreenshotLeash, colorSpace);
        }
        layoutScreenshot(newBounds, bufferWidth, bufferHeight, t);
        fadeInScreenshot();
        Log.d(TAG, "SplitDecor showBuffer created leash=" + mScreenshotLeash
                + " valid=" + mScreenshotLeash.isValid() + " bufferSize=" + bufferWidth
                + "x" + bufferHeight + " bufferFormat=" + buffer.getFormat()
                + " bounds=" + newBounds + " host=" + mHostLeash
                + " layer=" + SCREENSHOT_LAYER);
        return true;
    }

    private void layoutScreenshot(Rect bounds, int bufferWidth, int bufferHeight,
            SurfaceControl.Transaction t) {
        if (mScreenshotLeash == null || bufferWidth <= 0 || bufferHeight <= 0) {
            Log.d(TAG, "SplitDecor layoutScreenshot skipped leash=" + mScreenshotLeash
                    + " bufferSize=" + bufferWidth + "x" + bufferHeight
                    + " bounds=" + bounds);
            return;
        }
        final float scaleX;
        final float scaleY;
        final float contentX;
        final float contentY;
        if (mIsIconCover) {
            scaleX = 1f;
            scaleY = 1f;
            if (mIconBackgroundLeash != null) {
                t.setPosition(mIconBackgroundLeash, 0, 0)
                        .setWindowCrop(mIconBackgroundLeash, bounds.width(), bounds.height());
            }
            final float iconX = (bounds.width() - bufferWidth) / 2f;
            final float iconY = (bounds.height() - bufferHeight) / 2f;
            contentX = iconX;
            contentY = iconY;
            t.setPosition(mScreenshotLeash, iconX, iconY)
                    .setScale(mScreenshotLeash, 1f, 1f)
                    .setWindowCrop(mScreenshotLeash, bufferWidth, bufferHeight);
        } else {
            scaleX = bounds.width() / (float) bufferWidth;
            scaleY = bounds.height() / (float) bufferHeight;
            contentX = 0f;
            contentY = 0f;
            t.setPosition(mScreenshotLeash, 0, 0)
                    .setScale(mScreenshotLeash, scaleX, scaleY)
                    .setWindowCrop(mScreenshotLeash, bounds.width(), bounds.height());
        }
        if (!mLastLoggedLayoutBounds.equals(bounds)) {
            Log.d(TAG, "SplitDecor layoutScreenshot bounds=" + bounds
                    + " crop=" + bounds.width() + "x" + bounds.height()
                    + " bufferSize=" + bufferWidth + "x" + bufferHeight
                    + " localPosition=" + contentX + "," + contentY
                    + " scale=" + scaleX + "x" + scaleY
                    + " leashValid=" + mScreenshotLeash.isValid());
            mLastLoggedLayoutBounds.set(bounds);
        }
    }

    private int getScreenshotWidth() {
        if (mScreenshotBufferWidth > 0) {
            return mScreenshotBufferWidth;
        }
        if (mCustomBuffer != null) {
            return mCustomBufferWidth;
        }
        return mLastStableBounds.width();
    }

    private int getScreenshotHeight() {
        if (mScreenshotBufferHeight > 0) {
            return mScreenshotBufferHeight;
        }
        if (mCustomBuffer != null) {
            return mCustomBufferHeight;
        }
        return mLastStableBounds.height();
    }

    private void fadeInScreenshot() {
        final SurfaceControl leash = mScreenshotLeash;
        final SurfaceControl backgroundLeash = mIconBackgroundLeash;
        if (leash == null || !leash.isValid()) {
            return;
        }
        final SurfaceControl.Transaction animT = new SurfaceControl.Transaction();
        mFadeInAnimator = ValueAnimator.ofFloat(0f, 1f);
        mFadeInAnimator.setDuration(FADE_DURATION);
        mFadeInAnimator.addUpdateListener(animator -> {
            final float alpha = (float) animator.getAnimatedValue();
            if (leash.isValid()) {
                animT.setAlpha(leash, alpha);
            }
            if (backgroundLeash != null && backgroundLeash.isValid()) {
                animT.setAlpha(backgroundLeash, alpha);
            }
            animT.apply();
        });
        mFadeInAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                animT.close();
                if (mFadeInAnimator == animation) {
                    mFadeInAnimator = null;
                }
            }
        });
        mFadeInAnimator.start();
    }

    private void fadeOutScreenshot() {
        cancelFadeInAnimation();
        final SurfaceControl leash = mScreenshotLeash;
        final SurfaceControl backgroundLeash = mIconBackgroundLeash;
        if (leash == null || !leash.isValid()) {
            Log.d(TAG, "SplitDecor fadeOut skipped leash=" + leash);
            return;
        }
        Log.d(TAG, "SplitDecor fadeOut start leash=" + leash);
        final SurfaceControl.Transaction animT = new SurfaceControl.Transaction();
        mFadeAnimator = ValueAnimator.ofFloat(1f, 0f);
        mFadeAnimator.setDuration(FADE_DURATION);
        mFadeAnimator.addUpdateListener(animator -> {
            final float alpha = (float) animator.getAnimatedValue();
            if (leash.isValid()) {
                animT.setAlpha(leash, alpha);
            }
            if (backgroundLeash != null && backgroundLeash.isValid()) {
                animT.setAlpha(backgroundLeash, alpha);
            }
            animT.apply();
        });
        mFadeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                if (leash.isValid()) {
                    animT.remove(leash);
                }
                if (backgroundLeash != null && backgroundLeash.isValid()) {
                    animT.remove(backgroundLeash);
                }
                animT.apply();
                animT.close();
                if (mScreenshotLeash == leash) {
                    mScreenshotLeash = null;
                    mScreenshotBufferWidth = 0;
                    mScreenshotBufferHeight = 0;
                    mIsIconCover = false;
                }
                if (mIconBackgroundLeash == backgroundLeash) {
                    mIconBackgroundLeash = null;
                }
                mFadeAnimator = null;
                Log.d(TAG, "SplitDecor fadeOut end removed leash=" + leash);
            }
        });
        mFadeAnimator.start();
    }

    private void removeScreenshot(SurfaceControl.Transaction t) {
        if (mScreenshotLeash != null) {
            Log.d(TAG, "SplitDecor removeScreenshot leash=" + mScreenshotLeash);
            t.remove(mScreenshotLeash);
            mScreenshotLeash = null;
        }
        if (mIconBackgroundLeash != null) {
            Log.d(TAG, "SplitDecor removeIconBackground leash=" + mIconBackgroundLeash);
            t.remove(mIconBackgroundLeash);
            mIconBackgroundLeash = null;
        }
        mScreenshotBufferWidth = 0;
        mScreenshotBufferHeight = 0;
        mIsIconCover = false;
        mLastLoggedLayoutBounds.setEmpty();
    }

    private void cancelFadeAnimation() {
        cancelFadeInAnimation();
        if (mFadeAnimator != null) {
            Log.d(TAG, "SplitDecor cancelFadeAnimation");
            mFadeAnimator.cancel();
            mFadeAnimator = null;
        }
    }

    private void cancelFadeInAnimation() {
        if (mFadeInAnimator != null) {
            Log.d(TAG, "SplitDecor cancelFadeInAnimation");
            mFadeInAnimator.cancel();
            mFadeInAnimator = null;
        }
    }

    private void clearCustomBuffer() {
        mCustomBuffer = null;
        mCustomColorSpace = null;
        mCustomBufferWidth = 0;
        mCustomBufferHeight = 0;
    }
}
