/*package com.example.maca.ex2cameraandroid.helper;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class TapHelper implements View.OnTouchListener {
    private final GestureDetector gestureDetector;
    private final BlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(16);

    public TapHelper(Context context) {
        gestureDetector =
                new GestureDetector(
                        context,
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapUp(MotionEvent e) {
                                // Queue tap if there is space. Tap is lost if queue is full.
                                queuedSingleTaps.offer(e);
                                return true;
                            }

                            @Override
                            public boolean onDown(MotionEvent e) {
                                return true;
                            }
                        });
    }
    public MotionEvent poll() {
        return queuedSingleTaps.poll();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return gestureDetector.onTouchEvent(motionEvent);
    }
}
*/