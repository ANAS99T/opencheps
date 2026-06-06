package com.opencheps.ui;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.opencheps.R;
import com.opencheps.capture.ScreenCaptureService;

public class MainActivity extends AppCompatActivity {

    private Button btnOverlay, btnCapture, btnStart, btnStop;
    private RadioGroup sideGroup;
    private TextView status, log;
    private MediaProjectionManager mpm;

    private final ActivityResultLauncher<Intent> projectionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == Activity.RESULT_OK && r.getData() != null) {
                    Intent svc = new Intent(this, ScreenCaptureService.class);
                    svc.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, r.getResultCode());
                    svc.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, r.getData());
                    svc.putExtra(ScreenCaptureService.EXTRA_SIDE,
                            sideGroup.getCheckedRadioButtonId() == R.id.sideBlack ? "b" : "w");
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
                    else startService(svc);
                    status.setText(R.string.status_running);
                    btnStop.setEnabled(true);
                    btnStart.setEnabled(false);
                } else {
                    toast("Screen capture not granted");
                }
            });

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);
        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        btnOverlay = findViewById(R.id.btnOverlay);
        btnCapture = findViewById(R.id.btnCapture);
        btnStart   = findViewById(R.id.btnStart);
        btnStop    = findViewById(R.id.btnStop);
        sideGroup  = findViewById(R.id.sideGroup);
        status     = findViewById(R.id.status);
        log        = findViewById(R.id.log);

        btnOverlay.setOnClickListener(v -> requestOverlay());
        btnCapture.setOnClickListener(v -> projectionLauncher.launch(mpm.createScreenCaptureIntent()));
        btnStart.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) { toast("Grant overlay first"); return; }
            projectionLauncher.launch(mpm.createScreenCaptureIntent());
        });
        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, ScreenCaptureService.class));
            status.setText(R.string.status_idle);
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
        btnOverlay.setText(overlay ? "Overlay granted" : getString(R.string.grant_overlay));
        log.setText("Drop stockfish binary at app/src/main/assets/stockfish (arm64-v8a, PIE, UCI). "
                + "Optional: drop a 32x32 RGB TFLite piece classifier at "
                + "app/src/main/assets/piece_classifier.tflite for true FENs.");
    }

    private void requestOverlay() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
