package com.opencheps.vision;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class PieceClassifier {

    private static final String TAG = "OC/TFLite";
    private static final String MODEL_ASSET = "piece_classifier.tflite";
    private static final int INPUT = 32;
    private static final char[] LABELS = {
            '.', 'P', 'N', 'B', 'R', 'Q', 'K',
            'p', 'n', 'b', 'r', 'q', 'k'
    };

    private Interpreter interpreter;
    private boolean available;
    private final float[][][][] input = new float[1][INPUT][INPUT][3];
    private final float[][] output = new float[1][LABELS.length];

    public boolean tryLoad(Context ctx) {
        try {
            AssetFileDescriptor afd = ctx.getAssets().openFd(MODEL_ASSET);
            FileInputStream fis = new FileInputStream(afd.getFileDescriptor());
            FileChannel fc = fis.getChannel();
            MappedByteBuffer model = fc.map(FileChannel.MapMode.READ_ONLY,
                    afd.getStartOffset(), afd.getDeclaredLength());
            Interpreter.Options opt = new Interpreter.Options();
            opt.setNumThreads(2);
            interpreter = new Interpreter(model, opt);
            available = true;
            Log.i(TAG, "TFLite piece classifier loaded");
        } catch (Throwable t) {
            Log.i(TAG, "No TFLite model present, using heuristic fallback: " + t.getMessage());
            available = false;
        }
        return available;
    }

    public boolean isAvailable() { return available; }

    public char classify(Bitmap board, int cx, int cy, int rad) {
        if (!available) return '?';
        int W = board.getWidth(), H = board.getHeight();
        int x0 = Math.max(0, cx - rad), y0 = Math.max(0, cy - rad);
        int x1 = Math.min(W, cx + rad), y1 = Math.min(H, cy + rad);
        Bitmap crop = Bitmap.createBitmap(x1 - x0, y1 - y0, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(crop);
        c.drawBitmap(board, new Rect(x0, y0, x1, y1),
                new Rect(0, 0, crop.getWidth(), crop.getHeight()), null);
        Bitmap scaled = Bitmap.createScaledBitmap(crop, INPUT, INPUT, true);
        for (int y = 0; y < INPUT; y++) {
            for (int x = 0; x < INPUT; x++) {
                int p = scaled.getPixel(x, y);
                input[0][y][x][0] = Color.red(p)   / 255f;
                input[0][y][x][1] = Color.green(p) / 255f;
                input[0][y][x][2] = Color.blue(p)  / 255f;
            }
        }
        crop.recycle();
        scaled.recycle();
        try {
            interpreter.run(input, output);
        } catch (Throwable t) {
            Log.w(TAG, "TFLite run failed: " + t.getMessage());
            return '?';
        }
        int best = 0;
        float bestV = output[0][0];
        for (int i = 1; i < LABELS.length; i++) {
            if (output[0][i] > bestV) { bestV = output[0][i]; best = i; }
        }
        return LABELS[best];
    }

    public void close() {
        if (interpreter != null) interpreter.close();
        interpreter = null;
        available = false;
    }

    public static ByteBuffer allocBuffer(int n) {
        ByteBuffer b = ByteBuffer.allocateDirect(n * 4);
        b.order(ByteOrder.nativeOrder());
        return b;
    }
}
