package com.opencheps.overlay;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import java.util.List;

public class OverlayService extends Service {

    private static final String TAG = "OC/Overlay";
    private static OverlayService instance;

    private WindowManager wm;
    private ArrowOverlayView view;

    @Nullable @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        view = new ArrowOverlayView(this);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;

        try { wm.addView(view, lp); }
        catch (Exception e) { Log.e(TAG, "addView failed", e); }
    }

    @Override public void onDestroy() {
        try { if (view != null) wm.removeView(view); } catch (Exception ignored) {}
        instance = null;
        super.onDestroy();
    }

    public static void pushArrows(Context ctx, List<ArrowOverlayView.Arrow> arrows) {
        if (instance != null && instance.view != null) instance.view.setArrows(arrows);
    }
}
