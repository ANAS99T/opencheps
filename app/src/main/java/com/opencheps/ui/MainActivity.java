package com.opencheps.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.opencheps.R;
import com.opencheps.capture.ScreenCaptureService;
import com.opencheps.util.Prefs;

public class MainActivity extends AppCompatActivity {

    private Button btnOverlay, btnStart, btnStop;
    private TextView statusTv, thinkTv, multiPvTv;
    private SeekBar thinkBar, multiPvBar;
    private RadioGroup sideGroup;
    private RadioButton sideWhite, sideBlack;
    private MediaProjectionManager mpm;

    private final ActivityResultLauncher<Intent> projectionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == Activity.RESULT_OK && r.getData() != null) {
                    String side = sideBlack.isChecked() ? "b" : "w";
                    Prefs.setUserSide(this, side);
                    Intent svc = new Intent(this, ScreenCaptureService.class);
                    svc.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, r.getResultCode());
                    svc.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, r.getData());
                    svc.putExtra(ScreenCaptureService.EXTRA_USER_SIDE, side);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
                    else startService(svc);
                    statusTv.setText(R.string.status_running);
                    btnStop.setEnabled(true);
                    btnStart.setEnabled(false);
                    moveTaskToBack(true);
                } else {
                    toast("Screen capture not granted");
                }
            });

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);
        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        btnOverlay = findViewById(R.id.btnOverlay);
        btnStart   = findViewById(R.id.btnStart);
        btnStop    = findViewById(R.id.btnStop);
        statusTv   = findViewById(R.id.status);
        thinkTv    = findViewById(R.id.thinkLabel);
        multiPvTv  = findViewById(R.id.multiPvLabel);
        thinkBar   = findViewById(R.id.thinkBar);
        multiPvBar = findViewById(R.id.multiPvBar);
        sideGroup  = findViewById(R.id.sideGroup);
        sideWhite  = findViewById(R.id.sideWhite);
        sideBlack  = findViewById(R.id.sideBlack);

        SharedPreferences sp = Prefs.get(this);
        int think = sp.getInt(Prefs.KEY_THINK_MS, 1500);
        int pv    = sp.getInt(Prefs.KEY_MULTIPV, 3);
        String side = sp.getString(Prefs.KEY_USER_SIDE, "w");

        thinkBar.setMax(3000);
        thinkBar.setProgress(think);
        multiPvBar.setMax(2);
        multiPvBar.setProgress(pv - 1);
        thinkTv.setText(thinkLabelText(think));
        multiPvTv.setText("Arrows to show: " + pv);
        if ("b".equals(side)) sideBlack.setChecked(true); else sideWhite.setChecked(true);

        thinkBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int v, boolean u) {
                int t = Math.max(300, v);
                thinkTv.setText(thinkLabelText(t));
                sp.edit().putInt(Prefs.KEY_THINK_MS, t).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        multiPvBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int v, boolean u) {
                int n = v + 1;
                multiPvTv.setText("Arrows to show: " + n);
                sp.edit().putInt(Prefs.KEY_MULTIPV, n).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        sideGroup.setOnCheckedChangeListener((g, id) -> {
            Prefs.setUserSide(this, id == R.id.sideBlack ? "b" : "w");
        });

        btnOverlay.setOnClickListener(v -> requestOverlay());
        btnStart.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) { toast("Grant overlay permission first"); return; }
            projectionLauncher.launch(mpm.createScreenCaptureIntent());
        });
        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, ScreenCaptureService.class));
            statusTv.setText(R.string.status_idle);
            btnStop.setEnabled(false);
            btnStart.setEnabled(true);
        });

        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this,
                    new String[]{ android.Manifest.permission.POST_NOTIFICATIONS }, 1);
        }
    }

    private String thinkLabelText(int ms) {
        String strength;
        if (ms < 500)       strength = "fast (≈2200 elo)";
        else if (ms < 1000) strength = "balanced (≈2700 elo)";
        else if (ms < 2000) strength = "strong (≈3000 elo)";
        else                strength = "max (≈3300+ elo)";
        return "Think time: " + ms + " ms — " + strength;
    }

    @Override protected void onResume() {
        super.onResume();
        boolean overlay = Settings.canDrawOverlays(this);
        btnOverlay.setEnabled(!overlay);
        btnOverlay.setText(overlay
                ? getString(R.string.overlay_granted)
                : getString(R.string.grant_overlay));
        btnStart.setEnabled(overlay);
    }

    private void requestOverlay() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
