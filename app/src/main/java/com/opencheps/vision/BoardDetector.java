package com.opencheps.vision;

import android.graphics.Bitmap;
import android.graphics.Color;

public class BoardDetector {

    public static class Board {
        public final int left, top, side;
        public final float square;
        public Board(int left, int top, int side) {
            this.left = left; this.top = top; this.side = side;
            this.square = side / 8f;
        }
    }

    public static Board locate(Bitmap bmp) {
        if (bmp == null) return null;
        int W = bmp.getWidth(), H = bmp.getHeight();
        int[] colEdges = projection(bmp, true);
        int[] rowEdges = projection(bmp, false);

        int bestSide = -1, bestX = 0, bestY = 0;
        double bestScore = -1;
        int maxSide = Math.min(W, H);
        for (int s = (int) (maxSide * 0.55); s <= maxSide * 0.98; s += Math.max(8, s / 30)) {
            int[] colWin = strongestRun(colEdges, s);
            int[] rowWin = strongestRun(rowEdges, s);
            if (colWin == null || rowWin == null) continue;
            double periodScore = periodicity(colEdges, colWin[0], s)
                              + periodicity(rowEdges, rowWin[0], s);
            if (periodScore > bestScore) {
                bestScore = periodScore;
                bestSide = s;
                bestX = colWin[0];
                bestY = rowWin[0];
            }
        }
        if (bestSide < 64) return null;
        return new Board(bestX, bestY, bestSide);
    }

    private static double periodicity(int[] sig, int start, int span) {
        double sum = 0;
        for (int k = 1; k < 8; k++) {
            int p = start + (int) Math.round(span * k / 8.0);
            int lo = Math.max(start, p - 4), hi = Math.min(start + span, p + 4);
            int peak = 0;
            for (int i = lo; i < hi && i < sig.length; i++) peak = Math.max(peak, sig[i]);
            sum += peak;
        }
        return sum;
    }

    private static int[] projection(Bitmap bmp, boolean vertical) {
        int W = bmp.getWidth(), H = bmp.getHeight();
        int len = vertical ? W : H;
        int[] sums = new int[len];
        int step = 4;
        for (int y = 0; y < H - step; y += step) {
            for (int x = 0; x < W - step; x += step) {
                int p0 = bmp.getPixel(x, y);
                int p1 = vertical ? bmp.getPixel(x, y + step) : bmp.getPixel(x + step, y);
                int diff = Math.abs(Color.red(p0)   - Color.red(p1))
                         + Math.abs(Color.green(p0) - Color.green(p1))
                         + Math.abs(Color.blue(p0)  - Color.blue(p1));
                sums[vertical ? x : y] += diff;
            }
        }
        return sums;
    }

    private static int[] strongestRun(int[] sig, int approxLen) {
        if (sig.length < approxLen) return null;
        long best, window = 0;
        int bestStart = 0;
        for (int i = 0; i < approxLen; i++) window += sig[i];
        best = window;
        for (int i = approxLen; i < sig.length; i++) {
            window += sig[i] - sig[i - approxLen];
            if (window > best) { best = window; bestStart = i - approxLen + 1; }
        }
        return new int[]{ bestStart, bestStart + approxLen };
    }
}
