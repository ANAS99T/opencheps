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
import android.graphics.Rect;
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
import com.opencheps.overlay.FloatingPanel;
import com.opencheps.overlay.OverlayService;
import com.opencheps.util.Prefs;
import com.opencheps.util.Side;
import com.opencheps.vision.BoardDetector;
import com.opencheps.vision.FenBuilder;
import com.opencheps.vision.MoveAnalyzer;
import com.opencheps.vision.PieceClassifier;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScreenCaptureService extends Service {

    private static final String TAG = "OC/Capture";
    private static final String CHANNEL_ID = "opencheps_capture";

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_USER_SIDE   = "user_side";
    public static final String ACTION_STOP       = "com.opencheps.STOP";

    private static final int[]   ARROW_COLORS = { 0xFF00C853, 0xFFFFB300, 0xFF29B6F6 };
    private static final float[] ARROW_WIDTHS = { 18f, 13f, 10f };
    private static final long    SAMPLE_PERIOD_MS = 600;
    private static final int     STABLE_FRAMES_REQUIRED = 2;

    private MediaProjection projection;
    private VirtualDisplay   virtualDisplay;
    private ImageReader      reader;
    private HandlerThread    workerThread;
    private Handler          worker;

    private StockfishEngine  engine;
    private boolean engineReady;
    private PieceClassifier  classifier;

    private int captureW, captureH, density;
    private int screenW, screenH;
    private float capToScreenX = 1f, capToScreenY = 1f;
    private volatile boolean busy = false;
    private volatile boolean paused = false;

    private Side userSide = Side.WHITE;

    private char[][] confirmedGrid;
    private char[][] pendingGrid;
    private int pendingStableCount;
    private Side observedStm = Side.WHITE;
    private String lastEngineFen = "";
    private int noBoardCount = 0;
    private int unknownMoveCount = 0;

    @Nullable @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startInForeground();

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        String sideStr = intent.getStringExtra(EXTRA_USER_SIDE);
        if (sideStr == null) sideStr = Prefs.userSide(this);
        userSide = "b".equals(sideStr) ? Side.BLACK : Side.WHITE;

        MediaProjectionManager mpm =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = mpm.getMediaProjection(resultCode, data);
        if (projection == null) {
            Log.e(TAG, "MediaProjection acquisition failed");
            stopSelf();
            return START_NOT_STICKY;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopSelf(); }
        }, null);

        workerThread = new HandlerThread("oc-worker", Thread.NORM_PRIORITY);
        workerThread.start();
        worker = new Handler(workerThread.getLooper());

        engine = new StockfishEngine();
        classifier = new PieceClassifier();

        worker.post(() -> {
            try {
                engine.start(getApplicationContext());
                engineReady = true;
                panelStatus("Engine ready • playing as " + userSide);
            } catch (Exception e) {
                Log.e(TAG, "engine start failed", e);
                panelStatus("Engine error: " + e.getMessage());
            }
        });

        setupVirtualDisplay();
        wirePanel();
        worker.postDelayed(sampleLoop, 1200);
        panelStatus("Starting as " + userSide + "…");
        return START_STICKY;
    }

    private void wirePanel() {
        new Handler(getMainLooper()).postDelayed(() -> {
            FloatingPanel p = OverlayService.panel();
            if (p != null) {
                p.placeBottomLeft(screenW, screenH);
                p.setUserSide(userSide);
                p.setListener(new FloatingPanel.Listener() {
                    @Override public void onPauseToggle(boolean isPaused) {
                        paused = isPaused;
                        if (isPaused) {
                            OverlayService.clearOverlays();
                            panelStatus("Paused");
                        } else {
                            lastEngineFen = "";
                            pendingGrid = null;
                            pendingStableCount = 0;
                            if (worker != null) worker.post(() -> drainReader(true));
                            panelStatus("Resumed");
                        }
                    }
                    @Override public void onStop() { stopSelf(); }
                    @Override public void onRecalibrate() {
                        classifier.reset();
                        resetGameState();
                        panelStatus("Recalibrating — show starting position");
                    }
                    @Override public void onSideChange(Side newSide) {
                        userSide = newSide;
                        observedStm = newSide;
                        Prefs.setUserSide(getApplicationContext(),
                                newSide == Side.BLACK ? "b" : "w");
                        if (classifier.isTrained() && classifier.bootstrapSide() != newSide) {
                            classifier.reset();
                            resetGameState();
                            panelStatus("Switched to " + newSide + " — recalibrate");
                        } else {
                            lastEngineFen = "";
                            panelStatus("Now playing " + newSide);
                        }
                    }
                });
            }
        }, 400);
    }

    private void resetGameState() {
        confirmedGrid = null;
        pendingGrid = null;
        pendingStableCount = 0;
        lastEngineFen = "";
        observedStm = Side.WHITE;
        unknownMoveCount = 0;
    }

    private final Runnable sampleLoop = new Runnable() {
        @Override public void run() {
            try { if (!paused) sampleOnce(); else drainReader(false); }
            catch (Throwable t) { Log.e(TAG, "sample error", t); }
            if (worker != null) worker.postDelayed(this, SAMPLE_PERIOD_MS);
        }
    };

    private void drainReader(boolean keepLast) {
        if (reader == null) return;
        Image last = null;
        while (true) {
            Image im;
            try { im = reader.acquireNextImage(); }
            catch (Exception e) { break; }
            if (im == null) break;
            if (last != null) last.close();
            last = im;
        }
        if (last != null) {
            if (!keepLast) last.close();
            else {
                try { last.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void sampleOnce() {
        if (busy || reader == null) return;
        Image img;
        try { img = reader.acquireLatestImage(); }
        catch (Exception e) { return; }
        if (img == null) return;
        busy = true;
        Bitmap bmp = null;
        try {
            bmp = imageToBitmap(img);
            img.close();
            img = null;

            BoardDetector.Board board = BoardDetector.locate(bmp);
            if (board == null) {
                noBoardCount++;
                if (noBoardCount == 1 || noBoardCount % 5 == 0) {
                    panelStatus("Looking for board…");
                }
                OverlayService.clearOverlays();
                return;
            }
            noBoardCount = 0;
            Rect screenBounds = boardBoundsOnScreen(board);
            OverlayService.setBoardBounds(screenBounds);

            if (!engineReady) {
                panelStatus("Board found • waiting for engine");
                return;
            }

            if (!classifier.isTrained()) {
                boolean ok = classifier.tryBootstrap(bmp, board);
                if (!ok) {
                    panelStatus("Show STARTING position to calibrate");
                    return;
                }
                Side detected = classifier.bootstrapSide();
                if (detected != userSide) {
                    classifier.reset();
                    panelStatus("Board shows " + detected + " at bottom — switch side or flip");
                    return;
                }
                resetGameState();
                confirmedGrid = startingGridFor(userSide);
                observedStm = Side.WHITE;
                panelStatus("Calibrated • you play " + userSide);
                if (userSide == Side.WHITE) maybeQuery(board);
                return;
            }

            char[][] grid = classifier.classifyBoard(bmp, board);
            if (grid == null) return;

            grid = repairWithMoveConsistency(confirmedGrid, grid);

            if (!gridsEqual(grid, pendingGrid)) {
                pendingGrid = grid;
                pendingStableCount = 1;
                panelStatus("Stabilising…");
                return;
            }
            pendingStableCount++;
            if (pendingStableCount < STABLE_FRAMES_REQUIRED) {
                panelStatus("Stabilising… (" + pendingStableCount + "/" + STABLE_FRAMES_REQUIRED + ")");
                return;
            }

            if (gridsEqual(grid, confirmedGrid)) {
                classifier.commitPrevious(confirmedGrid, bmp, board);
                panelStatus(observedStm + "'s turn");
                if (observedStm == userSide && !lastEngineFen.isEmpty()) {
                    return;
                }
                if (observedStm == userSide) {
                    maybeQuery(board);
                } else {
                    OverlayService.clearOverlays();
                    OverlayService.setBoardBounds(screenBounds);
                }
                return;
            }

            MoveAnalyzer.Result m = MoveAnalyzer.analyze(confirmedGrid, grid);
            Log.i(TAG, "Position change: kind=" + m.kind + " mover=" + m.mover
                    + " changes=" + m.changeCount);

            if (m.kind == MoveAnalyzer.Kind.UNKNOWN || m.mover == null) {
                unknownMoveCount++;
                panelStatus("Unclear change (" + m.changeCount + " sq) — waiting");
                if (unknownMoveCount > 4) {
                    confirmedGrid = grid;
                    classifier.commitPrevious(confirmedGrid, bmp, board);
                    observedStm = userSide;
                    unknownMoveCount = 0;
                    lastEngineFen = "";
                    panelStatus("Forced re-sync");
                    if (observedStm == userSide) maybeQuery(board);
                }
                return;
            }
            unknownMoveCount = 0;

            confirmedGrid = grid;
            classifier.commitPrevious(confirmedGrid, bmp, board);
            observedStm = m.mover == Side.WHITE ? Side.BLACK : Side.WHITE;
            lastEngineFen = "";

            if (observedStm == userSide) {
                maybeQuery(board);
            } else {
                OverlayService.clearOverlays();
                OverlayService.setBoardBounds(screenBounds);
                panelStatus(observedStm + "'s turn — waiting");
                panelEval("");
            }
        } catch (Exception e) {
            Log.w(TAG, "loop iter failed: " + e.getMessage(), e);
        } finally {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
            if (img != null) try { img.close(); } catch (Exception ignored) {}
            busy = false;
        }
    }

    private void maybeQuery(BoardDetector.Board board) {
        if (confirmedGrid == null) return;
        FenBuilder.Validation v = FenBuilder.validate(
                classifier.bootstrapSide() == Side.BLACK
                        ? FenBuilder.flip(confirmedGrid) : confirmedGrid);
        if (!v.ok) {
            panelStatus("Invalid position: " + v.reason);
            OverlayService.clearOverlays();
            OverlayService.setBoardBounds(boardBoundsOnScreen(board));
            return;
        }
        String fen = FenBuilder.build(confirmedGrid, classifier.bootstrapSide(), userSide);
        if (fen == null) {
            panelStatus("FEN build failed");
            return;
        }
        if (fen.equals(lastEngineFen)) return;
        lastEngineFen = fen;

        int think   = Prefs.thinkMs(getApplicationContext());
        int multiPv = Math.max(1, Math.min(3, Prefs.multiPv(getApplicationContext())));
        panelStatus("Thinking " + think + " ms…");
        try {
            long t0 = System.currentTimeMillis();
            List<StockfishEngine.Pv> pvs = engine.topMoves(fen, think, multiPv);
            long dt = System.currentTimeMillis() - t0;
            if (pvs.isEmpty()) {
                panelStatus("Engine: no legal move");
                return;
            }
            renderArrows(pvs, board);
            StockfishEngine.Pv best = pvs.get(0);
            String evalStr = best.mate
                    ? "M" + Math.abs(plyToMate(best.cp))
                    : String.format("%+.2f", best.cp / 100.0);
            panelEval("YOU (" + userSide + ") " + formatUci(best.uci) + "  " + evalStr);
            panelStatus("Best move ready (" + dt + " ms)");
        } catch (Exception e) {
            Log.w(TAG, "engine query failed", e);
            panelStatus("Engine query failed");
        }
    }

    private boolean gridsEqual(char[][] a, char[][] b) {
        if (a == null || b == null) return false;
        for (int r = 0; r < 8; r++) {
            if (!Arrays.equals(a[r], b[r])) return false;
        }
        return true;
    }

    private char[][] repairWithMoveConsistency(char[][] prev, char[][] curr) {
        if (prev == null || curr == null) return curr;
        char emptiedPiece = 0;
        int emptiedCount = 0, filledCount = 0;
        int[] filledPos = null;
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                char p = prev[r][f] == 0 ? '.' : prev[r][f];
                char c = curr[r][f] == 0 ? '.' : curr[r][f];
                if (p == c) continue;
                if (p != '.' && c == '.') {
                    if (emptiedCount == 0) emptiedPiece = p;
                    emptiedCount++;
                } else if (p == '.' && c != '.') {
                    filledCount++;
                    filledPos = new int[]{r, f};
                } else {
                    if (Character.isUpperCase(p) == Character.isUpperCase(c) && p != c) {
                        if (emptiedCount == 0 && filledCount == 0) {
                            curr[r][f] = p;
                            return curr;
                        }
                    }
                }
            }
        }
        if (emptiedCount == 1 && filledCount == 1 && filledPos != null) {
            char arrived = curr[filledPos[0]][filledPos[1]];
            if (Character.isUpperCase(arrived) == Character.isUpperCase(emptiedPiece)
                    && arrived != emptiedPiece) {
                boolean isPromotion = Character.toLowerCase(emptiedPiece) == 'p'
                        && (filledPos[0] == 0 || filledPos[0] == 7);
                if (!isPromotion) {
                    curr[filledPos[0]][filledPos[1]] = emptiedPiece;
                }
            }
        }
        return curr;
    }

    private char[][] startingGridFor(Side userSide) {
        char[][] base = {
            {'r','n','b','q','k','b','n','r'},
            {'p','p','p','p','p','p','p','p'},
            {'.','.','.','.','.','.','.','.'},
            {'.','.','.','.','.','.','.','.'},
            {'.','.','.','.','.','.','.','.'},
            {'.','.','.','.','.','.','.','.'},
            {'P','P','P','P','P','P','P','P'},
            {'R','N','B','Q','K','B','N','R'},
        };
        if (userSide == Side.BLACK) {
            char[][] o = new char[8][8];
            for (int r=0;r<8;r++) for (int f=0;f<8;f++) o[r][f] = base[7-r][7-f];
            return o;
        }
        return base;
    }

    private int plyToMate(int cp) {
        return cp > 0 ? 100000 - cp : -(100000 + cp);
    }

    private String formatUci(String uci) {
        if (uci == null || uci.length() < 4) return uci == null ? "?" : uci;
        return uci.substring(0, 2) + "→" + uci.substring(2, 4)
                + (uci.length() > 4 ? "=" + Character.toUpperCase(uci.charAt(4)) : "");
    }

    private Rect boardBoundsOnScreen(BoardDetector.Board board) {
        return new Rect(
                Math.round(board.left * capToScreenX),
                Math.round(board.top  * capToScreenY),
                Math.round((board.left + board.side) * capToScreenX),
                Math.round((board.top  + board.side) * capToScreenY));
    }

    private void renderArrows(List<StockfishEngine.Pv> pvs, BoardDetector.Board board) {
        List<ArrowOverlayView.Arrow> out = new ArrayList<>();
        Side persp = classifier.bootstrapSide();
        float cellX = board.square * capToScreenX;
        float cellY = board.square * capToScreenY;
        float cellAvg = (cellX + cellY) * 0.5f;
        float ox = board.left * capToScreenX;
        float oy = board.top  * capToScreenY;

        for (int i = 0; i < pvs.size(); i++) {
            String uci = pvs.get(i).uci;
            if (uci == null || uci.length() < 4) continue;
            int fx = uci.charAt(0) - 'a', fy = 8 - (uci.charAt(1) - '0');
            int tx = uci.charAt(2) - 'a', ty = 8 - (uci.charAt(3) - '0');
            if (persp == Side.BLACK) {
                fx = 7 - fx; fy = 7 - fy; tx = 7 - tx; ty = 7 - ty;
            }
            float sx = ox + (fx + 0.5f) * cellX, sy = oy + (fy + 0.5f) * cellY;
            float ex = ox + (tx + 0.5f) * cellX, ey = oy + (ty + 0.5f) * cellY;
            String lbl = (i == 0 ? "★ " : "") + uci;
            float width = Math.max(8f, ARROW_WIDTHS[i] * Math.min(1.5f, cellAvg / 60f));
            out.add(new ArrowOverlayView.Arrow(
                    sx, sy, ex, ey, ARROW_COLORS[i], width, lbl));
        }
        OverlayService.pushArrows(out, boardBoundsOnScreen(board));
    }

    private void panelStatus(String s) {
        FloatingPanel p = OverlayService.panel();
        if (p != null) p.setStatus(s);
    }

    private void panelEval(String s) {
        FloatingPanel p = OverlayService.panel();
        if (p != null) p.setEval(s);
    }

    private void setupVirtualDisplay() {
        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE))
                .getDefaultDisplay().getRealMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        float maxEdge = Math.max(screenW, screenH);
        float scale = Math.min(1f, 720f / maxEdge);
        captureW = Math.round(screenW * scale);
        captureH = Math.round(screenH * scale);
        density  = dm.densityDpi;
        capToScreenX = (float) screenW / captureW;
        capToScreenY = (float) screenH / captureH;

        Log.i(TAG, "screen=" + screenW + "x" + screenH
                + " capture=" + captureW + "x" + captureH);

        reader = ImageReader.newInstance(captureW, captureH, PixelFormat.RGBA_8888, 4);
        virtualDisplay = projection.createVirtualDisplay(
                "OpenChepsVD", captureW, captureH, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, worker);
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buf = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int w = image.getWidth();
        int h = image.getHeight();
        int rowPadding = rowStride - pixelStride * w;
        int bitmapW = w + rowPadding / pixelStride;

        Bitmap raw = Bitmap.createBitmap(bitmapW, h, Bitmap.Config.ARGB_8888);
        buf.rewind();
        raw.copyPixelsFromBuffer(buf);
        if (bitmapW != w) {
            Bitmap cropped = Bitmap.createBitmap(raw, 0, 0, w, h);
            raw.recycle();
            return cropped;
        }
        return raw;
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
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .addAction(0, "Stop", stopPi)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(7, n);
        }
        startService(new Intent(this, com.opencheps.overlay.OverlayService.class));
    }

    @Override public void onDestroy() {
        if (worker != null) worker.removeCallbacksAndMessages(null);
        try { if (engine != null) engine.stop(); } catch (Exception ignored) {}
        if (virtualDisplay != null) virtualDisplay.release();
        if (reader != null) reader.close();
        if (projection != null) projection.stop();
        if (workerThread != null) workerThread.quitSafely();
        stopService(new Intent(this, com.opencheps.overlay.OverlayService.class));
        super.onDestroy();
    }
}
