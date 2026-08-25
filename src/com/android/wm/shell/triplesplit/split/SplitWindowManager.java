package com.android.wm.shell.triplesplit.split;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
import static android.view.WindowManager.LayoutParams.FLAG_SLIPPERY;
import static android.view.WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;
import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Binder;
import android.util.Log;
import android.view.IWindow;
import android.view.InsetsState;
import android.view.LayoutInflater;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.WindowManager;
import android.view.WindowlessWindowManager;

import com.android.wm.shell.triplesplit.R;
import com.android.wm.shell.triplesplit.split.view.DividerView;
import com.android.wm.shell.triplesplit.split.view.OffscreenTouchZone;


public class SplitWindowManager extends WindowlessWindowManager {
    private static final String TAG = SplitWindowManager.class.getSimpleName();

    private final String mWindowName;
    private final ParentContainerCallbacks mParentContainerCallbacks;
    private Context mContext;
    private SurfaceControlViewHost mViewHost;
    private SurfaceControl mLeash;
    private DividerView mDividerView;
    private WindowManager.LayoutParams mLayoutParams;
    @LayoutRes private int mDividerLayoutResId;
    @IdRes private int mDividerBarId;
    @IdRes private int mDividerHandleId;
    @IdRes private int mDividerCornerId;
    private final int mId;

    private SurfaceControl.Transaction mSyncTransaction = null;

    private boolean mLastDividerInteractive = true;
    private Boolean mLastDividerHidden;
    private final Rect mLastTouchRegion = new Rect();
    private boolean mHasLastTouchRegion;

    public interface ParentContainerCallbacks {
        void attachToParentSurface(SurfaceControl.Builder b);
        void onLeashReady(SurfaceControl leash);
        void inflateOnStageRoot(OffscreenTouchZone offscreenTouchZone);
        void onSplitLayoutAnimating(boolean animating);
    }

    public SplitWindowManager(String windowName, Context context, Configuration c,
                              ParentContainerCallbacks parentContainerCallbacks, int id) {
        super(c, null, null);
        mContext = context.createConfigurationContext(c);
        mParentContainerCallbacks = parentContainerCallbacks;
        mWindowName = windowName;
        mId = id;
    }

    public void setTouchRegion(@NonNull Rect region) {
        if (mViewHost == null) {
            return;
        }
        if (mHasLastTouchRegion && mLastTouchRegion.equals(region)) {
            return;
        }
        mLastTouchRegion.set(region);
        mHasLastTouchRegion = true;
        setTouchRegion(mViewHost.getWindowToken().asBinder(), new Region(region));
    }

    void setDividerLayout(@LayoutRes int layoutResId, @IdRes int dividerBarId,
            @IdRes int dividerHandleId, @IdRes int dividerCornerId) {
        mDividerLayoutResId = layoutResId;
        mDividerBarId = dividerBarId;
        mDividerHandleId = dividerHandleId;
        mDividerCornerId = dividerCornerId;
    }

    void applyDimens(SplitScreenDimenConfig dimenConfig) {
        if (mDividerView != null) {
            mDividerView.applyDimens(dimenConfig);
        }
    }

    void updateDividerBounds(@Nullable Rect dividerBounds) {
        if (mViewHost == null || mLayoutParams == null || dividerBounds == null
                || (mLayoutParams.width == dividerBounds.width()
                && mLayoutParams.height == dividerBounds.height())) {
            return;
        }
        mLayoutParams.width = dividerBounds.width();
        mLayoutParams.height = dividerBounds.height();
        mViewHost.relayout(mLayoutParams);
    }

    /** Refreshes the divider input region after its leash moves without recreating the view. */
    public void updateTouchableRegion() {
        if (mDividerView != null) {
            mDividerView.updateTouchableRegion();
        }
    }

    @Override
    public SurfaceControl getSurfaceControl(IWindow window) {
        return super.getSurfaceControl(window);
    }

    public void setConfiguration(Configuration c) {
        super.setConfiguration(c);
        mContext = mContext.createConfigurationContext(c);
    }

    @Override
    protected SurfaceControl getParentSurface(IWindow window, WindowManager.LayoutParams attrs) {
        final SurfaceControl.Builder builder = new SurfaceControl.Builder()
                .setContainerLayer()
                .setName(TAG)
                .setHidden(true)
                .setCallsite("SplitWindowManager#attachToParentSurface");
        mParentContainerCallbacks.attachToParentSurface(builder);
        mLeash = builder.build();
        mParentContainerCallbacks.onLeashReady(mLeash);
        return mLeash;
    }

    void init(SplitLayout splitLayout, InsetsState insetsState, boolean isRestoring,
            SplitScreenDimenConfig dimenConfig) {
        if (mDividerView != null || mViewHost != null) {
            throw new UnsupportedOperationException(
                    "Try to inflate divider view without release previous one"
            );
        }
        Log.i(TAG, "init splitWindowManager" + mId);
        mViewHost = new SurfaceControlViewHost(mContext, mContext.getDisplay(), this,
                "SplitWindowManager");
        mDividerView = inflateDividerView();
        mDividerView.setDividerViewIds(mDividerBarId, mDividerHandleId, mDividerCornerId);
        Rect dividerBounds;
        if (mId == 1) {
            dividerBounds = splitLayout.getDividerBounds(true);
        } else {
            dividerBounds = splitLayout.getDividerBounds(false);
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(dividerBounds.width(),
                dividerBounds.height(), TYPE_DOCK_DIVIDER,  FLAG_NOT_FOCUSABLE |
                FLAG_NOT_TOUCH_MODAL | FLAG_WATCH_OUTSIDE_TOUCH | FLAG_SPLIT_TOUCH |
                FLAG_SLIPPERY, PixelFormat.TRANSLUCENT);
        lp.token = new Binder();
        lp.setTitle(mWindowName);
        lp.privateFlags |= PRIVATE_FLAG_NO_MOVE_ANIMATION | PRIVATE_FLAG_TRUSTED_OVERLAY;
        lp.accessibilityTitle = mContext.getResources().getString(R.string.accessibility_divider);
        mLayoutParams = lp;
        mViewHost.setView(mDividerView, lp);
        mDividerView.setup(splitLayout, this, mViewHost, insetsState, mId, dimenConfig);
    }

    private DividerView inflateDividerView() {
        final int layoutResId = mDividerLayoutResId != 0
                ? mDividerLayoutResId : R.layout.split_divider;
        final Object view = LayoutInflater.from(mContext).inflate(layoutResId, null);
        if (view instanceof DividerView) {
            return (DividerView) view;
        }
        Log.w(TAG, "Custom divider layout must use DividerView as root; using default layout");
        return (DividerView) LayoutInflater.from(mContext).inflate(R.layout.split_divider, null);
    }

    void release(SurfaceControl.Transaction t) {
        if (mDividerView != null) {
            mLastDividerInteractive = mDividerView.isInteractive();
            mLastDividerHidden = mDividerView.isHandleHidden();
            mDividerView = null;
        }

        if (mViewHost != null) {
            mSyncTransaction = t;
            mViewHost.release();
            mSyncTransaction = null;
            mViewHost = null;
        }
        mLayoutParams = null;
        mHasLastTouchRegion = false;
        mLastTouchRegion.setEmpty();

        if (mLeash != null) {
            if (t == null) {
                new SurfaceControl.Transaction().remove(mLeash).apply();
            } else {
                t.remove(mLeash);
            }
            mLeash = null;
        }
    }

    @Override
    protected void removeSurface(SurfaceControl sc) {
        if (mSyncTransaction != null) {
            mSyncTransaction.remove(sc);
        } else {
            super.removeSurface(sc);
        }
    }

    void setInteractive(boolean interactive, boolean hideHandle, String from) {
        if (mDividerView == null) return;
        mDividerView.setInteractive(interactive, hideHandle, from);
    }

    DividerView getDividerView() {
        return mDividerView;
    }

    @Nullable
    SurfaceControl getSurfaceControl() {
        return mLeash;
    }

    void onInsetsChanged(InsetsState insetsState) {
        if (mDividerView != null) {
            mDividerView.onInsetsChanged(insetsState, true);
        }
    }
}
