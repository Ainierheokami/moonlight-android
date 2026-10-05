package com.limelight.utils;

import android.util.SparseBooleanArray;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a finger held on a floating panel (e.g. a key of the floating keyboard) from stealing the
 * other fingers. Once a gesture started on a panel and a further finger lands outside every panel,
 * the Activity-level event stream is split by owner: panel fingers keep going through the normal
 * view dispatch as their own gesture, the other fingers are delivered to the stream as a separate
 * gesture that starts with a plain ACTION_DOWN. This does not depend on how the view hierarchy
 * assigns extra pointers to touch targets.
 */
public final class FloatingPanelTouchRouter {
    public interface Hit {
        boolean isPanelAt(float x, float y);
    }

    public interface Sink {
        void toPanel(MotionEvent event);
        void toStream(MotionEvent event);
    }

    // pointer id -> true when owned by the panel, false when owned by the stream
    private final SparseBooleanArray owners = new SparseBooleanArray();
    private boolean gestureOnPanel;
    private boolean routing;
    private long streamDownTime;

    public boolean isRouting() {
        return routing;
    }

    /** Returns true when the event was fully handled here and must not be dispatched again. */
    public boolean route(MotionEvent ev, Hit hit, Sink sink) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            reset();
            gestureOnPanel = hit.isPanelAt(ev.getX(), ev.getY());
            if (gestureOnPanel) {
                owners.put(ev.getPointerId(0), true);
            }
            return false;
        }
        if (!gestureOnPanel) {
            return false;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean wasRouting = routing;
            if (!wasRouting) {
                reset();
                return false;
            }
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = ev.getActionIndex();
            boolean onPanel = hit.isPanelAt(ev.getX(index), ev.getY(index));
            owners.put(ev.getPointerId(index), onPanel);
            if (!onPanel && !routing) {
                routing = true;
                streamDownTime = ev.getEventTime();
            }
            if (!routing) {
                return false;
            }
        }
        else if (!routing) {
            return false;
        }

        dispatch(ev, action, sink);

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            reset();
        }
        else if (action == MotionEvent.ACTION_POINTER_UP) {
            owners.delete(ev.getPointerId(ev.getActionIndex()));
        }
        return true;
    }

    private void reset() {
        owners.clear();
        gestureOnPanel = false;
        routing = false;
    }

    private void dispatch(MotionEvent ev, int action, Sink sink) {
        for (int pass = 0; pass < 2; pass++) {
            boolean panel = pass == 0;
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < ev.getPointerCount(); i++) {
                if (owners.get(ev.getPointerId(i), true) == panel) {
                    indices.add(i);
                }
            }
            if (indices.isEmpty()) {
                continue;
            }

            int outAction;
            switch (action) {
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_CANCEL:
                    outAction = action;
                    break;
                case MotionEvent.ACTION_UP:
                    outAction = MotionEvent.ACTION_UP;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_POINTER_UP: {
                    int position = indices.indexOf(ev.getActionIndex());
                    if (position < 0) {
                        continue;
                    }
                    boolean down = action == MotionEvent.ACTION_POINTER_DOWN;
                    if (indices.size() == 1) {
                        outAction = down ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP;
                    }
                    else {
                        outAction = action | (position << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
                    }
                    break;
                }
                default:
                    continue;
            }

            long downTime = panel ? ev.getDownTime() : streamDownTime;
            MotionEvent out = build(ev, indices, outAction, downTime, action == MotionEvent.ACTION_MOVE);
            try {
                if (panel) {
                    sink.toPanel(out);
                }
                else {
                    sink.toStream(out);
                }
            }
            finally {
                out.recycle();
            }
        }
    }

    private static MotionEvent build(MotionEvent ev, List<Integer> indices, int action,
                                     long downTime, boolean withHistory) {
        int count = indices.size();
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int n = 0; n < count; n++) {
            props[n] = new MotionEvent.PointerProperties();
            coords[n] = new MotionEvent.PointerCoords();
            ev.getPointerProperties(indices.get(n), props[n]);
        }

        int historySize = withHistory ? ev.getHistorySize() : 0;
        MotionEvent out = null;
        for (int h = 0; h <= historySize; h++) {
            boolean current = h == historySize;
            for (int n = 0; n < count; n++) {
                int i = indices.get(n);
                if (current) {
                    ev.getPointerCoords(i, coords[n]);
                }
                else {
                    ev.getHistoricalPointerCoords(i, h, coords[n]);
                }
            }
            long time = current ? ev.getEventTime() : ev.getHistoricalEventTime(h);
            if (out == null) {
                out = MotionEvent.obtain(downTime, time, action, count, props, coords,
                        ev.getMetaState(), ev.getButtonState(), ev.getXPrecision(),
                        ev.getYPrecision(), ev.getDeviceId(), ev.getEdgeFlags(),
                        ev.getSource(), ev.getFlags());
            }
            else {
                out.addBatch(time, coords, ev.getMetaState());
            }
        }
        return out;
    }
}
