package com.android.wm.shell.triplesplit.split.view;

import android.app.TaskInfo;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.DragEvent;
import android.view.IWindow;
import android.view.IWindowSession;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import com.android.wm.shell.triplesplit.split.SplitLayout;

public class TouchInterceptLayer {
    private static final String TAG = TouchInterceptLayer.class.getSimpleName();

    private final Rect mTouchBounds = new Rect();
    private final String mName;

    private IBinder mClientToken;
    private IWindow mWindowToken;
    private InputChannel mInputChannel;
    private InputEventReceiver mInputEventReceiver;
    private SurfaceControl mLayerLeash;

    View.OnTouchListener touchListener;
    View.OnDragListener onDragListener;

    public TouchInterceptLayer(String name) {
        mName = name;
    }

    public View.OnTouchListener getTouchListener() {
        return touchListener;
    }

    public void setTouchListener(View.OnTouchListener touchListener) {
        this.touchListener = touchListener;
    }

    public View.OnDragListener getOnDragListener() {
        return onDragListener;
    }

    public void setDragListener(View.OnDragListener onDragListener) {
        this.onDragListener = onDragListener;
    }

    public void inflate(android.content.Context context, SurfaceControl rootLeash,
                        TaskInfo rootTaskInfo, Rect touchBounds) {
        if (rootLeash == null || rootTaskInfo == null || touchBounds == null) {
            Log.w(TAG, "Skip inflate because a required argument is null. name=" + mName);
            return;
        }
        if (mLayerLeash != null || mInputChannel != null) {
            Log.w(TAG, "inflate called before release, replacing existing layer name=" + mName);
            release();
        }

        mTouchBounds.set(touchBounds);
        mClientToken = new Binder();
        mWindowToken = IWindow.Stub.asInterface(mClientToken);
        mInputChannel = new InputChannel();

        final SurfaceControl layer = new SurfaceControl.Builder()
                .setContainerLayer()
                .setName(mName)
                .setCallsite("TouchInterceptLayer inflate")
                .setParent(rootLeash)
                .build();
        mLayerLeash = layer;

        Log.i(TAG, "inflate name=" + mName + " bounds=" + mTouchBounds
                + " parentLeash=" + rootLeash + " task=" + rootTaskInfo.taskId);

        final IWindowSession wm = WindowManagerGlobal.getWindowSession();
        try {
            wm.grantInputChannel(rootTaskInfo.displayId,
                    layer,
                    mWindowToken,
                    null,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY,
                    0,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    null,
                    null,
                    mName,
                    mInputChannel
            );
        } catch (RemoteException e) {
            Log.e(TAG, "grantInputChannel failed name=" + mName, e);
        }

        final Looper looper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();
        mInputEventReceiver = new InputEventReceiver(mInputChannel, looper) {
            @Override
            public void onInputEvent(InputEvent event) {
                boolean handled = false;
                if (event instanceof MotionEvent && touchListener != null) {
                    handled = touchListener.onTouch(null, (MotionEvent) event);
                }
                finishInputEvent(event, handled);
            }

            @Override
            public void onDragEvent(boolean isExiting, float x, float y) {
                if (onDragListener == null) {
                    return;
                }
                final DragEvent dragEvent = DragEvent.obtain(
                        isExiting ? DragEvent.ACTION_DRAG_EXITED : DragEvent.ACTION_DRAG_ENTERED,
                        x, y, 0f, 0f, null, null, null, null, null, false
                );
                onDragListener.onDrag(null, dragEvent);
            }
        };

        new SurfaceControl.Transaction()
                .setLayer(layer, SplitLayout.RESTING_TOUCHING_LAYER)
                .setPosition(layer, mTouchBounds.left, mTouchBounds.top)
                .setWindowCrop(layer, Math.max(1, mTouchBounds.width()),
                        Math.max(1, mTouchBounds.height()))
                .show(layer)
                .apply();
        Log.i(TAG, "inflate complete name=" + mName + " leash=" + mLayerLeash);
    }

    public void release() {
        Log.i(TAG, "release name=" + mName + " bounds=" + mTouchBounds
                + " leash=" + mLayerLeash + " channel=" + mInputChannel);
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
            mInputEventReceiver = null;
        }
        if (mInputChannel != null) {
            mInputChannel.dispose();
            mInputChannel = null;
        }
        if (mLayerLeash != null) {
            new SurfaceControl.Transaction().remove(mLayerLeash).apply();
            mLayerLeash = null;
        }
        if (mWindowToken != null) {
            try {
                WindowManagerGlobal.getWindowSession().remove(mWindowToken);
            } catch (RemoteException e) {
                Log.w(TAG, "remove window token failed name=" + mName, e);
            }
            mWindowToken = null;
        }
        mClientToken = null;
        touchListener = null;
        onDragListener = null;
    }
}
