package com.opencheps.vision;

import android.graphics.Bitmap;
import android.graphics.Color;

public class SquareSampler {

    public static final int N = 20;

    public static float[] sample(Bitmap bmp, int cx, int cy, int rad) {
        float[] feat = new float[N * N * 3];
        int W = bmp.getWidth(), H = bmp.getHeight();
        int size = rad * 2;
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                int sx = cx - rad + (x * size) / N;
                int sy = cy - rad + (y * size) / N;
                if (sx < 0) sx = 0; if (sx >= W) sx = W - 1;
                if (sy < 0) sy = 0; if (sy >= H) sy = H - 1;
                int p = bmp.getPixel(sx, sy);
                int i = (y * N + x) * 3;
                feat[i]     = Color.red(p)   / 255f;
                feat[i + 1] = Color.green(p) / 255f;
                feat[i + 2] = Color.blue(p)  / 255f;
            }
        }
        return feat;
    }

    public static float distance(float[] a, float[] b) {
        float s = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            float d = a[i] - b[i];
            s += d * d;
        }
        return s;
    }

    public static float meanLum(float[] f) {
        float s = 0;
        int count = f.length / 3;
        for (int i = 0; i < f.length; i += 3) {
            s += 0.299f * f[i] + 0.587f * f[i + 1] + 0.114f * f[i + 2];
        }
        return s / count;
    }

    public static float[] subtract(float[] f, float bgLum) {
        float[] o = new float[f.length];
        for (int i = 0; i < f.length; i++) o[i] = f[i] - bgLum;
        return o;
    }
}
