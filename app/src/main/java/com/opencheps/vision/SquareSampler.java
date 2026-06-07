package com.opencheps.vision;

import android.graphics.Bitmap;

public class SquareSampler {

    public static final int N = 20;

    public static class Sample {
        public final float[] feature;
        public final float meanR, meanG, meanB;
        public final float meanLum;
        public final float edgeEnergy;
        Sample(float[] feature, float mr, float mg, float mb,
               float meanLum, float edgeEnergy) {
            this.feature = feature;
            this.meanR = mr; this.meanG = mg; this.meanB = mb;
            this.meanLum = meanLum;
            this.edgeEnergy = edgeEnergy;
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
            return new Sample(new float[N * N], 0.5f, 0.5f, 0.5f, 0.5f, 0f);
        }

        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, x0, y0, w, h);

        float[] lumGrid = new float[N * N];
        float sR = 0, sG = 0, sB = 0;
        int count = N * N;
        for (int y = 0; y < N; y++) {
            int sy = (y * h) / N;
            int rowBase = sy * w;
            for (int x = 0; x < N; x++) {
                int sx = (x * w) / N;
                int p = px[rowBase + sx];
                float rf = ((p >> 16) & 0xFF) / 255f;
                float gf = ((p >> 8)  & 0xFF) / 255f;
                float bf = ( p        & 0xFF) / 255f;
                lumGrid[y * N + x] = 0.299f * rf + 0.587f * gf + 0.114f * bf;
                sR += rf; sG += gf; sB += bf;
            }
        }
        float mR = sR / count, mG = sG / count, mB = sB / count;
        float meanLum = 0.299f * mR + 0.587f * mG + 0.114f * mB;

        float edge = 0;
        for (int y = 1; y < N - 1; y++) {
            for (int x = 1; x < N - 1; x++) {
                float l = lumGrid[y * N + x];
                float dx = lumGrid[y * N + (x + 1)] - lumGrid[y * N + (x - 1)];
                float dy = lumGrid[(y + 1) * N + x] - lumGrid[(y - 1) * N + x];
                edge += dx * dx + dy * dy;
            }
        }
        edge /= (N - 2) * (N - 2);

        return new Sample(lumGrid, mR, mG, mB, meanLum, (float) Math.sqrt(edge));
    }

    public static float lumDistance(float[] a, float[] b) {
        float s = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            float d = a[i] - b[i];
            s += d * d;
        }
        return s;
    }
}
