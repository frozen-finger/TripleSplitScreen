package com.android.wm.shell.triplesplit.split.view;

import android.app.TaskInfo;
import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewConfiguration;

public class OffscreenTouchZone {
    private static final String TAG = OffscreenTouchZone.class.getSimpleName();

    private final Runnable mOnClickRunnable;
    private final Rect mBounds = new Rect();

    private TouchInterceptLayer mInterceptLayer;
    private final int mIndex;
    private int mTouchSlop;
    private float mDownX;
    private float mDownY;
    private boolean mTriggered;

    private final View.OnDragListener mDragListener = new View.OnDragListener() {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            if (event.getAction() == DragEvent.ACTION_DRAG_ENTERED) {
                trigger();
            }
            return true;
        }
    };

    private final View.OnTouchListener mTouchListener = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = event.getX();
                    mDownY = event.getY();
                    mTriggered = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!mTriggered && Math.abs(event.getX() - mDownX) > mTouchSlop) {
                        trigger();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!mTriggered
                            && Math.abs(event.getX() - mDownX) <= mTouchSlop
                            && Math.abs(event.getY() - mDownY) <= mTouchSlop) {
                        trigger();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    mTriggered = false;
                    return true;
                default:
                    return true;
            }
        }
    };

    public OffscreenTouchZone(int index, Rect bounds, Runnable runnable) {
        this.mIndex = index;
        this.mBounds.set(bounds);
        this.mOnClickRunnable = runnable;
    }

    public void inflate(Context context, SurfaceControl rootLeash, TaskInfo rootTaskInfo) {
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mInterceptLayer = new TouchInterceptLayer(TAG + mIndex);
        mInterceptLayer.setTouchListener(mTouchListener);
        mInterceptLayer.setDragListener(mDragListener);
        Log.i(TAG, "inflate index=" + mIndex + " bounds=" + mBounds
                + " parentLeash=" + rootLeash + " task=" + rootTaskInfo.taskId);
        mInterceptLayer.inflate(context, rootLeash, rootTaskInfo, mBounds);
    }

    public void release() {
        if (mInterceptLayer != null) {
            Log.i(TAG, "release index=" + mIndex + " bounds=" + mBounds);
            mInterceptLayer.release();
            mInterceptLayer = null;
        }
    }

    public int getIndex() {
        return mIndex;
    }

    public void getBounds(Rect outBounds) {
        outBounds.set(mBounds);
    }

    private void trigger() {
        if (mTriggered) {
            return;
        }
        mTriggered = true;
        Log.i(TAG, "trigger index=" + mIndex + " bounds=" + mBounds);
        mOnClickRunnable.run();
    }
}
