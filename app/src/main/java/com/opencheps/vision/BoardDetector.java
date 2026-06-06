package com.opencheps.vision;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import com.opencheps.util.Side;

public class BoardDetector {

    private static final String TAG = "OC/Vision";

    public static class Detected {
        public final String fen;
        public final float boardLeft, boardTop, squarePx;
        public final Side detectedSide;
        public Detected(String fen, float l, float t, float sq, Side side) {
            this.fen = fen; this.boardLeft = l; this.boardTop = t; this.squarePx = sq;
            this.detectedSide = side;
        }
    }

    private PieceClassifier classifier;

    public void setClassifier(PieceClassifier c) { this.classifier = c; }

    public Detected detect(Bitmap bmp, Side userSide) {
        if (bmp == null) return null;
        int W = bmp.getWidth(), H = bmp.getHeight();

        int side = Math.min(W, H);
        int boardSide = (int) (side * 0.95f);
        int left = (W - boardSide) / 2;
        int top  = (H - boardSide) / 2;

        int[] colEdges = edgeProjection(bmp, true);
        int[] rowEdges = edgeProjection(bmp, false);
        int[] colBounds = strongestRun(colEdges, boardSide);
        int[] rowBounds = strongestRun(rowEdges, boardSide);
        if (colBounds != null) { left = colBounds[0]; boardSide = colBounds[1] - colBounds[0]; }
        if (rowBounds != null) { top  = rowBounds[0]; }

        float sq = boardSide / 8f;
        if (sq < 16f) {
            Log.d(TAG, "board too small (" + sq + " px/sq) — skip");
            return null;
        }

        char[][] grid = new char[8][8];
        boolean haveModel = classifier != null && classifier.isAvailable();
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                int cx = (int) (left + (f + 0.5f) * sq);
                int cy = (int) (top  + (r + 0.5f) * sq);
                if (haveModel) {
                    char c = classifier.classify(bmp, cx, cy, (int) (sq * 0.45f));
                    grid[r][f] = c == '?' ? heuristic(bmp, cx, cy, (int) (sq * 0.32f)) : c;
                } else {
                    grid[r][f] = heuristic(bmp, cx, cy, (int) (sq * 0.32f));
                }
            }
        }

        Side actual = inferOrientation(grid, userSide, haveModel);
        if (actual == Side.BLACK) grid = flip(grid);

        String fen = haveModel ? toRealFen(grid, actual) : toApproxFen(grid, actual);
        return new Detected(fen, left, top, sq, actual);
    }

    private Side inferOrientation(char[][] g, Side fallback, boolean haveModel) {
        if (!haveModel) return fallback;
        int topWhite = 0, topBlack = 0, botWhite = 0, botBlack = 0;
        for (int f = 0; f < 8; f++) {
            char a = g[0][f], b = g[7][f];
            if (Character.isUpperCase(a)) topWhite++;
            if (Character.isLowerCase(a)) topBlack++;
            if (Character.isUpperCase(b)) botWhite++;
            if (Character.isLowerCase(b)) botBlack++;
        }
        if (topBlack + botWhite > topWhite + botBlack) return Side.WHITE;
        if (topWhite + botBlack > topBlack + botWhite) return Side.BLACK;
        return fallback;
    }

    private char heuristic(Bitmap bmp, int cx, int cy, int rad) {
        int W = bmp.getWidth(), H = bmp.getHeight();
        int x0 = Math.max(0, cx - rad), y0 = Math.max(0, cy - rad);
        int x1 = Math.min(W, cx + rad), y1 = Math.min(H, cy + rad);
        long lumSum = 0, n = 0;
        int veryDark = 0, veryBright = 0;
        for (int y = y0; y < y1; y += 2) {
            for (int x = x0; x < x1; x += 2) {
                int p = bmp.getPixel(x, y);
                int lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000;
                lumSum += lum; n++;
                if (lum < 60)  veryDark++;
                if (lum > 200) veryBright++;
            }
        }
        if (n == 0) return '.';
        int corner = bmp.getPixel(Math.max(0, x0 + 2), Math.max(0, y0 + 2));
        int cornerLum = (Color.red(corner) * 299 + Color.green(corner) * 587 + Color.blue(corner) * 114) / 1000;
        int centreLum = (int) (lumSum / n);
        int delta = Math.abs(centreLum - cornerLum);
        if (delta < 18 && veryDark < n * 0.12 && veryBright < n * 0.12) return '.';
        return veryDark > veryBright ? 'p' : 'P';
    }

    private char[][] flip(char[][] g) {
        char[][] o = new char[8][8];
        for (int r = 0; r < 8; r++)
            for (int f = 0; f < 8; f++) o[r][f] = g[7 - r][7 - f];
        return o;
    }

    private String toRealFen(char[][] g, Side stm) {
        StringBuilder sb = new StringBuilder();
        boolean haveWK = false, haveBK = false;
        for (int r = 0; r < 8; r++) {
            int run = 0;
            for (int f = 0; f < 8; f++) {
                char c = g[r][f];
                if (c == 'K') haveWK = true;
                if (c == 'k') haveBK = true;
                if (c == '.' || c == 0) { run++; continue; }
                if (run > 0) { sb.append(run); run = 0; }
                sb.append(c);
            }
            if (run > 0) sb.append(run);
            if (r < 7) sb.append('/');
        }
        if (!haveWK || !haveBK) return toApproxFen(g, stm);
        String s = stm == Side.WHITE ? "w" : "b";
        return sb.toString() + " " + s + " - - 0 1";
    }

    private String toApproxFen(char[][] g, Side stm) {
        for (int f = 0; f < 8; f++) {
            if (g[0][f] == 'p' || g[0][f] == 'P') g[0][f] = 'k';
            if (g[7][f] == 'p' || g[7][f] == 'P') g[7][f] = 'K';
        }
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            int run = 0;
            for (int f = 0; f < 8; f++) {
                char c = g[r][f];
                if (c == '.' || c == 0) { run++; continue; }
                if (run > 0) { sb.append(run); run = 0; }
                sb.append(c);
            }
            if (run > 0) sb.append(run);
            if (r < 7) sb.append('/');
        }
        if (sb.indexOf("K") < 0) sb.setCharAt(0, 'K');
        if (sb.indexOf("k") < 0) sb.append("/k7");
        String s = stm == Side.WHITE ? "w" : "b";
        return sb.toString() + " " + s + " - - 0 1";
    }

    private int[] edgeProjection(Bitmap bmp, boolean vertical) {
        int W = bmp.getWidth(), H = bmp.getHeight();
        int len = vertical ? W : H;
        int[] sums = new int[len];
        int step = 4;
        for (int y = 0; y < H - step; y += step) {
            for (int x = 0; x < W - step; x += step) {
                int p0 = bmp.getPixel(x, y);
                int p1 = vertical ? bmp.getPixel(x, y + step) : bmp.getPixel(x + step, y);
                int diff = Math.abs(Color.red(p0) - Color.red(p1))
                         + Math.abs(Color.green(p0) - Color.green(p1))
                         + Math.abs(Color.blue(p0) - Color.blue(p1));
                sums[vertical ? x : y] += diff;
            }
        }
        return sums;
    }

    private int[] strongestRun(int[] sig, int approxLen) {
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
