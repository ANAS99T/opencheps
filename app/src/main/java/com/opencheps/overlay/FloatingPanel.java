package com.opencheps.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import com.opencheps.R;

public class FloatingPanel {

    private final Context ctx;
    private final WindowManager wm;
    private View root;
    private WindowManager.LayoutParams lp;
    private TextView statusTv, evalTv;
    private ImageButton toggleBtn;
    private boolean paused = false;
    private int screenW, screenH;

    public interface Listener {
        void onPauseToggle(boolean paused);
        void onStop();
        void onRecalibrate();
    }
    private Listener listener;

    public FloatingPanel(Context ctx) {
        this.ctx = ctx;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    public void setListener(Listener l) { this.listener = l; }

    @SuppressLint("ClickableViewAccessibility")
    public void attach() {
        if (root != null) return;
        root = LayoutInflater.from(ctx).inflate(R.layout.floating_panel, null);
        statusTv  = root.findViewById(R.id.panelStatus);
        evalTv    = root.findViewById(R.id.panelEval);
        toggleBtn = root.findViewById(R.id.panelToggle);
        ImageButton stopBtn  = root.findViewById(R.id.panelStop);
        ImageButton recalBtn = root.findViewById(R.id.panelRecal);

        toggleBtn.setOnClickListener(v -> {
            paused = !paused;
            toggleBtn.setImageResource(paused
                    ? android.R.drawable.ic_media_play
                    : android.R.drawable.ic_media_pause);
            if (listener != null) listener.onPauseToggle(paused);
        });
        stopBtn.setOnClickListener(v -> {
            if (listener != null) listener.onStop();
        });
        recalBtn.setOnClickListener(v -> {
            if (listener != null) listener.onRecalibrate();
        });

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 20;
        lp.y = 80;

        root.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int sx, sy; long downAt;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX(); dy = e.getRawY();
                        sx = lp.x; sy = lp.y;
                        downAt = System.currentTimeMillis();
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        int nx = sx + (int) (e.getRawX() - dx);
                        int ny = sy + (int) (e.getRawY() - dy);
                        if (Math.hypot(e.getRawX() - dx, e.getRawY() - dy) > 12) {
                            lp.x = nx; lp.y = ny;
                            try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
                            return true;
                        }
                        return false;
                    default:
                        return false;
                }
            }
        });

        try { wm.addView(root, lp); } catch (Exception ignored) {}
    }

    public void placeBottomLeft(int screenWidth, int screenHeight) {
        this.screenW = screenWidth;
        this.screenH = screenHeight;
        if (root == null || lp == null) return;
        root.post(() -> {
            int h = root.getHeight();
            if (h <= 0) h = 140;
            lp.x = 16;
            lp.y = Math.max(40, screenHeight - h - 160);
            try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
        });
    }

    public boolean isPaused() { return paused; }

    public void setStatus(String s) {
        if (statusTv != null) statusTv.post(() -> statusTv.setText(s));
    }

    public void setEval(String s) {
        if (evalTv != null) evalTv.post(() -> evalTv.setText(s == null ? "" : s));
    }

    public void detach() {
        if (root != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
            root = null;
        }
    }
}
