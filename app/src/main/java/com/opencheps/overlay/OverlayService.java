package com.opencheps.overlay;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
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
    private ArrowOverlayView arrows;
    private FloatingPanel panel;

    @Nullable @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        arrows = new ArrowOverlayView(this);

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

        try { wm.addView(arrows, lp); }
        catch (Exception e) { Log.e(TAG, "addView arrows failed", e); }

        panel = new FloatingPanel(this);
        panel.attach();
    }

    @Override public void onDestroy() {
        try { if (arrows != null) wm.removeView(arrows); } catch (Exception ignored) {}
        if (panel != null) panel.detach();
        instance = null;
        super.onDestroy();
    }

    public static FloatingPanel panel() {
        return instance == null ? null : instance.panel;
    }

    public static void pushArrows(List<ArrowOverlayView.Arrow> a, Rect bounds) {
        if (instance == null) return;
        instance.arrows.setBoardBounds(bounds);
        instance.arrows.setArrows(a);
    }

    public static void clearOverlays() {
        if (instance != null) instance.arrows.clearAll();
    }
}
