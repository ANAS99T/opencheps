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
    private MediaProjectionManager mpm;

    private final ActivityResultLauncher<Intent> projectionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == Activity.RESULT_OK && r.getData() != null) {
                    Intent svc = new Intent(this, ScreenCaptureService.class);
                    svc.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, r.getResultCode());
                    svc.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, r.getData());
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
                    else startService(svc);
                    statusTv.setText(R.string.status_running);
                    btnStop.setEnabled(true);
                    btnStart.setEnabled(false);
                    moveAppToBackground();
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

        SharedPreferences sp = Prefs.get(this);
        int think = sp.getInt(Prefs.KEY_THINK_MS, 700);
        int pv    = sp.getInt(Prefs.KEY_MULTIPV, 3);
        thinkBar.setMax(2400);
        thinkBar.setProgress(think);
        multiPvBar.setMax(2);
        multiPvBar.setProgress(pv - 1);
        thinkTv.setText("Think time: " + think + " ms");
        multiPvTv.setText("Candidate moves: " + pv);

        thinkBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int v, boolean u) {
                int t = Math.max(200, v);
                thinkTv.setText("Think time: " + t + " ms");
                sp.edit().putInt(Prefs.KEY_THINK_MS, t).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        multiPvBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int v, boolean u) {
                int n = v + 1;
                multiPvTv.setText("Candidate moves: " + n);
                sp.edit().putInt(Prefs.KEY_MULTIPV, n).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
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

    @Override protected void onResume() {
        super.onResume();
        boolean overlay = Settings.canDrawOverlays(this);
        btnOverlay.setEnabled(!overlay);
        btnOverlay.setText(overlay
                ? getString(R.string.overlay_granted)
                : getString(R.string.grant_overlay));
        btnStart.setEnabled(overlay);
    }

    private void moveAppToBackground() {
        moveTaskToBack(true);
    }

    private void requestOverlay() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
