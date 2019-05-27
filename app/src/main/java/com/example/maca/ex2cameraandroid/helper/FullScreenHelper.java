package com.example.maca.ex2cameraandroid.helper;

import android.app.Activity;
import android.view.View;
import android.view.WindowManager;

//Helper para full screen
public final class FullScreenHelper {

    public static void setFullScreenOnWindowFocusChanged(Activity activity, boolean hasFocus) {
        if (hasFocus) {
            // https://developer.android.com/training/system-ui/immersive.html#sticky
            activity
                    .getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

}
