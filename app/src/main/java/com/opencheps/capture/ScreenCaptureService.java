package com.opencheps.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.opencheps.R;
import com.opencheps.engine.StockfishEngine;
import com.opencheps.overlay.ArrowOverlayView;
import com.opencheps.overlay.OverlayService;
import com.opencheps.util.Side;
import com.opencheps.vision.BoardDetector;
import com.opencheps.vision.BoardDetector.Detected;
import com.opencheps.vision.PieceClassifier;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ScreenCaptureService extends Service {

    private static final String TAG = "OC/Capture";
    private static final String CHANNEL_ID = "opencheps_capture";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_SIDE        = "side";
    public static final String ACTION_STOP       = "com.opencheps.STOP";

    private static final int[] ARROW_COLORS = { 0xFF00C853, 0xFFFFA000, 0xFF2196F3 };
    private static final float[] ARROW_WIDTHS = { 16f, 12f, 9f };

    private MediaProjection projection;
    private VirtualDisplay   virtualDisplay;
    private ImageReader      reader;
    private HandlerThread    workerThread;
    private Handler          worker;
    private StockfishEngine  engine;
    private BoardDetector    detector;
    private PieceClassifier  classifier;
    private Side             userSide = Side.WHITE;

    private int width, height, density;
    private volatile boolean busy = false;
    private String lastFen = "";

    @Nullable @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startInForeground();

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        userSide = "b".equals(intent.getStringExtra(EXTRA_SIDE)) ? Side.BLACK : Side.WHITE;

        MediaProjectionManager mpm =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = mpm.getMediaProjection(resultCode, data);
        if (projection == null) {
            Log.e(TAG, "Could not obtain MediaProjection");
            stopSelf();
            return START_NOT_STICKY;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopSelf(); }
        }, null);

        workerThread = new HandlerThread("oc-worker");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());

        engine = new StockfishEngine();
        classifier = new PieceClassifier();
        detector = new BoardDetector();

        worker.post(() -> {
            classifier.tryLoad(getApplicationContext());
            detector.setClassifier(classifier);
            try { engine.start(getApplicationContext()); }
            catch (Exception e) { Log.e(TAG, "engine start failed", e); }
        });

        setupVirtualDisplay();
        worker.postDelayed(sampleLoop, 1500);
        return START_STICKY;
    }

    private final Runnable sampleLoop = new Runnable() {
        @Override public void run() {
            try { sampleOnce(); }
            catch (Throwable t) { Log.e(TAG, "sample error", t); }
            if (worker != null) worker.postDelayed(this, 900);
        }
    };

    private void sampleOnce() {
        if (busy || reader == null) return;
        Image img = reader.acquireLatestImage();
        if (img == null) return;
        busy = true;
        try {
            Bitmap bmp = imageToBitmap(img);
            img.close();
            Detected det = detector.detect(bmp, userSide);
            if (det == null) return;
            if (det.fen.equals(lastFen)) return;
            lastFen = det.fen;

            List<StockfishEngine.Pv> pvs = engine.topMoves(det.fen, 600, 3);
            if (pvs.isEmpty()) return;

            List<ArrowOverlayView.Arrow> arrows = new ArrayList<>();
            for (int i = 0; i < pvs.size(); i++) {
                String uci = pvs.get(i).uci;
                if (uci == null || uci.length() < 4) continue;
                int fx = uci.charAt(0) - 'a', fy = 8 - (uci.charAt(1) - '0');
                int tx = uci.charAt(2) - 'a', ty = 8 - (uci.charAt(3) - '0');
                if (det.detectedSide == Side.BLACK) {
                    fx = 7 - fx; fy = 7 - fy; tx = 7 - tx; ty = 7 - ty;
                }
                float cell = det.squarePx;
                float ox = det.boardLeft, oy = det.boardTop;
                float sx = ox + (fx + 0.5f) * cell, sy = oy + (fy + 0.5f) * cell;
                float ex = ox + (tx + 0.5f) * cell, ey = oy + (ty + 0.5f) * cell;
                arrows.add(new ArrowOverlayView.Arrow(
                        sx, sy, ex, ey,
                        ARROW_COLORS[i % ARROW_COLORS.length],
                        ARROW_WIDTHS[i % ARROW_WIDTHS.length]));
            }
            OverlayService.pushArrows(this, arrows);
        } catch (Exception e) {
            Log.w(TAG, "loop iter failed: " + e.getMessage());
        } finally {
            busy = false;
        }
    }

    private void setupVirtualDisplay() {
        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE))
                .getDefaultDisplay().getRealMetrics(dm);
        float scale = Math.min(1f, 720f / Math.max(dm.widthPixels, dm.heightPixels));
        width   = Math.round(dm.widthPixels  * scale);
        height  = Math.round(dm.heightPixels * scale);
        density = dm.densityDpi;

        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = projection.createVirtualDisplay(
                "OpenChepsVD", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, worker);
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buf = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int rowPad = rowStride - pixelStride * image.getWidth();
        Bitmap bmp = Bitmap.createBitmap(
                image.getWidth() + rowPad / pixelStride,
                image.getHeight(), Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buf);
        if (rowPad != 0) {
            Bitmap cropped = Bitmap.createBitmap(bmp, 0, 0,
                    image.getWidth(), image.getHeight());
            bmp.recycle();
            return cropped;
        }
        return bmp;
    }

    private void startInForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Intent stopIntent = new Intent(this, ScreenCaptureService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .addAction(0, "Stop", stopPi)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(7, n);
        }
        startService(new Intent(this, OverlayService.class));
    }

    @Override public void onDestroy() {
        if (worker != null) worker.removeCallbacksAndMessages(null);
        try { if (engine != null) engine.stop(); } catch (Exception ignored) {}
        try { if (classifier != null) classifier.close(); } catch (Exception ignored) {}
        if (virtualDisplay != null) virtualDisplay.release();
        if (reader != null) reader.close();
        if (projection != null) projection.stop();
        if (workerThread != null) workerThread.quitSafely();
        stopService(new Intent(this, OverlayService.class));
        super.onDestroy();
    }
}
