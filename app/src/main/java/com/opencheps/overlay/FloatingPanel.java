package com.opencheps.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
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
import com.opencheps.capture.ScreenCaptureService;

public class FloatingPanel {

    private final Context ctx;
    private final WindowManager wm;
    private View root;
    private WindowManager.LayoutParams lp;
    private TextView statusTv, evalTv;
    private ImageButton toggleBtn;
    private boolean paused = false;

    public interface Listener {
        void onPauseToggle(boolean paused);
        void onStop();
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
        ImageButton stopBtn = root.findViewById(R.id.panelStop);

        toggleBtn.setOnClickListener(v -> {
            paused = !paused;
            toggleBtn.setImageResource(paused
                    ? android.R.drawable.ic_media_play
                    : android.R.drawable.ic_media_pause);
            if (listener != null) listener.onPauseToggle(paused);
        });
        stopBtn.setOnClickListener(v -> {
            if (listener != null) listener.onStop();
            Intent stop = new Intent(ctx, ScreenCaptureService.class)
                    .setAction(ScreenCaptureService.ACTION_STOP);
            ctx.startService(stop);
        });

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 40;
        lp.y = 120;

        root.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int sx, sy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX(); dy = e.getRawY();
                        sx = lp.x; sy = lp.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = sx + (int) (e.getRawX() - dx);
                        lp.y = sy + (int) (e.getRawY() - dy);
                        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
                        return true;
                    default:
                        return false;
                }
            }
        });

        try { wm.addView(root, lp); } catch (Exception ignored) {}
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
