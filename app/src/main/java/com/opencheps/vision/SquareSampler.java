package com.opencheps.vision;

import android.graphics.Bitmap;

public class SquareSampler {

    public static final int N = 16;

    public static class Sample {
        public final float[] feature;
        public final float meanR, meanG, meanB;
        public final float meanLum;
        public final float colorVar;
        Sample(float[] feature, float mr, float mg, float mb,
               float meanLum, float colorVar) {
            this.feature = feature;
            this.meanR = mr; this.meanG = mg; this.meanB = mb;
            this.meanLum = meanLum;
            this.colorVar = colorVar;
        }
    }

    public static Sample sample(Bitmap bmp, int cx, int cy, int rad) {
        int W = bmp.getWidth(), H = bmp.getHeight();
        int size = rad * 2;
        int x0 = Math.max(0, cx - rad);
        int y0 = Math.max(0, cy - rad);
        int x1 = Math.min(W, x0 + size);
        int y1 = Math.min(H, y0 + size);
        int w = x1 - x0, h = y1 - y0;
        if (w <= 0 || h <= 0) {
            return new Sample(new float[N * N * 3], 0.5f, 0.5f, 0.5f, 0.5f, 0f);
        }

        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, x0, y0, w, h);

        float[] feat = new float[N * N * 3];
        float sR = 0, sG = 0, sB = 0;
        int count = N * N;
        for (int y = 0; y < N; y++) {
            int sy = (y * h) / N;
            int rowBase = sy * w;
            for (int x = 0; x < N; x++) {
                int sx = (x * w) / N;
                int p = px[rowBase + sx];
                int i = (y * N + x) * 3;
                float rf = ((p >> 16) & 0xFF) / 255f;
                float gf = ((p >> 8)  & 0xFF) / 255f;
                float bf = ( p        & 0xFF) / 255f;
                feat[i] = rf; feat[i + 1] = gf; feat[i + 2] = bf;
                sR += rf; sG += gf; sB += bf;
            }
        }
        float mR = sR / count, mG = sG / count, mB = sB / count;
        float meanLum = 0.299f * mR + 0.587f * mG + 0.114f * mB;

        float var = 0;
        for (int i = 0; i < feat.length; i += 3) {
            float dr = feat[i]     - mR;
            float dg = feat[i + 1] - mG;
            float db = feat[i + 2] - mB;
            var += dr * dr + dg * dg + db * db;
        }
        var /= count;

        return new Sample(feat, mR, mG, mB, meanLum, var);
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

    public static float colorDistance(Sample a, Sample b) {
        float dr = a.meanR - b.meanR;
        float dg = a.meanG - b.meanG;
        float db = a.meanB - b.meanB;
        return dr * dr + dg * dg + db * db;
    }
}
