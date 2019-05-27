package com.example.maca.ex2cameraandroid.helper;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.Display;
import android.view.WindowManager;
import com.google.ar.core.Session;

public final class DisplayRotationHelper implements DisplayListener {
    private boolean viewportChanged;
    private int viewportWidth;
    private int viewportHeight;
    private final Context context;
    private final Display display;

    @RequiresApi(api = Build.VERSION_CODES.M)
    public DisplayRotationHelper(Context context){
        this.context = context;
        display = context.getSystemService(WindowManager.class).getDefaultDisplay();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void onResume(){
        context.getSystemService(DisplayManager.class).registerDisplayListener(this,null);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void onPause(){
        context.getSystemService(DisplayManager.class).unregisterDisplayListener(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void onSurfaceChanged(int width, int height){
        viewportWidth = width;
        viewportHeight = height;
        viewportChanged = true;
    }

    public void updateSessionIfNeeded(Session session) {
        if (viewportChanged) {
            int displayRotation = display.getRotation();
            session.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight);
            viewportChanged = false;
        }
    }

    public int getRotation() {
        return display.getRotation();
    }

    @Override
    public void onDisplayAdded(int displayId) {

    }

    @Override
    public void onDisplayRemoved(int displayId) {

    }

    @Override
    public void onDisplayChanged(int displayId) {
        viewportChanged = true;
    }
}
