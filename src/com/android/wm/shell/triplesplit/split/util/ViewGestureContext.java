package com.android.wm.shell.triplesplit.split.util;

import android.content.Context;
import android.renderscript.ScriptGroup;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.List;

public interface ViewGestureContext {

    public interface GestureContextUpdateListener {
        void onGestureContextUpdated();
    }

    InputDirection getDirection();
    float getDragOffset();
    void addUpdateCallback(GestureContextUpdateListener listener);
    void removeUpdateCallback(GestureContextUpdateListener listener);
    class DistanceGestureContext implements ViewGestureContext {

        private final float directionChangeSlop;
        private final List<GestureContextUpdateListener> callbacks = new ArrayList<>();

        private float dragOffset;
        private float furthestDragOffset;
        private InputDirection direction;

        public DistanceGestureContext(float initialDragOffset, InputDirection initialDirection,
                                      float directionChangeSlop) {
            if (directionChangeSlop <= 0) {
                throw new IllegalArgumentException("directionChangeSlop must be greater than 0");
            }
            this.dragOffset = initialDragOffset;
            this.furthestDragOffset = initialDragOffset;
            this.direction = initialDirection;
            this.directionChangeSlop = directionChangeSlop;
        }

        public static DistanceGestureContext create(Context context) {
            return create(context, 0f, InputDirection.Max);
        }

        public static DistanceGestureContext create(Context context,
                                                    float initialDragOffset) {
            return create(context, initialDragOffset, InputDirection.Max);
        }

        public static DistanceGestureContext create(Context context,
                                                    float initialDragOffset,
                                                    InputDirection direction) {
            float slop = ViewConfiguration.get(context).getScaledTouchSlop();
            return new DistanceGestureContext(initialDragOffset,
                    direction,
                    slop);
        }
        @Override
        public InputDirection getDirection() {
            return direction;
        }

        public void reset(float dragOffset, InputDirection direction) {
            this.dragOffset = dragOffset;
            this.direction = direction;
            this.furthestDragOffset = dragOffset;

            invokeCallbacks();
        }

        @Override
        public float getDragOffset() {
            return dragOffset;
        }

        public void setDragOffset(float value) {
            if (this.dragOffset == value) {
                return;
            }

            this.dragOffset = value;

            switch (direction) {
                case Max:
                    if (furthestDragOffset - value > directionChangeSlop) {
                        furthestDragOffset = value;
                        direction = InputDirection.Min;
                    } else {
                        furthestDragOffset = Math.max(value, furthestDragOffset);
                        direction = InputDirection.Max;
                    }
                    break;
                case Min:
                    if (furthestDragOffset - value > directionChangeSlop) {
                        furthestDragOffset = value;
                        direction = InputDirection.Max;
                    } else {
                        furthestDragOffset = Math.min(value, furthestDragOffset);
                        direction = InputDirection.Min;
                    }
                    break;
            }
            invokeCallbacks();
        }

        @Override
        public void addUpdateCallback(GestureContextUpdateListener listener) {
            callbacks.add(listener);
        }

        @Override
        public void removeUpdateCallback(GestureContextUpdateListener listener) {
            callbacks.remove(listener);
        }

        private void invokeCallbacks() {
            for (GestureContextUpdateListener listener: callbacks) {
                listener.onGestureContextUpdated();
            }
        }
    }
}
